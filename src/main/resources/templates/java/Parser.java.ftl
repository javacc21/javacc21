[#ftl strict_vars=true]
[#--
/* Copyright (c) 2008-2020 Jonathan Revusky, revusky@javacc.com
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provide that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notices,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary format must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name Jonathan Revusky, Sun Microsystems, Inc.
 *       nor the names of any contributors may be used to endorse 
 *       or promote products derived from this software without specific prior written 
 *       permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
 --]
/* Generated by: ${generated_by}. ${filename} */


[#var tokenCount=grammar.lexerData.tokenCount]

[#if grammar.parserPackage?has_content]
package ${grammar.parserPackage};
[/#if]

[#if grammar.nodePackage?has_content && grammar.parserPackage! != grammar.nodePackage]
import ${grammar.nodePackage}.*;  
[/#if]
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.logging.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
[#if grammar.parserPackage?has_content]
import static ${grammar.parserPackage}.${grammar.constantsClassName}.TokenType.*;
[/#if]

@SuppressWarnings("unused")
public class ${grammar.parserClassName} implements ${grammar.constantsClassName} {

    private static final java.util.logging.Logger LOGGER = Logger.getLogger(${grammar.parserClassName}.class.getName());
    
[#if grammar.debugParser||grammar.debugLexer||grammar.debugFaultTolerant]
     static {
         LOGGER.setLevel(Level.FINEST);
     }
[/#if]    

    public static void setLogLevel(Level level) {
        LOGGER.setLevel(level);
        Logger.getGlobal().getParent().getHandlers()[0].setLevel(level);
    }
static final int UNLIMITED = Integer.MAX_VALUE;    
// The last token successfully "consumed"     
Token lastConsumedToken = new Token(); // We start with a dummy token. REVISIT
private TokenType nextTokenType;
private Token currentLookaheadToken;
private int remainingLookahead;
private boolean scanToEnd, hitFailure, lastLookaheadSucceeded;
private String currentlyParsedProduction, currentLookaheadProduction;
private int lookaheadRoutineNesting;
private EnumSet<TokenType> outerFollowSet;

[#--
 REVISIT these.
//private Token nextToken; 
//private EnumSet<Token> currentFollowSet;
// private TokenType upToTokenType;
// private EnumSet<TokenType> upToFirstSet;
--]

private boolean cancelled;
public void cancel() {cancelled = true;}
public boolean isCancelled() {return cancelled;}
[#if grammar.userDefinedLexer]
  /** User defined Lexer. */
  public Lexer token_source;
  String inputSource = "input";
[#else]
  /** Generated Lexer. */
  public ${grammar.lexerClassName} token_source;
  
  public void setInputSource(String inputSource) {
      token_source.setInputSource(inputSource);
  }
  
[/#if]

  String getInputSource() {
      return token_source.getInputSource();
  }
  
 //=================================
 // Generated constructors
 //=================================

[#if !grammar.userDefinedLexer]
 [#if !grammar.hugeFileSupport]
   public ${grammar.parserClassName}(String inputSource, CharSequence content) {
       this(new ${grammar.lexerClassName}(inputSource, content));
      [#if grammar.lexerUsesParser]
      token_source.parser = this;
      [/#if]
  }

  public ${grammar.parserClassName}(CharSequence content) {
    this("input", content);
  }

  public ${grammar.parserClassName}(String inputSource, Path path) throws IOException {
    this(inputSource, FileLineMap.stringFromBytes(Files.readAllBytes(path)));
  }

  public ${grammar.parserClassName}(Path path) throws IOException {
    this(path.toString(), path);
  }
 [/#if]
  public ${grammar.parserClassName}(java.io.InputStream stream) {
      this(new InputStreamReader(stream));
  }
  
  public ${grammar.parserClassName}(Reader reader) {
    this(new ${grammar.lexerClassName}("input", reader));
      [#if grammar.lexerUsesParser]
      token_source.parser = this;
      [/#if]
  }

[/#if]

[#if grammar.userDefinedLexer]
  /** Constructor with user supplied Lexer. */
  public ${grammar.parserClassName}(Lexer lexer) {
[#else]
  /** Constructor with user supplied Lexer. */
  public ${grammar.parserClassName}(${grammar.lexerClassName} lexer) {
[/#if]
    token_source = lexer;
      [#if grammar.lexerUsesParser]
      token_source.parser = this;
      [/#if]
      lastConsumedToken.setInputSource(lexer.getInputSource());
  }

  // If tok already has a next field set, it returns that
  // Otherwise, it goes to the token_source, i.e. the Lexer.
  final private Token nextToken(final Token tok) {
    Token result = tok == null? null : tok.getNext();
[#if grammar.parserTokenHooks?size>0]    
    if (result != null) {
[#list grammar.parserTokenHooks as methodName] 
    result = ${methodName}(result);
[/#list]
    }
[/#if]    
    Token previous = null;
    while (result == null) {
      nextTokenType = null;
      Token next = token_source.getNextToken();
[#if grammar.legacyAPI]      
      if (previous != null && !(previous instanceof InvalidToken)) {
        next.setSpecialToken(previous);
      }
[/#if]      
      previous = next;
[#list grammar.parserTokenHooks as methodName] 
      next = ${methodName}(next);
[/#list]
      if (!next.isUnparsed()) {
        result = next;
      } else if (next instanceof InvalidToken) {
[#if grammar.faultTolerant]
        if (debugFaultTolerant) LOGGER.info("Skipping invalid text: " + next.getImage() + " at: " + next.getLocation());
[/#if]
        result = next.getNextToken();
      }
    }
    if (tok != null) tok.setNext(result);
    nextTokenType=null;
    return result;
  }

  /**
   * @return the next Token off the stream. This is the same as #getToken(1)
   */
  final public Token getNextToken() {
    return getToken(1);
  }

/**
 * @param index how many tokens to look ahead
 * @return the specific Token index ahead in the stream. 
 * If we are in a lookahead, it looks ahead from the currentLookaheadToken
 * Otherwise, it is the lastConsumedToken
 */
  final public Token getToken(int index) {
    Token t = currentLookaheadToken == null ? lastConsumedToken : currentLookaheadToken;
    for (int i = 0; i < index; i++) {
      t = nextToken(t);
    }
    return t;
  }

  private final TokenType nextTokenType() {
    if (nextTokenType == null) {
       nextTokenType = nextToken(lastConsumedToken).getType();
    }
    return nextTokenType;
  }

[#if !grammar.userDefinedLexer]  
  boolean activateTokenTypes(TokenType type, TokenType... types) {
    boolean result = token_source.activeTokenTypes.add(type);
    for (TokenType tt : types) {
      result |= token_source.activeTokenTypes.add(tt);
    }
  [#if !grammar.hugeFileSupport]
    if (result) {
      token_source.reset(getToken(0));
      nextTokenType = null;
    }
  [/#if]
    return result;
  }

  boolean deactivateTokenTypes(TokenType type, TokenType... types) {
    boolean result = token_source.activeTokenTypes.remove(type);
    for (TokenType tt : types) {
      result |= token_source.activeTokenTypes.remove(tt);
    }
  [#if !grammar.hugeFileSupport]
    if (result) {
      token_source.reset(getToken(0));
      nextTokenType = null;
    }
  [/#if]
    return result;
  }
[/#if]

  private void fail(String message) throws ParseException {
    if (currentLookaheadToken == null) {
      throw new ParseException(this, message);
    }
    this.hitFailure = true;
  }

  /**
   *Are we in the production of the given name, either scanning ahead or parsing?
   */
  private boolean isInProduction(String productionName, String... prods) {
    if (currentlyParsedProduction != null) {
      if (currentlyParsedProduction.equals(productionName)) return true;
      for (String name : prods) {
        if (currentlyParsedProduction.equals(name)) return true;
      }
    }
    if (currentLookaheadProduction != null ) {
      if (currentLookaheadProduction.equals(productionName)) return true;
      for (String name : prods) {
        if (currentLookaheadProduction.equals(name)) return true;
      }
    }
    Iterator<NonTerminalCall> it = stackIteratorBackward();
    while (it.hasNext()) {
      NonTerminalCall ntc = it.next();
      if (ntc.productionName.equals(productionName)) {
        return true;
      }
      for (String name : prods) {
        if (ntc.productionName.equals(name)) {
          return true;
        }
      }
    }
    return false;
  }


[#import "ParserProductions.java.ftl" as ParserCode]
[@ParserCode.Productions /]
[#import "LookaheadRoutines.java.ftl" as LookaheadCode]
[@LookaheadCode.Generate/]
 
[#embed "ErrorHandling.java.ftl"]

[#if grammar.treeBuildingEnabled]
   [#embed "TreeBuildingCode.java.ftl"]
[#else]
  public boolean isTreeBuildingEnabled() {
    return false;
  } 
[/#if]
}
  
}
[#list grammar.otherParserCodeDeclarations as decl]
//Generated from code at ${decl.location}
   ${decl}
[/#list]

