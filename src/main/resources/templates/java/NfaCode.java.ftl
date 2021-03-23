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

 [--
   This file contains the macro that generates the Java code 
   for the NFA. Needless to say, this still needs some cleanup.
 --]

[#var utils = grammar.utils, lexerData=grammar.lexerData]
[#var multipleLexicalStates = lexerData.lexicalStates?size >1]
[#var MAX_INT=2147483647]

[#macro NfaStateMove nfaState]
   private static boolean canMove_${nfaState.lexicalState.name}_${nfaState.index}(int ch) {
//TODO!!!
   }
[/#macro]

[#macro OutputNfaStateMoves]  
   [#-- There is something screwy about this. If I can 
      rewrite this loop, then maybe the whole Rube Goldberg 
      contraption will start to unravel.--]
   [#list lexerData.nonAsciiTableForMethod as nfaState]
      private static boolean jjCanMove_${nfaState.lexicalState.name}_${nfaState.index}(int ch) {
         int hiByte = ch >> 8;
         int i1 = hiByte >> 6;
         long l1 = 1L << (hiByte & 077);
         int i2 = (ch & 0xff) >> 6;
         long l2 = 1L << (ch & 077);
         switch(hiByte) {
         [#list nfaState.loByteVec as kase]
            [#if kase_index%2 = 0]       
            case ${kase} :
               return (jjbitVec${nfaState.loByteVec[kase_index+1]}[i2] &l2) != 0L;
            [/#if]
         [/#list]
            default : 
               return false;
         }		
      }
   [/#list]
[/#macro]

[#macro DumpMoveNfa lexicalState]
  [#var hasNfa = lexicalState.numNfaStates>0]
    private int jjMoveNfa_${lexicalState.name}(int startState, int curPos) {
    [#if !hasNfa]
        return curPos;
    }
       [#return]
    [/#if]
    [#if lexicalState.mixedCase]
        int strKind = jjmatchedKind;
        int strPos = jjmatchedPos;
        int seenUpto = curPos+1;
        input_stream.backup(seenUpto);
        curChar = input_stream.readChar(); //REVISIT, deal with error return code
        curPos = 0;
    [/#if]
        int startsAt = 0;
        jjnewStateCnt = ${lexicalState.numNfaStates};
        int stateIndex=1;
        jjstateSet[0] = startState;
        int kind = 0x7fffffff;
        while (true) {
            if (++jjround == 0x7fffffff) {
               jjround = 0x80000001;
               Arrays.fill(jjrounds,0x80000000);
            }
            if (curChar < 64) {
            	long l = 1L << curChar;
	            do {
	                switch (jjstateSet[--stateIndex]) {
	                    [@DumpAsciiMoves lexicalState, 0/]
	                    default : break;
	                }
	            } while (stateIndex != startsAt);
            }
            else if (curChar <128) {
            	long l = 1L << (curChar & 077);
	            do {
	                switch (jjstateSet[--stateIndex]) {
 	                    [@DumpAsciiMoves lexicalState, 1/]
                	     default : break;
                	}
                } while (stateIndex!= startsAt);
            }
            else {
	            do {
	                switch (jjstateSet[--stateIndex]) {
	                    [@DumpMovesNonAscii lexicalState/]
                        default : break;
                    }
                } while(stateIndex != startsAt);
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
            stateIndex = jjnewStateCnt;
            jjnewStateCnt = startsAt;
            startsAt = ${lexicalState.numNfaStates} - startsAt;
            if (stateIndex == startsAt)
    [#if lexicalState.mixedCase]
                 break;
    [#else]
                 return curPos;
    [/#if]
            int retval = input_stream.readChar();
            if (retval >=0) {
                 curChar = retval;
            }
            else  {
    [#if lexicalState.mixedCase]            
                break;
    [#else]
                return curPos;
    [/#if]
            }
            if (trace_enabled) LOGGER.info("" + 
            [#if multipleLexicalStates]
               "<" + lexicalState + ">" + 
            [/#if]
               [#-- REVISIT --]
               addEscapes(String.valueOf(curChar)) + " (" + curChar + ") "
              + "at line " + input_stream.getEndLine() + " column " + input_stream.getEndColumn());
        }
    [#if lexicalState.mixedCase]
        if (jjmatchedPos > strPos) {
            return curPos;
        }
        int toRet = Math.max(curPos, seenUpto);
        if (curPos < toRet) {
           for (i = toRet - Math.min(curPos, seenUpto); i-- >0;) {
                   curChar = input_stream.readChar(); // REVISIT, not handling error return code
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

[#macro DumpMovesNonAscii lexicalState]
   [#var statesDumped = utils.newBitSet()]
   [#list lexicalState.nfaData.allCompositeStates as key]
        [@DumpCompositeStatesMovesNonAscii lexicalState, key, statesDumped/]
   [/#list]
   [#list lexicalState.nfaData.allStates as state]
      [#if state.index>=0&&!statesDumped.get(state.index)]
         [#if state.nonAscii]
            ${statesDumped.set(state.index)!}
            case ${state.index} :
              [@DumpMoveNonAscii state, statesDumped /]
         [/#if]
      [/#if]
   [/#list]
[/#macro]


[#macro DumpAsciiMoves lexicalState byteNum]
   [#var statesDumped = utils.newBitSet()]
   [#list lexicalState.nfaData.allCompositeStates as key]
        [@DumpAsciiCompositeStatesMoves lexicalState, key, byteNum, statesDumped/]
   [/#list]
   [#list lexicalState.nfaData.allStates as state]
      [#if state.index>=0&&!statesDumped.get(state.index)]
         [#if state.isNeeded(byteNum)]
            ${statesDumped.set(state.index)!}
            case ${state.index} :
              [@DumpAsciiMove state, byteNum, statesDumped/]
         [/#if]
      [/#if]
   [/#list]
[/#macro]

[#macro DumpCompositeStatesMovesNonAscii lexicalState key statesDumped]
   [#var stateSet=lexicalState.nfaData.getStateSetFromCompositeKey(key)]
   [#var stateIndex=lexicalState.nfaData.getStartStateIndex(key)]
   [#if stateSet?size = 1 || statesDumped.get(stateIndex)][#return][/#if]
   [#var neededStates=0]
   [#var toBePrinted]
   [#list stateSet as state]
       [#if state.nonAscii]
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
          case ${stateIndex} :
      [#if !statesDumped.get(toBePrinted.index)]
          case ${toBePrinted.index} :
      [/#if]
              ${statesDumped.set(toBePrinted.index)!}
              [@DumpMoveNonAscii toBePrinted, statesDumped/]
      [#return] 
   [/#if]
              case ${stateIndex} :
              [#if stateIndex<lexicalState.numNfaStates]
                 ${statesDumped.set(keyState)!}
              [/#if]
          break;
[/#macro]


[#macro DumpAsciiCompositeStatesMoves lexicalState key byteNum statesDumped]
   [#var stateSet=lexicalState.nfaData.getStateSetFromCompositeKey(key)]
   [#var stateIndex=lexicalState.nfaData.getStartStateIndex(key)]
   [#if stateSet?size = 1 || statesDumped.get(stateIndex)][#return][/#if]
   [#var neededStates=0]
   [#var toBePrinted]
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
          case ${stateIndex} :
      [#if !statesDumped.get(toBePrinted.index)]
          case ${toBePrinted.index} :
      [/#if]
              ${statesDumped.set(toBePrinted.index)!}
              [@DumpAsciiMove toBePrinted, byteNum, statesDumped/]
      [#return] 
   [/#if]
              case ${stateIndex} :
              [#if stateIndex<lexicalState.numNfaStates]
                 ${statesDumped.set(stateIndex)!}
              [/#if]
         [#var partition=lexicalState.nfaData.partitionStatesSetForAscii(stateSet, byteNum)]
         [#list partition as subSet]
            [#list subSet as state]
              [@DumpAsciiMoveForCompositeState state, byteNum, state_index!=0/]
            [/#list]
         [/#list]
             break;
[/#macro]

[#macro DumpAsciiMoveForCompositeState nfaState byteNum elseNeeded]
   [#var nextIntersects=nfaState.nextIntersects]
   [#var kindToPrint=(nfaState.nextState.type.ordinal)!MAX_INT
         asciiMoves=nfaState.asciiMoves 
         nextState=nfaState.nextState
         lexicalState=nfaState.lexicalState]
         [#if elseNeeded] else [/#if] if ((${utils.toHexStringL(asciiMoves[byteNum])} &l) != 0L) {
   [#if kindToPrint != MAX_INT]
          kind = Math.min(kind, ${kindToPrint});
   [/#if]
   [#if !nextState?is_null&&nextState.epsilonMoveCount>0]
       [#var stateNames = nextState.states]
       [#if stateNames?size = 1]
          [#var name=stateNames[0]]
          [#if nextIntersects]
                   jjCheckNAdd(${name});
          [#else]
                   jjstateSet[jjnewStateCnt++] = ${name};
          [/#if]
       [#elseif stateNames?size = 2 && nextIntersects]
                   jjCheckNAddTwoStates(${stateNames[0]}, ${stateNames[1]});
       [#else]
           [#-- Note that the getStateSetIndicesForUse() method builds up a needed
                data structure lexicalState.orderedStateSet, which is used to output
                the jjnextStates vector. --]
           [#var indices=nfaState.lexicalState.nfaData.getStateSetIndicesForUse(nextState)]
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
         }
[/#macro]

[#macro DumpMoveNonAscii nfaState statesDumped]
   [#var nextState = nfaState.nextState]
   [#var nextIntersects= nfaState.nextIntersects]
   [#var onlyState= false]
   [#var lexicalState=nfaState.lexicalState]
   [#var kindToPrint=(nfaState.nextState.type.ordinal)!MAX_INT]
   [#list nfaState.getMoveStates(-1, statesDumped) as state]
                   case ${state.index} :
   [/#list]
   [#if nextState?is_null || nextState.epsilonMoveCount==0]
         [#var kindCheck=" && kind > "+kindToPrint]
         [#if onlyState][#set kindCheck = ""][/#if]
            if (jjCanMove_${nfaState.lexicalState.name}_${nfaState.index}(curChar) ${kindCheck})
            kind = ${kindToPrint};
            break;
         [#return]
   [/#if]
   [#if kindToPrint != MAX_INT]
                    if (!jjCanMove_${nfaState.lexicalState.name}_${nfaState.index}(curChar))
                          break;
                    kind = Math.min(kind, ${kindToPrint});
   [#else]
                    if (jjCanMove_${nfaState.lexicalState.name}_${nfaState.index}(curChar))
   [/#if]
   [#if !nextState?is_null&&nextState.epsilonMoveCount>0]
       [#var stateNames = nextState.states]
       [#if stateNames?size = 1]
          [#var name=stateNames[0]]
          [#if nextIntersects]
                    jjCheckNAdd(${name});
          [#else]
                    jjstateSet[jjnewStateCnt++] = ${name};
          [/#if]
       [#elseif stateNames?size = 2 && nextIntersects]
                    jjCheckNAddTwoStates(${stateNames[0]}, ${stateNames[1]});
       [#else]
          [#var indices=lexicalState.nfaData.getStateSetIndicesForUse(nextState)]
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

[#macro DumpAsciiMove nfaState byteNum statesDumped]
   [#var nextState = nfaState.nextState]
   [#var nextIntersects=nfaState.nextIntersects]
   [#var onlyState=(byteNum>=0)&&nfaState.isOnlyState(byteNum)]
   [#var lexicalState=nfaState.lexicalState]
   [#var kindToPrint=(nfaState.nextState.type.ordinal)!MAX_INT]
   [#list nfaState.getMoveStates(byteNum, statesDumped) as state]
                   case ${state.index} :
   [/#list]
   [#if nfaState.asciiMoves[byteNum] != -1]
      [#if nextState?is_null || nextState.epsilonMoveCount==0]
          [#var kindCheck=" && kind > "+kindToPrint]
          [#if onlyState][#set kindCheck = ""][/#if]
               if ((${utils.toHexStringL(nfaState.asciiMoves[byteNum])} & l) != 0L ${kindCheck})
               kind = ${kindToPrint};
               break;
          [#return]
      [/#if]
   [/#if]
   [#if kindToPrint != MAX_INT]
      [#if nfaState.asciiMoves[byteNum] != -1]
               if ((${utils.toHexStringL(nfaState.asciiMoves[byteNum])} &l) == 0L)
                     break;
      [/#if]
       [#if onlyState]
                    kind = ${kindToPrint};
       [#else]
                    kind = Math.min(kind, ${kindToPrint});
       [/#if]
   [#else]
       [#if nfaState.asciiMoves[byteNum] != -1]
                    if ((${utils.toHexStringL(nfaState.asciiMoves[byteNum])} & l) != 0L)
       [/#if]
   [/#if]
   [#if !nextState?is_null&&nextState.epsilonMoveCount>0]
       [#var stateNames = nextState.states]
       [#if stateNames?size = 1]
          [#var name=stateNames[0]]
          [#if nextIntersects]
                    jjCheckNAdd(${name});
          [#else]
                    jjstateSet[jjnewStateCnt++] = ${name};
          [/#if]
       [#elseif stateNames?size = 2 && nextIntersects]
                    jjCheckNAddTwoStates(${stateNames[0]}, ${stateNames[1]});
       [#else]
          [#var indices=lexicalState.nfaData.getStateSetIndicesForUse(nextState)]
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

[#macro DumpNfaStartStatesCode lexicalState lexicalState_index]
  [#var dfaData = lexicalState.dfaData] 
  [#var stateSetForPos = lexicalState.nfaData.stateSetForPos]
  [#var maxKindsReqd=(1+lexicalState.maxStringIndex/64)?int]
  [#var ind=0]
  [#var maxStringIndex=lexicalState.maxStringIndex]
  [#var maxStringLength=lexicalState.maxStringLength]
  [#var hasNfa = lexicalState.numNfaStates>0]

  
    private int jjStartNfa_${lexicalState.name}(int pos, 
  [#list 0..(maxKindsReqd-1) as i]
       long active${i}[#if i_has_next], [#else]) {[/#if]
  [/#list]
  [#if lexicalState.mixedCase] [#--  FIXME! Currently no test coverage of any sort for this. --]
    [#if hasNfa]
       return jjMoveNfa_${lexicalState.name}(${lexicalState.nfaData.initStateName()}, pos+1);
    [#else]
       return pos + 1;
    [/#if]
    }
  [#else]
       return jjMoveNfa_${lexicalState.name}(jjStopStringLiteralDfa_${lexicalState.name}(pos, 
     [#list 0..(maxKindsReqd-1) as i]
        active${i}[#if i_has_next], [#else])[/#if]
     [/#list]
        , pos+1);}
   [/#if]

  
    private final int jjStopStringLiteralDfa_${lexicalState.name}(int pos, 
   [#list 0..(maxKindsReqd-1) as i]
    long active${i}[#if i_has_next], [/#if]
   [/#list]
  ) { 
        if (trace_enabled) LOGGER.info("   No more string literal token matches are possible.");
        switch (pos) {
  [#list 0..(maxStringLength-1) as i]
	 [#if stateSetForPos[i]??]
            case ${i} :
        [#list stateSetForPos[i]?keys as stateSetString]
           [#var condGenerated=false]
           [#var activeSet=stateSetForPos[i][stateSetString]]
           [#var actives = utils.bitSetToLongArray(activeSet, maxKindsReqd)]
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
              [#set ind = stateSetString?index_of(",")]
              [#var kindStr=stateSetString?substring(0, ind)]
              [#var afterKind=stateSetString?substring(ind+1)] 
              [#var jjmatchedPos=afterKind?substring(0, afterKind?index_of(","))?number]
              [#if kindStr != "2147483647"]
                 {
                 [#if i = 0]
                    jjmatchedKind = ${kindStr};
                    jjmatchedPos = 0;
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
              [#set ind = stateSetString?index_of(",")]
			  [#set kindStr = stateSetString?substring(0, ind)]
			  [#set afterKind = stateSetString?substring(ind+1)]
			  [#set stateSetString = afterKind?substring(afterKind?index_of(",")+1)]
              [#if stateSetString = "null;"]
                        return -1;
              [#else]
                   return ${lexicalState.nfaData.getStartStateIndex(stateSetString)};
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

[#macro DumpStartWithStates lexicalState]
    private int jjStartNfaWithStates_${lexicalState.name}(int pos, int kind, int state) {
        jjmatchedKind = kind;
        jjmatchedPos = pos;
        if (trace_enabled) LOGGER.info("   No more string literal token matches are possible.");
        if (trace_enabled) LOGGER.info("   Currently matched the first " + (jjmatchedPos + 1) + " characters as a " + tokenImage[jjmatchedKind] + " token.");
         int retval = input_stream.readChar();
       if (retval >=0) {
           curChar = retval;
       } 
       else  { 
            return pos + 1; 
        }
        if (trace_enabled) LOGGER.info("" + 
     [#if multipleLexicalStates]
            "<${lexicalState.name}>"+  
     [/#if]
            [#-- REVISIT --]
            "Current character : " + addEscapes(String.valueOf(curChar)) 
            + " (" + curChar + ") " + "at line " + input_stream.getEndLine() 
            + " column " + input_stream.getEndColumn());
        return jjMoveNfa_${lexicalState.name}(state, pos+1);
   }
[/#macro]
 
