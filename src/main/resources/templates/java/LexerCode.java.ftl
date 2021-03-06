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
  private int jjround;
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

    static private final int STATE_SET_SIZE = ${lexerData.stateSetSize};

    private final int[] jjrounds = new int[STATE_SET_SIZE];
    private final int[] jjstateSet = new int[2*STATE_SET_SIZE];

    private final StringBuilder image = new StringBuilder();
    private int matchedCharsLength;

    int curChar;
    
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
    int error_line = input_stream.getEndLine();
    int error_column = input_stream.getEndColumn();
    String error_after = null;
    error_after = curPos <= 1 ? "" : input_stream.getImage();
    StringBuilder sb = new StringBuilder();
    sb.appendCodePoint(curChar);
    if (invalidToken == null) {
       invalidToken = new InvalidToken(sb.toString(), inputSource);
       invalidToken.setBeginLine(error_line);
       invalidToken.setBeginColumn(error_column);
    } else {
       invalidToken.setImage(invalidToken.getImage() + sb.toString());
    }
    invalidToken.setEndLine(error_line);
    invalidToken.setEndColumn(error_column);
    return invalidToken;
[#if lexerData.hasMore]
    }
[/#if]
     }
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
        if (jjrounds[state] != jjround) {
            jjstateSet[jjnewStateCnt++] = state;
            jjrounds[state] = jjround;
        }
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
    
    
[#list lexerData.nonAsciiTableForMethod as nfaState]

	private static boolean jjCanMove_${nfaState.nonAsciiMethod}
	   (int hiByte, int i1, int i2, long l1, long l2) {
	
	   switch(hiByte) {
	   [#list nfaState.loByteVec as kase]
	       [#if kase_index%2 = 0]       
	      case ${kase} :
	          return (jjbitVec${nfaState.loByteVec[kase_index+1]}[i2] &l2) != 0L;
	       [/#if]
	   [/#list]
	   	  default : 
	   [#if nfaState.nonAsciiMoveIndices?has_content]
	       [#var j=nfaState.nonAsciiMoveIndices?size] 
	       [#list 1..10000 as xxx]
	   	     if ((jjbitVec${nfaState.nonAsciiMoveIndices[j-2]}[i1] & l1) != 0L) {
	   	        return (jjbitVec${nfaState.nonAsciiMoveIndices[j-1]}[i2] & l2) != 0L;
	   	     }
		      [#set j = j-2]
		      [#if j = 0][#break][/#if]
	   	   [/#list]
	    [/#if]
	   	       return false;
	   }		
	}
		
[/#list]
    
    private int jjStopAtPos(int pos, int kind) {
         jjmatchedKind = kind;
         jjmatchedPos = pos;
         if (trace_enabled) LOGGER.info("   No more string literal token matches are possible.");
         if (trace_enabled) LOGGER.info("   Currently matched the first " + (jjmatchedPos + 1) 
                            + " characters as a " + tokenImage[jjmatchedKind] + " token.");
         return pos + 1;
    }
    
[#list lexerData.allBitVectors as bitVec]
    private static final long[] jjbitVec${bitVec_index} = ${bitVec};
[/#list]    

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
[#var count=0]    
[#list lexerData.orderedStateSet as set]
    [#list set as i]
        [#if count%16 = 0]${"
    "}[/#if]
        ${i}[#if set_has_next || i_has_next], [/#if]
        [#set count = count+1]
    [/#list]
[/#list]
  };

