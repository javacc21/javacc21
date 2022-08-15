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

import java.util.Arrays;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.Charset;

[#if grammar.nodePackage?has_content && grammar.parserPackage! != grammar.nodePackage]
[#list grammar.nodeNames as node]
[#if node?index_of('.')>0]
   import ${node};
[#else]
   import ${grammar.nodePackage}.${node};
[/#if]
[/#list]

[/#if]
[#if grammar.parserPackage?has_content]
import static ${grammar.parserPackage}.${grammar.constantsClassName}.TokenType.*;
[/#if]

@SuppressWarnings("unused")
public class ${grammar.parserClassName} implements ${grammar.constantsClassName} {
static final int UNLIMITED = Integer.MAX_VALUE;    
// The last token successfully "consumed"
Token lastConsumedToken;
private TokenType nextTokenType;
private Token currentLookaheadToken;
private int remainingLookahead;
private boolean scanToEnd, hitFailure, lastLookaheadSucceeded;
private String currentlyParsedProduction, currentLookaheadProduction;
private int lookaheadRoutineNesting, nonTerminalNesting;
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
  /** Generated Lexer. */
  public ${grammar.lexerClassName} token_source;
  
  public void setInputSource(String inputSource) {
      token_source.setInputSource(inputSource);
  }
  
  String getInputSource() {
      return token_source.getInputSource();
  }
  
 //=================================
 // Generated constructors
 //=================================

   public ${grammar.parserClassName}(String inputSource, CharSequence content) {
       this(new ${grammar.lexerClassName}(inputSource, content));
      [#if grammar.lexerUsesParser]
      token_source.parser = this;
      [/#if]
  }

  public ${grammar.parserClassName}(CharSequence content) {
    this("input", content);
  }

  /**
   * @param inputSource just the name of the input source (typically the filename) that 
   * will be used in error messages and so on.
   * @param path The location (typically the filename) from which to get the input to parse
   */
  public ${grammar.parserClassName}(String inputSource, Path path) throws IOException {
    this(inputSource, ${grammar.lexerClassName}.stringFromBytes(Files.readAllBytes(path)));
  }

  public ${grammar.parserClassName}(String inputSource, Path path, Charset charset) throws IOException {
    this(inputSource, ${grammar.lexerClassName}.stringFromBytes(Files.readAllBytes(path), charset));
  }

  /**
   * @param path The location (typically the filename) from which to get the input to parse
   */
  public ${grammar.parserClassName}(Path path) throws IOException {
    this(path.toString(), path);
  }

  /**
   * @Deprecated Use the constructor that takes a #java.nio.files.Path or just 
   * a String (i.e. CharSequence) directly.
   */
  public ${grammar.parserClassName}(java.io.InputStream stream) {
      this(new InputStreamReader(stream));
  }

  /**
   * @Deprecated Use the constructor that takes a #java.nio.files.Path or just 
   * a String (i.e. CharSequence) directly.
   */
  public ${grammar.parserClassName}(Reader reader) {
    this(new ${grammar.lexerClassName}("input", reader));
      [#if grammar.lexerUsesParser]
      token_source.parser = this;
      [/#if]
  }


  /** Constructor with user supplied Lexer. */
  public ${grammar.parserClassName}(${grammar.lexerClassName} lexer) {
    token_source = lexer;
      [#if grammar.lexerUsesParser]
      token_source.parser = this;
      [/#if]
      lastConsumedToken = lexer.DUMMY_START_TOKEN;
      lastConsumedToken.setTokenSource(lexer);
  }

  // If the next token is cached, it returns that
  // Otherwise, it goes to the token_source, i.e. the Lexer.
  final private Token nextToken(final Token tok) {
    Token result = token_source.getNextToken(tok);
    while (result.isUnparsed()) {
     [#list grammar.parserTokenHooks as methodName] 
      result = ${methodName}(result);
     [/#list]
      result = token_source.getNextToken(result);
    }
[#list grammar.parserTokenHooks as methodName] 
    result = ${methodName}(result);
[/#list]
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
 * @return the specific regular (i.e. parsed) Token index ahead/behind in the stream. 
 * If we are in a lookahead, it looks ahead from the currentLookaheadToken
 * Otherwise, it is the lastConsumedToken. If you pass in a negative
 * number it goes backward.
 */
  final public Token getToken(final int index) {
    Token t = currentLookaheadToken == null ? lastConsumedToken : currentLookaheadToken;
    for (int i = 0; i < index; i++) {
      t = nextToken(t);
    }
    for (int i = 0; i > index; i--) {
      t = t.getPrevious();
      if (t == null) break;
    }
    return t;
  }

  private String tokenImage(int n) {
     return getToken(n).getImage();
  }

  private boolean checkNextTokenImage(String img) {
    return tokenImage(1).equals(img);
  }

  private boolean checkNextTokenType(TokenType type) {
    return getToken(1).getType() == type;
  }

  private final TokenType nextTokenType() {
    if (nextTokenType == null) {
       nextTokenType = nextToken(lastConsumedToken).getType();
    }
    return nextTokenType;
  }

  boolean activateTokenTypes(TokenType... types) {
    boolean result = false;
    for (TokenType tt : types) {
      result |= token_source.activeTokenTypes.add(tt);
    }
    token_source.reset(getToken(0));
    nextTokenType = null;
    return result;
  }


  private void uncacheTokens() {
      token_source.reset(getToken(0));
  }

  private void resetTo(LexicalState state) {
    token_source.reset(getToken(0), state);
  }

  private void resetTo(Token tok, LexicalState state) {
    token_source.reset(tok, state);
  } 

  boolean deactivateTokenTypes(TokenType... types) {
    boolean result = false;
    for (TokenType tt : types) {
      result |= token_source.activeTokenTypes.remove(tt);
    }
    token_source.reset(getToken(0));
    nextTokenType = null;
    return result;
  }

  private void fail(String message) [#if grammar.useCheckedException] throws ParseException [/#if] 
  {
    if (currentLookaheadToken == null) {
      throw new ParseException(this, message);
    }
    this.hitFailure = true;
  }

  private static HashMap<TokenType[], EnumSet<TokenType>> enumSetCache = new HashMap<>();

  private static EnumSet<TokenType> tokenTypeSet(TokenType first, TokenType... rest) {
    TokenType[] key = new TokenType[1 + rest.length];

    key[0] = first;
    if (rest.length > 0) {
      System.arraycopy(rest, 0, key, 1, rest.length);
    }
    Arrays.sort(key);
    if (enumSetCache.containsKey(key)) {
      return enumSetCache.get(key);
    }
    EnumSet<TokenType> result = (rest.length == 0) ? EnumSet.of(first) : EnumSet.of(first, rest);
    enumSetCache.put(key, result);
    return result;
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

