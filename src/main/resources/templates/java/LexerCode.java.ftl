[#ftl strict_vars=true]
[#--
/* Copyright (c) 2008-2020 Jonathan Revusky, revusky@javacc.com
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
        This is the one remaining template file that is still a god-awful mess and, I (JR) have to admit 
        still quite opaque to me. The corresponding Java code is the stuff in com.javacc.lexgen package
        that, along with this template, will eventually be cleaned up, probably a more accurate 
        description is that it will be torn up and rewritten. 
  --]
 [#import "DfaCode.java.ftl" as dfa]
 [#import "NfaCode.java.ftl" as nfa]
 [#var lexerData=grammar.lexerData]
 [#var utils=grammar.utils]
 [#var tokenCount=lexerData.tokenCount]
 [#var numLexicalStates=lexerData.lexicalStates?size]
 [#var multipleLexicalStates = numLexicalStates>1]

[#var MAX_INT=2147483647]

   private int[] jjemptyLineNo = new int[${numLexicalStates}];
   private int[] jjemptyColNo = new int[${numLexicalStates}];
   private boolean[] jjbeenHere = new boolean[${numLexicalStates}];
  
  
  private int jjnewStateCnt;
  private int jjmatchedPos;
  //FIXME,should be an enum.
  private int jjmatchedKind;
  private TokenType matchedType;
  private String inputSource = "input";

 [#macro BitSetFromLongArray bitSet]
      BitSet.valueOf(new long[] {
          [#list bitSet.toLongArray() as long]
             ${utils.toHexStringL(long)}
             [#if long_has_next],[/#if]
          [/#list]
      })
[/#macro]
  
 
    static private final BitSet tokenSet = ${BitSetFromLongArray(lexerData.tokenSet)},
                                specialSet = ${BitSetFromLongArray(lexerData.specialSet)},
                                skipSet = ${BitSetFromLongArray(lexerData.skipSet)},
                                moreSet = ${BitSetFromLongArray(lexerData.moreSet)};

    private final int[] jjstateSet = new int[${2*lexerData.stateSetSize}];

    private final StringBuilder image = new StringBuilder();
    private int matchedCharsLength;

    private int curChar;
    
    private Token generateEOF() {
      if (trace_enabled) LOGGER.info("Returning the <EOF> token.");
	   jjmatchedKind = 0;
      matchedType = TokenType.EOF;
      Token eof = jjFillToken();
      tokenLexicalActions();
[#list grammar.lexerTokenHooks as tokenHookMethodName]
      [#if tokenHookMethodName != "CommonTokenAction"]
         eof =
      [/#if]
      ${tokenHookMethodName}(eof);
[/#list]
      return eof;
    }


  
  [#--  Need to figure out how to simplify this --]
  private Token nextToken() {
    Token matchedToken;
    int curPos = 0;

    EOFLoop :
    while (true) {
        curChar = input_stream.beginToken();
        if (curChar == -1) {
           return generateEOF();
        }
        else if (curChar >0xFFFF)  {
          // For now, I guess we'll treat any characters
          // beyond the BMP as invalid input.
          return handleInvalidChar(curChar);
        }
       image.setLength(0);
       matchedCharsLength = 0;

[#if lexerData.hasMore]
       while (true) {
[/#if]
    [#-- this also sets up the start state of the nfa --]
[#if multipleLexicalStates]
       switch(lexicalState) {
[/#if]
    
[#list lexerData.lexicalStates as lexicalState]
    [#if multipleLexicalStates]
            case ${lexicalState.name} : 
    [/#if]
    [@dfa.SkipSingles lexicalState.dfaData /]
    jjmatchedKind = 0x7FFFFFFF;
    matchedType = null;
    jjmatchedPos = 0;
    [#var debugOutput]
    [#set debugOutput]
        [#if multipleLexicalStates]
            "<" + lexicalState + ">" + 
        [/#if]
        [#-- REVISIT--]
        "Current character : " + addEscapes(String.valueOf(curChar)) + " (" + curChar + ") " +
        "at line " + input_stream.getEndLine() + " column " + input_stream.getEndColumn()
    [/#set]
    if (trace_enabled) LOGGER.info(${debugOutput?trim}); 
    curPos = jjMoveStringLiteralDfa0_${lexicalState.name}();
    [#if multipleLexicalStates]
        break;
    [/#if]
[/#list]
  [#if multipleLexicalStates]
      }
  [/#if]
  if (jjmatchedKind != 0x7FFFFFFF) { 
      if (jjmatchedPos + 1 < curPos) {
        if (trace_enabled) LOGGER.info("   Putting back " + (curPos - jjmatchedPos - 1) + " characters into the input stream.");
        input_stream.backup(curPos - jjmatchedPos - 1);
      }
       if (trace_enabled) LOGGER.info("****** FOUND A " + tokenImage[jjmatchedKind] + " MATCH ("
          + addEscapes(input_stream.getSuffix(jjmatchedPos + 2)) + ") ******\n");
 
       if (tokenSet.get(jjmatchedKind) || specialSet.get(jjmatchedKind)) {

         matchedToken = jjFillToken();
 [#list grammar.lexerTokenHooks as tokenHookMethodName]
      [#if tokenHookMethodName = "CommonTokenAction"]
         ${tokenHookMethodName}(matchedToken);
      [#else]
         matchedToken = ${tokenHookMethodName}(matchedToken);
      [/#if]
[/#list]
      tokenLexicalActions();
      jjmatchedKind = matchedToken.getType().ordinal();
 
 [#if multipleLexicalStates]
      if (newLexicalStates[jjmatchedKind] != null) {
[#--          matchedToken.setFollowingLexicalState(newLexicalStates[jjmatchedKind]);--]
          switchTo(newLexicalStates[jjmatchedKind]);
      }
 [/#if]
      matchedToken.setUnparsed(specialSet.get(jjmatchedKind));
      return matchedToken;

     }
         [#if lexerData.hasSkip || lexerData.hasSpecial]
            [#if lexerData.hasMore]
          else if (skipSet.get(jjmatchedKind))
            [#else]
          else
            [/#if]

          {
          [#if lexerData.hasSkipActions]
                 tokenLexicalActions();
          [/#if]
          [#if multipleLexicalStates]
            if (newLexicalStates[jjmatchedKind] != null) {
               this.lexicalState = newLexicalStates[jjmatchedKind];
            }
          [/#if]

            continue EOFLoop;
          }
         [#if lexerData.hasMore]
          [#if lexerData.hasMoreActions]
          tokenLexicalActions();
          [#else]
          matchedCharsLength += jjmatchedPos + 1;
		  [/#if]
		  
          [#if multipleLexicalStates]
             doLexicalStateSwitch(jjmatchedKind);
          [/#if]
          curPos = 0;
          jjmatchedKind = 0x7FFFFFFF;
          int retval = input_stream.readChar();
          if (retval >=0) {
               curChar = retval;
	
	            [#var debugOutput]
	            [#set debugOutput]
	              [#if multipleLexicalStates]
	                 "<" + lexicalState + ">" + 
	              [/#if]
                  [#-- REVISIT --]
	              "Current character : " + addEscapes(String.valueOf(curChar)) + " (" + curChar + ") " +
	              "at line " + input_stream.getEndLine() + " column " + input_stream.getEndColumn()
	            [/#set]
	              if (trace_enabled) LOGGER.info(${debugOutput?trim});
	          continue;
	      }
     [/#if]
   [/#if]
   }
    return handleInvalidChar(curChar);
[#if lexerData.hasMore]
    }
[/#if]
     }
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

  private void tokenLexicalActions() {
       switch(jjmatchedKind) {
   [#list lexerData.regularExpressions as regexp]
        [#if regexp.codeSnippet?has_content]
		  case ${regexp.ordinal} :
            [#if regexp.ordinal = 0]
              image.setLength(0); // For EOF no chars are matched
            [#else]
              image.append(input_stream.getSuffix(matchedCharsLength + jjmatchedPos + 1));
            [/#if]
		      ${regexp.codeSnippet.javaCode}
           break;
        [/#if]
   [/#list]
      }
    }

    private Token jjFillToken() {
        final Token t;
        final String curTokenImage;
        final int beginLine;
        final int endLine;
        final int beginColumn;
        final int endColumn;
        curTokenImage = input_stream.getImage();
        beginLine = input_stream.getBeginLine();
        beginColumn = input_stream.getBeginColumn();
        endLine = input_stream.getEndLine();
        endColumn = input_stream.getEndColumn();
    [#if grammar.settings.TOKEN_FACTORY??]
        t = ${grammar.settings.TOKEN_FACTORY}.newToken(TokenType.values()[jjmatchedKind], curTokenImage, inputSource);
    [#elseif !grammar.hugeFileSupport]
        t = Token.newToken(TokenType.values()[jjmatchedKind], curTokenImage, this);
    [#else]
        t = Token.newToken(TokenType.values()[jjmatchedKind], curTokenImage, inputSource);
    [/#if]
        t.setBeginLine(beginLine);
        t.setEndLine(endLine);
        t.setBeginColumn(beginColumn);
        t.setEndColumn(endColumn);
//        t.setInputSource(this.inputSource);
     [#if false]
        t.setLexicalState(lexicalState);
     [/#if]        
        return t;
    }

    private void jjCheckNAdd(int state) {
        jjstateSet[jjnewStateCnt++] = state;
    }
    
    private void jjAddStates(int start, int end) {
       do {
           jjstateSet[jjnewStateCnt++] = jjnextStates[start];
       }   while (start++ != end);
    }

    private void jjCheckNAddTwoStates(int state1, int state2) {
        jjCheckNAdd(state1);
        jjCheckNAdd(state2);
    }
    
    private void jjCheckNAddStates(int start, int end) {
        do {
            jjCheckNAdd(jjnextStates[start]);
        } while (start++ != end);
    }

    private void jjCheckNAddStates(int start) {
        jjCheckNAdd(jjnextStates[start]);
        jjCheckNAdd(jjnextStates[start + 1]);
    }

   
    private int jjStopAtPos(int pos, int kind) {
         jjmatchedKind = kind;
         jjmatchedPos = pos;
         if (trace_enabled) LOGGER.info("   No more string literal token matches are possible.");
         if (trace_enabled) LOGGER.info("   Currently matched the first " + (jjmatchedPos + 1) 
                            + " characters as a " + tokenImage[jjmatchedKind] + " token.");
         return pos + 1;
    }
    
[@nfa.OutputNfaStateMoves/]

[#list lexerData.lexicalStates as lexicalState]
  [#if lexicalState.nfaData.dumpNfaStarts]
  [@nfa.DumpNfaStartStatesCode lexicalState, lexicalState_index/]
  [/#if]
  [#if lexicalState.createStartNfa]
     [@nfa.DumpStartWithStates lexicalState/]
  [/#if]
   [@dfa.DumpDfaCode lexicalState/]
   [@nfa.DumpMoveNfa lexicalState/]
[/#list]

[#--
  NB. The following must occur after the preceding loop,
  since (and I don't like it) the DumpXXX macros
  build up the lexerData.orderedStateSet structure
  --]  
  private static final int[] jjnextStates = {
  [#list lexerData.orderedStateSet as set]
    [#list set as i]
        ${i},
    [/#list]
[/#list]
  };
