[#ftl strict_vars=true]
[#--
/* Copyright (c) 2008-2021 Jonathan Revusky, revusky@javacc.com
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notices,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
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

 [#--
    This template generates the XXXLexer.java class.
    The details of generating the code for the NFA state machine
    are in the imported template NfaCode.java.ftl
 --]

[#import "NfaCode.java.ftl" as nfa]
[#var lexerData=grammar.lexerData]
[#var tokenBuilderClass = grammar.hugeFileSupport?string("TokenBuilder", "FileLineMap")]
[#var lexerData=grammar.lexerData]
[#var multipleLexicalStates = lexerData.lexicalStates.size()>1]

[#macro EnumSet varName tokenNames]
   [#if tokenNames?size=0]
       static private final EnumSet<TokenType> ${varName} = EnumSet.noneOf(TokenType.class);
   [#else]
       static final EnumSet<TokenType> ${varName} = EnumSet.of(
       [#list tokenNames as type]
          [#if type_index > 0],[/#if]
          TokenType.${type} 
       [/#list]
     ); 
   [/#if]
[/#macro]

[#list grammar.parserCodeImports as import]
   ${import}
[/#list]

import java.io.Reader;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.*;
import java.util.function.ToIntBiFunction;

public class ${grammar.lexerClassName} implements ${grammar.constantsClassName} {

  private Token matchedToken;
  private TokenType matchedType;
  private String inputSource = "input";
 [#if grammar.lexerUsesParser]
  public ${grammar.parserClassName} parser;
 [/#if]

  // A lookup for lexical state transitions triggered by a certain token type
  private static EnumMap<TokenType, LexicalState> tokenTypeToLexicalStateMap = new EnumMap<>(TokenType.class);
  // Token types that are "regular" tokens that participate in parsing,
  // i.e. declared as TOKEN
  [@EnumSet "regularTokens" lexerData.tokenSet.tokenNames /]
  // Token types that do not participate in parsing, a.k.a. "special" tokens in legacy JavaCC,
  // i.e. declared as UNPARSED (or SPECIAL_TOKEN)
  [@EnumSet "unparsedTokens" lexerData.specialSet.tokenNames /]
  // Tokens that are skipped, i.e. SKIP
  [@EnumSet "skippedTokens" lexerData.skipSet.tokenNames /]
  // Tokens that correspond to a MORE, i.e. that are pending 
  // additional input
  [@EnumSet "moreTokens" lexerData.moreSet.tokenNames /]

  private static final Logger LOGGER = Logger.getLogger("${grammar.parserClassName}");
    [#if grammar.debugLexer]  
  private boolean trace_enabled = true;
    [#else]  
  private boolean trace_enabled = false;
    [/#if]
    
  private void setTracingEnabled(boolean trace_enabled) {
     this.trace_enabled = trace_enabled;
  }

  public String getInputSource() {
      return inputSource;
  }
  
  public void setInputSource(String inputSource) {
      this.inputSource = inputSource;
[#if !grammar.hugeFileSupport]
      input_stream.setInputSource(inputSource);
[/#if]            
  }
   
[#if !grammar.hugeFileSupport]
     public ${grammar.lexerClassName}(CharSequence chars) {
        this("input", chars);
     }

     public ${grammar.lexerClassName}(String inputSource, CharSequence chars) {
        this(inputSource, chars, LexicalState.${lexerData.lexicalStates[0].name}, 1, 1);
     }
     public ${grammar.lexerClassName}(String inputSource, CharSequence chars, LexicalState lexState, int line, int column) {
         this.inputSource = inputSource;
        input_stream = new ${tokenBuilderClass}(inputSource, chars, line, column);
        switchTo(lexState);
     }
[/#if]

    public ${grammar.lexerClassName}(Reader reader) {
       this("input", reader, LexicalState.${lexerData.lexicalStates[0].name}, 1, 1);
    }

    public ${grammar.lexerClassName}(String inputSource, Reader reader) {
       this(inputSource, reader, LexicalState.${lexerData.lexicalStates[0].name}, 1, 1);
    }

    public ${grammar.lexerClassName}(String inputSource, Reader reader, LexicalState lexState, int line, int column) {
        this.inputSource = inputSource;
        input_stream = new ${tokenBuilderClass}(inputSource, reader, line, column);
        switchTo(lexState);
    }
    
${tokenBuilderClass} input_stream;

public final void backup(int amount) {
    input_stream.backup(amount);
}

  LexicalState lexicalState = LexicalState.values()[0];
[#if multipleLexicalStates]
  boolean doLexicalStateSwitch(TokenType tokenType) {
       LexicalState newState = tokenTypeToLexicalStateMap.get(tokenType);
       if (newState == null) return false;
       return switchTo(newState);
  }
[/#if]
  
    /** 
     * Switch to specified lexical state. 
     * @param lexState the lexical state to switch to
     * @return whether we switched (i.e. we weren't already in the desired lexical state)
     */
    public boolean switchTo(LexicalState lexState) {
        if (this.lexicalState != lexState) {
           if (trace_enabled) LOGGER.info("Switching from lexical state " + this.lexicalState + " to " + lexState);
           this.lexicalState = lexState;
           return true;
        }
        return false;
    }
[#if grammar.legacyAPI]
    /**
      * @deprecated Use the switchTo method that takes an Enum
      */
    @Deprecated
    public boolean SwitchTo(int lexState) {
       return switchTo(LexicalState.values()[lexState]);
    }
    @Deprecated
    public void setTabSize(int  size) {this.tabSize = size;}
[/#if]
  private int tabSize =8;
  private InvalidToken invalidToken;
  private Token previousToken;

  /**
   * The public method for getting the next token.
   * Most of the work is done in the private method
   * nextToken, which invokes the NFA machinery
   */ 
  public Token getNextToken() {
      Token token = null;
      do {
          token = nextToken();
      } while (token instanceof InvalidToken);
      if (invalidToken != null) {
          invalidToken.setNextToken(token);
          token.setPreviousToken(invalidToken);
          Token it = invalidToken;
          this.invalidToken = null;
[#if grammar.faultTolerant]
          it.setUnparsed(true);
[/#if]
          return it;
      }
      token.setPreviousToken(previousToken);
      if (previousToken != null) previousToken.setNextToken(token);
      return previousToken = token;
 }
 
 [#if grammar.hugeFileSupport]
    [#embed "LegacyTokenBuilder.java.ftl"]
 [#else]
        // Reset the token source input
    // to just after the Token passed in.
    void reset(Token t, LexicalState state) {
        input_stream.goTo(t.getEndLine(), t.getEndColumn());
        input_stream.forward(1);
        t.setNext(null);
        t.setNextToken(null);
        if (state != null) {
            switchTo(state);
        }
    }

    void reset(Token t) {
        reset(t, null);
    }
    
    FileLineMap getFileLineMap() {
        return input_stream;
    }
 [/#if]
    
  private Token generateEOF() {
      matchedType = TokenType.EOF;
      Token eof = fillToken();
      tokenLexicalActions();
[#list grammar.lexerTokenHooks as tokenHookMethodName]
      [#if tokenHookMethodName != "CommonTokenAction"]
         eof =
      [/#if]
      ${tokenHookMethodName}(eof);
[/#list]
      return eof;
    }

  private InvalidToken handleInvalidChar(int ch) {
    int line = input_stream.getEndLine();
    int column = input_stream.getEndColumn();
    String img = new String(new int[] {ch}, 0, 1);
    if (invalidToken == null) {
       invalidToken = new InvalidToken(img, inputSource);
       invalidToken.setBeginLine(line);
       invalidToken.setBeginColumn(column);
    } else {
       invalidToken.setImage(invalidToken.getImage() + img);
    }
    invalidToken.setEndLine(line);
    invalidToken.setEndColumn(column);
    return invalidToken;
  }

  private void instantiateToken() {
      matchedToken = fillToken();
 [#list grammar.lexerTokenHooks as tokenHookMethodName]
    [#if tokenHookMethodName = "CommonTokenAction"]
      ${tokenHookMethodName}(matchedToken);
    [#else]
      matchedToken = ${tokenHookMethodName}(matchedToken);
    [/#if]
 [/#list]
      this.matchedType = matchedToken.getType();
      matchedToken.setUnparsed(unparsedTokens.contains(matchedType));
  }

  private void tokenLexicalActions() {
    switch(matchedType) {
   [#list lexerData.regularExpressions as regexp]
        [#if regexp.codeSnippet?has_content]
		  case ${regexp.label} :
		      ${regexp.codeSnippet.javaCode}
           break;
        [/#if]
   [/#list]
      default : break;
    }
  }

  private Token fillToken() {
        final String curTokenImage = input_stream.getImage();
        final int beginLine = input_stream.getBeginLine();
        final int beginColumn = input_stream.getBeginColumn();
        final int endLine = input_stream.getEndLine();
        final int endColumn = input_stream.getEndColumn();
    [#if grammar.settings.TOKEN_FACTORY??]
        final Token t = ${grammar.settings.TOKEN_FACTORY}.newToken(matchedType, curTokenImage, inputSource);
    [#elseif !grammar.hugeFileSupport]
        final Token t = Token.newToken(matchedType, curTokenImage, this);
    [#else]
        final Token t = Token.newToken(matchedType, curTokenImage, inputSource);
    [/#if]
        t.setBeginLine(beginLine);
        t.setEndLine(endLine);
        t.setBeginColumn(beginColumn);
        t.setEndColumn(endColumn);
//        t.setInputSource(this.inputSource);
        return t;
  }


[@nfa.GenerateMainLoop/]

[#--
  Outer loop to generate all the NFA (non-deterministic finite automaton)
  related code for all the various lexical states
--]    
[#list lexerData.lexicalStates as lexicalState]
  [@nfa.GenerateStateCode lexicalState/]
[/#list]
}
