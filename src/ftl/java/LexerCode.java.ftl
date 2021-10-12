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

[#import "CommonUtils.java.ftl" as CU  ]

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
          ${CU.TT}${type} 
       [/#list]
     ); 
   [/#if]
[/#macro]

[#list grammar.parserCodeImports as import]
   ${import}
[/#list]

import java.io.*;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;

public class ${grammar.lexerClassName} implements ${grammar.constantsClassName} {

  private String inputSource = "input";
 [#if grammar.lexerUsesParser]
  public ${grammar.parserClassName} parser;
 [/#if]
  // The following two BitSets are used to store 
  // the current active NFA states in the core tokenization loop
  private BitSet nextStates=new BitSet(), currentStates = new BitSet();

  // Holder for the pending characters we read from the input stream
  private final StringBuilder charBuff = new StringBuilder();

  EnumSet<TokenType> activeTokenTypes = EnumSet.allOf(TokenType.class);
  [#if grammar.deactivatedTokens?size>0 || grammar.extraTokens?size >0]
     {
       [#list grammar.deactivatedTokens as token]
          activeTokenTypes.remove(${CU.TT}${token});
       [/#list]
       [#list grammar.extraTokens as token]
          regularTokens.add(${CU.TT}${token});
       [/#list]
     }
  [/#if]
[#--  
  // Holder for invalid characters, i.e. that cannot be matched as part of a token
  private final StringBuilder pendingInvalidChars = new StringBuilder();--]

  // Just used to "bookmark" the starting location for a token
  // for when we put in the location info at the end.
  private int tokenBeginLine, tokenBeginColumn;

[#if lexerData.hasLexicalStateTransitions]
  // A lookup for lexical state transitions triggered by a certain token type
  private static EnumMap<TokenType, LexicalState> tokenTypeToLexicalStateMap = new EnumMap<>(TokenType.class);
[/#if]
  // Token types that are "regular" tokens that participate in parsing,
  // i.e. declared as TOKEN
  [@EnumSet "regularTokens" lexerData.regularTokens.tokenNames /]
  // Token types that do not participate in parsing, a.k.a. "special" tokens in legacy JavaCC,
  // i.e. declared as UNPARSED (or SPECIAL_TOKEN)
  [@EnumSet "unparsedTokens" lexerData.unparsedTokens.tokenNames /]
  // Tokens that are skipped, i.e. SKIP
  [@EnumSet "skippedTokens" lexerData.skippedTokens.tokenNames /]
  // Tokens that correspond to a MORE, i.e. that are pending 
  // additional input
  [@EnumSet "moreTokens" lexerData.moreTokens.tokenNames /]

  private static final Logger LOGGER = Logger.getLogger(${grammar.constantsClassName}.class.getName());
    [#if grammar.debugLexer]  
  private boolean trace_enabled = true;
    [#else]  
  private boolean trace_enabled = false;
    [/#if]
  private int tabSize =8;
  private InvalidToken invalidToken;
  Token previousToken;
  // The source of the raw characters that we are scanning  
  ${tokenBuilderClass} input_stream;
    
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
      return previousToken = token;
 }

// The main method to invoke the NFA machinery
   private final Token nextToken() {
      Token matchedToken = null;
      boolean inMore = false;
      int matchedPos, charsRead, curChar;
      // The core tokenization loop
      while (matchedToken == null) {
        TokenType matchedType = null;
        matchedPos = charsRead = 0;
        if (inMore) {
            curChar = input_stream.readChar();
            if (curChar >= 0) charBuff.appendCodePoint(curChar);
        }
        else {
            charBuff.setLength(0);
[#-- The following is a temporary kludge. Need to rewrite the LegacyTokenBuilder
     that the hugeFileSupport option uses. 
     It's surely broken in various ways, like wrt full Unicode etc.--]            
[#if grammar.hugeFileSupport]            
            curChar = input_stream.beginToken();
            tokenBeginLine = input_stream.getBeginLine();
            tokenBeginColumn = input_stream.getBeginColumn();
[#else]
            tokenBeginLine = input_stream.getLine();
            tokenBeginColumn = input_stream.getColumn();
            curChar = input_stream.readChar();
[/#if]
            if (trace_enabled) 
                LOGGER.info("Starting new token on line: " + tokenBeginLine + ", column: " + tokenBeginColumn);
            if (curChar == -1) {
              if (trace_enabled) 
                LOGGER.info("Reached end of input");
              matchedType = TokenType.EOF;
            }
            else {
              if (trace_enabled) 
                LOGGER.info("Read character " + ${grammar.constantsClassName}.displayChar(curChar));
              charBuff.appendCodePoint(curChar);
            }
        } 
      [#if multipleLexicalStates]
       // Get the NFA function table current lexical state
       // There is some possibility that there was a lexical state change
       // since the last iteration of this loop!
      [/#if]
        ${grammar.nfaDataClassName}.NfaFunction[] nfaFunctions= ${grammar.nfaDataClassName}.getFunctionTableMap(lexicalState);
        // the core NFA loop
        if (matchedType != TokenType.EOF) do {
            // Holder for the new type (if any) matched on this iteration
            TokenType newType = null;
            if (charsRead > 0) {
                // What was nextStates on the last iteration 
                // is now the currentStates!
                BitSet temp = currentStates;
                currentStates = nextStates;
                nextStates = temp;
                int retval = input_stream.readChar();
                if (trace_enabled) LOGGER.info("Read character " + ${grammar.constantsClassName}.displayChar(retval));
                if (retval >=0) {
                    curChar = retval;
                    charBuff.appendCodePoint(curChar);
                }
                else break;
            }
            nextStates.clear();
            if (charsRead == 0) {
                TokenType returnedType = nfaFunctions[0].apply(curChar, nextStates, activeTokenTypes);
                if (returnedType != null && (newType == null || returnedType.ordinal() < newType.ordinal())) {
                  newType = returnedType;
                  if (trace_enabled) 
                    LOGGER.info("Potential match: " + newType);
                } 
            } else {
                int nextActive = currentStates.nextSetBit(0);
                while (nextActive != -1) {
                    TokenType returnedType = nfaFunctions[nextActive].apply(curChar, nextStates, activeTokenTypes);
                    if (returnedType != null && (newType == null || returnedType.ordinal() < newType.ordinal())) {
                      newType = returnedType;
                      if (trace_enabled) 
                         LOGGER.info("Potential match: " + newType);
                    }
                    nextActive = currentStates.nextSetBit(nextActive+1);
                } 
            }
            ++charsRead;
            if (newType != null) {
                matchedType = newType;
                inMore = moreTokens.contains(matchedType);
                matchedPos = charsRead;
            }
        } while (!nextStates.isEmpty());
        if (matchedType == null) {
            backup(charsRead-1);
            if (trace_enabled) 
               LOGGER.info("Invalid input: " + ${grammar.constantsClassName}.displayChar(charBuff.codePointAt(0)));
            return handleInvalidChar(charBuff.codePointAt(0));
        } else {
          if (trace_enabled)
              LOGGER.info("Matched pattern of type: " + matchedType + ": " + ${grammar.constantsClassName}.addEscapes(charBuff.toString()));
        }
        if (charsRead > matchedPos) backup(charsRead-matchedPos);
        if (regularTokens.contains(matchedType) || unparsedTokens.contains(matchedType)) {
            matchedToken = instantiateToken(matchedType);            
        }
     [#if lexerData.hasTokenActions]
        matchedToken = tokenLexicalActions(matchedToken, matchedType);
     [/#if]
     [#if multipleLexicalStates]
        doLexicalStateSwitch(matchedType);
     [/#if]
      }
      return matchedToken;
   }

  public final void backup(int amount) {
    input_stream.backup(amount);
    truncateCharBuff(charBuff, amount);
  }

  /**
   * Truncate a StringBuilder by a certain number of code points
   * @param buf the StringBuilder
   * @param amount the number of code points to truncate
   */
  static final void truncateCharBuff(StringBuilder buf, int amount) {
    int idx = buf.length();
    if (idx <= amount) idx = 0;
    while (idx > 0 && amount-- > 0) {
      char ch = buf.charAt(--idx);
      if (Character.isLowSurrogate(ch)) --idx;
    }
    buf.setLength(idx);
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

  private Token instantiateToken(TokenType type) {
    String tokenImage = charBuff.toString();
    [#if grammar.settings.TOKEN_FACTORY??]
        Token matchedToken = ${grammar.settings.TOKEN_FACTORY}.newToken(type, tokenImage, inputSource);
    [#elseif !grammar.hugeFileSupport]
        Token matchedToken = Token.newToken(type, tokenImage, this);
    [#else]
        Token matchedToken = Token.newToken(type, tokenImage, inputSource);
    [/#if]
        matchedToken.setBeginLine(tokenBeginLine);
        matchedToken.setEndLine(input_stream.getEndLine());
        matchedToken.setBeginColumn(tokenBeginColumn);
        matchedToken.setEndColumn(input_stream.getEndColumn());
        matchedToken.setInputSource(this.inputSource);
        if (previousToken != null) {
            matchedToken.setPreviousToken(this.previousToken);
            previousToken.setNextToken(matchedToken);
        }
        matchedToken.setUnparsed(unparsedTokens.contains(type));
 [#list grammar.lexerTokenHooks as tokenHookMethodName]
    [#if tokenHookMethodName = "CommonTokenAction"]
      ${tokenHookMethodName}(matchedToken);
    [#else]
      matchedToken = ${tokenHookMethodName}(matchedToken);
    [/#if]
 [/#list]
      return matchedToken;
  }

 [#if lexerData.hasTokenActions]
  private Token tokenLexicalActions(Token matchedToken, TokenType matchedType) {
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
    return matchedToken;
  }
 [/#if]

 [#if lexerData.hasLexicalStateTransitions]
  // Generate the map for lexical state transitions from the various token types
  static {
    [#list grammar.lexerData.regularExpressions as regexp]
      [#if !regexp.newLexicalState?is_null]
          tokenTypeToLexicalStateMap.put(TokenType.${regexp.label},LexicalState.${regexp.newLexicalState.name});
      [/#if]
    [/#list]
  }
 [/#if]
}
