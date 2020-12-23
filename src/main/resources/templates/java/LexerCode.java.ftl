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

 [#var options=grammar.options, lexerData=grammar.lexerData]
 [#var utils=grammar.utils]
 [#var tokenCount=lexerData.tokenCount]
 [#var numLexicalStates=lexerData.lexicalStates?size]

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
  
 
      // BitSet for TOKEN
      static private BitSet tokenSet = BitSet.valueOf(new long[] {
          [#list lexerData.tokenSet.toLongArray() as long]${long}L,[/#list]
      });

      // BitSet for SKIP
      static private BitSet skipSet = BitSet.valueOf(new long[] {
          [#list lexerData.skipSet.toLongArray() as long]${long}L,[/#list]
      });

      // BitSet for SPECIAL
      static private BitSet specialSet = BitSet.valueOf(new long[] {
          [#list lexerData.specialSet.toLongArray() as long]${long}L,[/#list]
      });      

      // BitSet for MORE
      static private BitSet moreSet = BitSet.valueOf(new long[] {
          [#list lexerData.moreSet.toLongArray() as long]${long}L,[/#list]
      });      

    private final int[] jjrounds = new int[${lexerData.stateSetSize}];
    private final int[] jjstateSet = new int[${2*lexerData.stateSetSize}];

    private final StringBuilder image = new StringBuilder();
    private int matchedCharsLength;

    char curChar;
    
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
        curChar = (char)  input_stream.beginToken();
        if (curChar == (char) -1) {
           return generateEOF();
        }
       image.setLength(0);
       matchedCharsLength = 0;

[#if lexerData.hasMore]
       while (true) {
[/#if]
    [#-- this also sets up the start state of the nfa --]
[#if numLexicalStates>1]
       switch(lexicalState) {
[/#if]
    
[#list lexerData.lexicalStates as lexicalState]
    [#var dfaData = lexicalState.dfaData]
    [#if numLexicalStates>1]
            case ${lexicalState.name} : 
    [/#if]
    [#if dfaData.hasSinglesToSkip]
       [#var byteMask1 = utils.toHexStringL(dfaData.getSinglesToSkip(0))]
       [#var byteMask2 = utils.toHexStringL(dfaData.getSinglesToSkip(1))]
       while ((curChar < 64 && ((${byteMask1} & (1L << curChar)) != 0)) 
             || (curChar >=64 && curChar < 128 && (${byteMask2} & (1L<<(curChar-64)))!=0))
            {
               [#var debugOutput]
               [#set debugOutput]
               [#if numLexicalStates>1]
               "<" + lexicalState + ">" + 
               [/#if]
               "Skipping character : " + addEscapes(String.valueOf(curChar)) + " (" + (int) curChar + ")"
               [/#set] 
               if (trace_enabled) LOGGER.info(${debugOutput?trim}); 
               curChar = (char) input_stream.beginToken();
               if (curChar == (char) -1) {
                  return generateEOF();
               }
            }
   [/#if]
             
   [#if lexicalState.initMatch != MAX_INT&&lexicalState.initMatch != 0]
        if (trace_enabled) LOGGER.info("   Matched the empty string as " + tokenImage[${lexicalState.initMatch}] + " token.");
        jjmatchedKind = ${lexicalState.initMatch};
        matchedType = TokenType.values()[${lexicalState.initMatch}];
        jjmatchedPos = -1;
        curPos = 0;
    [#else]
        jjmatchedKind = 0x7FFFFFFF;
        matchedType = null;
        jjmatchedPos = 0;
    [/#if]
        [#var debugOutput]
        [#set debugOutput]
            [#if numLexicalStates>1]
               "<" + lexicalState + ">" + 
            [/#if]
            "Current character : " + addEscapes(String.valueOf(curChar)) + " (" + (int) curChar + ") " +
            "at line " + input_stream.getEndLine() + " column " + input_stream.getEndColumn()
        [/#set]
        if (trace_enabled) LOGGER.info(${debugOutput?trim}); 
        curPos = jjMoveStringLiteralDfa0${lexicalState.suffix}();
    [#if numLexicalStates>1]
        break;
    [/#if]
[/#list]
  [#if numLexicalStates>1]
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
 
 [#if numLexicalStates>1]
      if (newLexicalStates[jjmatchedKind] != null) {
          matchedToken.setFollowingLexicalState(newLexicalStates[jjmatchedKind]);
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
          [#if numLexicalStates>1]
            if (newLexicalStates[jjmatchedKind] != null) {
               this.lexicalState = newLexicalStates[jjmatchedKind];
            }
          [/#if]

            continue EOFLoop;
          }
         [#if lexerData.hasMore]
          [#if lexerData.hasMoreActions]
          tokenLexicalActions();
          [#elseif true || lexerData.hasSkipActions || lexerData.hasTokenActions]
          matchedCharsLength += jjmatchedPos + 1;
		  [/#if]
		  
          [#if numLexicalStates>1]
             doLexicalStateSwitch(jjmatchedKind);
          [/#if]
          curPos = 0;
          jjmatchedKind = 0x7FFFFFFF;
          int retval = input_stream.readChar();
          if (retval >=0) {
               curChar = (char) retval;
	
	            [#var debugOutput]
	            [#set debugOutput]
	              [#if numLexicalStates>1]
	                 "<" + lexicalState + ">" + 
	              [/#if]
	              "Current character : " + addEscapes(String.valueOf(curChar)) + " (" + (int) curChar + ") " +
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
    if (invalidToken == null) {
    [#if grammar.options.hugeFileSupport]
       invalidToken = new InvalidToken(""+ curChar, inputSource);
    [#else]
       invalidToken = new InvalidToken(""+ curChar, input_stream);
    [/#if]       
       invalidToken.setBeginLine(error_line);
       invalidToken.setBeginColumn(error_column);
    } else {
       invalidToken.setImage(invalidToken.getImage() + curChar);
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
    [#if lexerData.hasEmptyMatch]
        if (jjmatchedPos < 0) {
          curTokenImage = image.toString();
          beginLine = endLine = input_stream.getBeginLine();
          beginColumn = endColumn = input_stream.getBeginColumn();
        } else {
               curTokenImage = input_stream.getImage(); 
               beginLine = input_stream.getBeginLine();
               beginColumn = input_stream.getBeginColumn();
               endLine = input_stream.getEndLine();
               endColumn = input_stream.getEndColumn();
        }
    [#else]
        curTokenImage = input_stream.getImage();
        beginLine = input_stream.getBeginLine();
        beginColumn = input_stream.getBeginColumn();
        endLine = input_stream.getEndLine();
        endColumn = input_stream.getEndColumn();
    [/#if]
    [#if options.tokenFactory != ""] 
        t = ${options.tokenFactory}.newToken(TokenType.values()[jjmatchedKind], curTokenImage, inputSource);
    [#elseif !grammar.options.hugeFileSupport]
        t = Token.newToken(TokenType.values()[jjmatchedKind], curTokenImage, this);
    [#else]
        t = Token.newToken(TokenType.values()[jjmatchedKind], curTokenImage, inputSource);
    [/#if]
        t.setBeginLine(beginLine);
        t.setEndLine(endLine);
        t.setBeginColumn(beginColumn);
        t.setEndColumn(endColumn);
//        t.setInputSource(this.inputSource);
     [#if numLexicalStates >1]
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
	
	[#var allBitVectors=lexerData.allBitVectors]
	   switch(hiByte) {
	   [#list nfaState.loByteVec! as kase]
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
    
[#if options.debugLexer]

    protected static final int[][][] statesForState =  
    [#if false]
        null;
    [#else]
    {
    [/#if]
       [#list lexerData.lexicalStates as lexicalState]
          [#var states=lexicalState.statesForState]
          [#if !states??] null, [#else]
      {
            [#list states as stateSet]
               [#if !stateSet??]   { ${stateSet_index} },
               [#else]
                {[#list stateSet as state]${state}, [/#list]},
               [/#if]
            [/#list]
      }, 
          [/#if]
       [/#list]
    };
    
    
    private static final int[][] kindForState =
    {
    [#list lexerData.lexicalStates as lexicalState]
      [#if lexicalState_index != 0], [/#if]
      [#if lexicalState.kindsForStates?is_null]null 
      [#else]
       { 
        [#list lexicalState.kindsForStates as kind]
		  [#if kind_index%15 = 0]${"
   "}[/#if]
          ${kind}[#if kind_has_next], [/#if]
        [/#list]
       }
      [/#if]
    [/#list]
    };


      int kindCnt = 0;
      
      protected final String jjKindsForBitVector(int i, long vec)
      {
        String retVal = "";
        if (i == 0)
           kindCnt = 0;
        for (int j = 0; j < 64; j++)
        {
           if ((vec & (1L << j)) != 0L)
           {
              if (kindCnt++ > 0)
                 retVal += ", ";
              if (kindCnt % 5 == 0)
                 retVal += "\n     ";
              retVal += tokenImage[i * 64 + j];
           }
        }
        return retVal;
      }

    protected final String jjKindsForStateVector(
       int lexState, int[] vec, int start, int end)   
    {
        boolean[] kindDone = new boolean[${tokenCount}];
        String retVal = "";
        int cnt = 0;
        for (int i = start; i < end; i++)
        {
         if (vec[i] == -1)
           continue;
         int[] stateSet = statesForState[lexicalState.ordinal()][vec[i]];
         for (int j = 0; j < stateSet.length; j++)
         {
           int state = stateSet[j];
           if (!kindDone[kindForState[lexState][state]])
           {
              kindDone[kindForState[lexState][state]] = true;
              if (cnt++ > 0)
                 retVal += ", ";
              if (cnt % 5 == 0)
                 retVal += "\n     ";
              retVal += tokenImage[kindForState[lexState][state]];
           }
         }
        }
        if (cnt == 0)
           return "{  }";
        else
           return "{ " + retVal + " }";
  }
[/#if]
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
  [#if lexicalState.dumpNfaStarts]
  [@DumpNfaStartStatesCode lexicalState, lexicalState_index/]
  [/#if]
  [#if lexicalState.createStartNfa]
     [@DumpStartWithStates lexicalState/]
  [/#if]
   [@DumpDfaCode lexicalState/]
   [@DumpMoveNfa lexicalState/]
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

[#macro DumpMoveNfa lexicalState]
    private int jjMoveNfa${lexicalState.suffix}(int startState, int curPos) {
    [#if !lexicalState.hasNfa()]
        return curPos;
    }
       [#return]
    [/#if]
    [#if lexicalState.mixedCase]
        int strKind = jjmatchedKind;
        int strPos = jjmatchedPos;
        int seenUpto = curPos+1;
        input_stream.backup(seenUpto);
        curChar = (char) input_stream.readChar(); //REVISIT, deal with error return code
        curPos = 0;
    [/#if]
        int startsAt = 0;
        jjnewStateCnt = ${lexicalState.indexedAllStates?size};
        int i=1;
        jjstateSet[0] = startState;
    [#if grammar.options.debugLexer]
        if (trace_enabled) LOGGER.info("   Starting NFA to match one of : " + jjKindsForStateVector(lexicalState.ordinal(), jjstateSet, 0, 1));
        if (trace_enabled) LOGGER.info("" + 
        [#if numLexicalStates != 1]
            "<" + lexicalState + ">" +  
        [/#if]
            "Current character : " + addEscapes(String.valueOf(curChar)) + " (" + (int)curChar + ") "
           + "at line " + input_stream.getEndLine() + " column " + input_stream.getEndColumn());
    [/#if]
        int kind = 0x7fffffff;
        while (true) {
            if (++jjround == 0x7fffffff) {
               jjround = 0x80000001;
               Arrays.fill(jjrounds,0x80000000);
            }
            if (curChar < 64) {
            	long l = 1L << curChar;
	            do {
	                switch (jjstateSet[--i]) {
	                    [@DumpMoves lexicalState, 0/]
	                    default : break;
	                }
	            } while (i != startsAt);
            }
            else if (curChar <128) {
            	long l = 1L << (curChar & 077);
	            do {
	                switch (jjstateSet[--i]) {
 	                    [@DumpMoves lexicalState, 1/]
                	     default : break;
                	}
                } while (i!= startsAt);
            }
            else {
                int hiByte = (int)(curChar >> 8);
                int i1 = hiByte >> 6;
                long l1 = 1L << (hiByte & 077);
                int i2 = (curChar & 0xff) >> 6;
                long l2 = 1L << (curChar & 077);
	            do {
	                switch (jjstateSet[--i]) {
	                    [@DumpMoves lexicalState, -1/]
                        default : break;
                    }
                } while(i != startsAt);
	                
            }
            if (kind != 0x7fffffff) {
                jjmatchedKind = kind;
                jjmatchedPos = curPos;
                kind = 0x7fffffff;
            }
            ++curPos;
            if (jjmatchedKind != 0 && jjmatchedKind != 0x7fffffff) {
                if (trace_enabled) LOGGER.info("   Currently matched the first " + (jjmatchedPos +1) + " characters as a " 
                                     + tokenImage[jjmatchedKind] + " token.");
            }
            if ((i = jjnewStateCnt) == (startsAt = ${lexicalState.indexedAllStates?size} - (jjnewStateCnt = startsAt)))
    [#if lexicalState.mixedCase]
                 break;
    [#else]
                 return curPos;
    [/#if]
    [#if grammar.options.debugLexer]
            if (trace_enabled) LOGGER.info("   Possible kinds of longer matches : " + jjKindsForStateVector(lexicalState.ordinal(), jjstateSet, startsAt, i));
    [/#if]
            int retval = input_stream.readChar();
            if (retval >=0) {
                 curChar = (char) retval;
            }
            else  {
    [#if lexicalState.mixedCase]            
                break;
    [#else]
                return curPos;
    [/#if]
            }
            if (trace_enabled) LOGGER.info("" + 
            [#if numLexicalStates != 1]
               "<" + lexicalState + ">" + 
            [/#if]
               addEscapes(String.valueOf(curChar)) + " (" + (int)curChar + ") "
              + "at line " + input_stream.getEndLine() + " column " + input_stream.getEndColumn());
        }
    [#if lexicalState.mixedCase]
        if (jjmatchedPos > strPos) {
            return curPos;
        }
        int toRet = Math.max(curPos, seenUpto);
        if (curPos < toRet) {
           for (i = toRet - Math.min(curPos, seenUpto); i-- >0;) {
                   curChar = (char) input_stream.readChar(); // REVISIT, not handling error return code
           }
        }
        if (jjmatchedPos < strPos) {
            jjmatchedKind = strKind;
            jjmatchedPos = strPos;
        }
        else if (jjmatchedPos == strPos && jjmatchedKind > strKind) {
            jjmatchedKind = strKind;
        }
        return toRet;
    [/#if]
    }
[/#macro]

[#macro DumpMoves lexicalState byteNum]
   [#var statesDumped = utils.newBitSet()]
   [#list lexicalState.compositeStateTable?keys as key]
      [@dumpCompositeStatesMoves lexicalState, key, byteNum, statesDumped/]
   [/#list]
   [#list lexicalState.allStates as state]
      [#if state.index>=0&&!statesDumped.get(state.index)&&state.hasTransitions()]
         [#var toPrint=""]
         [#if state.isNeeded(byteNum)]
            ${toPrint}
            ${statesDumped.set(state.index)!}
            case ${state.index} :
            [@DumpMove state, byteNum, statesDumped/]
         [/#if]
      [/#if]
   [/#list]
[/#macro]

[#macro DumpMove nfaState byteNum statesDumped]
   [#var nextIntersects=nfaState.composite || nfaState.nextIntersects]
   [#var onlyState=(byteNum>=0)&&nfaState.isOnlyState(byteNum)]
   [#var lexicalState=nfaState.lexicalState]
   [#var kindToPrint=nfaState.kindToPrint]
   [#list nfaState.getMoveStates(byteNum, statesDumped) as state]
                   case ${state.index} :
   [/#list]
   [#if byteNum<0 || nfaState.asciiMoves[byteNum] != -1]
      [#if nfaState.next?is_null || nfaState.next.usefulEpsilonMoves<=0]
          [#var kindCheck=" && kind > "+kindToPrint]
          [#if onlyState][#set kindCheck = ""][/#if]
          [#if byteNum>=0]
               if ((${utils.toHexStringL(nfaState.asciiMoves[byteNum])} & l) != 0L ${kindCheck})
          [#else]
               if (jjCanMove_${nfaState.nonAsciiMethod}(hiByte, i1, i2, l1, l2) ${kindCheck})
          [/#if]
               kind = ${kindToPrint};
               break;
          [#return]
      [/#if]
   [/#if]
   [#if kindToPrint != MAX_INT]
       [#if byteNum>=0]
          [#if nfaState.asciiMoves[byteNum] != -1]
                    if ((${utils.toHexStringL(nfaState.asciiMoves[byteNum])} &l) == 0L)
                          break;
          [/#if]
       [#else]
                    if (!jjCanMove_${nfaState.nonAsciiMethod}(hiByte, i1, i2, l1, l2))
                          break;
       [/#if]
       [#if onlyState]
                    kind = ${kindToPrint};
       [#else]
                    if (kind > ${kindToPrint})
                         kind = ${kindToPrint};
       [/#if]
   [#elseif (byteNum>=0)]
       [#if nfaState.asciiMoves[byteNum] != -1]
                    if ((${utils.toHexStringL(nfaState.asciiMoves[byteNum])} & l) != 0L)
       [/#if]
   [#else]
                    if (jjCanMove_${nfaState.nonAsciiMethod}(hiByte, i1, i2, l1, l2))
   [/#if]
   [#if !nfaState.next?is_null&&nfaState.next.usefulEpsilonMoves>0]
       [#var stateNames=lexicalState.nextStatesFromKey(nfaState.next.epsilonMovesString)]
       [#if nfaState.next.usefulEpsilonMoves = 1]
          [#var name=stateNames[0]]
          [#if nextIntersects]
                    jjCheckNAdd(${name});
          [#else]
                    jjstateSet[jjnewStateCnt++] = ${name};
          [/#if]
       [#elseif nfaState.next.usefulEpsilonMoves = 2&&nextIntersects]
                    jjCheckNAddTwoStates(${stateNames[0]}, ${stateNames[1]});
       [#else]
          [#var indices=lexicalState.getStateSetIndicesForUse(nfaState.next.epsilonMovesString)]
          [#var notTwo=(indices[0]+1 != indices[1])]
          [#if nextIntersects]
                    jjCheckNAddStates(${indices[0]}
              [#if notTwo]
                    , ${indices[1]}
              [/#if]
                    );
          [#else]
                    jjAddStates(${indices[0]}, ${indices[1]});
          [/#if]
       [/#if]
   [/#if]
                         break;
[/#macro]

[#macro dumpCompositeStatesMoves lexicalState key byteNum statesDumped]
   [#var stateSet=lexicalState.getStateSetFromCompositeKey(key)]
   [#var stateIndex=lexicalState.stateIndexFromComposite(key)]
   [#if stateSet?size = 1 || statesDumped.get(stateIndex)][#return][/#if]
   [#var neededStates=0]
   [#var toBePrinted toPrint=""]
   [#list stateSet as state]
       [#if state.isNeeded(byteNum)]
          [#set neededStates = neededStates+1]
          [#if neededStates = 2]
             [#break]
          [#else]
             [#set toBePrinted = state]
          [/#if]
       [#else]
          ${statesDumped.set(state.index)!}
       [/#if]
   [/#list]
   [#if neededStates = 0]
        [#return]
   [/#if]
   [#if neededStates = 1]
          ${toPrint}
          case ${lexicalState.stateIndexFromComposite(key)} :
      [#if !statesDumped.get(toBePrinted.index)&&toBePrinted.inNextOf>1]
          case ${toBePrinted.index} :
      [/#if]
              ${statesDumped.set(toBePrinted.index)!}
              [@DumpMove toBePrinted, byteNum, statesDumped/]
      [#return] 
   [/#if]
              ${toPrint}
              [#var keyState=lexicalState.stateIndexFromComposite(key)]
              case ${keyState} :
              [#if keyState<lexicalState.indexedAllStates?size]
                 ${statesDumped.set(keyState)!}
              [/#if]
   [#if (byteNum>=0)]
         [#var partition=lexicalState.partitionStatesSetForAscii(stateSet, byteNum)]
         [#list partition as subSet]
            [#list subSet as state]
              [@DumpMoveForCompositeState state, byteNum, state_index!=0/]
            [/#list]
         [/#list]
   [/#if]
                  break;
[/#macro]

[#macro DumpMoveForCompositeState nfaState byteNum elseNeeded]
   [#var nextIntersects=nfaState.nextIntersects]
   [#var kindToPrint=nfaState.kindToPrint 
         asciiMoves=nfaState.asciiMoves 
         loByteVec=nfaState.loByteVec 
         next=nfaState.next 
         lexicalState=nfaState.lexicalState]
   [#if (byteNum>=0)]
      [#if byteNum<0 || asciiMoves[byteNum] != -1]
         [#if elseNeeded] else [/#if] if ((${utils.toHexStringL(asciiMoves[byteNum])} &l) != 0L)
      [/#if]
   [#else]
              if (jjCanMove_${nonAsciiMethod}(hiByte, i1, i2, l1, l2))
   [/#if]
   [#if kindToPrint != MAX_INT] {
                  if (kind > ${kindToPrint})
                      kind = ${kindToPrint};
   [/#if]
   [#if !next?is_null&&next.usefulEpsilonMoves>0]
       [#var stateNames=lexicalState.nextStatesFromKey(next.epsilonMovesString)]
       [#if next.usefulEpsilonMoves = 1]
          [#var name=stateNames[0]]
          [#if nextIntersects]
                   jjCheckNAdd(${name});
          [#else]
                   jjstateSet[jjnewStateCnt++] = ${name};
          [/#if]
       [#elseif next.usefulEpsilonMoves = 2&&nextIntersects]
                   jjCheckNAddTwoStates(${stateNames[0]}, ${stateNames[1]});
       [#else]
           [#-- Note that the getStateSetIndicesForUse() method builds up a needed
                data structure lexicalState.orderedStateSet, which is used to output
                the jjnextStates vector. --]
           [#var indices=nfaState.lexicalState.getStateSetIndicesForUse(next.epsilonMovesString)]
           [#var notTwo=(indices[0]+1 != indices[1])]
           [#if nextIntersects]
                   jjCheckNAddStates(${indices[0]}
               [#if notTwo]
                   , ${indices[1]}
               [/#if]
                  );
           [#else]
                   jjAddStates(${indices[0]}, ${indices[1]});
           [/#if]
       [/#if]
   [/#if]
   [#if kindToPrint != MAX_INT]
         }
   [/#if]
[/#macro]

[#macro DumpDfaCode lexicalState]
  [#var dfaData = lexicalState.dfaData]
  [#var initState=lexicalState.initStateName()]
  [#var maxStringLength=dfaData.maxStringLength]
  [#var maxStringIndex=dfaData.maxStringIndex]
  [#var maxStringLengthForActive=dfaData.maxStringLengthForActive]
  [#if maxStringLength = 0]
    private int jjMoveStringLiteralDfa0${lexicalState.suffix}() {
    [#if lexicalState.hasNfa()]
        return jjMoveNfa${lexicalState.suffix}(${initState}, 0);
    [#else]
        return 1;        
    [/#if]
    }
    [#return]
  [/#if]
  
  [#list dfaData.stringLiteralTables as table]
    [#var startNfaNeeded=false]
    [#var first = (table_index==0)]
    
    private int jjMoveStringLiteralDfa${table_index}${lexicalState.suffix}
    [@ArgsList]
        [#list 0..maxStringIndex/64 as j]
           [#if !first && table_index<=maxStringLengthForActive[j]+1&&maxStringLengthForActive[j] != 0]
              [#if table_index != 1]
                 long old${j}
              [/#if]
               long active${j}
           [/#if]
        [/#list]
    [/@ArgsList] {
    [#if !first]
      [#if table_index > 1]
         [#list 0..maxStringIndex/64 as j]
           [#if table_index<=lexicalState.maxStringLengthForActive[j]+1]
        active${j} = active${j} & old${j};
           [/#if]
         [/#list]
        if ([@ArgsList delimiter=" | "]
         [#list 0..maxStringIndex/64 as j]
           [#if table_index<=lexicalState.maxStringLengthForActive[j]+1]
            active${j}
           [/#if]
         [/#list]
         [/@ArgsList] == 0L)
         [#if !lexicalState.mixedCase&&lexicalState.hasNfa()]
            return jjStartNfa${lexicalState.suffix}
            [@ArgsList]
               ${table_index-2}
               [#list 0..maxStringIndex/64 as j]
                 [#if table_index<=lexicalState.maxStringLengthForActive[j]+1]
                   old${j}
                 [#else]
                   0L
                 [/#if]
               [/#list]
            [/@ArgsList];
         [#elseif lexicalState.hasNfa()]
            return jjMoveNfa${lexicalState.suffix}(${initState}, ${table_index-1});
         [#else]
            return ${table_index};
         [/#if]   
      [/#if]
      [#if grammar.options.debugLexer]
        if (trace_enabled && jjmatchedKind !=0 && jjmatchedKind != 0x7fffffff) {
            LOGGER.info("    Currently matched the first " + (jjmatchedPos + 1) + " characters as a " + tokenImage[jjmatchedKind] + " token.");
        }
        if (trace_enabled) LOGGER.info("   Possible string literal matches : { "
        [#list 0..maxStringIndex/64 as vecs]
           [#if table_index<=maxStringLengthForActive[vecs]]
             + jjKindsForBitVector(${vecs}, active${vecs}) 
           [/#if]
        [/#list]
        + " } ");
      [/#if]
       int retval = input_stream.readChar();
       if (retval >=0) {
           curChar = (char) retval;
       }
       else  {
         [#if !lexicalState.mixedCase&&lexicalState.hasNfa()]
           jjStopStringLiteralDfa${lexicalState.suffix}[@ArgsList]
              ${table_index-1}
           [#list 0..maxStringIndex/64 as k]
              [#if (table_index<=maxStringLengthForActive[k])]
                active${k}
              [#else]
                0L
              [/#if]
           [/#list][/@ArgsList];
          if (trace_enabled && jjmatchedKind != 0 && jjmatchedKind != 0x7fffffff) {
             LOGGER.info("    Currently matched the first " + (jjmatchedPos + 1) + " characters as a " + tokenImage[jjmatchedKind] + " token. ");
          }
           return ${table_index};
         [#elseif lexicalState.hasNfa()]
           return jjMoveNfa${lexicalState.suffix}(${initState}, ${table_index-1}); 
         [#else]
           return ${table_index};
         [/#if]
       }
    [/#if]
    [#if !first]
      if (trace_enabled) LOGGER.info("" + 
        [#if lexerData.lexicalStates?size != 1]
           "<${lexicalState.name}>" +
        [/#if]
        "Current character : " + addEscapes(String.valueOf(curChar)) + " ("
        + (int) curChar + ") at line " + input_stream.getEndLine() + " column " + input_stream.getEndColumn());
    [/#if]
      switch (curChar) {
    [#list dfaData.rearrange(table) as key]
       [#var info=table[key]]
       [#var ifGenerated=false]
	   [#var c=key[0..0]]
	   [#if dfaData.generateDfaCase(key, info, table_index)]
	      [#-- We know key is a single character.... --]
	      [#if grammar.options.ignoreCase]
	         [#if c != c?upper_case]
	           case ${utils.firstCharAsInt(c?upper_case)} :
	         [/#if]
	         [#if c != c?lower_case]
	           case ${utils.firstCharAsInt(c?lower_case)} : 
	         [/#if]
	      [/#if]
	           case ${utils.firstCharAsInt(c)} :
	      [#if info.finalKindCnt != 0]
	        [#list 0..maxStringIndex as j]
	          [#var matchedKind=info.finalKinds[(j/64)?int]]
              [#if utils.isBitSet(matchedKind, j%64)]
                 [#if ifGenerated]
                 else if 
                 [#elseif table_index != 0]
                 if 
                 [/#if]
                 [#set ifGenerated = true]
                 [#if table_index != 0]
                   ((active${(j/64)?int} & ${utils.powerOfTwoInHex(j%64)}) != 0L) 
                 [/#if]
                 [#var kindToPrint=lexicalState.getKindToPrint(j, table_index)]
                 [#if !dfaData.subString[j]]
                    [#var stateSetIndex=lexicalState.getStateSetForKind(table_index, j)]
                    [#if stateSetIndex != -1]
                    return jjStartNfaWithStates${lexicalState.suffix}(${table_index}, ${kindToPrint}, ${stateSetIndex});
                    [#else]
                    return jjStopAtPos(${table_index}, ${kindToPrint});
                    [/#if]
                 [#else]
                    [#if table_index != 0 || (lexicalState.initMatch != 0&&lexicalState.initMatch != MAX_INT)]
                     {
                    jjmatchedKind = ${kindToPrint};
                    jjmatchedPos = ${table_index};
                 }
                    [#else]
                    jjmatchedKind = ${kindToPrint};
                    [/#if]
                 [/#if]
              [/#if]
	        [/#list]
	      [/#if]
	      [#if info.validKindCnt != 0]
	           return jjMoveStringLiteralDfa${table_index+1}${lexicalState.suffix}[@ArgsList]
	              [#list 0..maxStringIndex/64 as j]
	                 [#if table_index<=maxStringLengthForActive[j]&&maxStringLengthForActive[j] != 0]
	                    [#if table_index != 0]
	                       active${j}
	                    [/#if]
	                    ${utils.toHexStringL(info.validKinds[j])}
	                 [/#if]
	              [/#list]
	           [/@ArgsList];
	      [#else][#-- a very special case--]
	        [#if table_index = 0&&lexicalState.mixedCase]
	           [#if lexicalState.hasNfa()]
	           return jjMoveNfa${lexicalState.suffix}(${initState}, 0);
	           [#else]
	           return 1;
	           [/#if]
	        [#elseif table_index != 0][#-- No more str literals to look for --]
	           break;
	           [#set startNfaNeeded = true]
	        [/#if]
	      [/#if]
	   [/#if]       
    [/#list]
    [#-- default means that the current characters is not in any of
    the strings at this position--]
         default : 
            if (trace_enabled) LOGGER.info("   No string literal matches possible.");
    [#if lexicalState.hasNfa()]
       [#if table_index = 0]
            return jjMoveNfa${lexicalState.suffix}(${initState}, 0);
       [#else]
            break;
          [#set startNfaNeeded = true]
       [/#if]
    [#else]
           return ${table_index+1};
    [/#if]
      }
    [#if table_index != 0]
       [#if startNfaNeeded]
          [#if !lexicalState.mixedCase&&lexicalState.hasNfa()]
            [#-- Here a string literal is successfully matched and no
                 more string literals are possible. So set the kind and t
                 state set up to and including this position for the matched
                 string. --]
            return jjStartNfa${lexicalState.suffix}[@ArgsList]
               ${table_index-1}
               [#list 0..maxStringIndex/64 as k]
                 [#if table_index<=maxStringLengthForActive[k]]
                  active${k}
                 [#else]
                   0L
                 [/#if]
               [/#list]
            [/@ArgsList];
          [#elseif lexicalState.hasNfa()]
             return jjMoveNfa${lexicalState.suffix}(${initState}, ${table_index});
          [#else]
             return ${table_index+1};
          [/#if]        
       [/#if]
    [/#if]
   }
  [/#list]
[/#macro] 
  
[#macro DumpStartWithStates lexicalState]
    private int jjStartNfaWithStates${lexicalState.suffix}(int pos, int kind, int state) {
        jjmatchedKind = kind;
        jjmatchedPos = pos;
        if (trace_enabled) LOGGER.info("   No more string literal token matches are possible.");
        if (trace_enabled) LOGGER.info("   Currently matched the first " + (jjmatchedPos + 1) + " characters as a " + tokenImage[jjmatchedKind] + " token.");
         int retval = input_stream.readChar();
       if (retval >=0) {
           curChar = (char) retval;
       } 
       else  { 
            return pos + 1; 
        }
        if (trace_enabled) LOGGER.info("" + 
     [#if numLexicalStates != 1]
            "<${lexicalState.name}>"+  
     [/#if]
            "Current character : " + addEscapes(String.valueOf(curChar)) 
            + " (" + (int)curChar + ") " + "at line " + input_stream.getEndLine() 
            + " column " + input_stream.getEndColumn());
        return jjMoveNfa${lexicalState.suffix}(state, pos+1);
   }
[/#macro]
 
[#macro DumpNfaStartStatesCode lexicalState lexicalState_index]
  [#var dfaData = lexicalState.dfaData] 
  [#var statesForPos=lexicalState.statesForPos]
  [#var maxKindsReqd=(1+dfaData.maxStringIndex/64)?int]
  [#var ind=0]
  [#var maxStringIndex=dfaData.maxStringIndex]
  [#var maxStringLength=dfaData.maxStringLength]
  
    private int jjStartNfa${lexicalState.suffix}(int pos, 
  [#list 0..(maxKindsReqd-1) as i]
       long active${i}[#if i_has_next], [#else]) {[/#if]
  [/#list]
  [#if lexicalState.mixedCase] [#--  FIXME! Currently no test coverage of any sort for this. --]
    [#if lexicalState.hasNfa()]
       return jjMoveNfa${lexicalState.suffix}(${lexicalState.initStateName()}, pos+1);
    [#else]
       return pos + 1;
    [/#if]
    }
  [#else]
       return jjMoveNfa${lexicalState.suffix}(jjStopStringLiteralDfa${lexicalState.suffix}(pos, 
     [#list 0..(maxKindsReqd-1) as i]
        active${i}[#if i_has_next], [#else])[/#if]
     [/#list]
        , pos+1);}
   [/#if]

  
    private final int jjStopStringLiteralDfa${lexicalState.suffix}(int pos, 
   [#list 0..(maxKindsReqd-1) as i]
    long active${i}[#if i_has_next], [/#if]
   [/#list]
  ) { 
        if (trace_enabled) LOGGER.info("   No more string literal token matches are possible.");
        switch (pos) {
  [#list 0..(maxStringLength-1) as i]
	 [#if statesForPos[i]??]
            case ${i} :
        [#list statesForPos[i]?keys as stateSetString]
           [#var condGenerated=false]
           [#var actives=statesForPos[i][stateSetString]]
           [#list 0..(maxKindsReqd-1) as j]
             [#if actives[j] != 0]
               [#if !condGenerated]
               if (
               [#else]
               ||
               [/#if]
               [#set condGenerated = true]
              (active${j} & ${utils.toHexStringL(actives[j])}) != 0L 
             [/#if]
           [/#list]
           [#if condGenerated]
               ) 
              [#set ind = stateSetString?index_of(", ")]
              [#var kindStr=stateSetString?substring(0, ind)]
              [#var afterKind=stateSetString?substring(ind+2)] 
              [#var jjmatchedPos=afterKind?substring(0, afterKind?index_of(", "))?number]
              [#if kindStr != "2147483647"]
                 {
                 [#if i = 0]
                    jjmatchedKind = ${kindStr};
					[#if lexicalState.initMatch != 0&&lexicalState.initMatch != MAX_INT]
                    jjmatchedPos = 0;
                    [/#if]
                 [#elseif i = jjmatchedPos]
                    [#if dfaData.subStringAtPos[i]]
                    if (jjmatchedPos != ${i}) {
                        jjmatchedKind = ${kindStr};
                        jjmatchedPos = ${i};
                    }
                    [#else]
                    jjmatchedKind = ${kindStr};
                    jjmatchedPos = ${i};
                    [/#if]
                 [#else]
                    [#if jjmatchedPos>0]
                    if (jjmatchedPos < ${jjmatchedPos}) {
                    [#else]
                    if (jjmatchedPos == 0) {
                    [/#if]
                        jjmatchedKind = ${kindStr};
                        jjmatchedPos = ${jjmatchedPos};
                    }
                 [/#if]
              [/#if]
              [#set ind = stateSetString?index_of(", ")]
			  [#set kindStr = stateSetString?substring(0, ind)]
			  [#set afterKind = stateSetString?substring(ind+2)]
			  [#set stateSetString = afterKind?substring(afterKind?index_of(",")+2)]
              [#if stateSetString = "null;"]
                        return -1;
              [#else]
                   return ${lexicalState.addStartStateSet(stateSetString)};
              [/#if]
              [#if kindStr != "2147483647"]
              }
              [/#if]
           [/#if]
           [#set condGenerated = false]
     [/#list]
                       return -1;
    [/#if]
  [/#list]
                   default :
                       return -1;
      }
    }
[/#macro]

[#---
   Utility macro to output a sequence of args, typically
   to a method. The input can be passed in as an argument,
   or via the macro's nested content. In either case, it
   is just one argument per line. The macro takes care of 
   commas and the opening and closing parentheses.  
--]   

[#macro ArgsList input="" delimiter=","]
   [#if input?length = 0]
     [#set input]
       [#nested]
     [/#set]
   [/#if]
   [#set input = input?trim?split("
")]
   (
   [#list input as arg]
      [#set arg = arg?trim]
      [#if arg?length != 0]
        ${arg}
        [#if arg_has_next]
           ${delimiter} 
        [/#if]
      [/#if]
   [/#list] 
   )
[/#macro]
