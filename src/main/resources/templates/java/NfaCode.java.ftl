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

[#macro DumpMoveNfa lexicalState]
    private int jjMoveNfa_${lexicalState.name}(int startState, int curPos) {
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
        curChar = input_stream.readChar(); //REVISIT, deal with error return code
        curPos = 0;
    [/#if]
        int startsAt = 0;
        jjnewStateCnt = ${lexicalState.indexedAllStates?size};
        int i=1;
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


[#macro DumpMoves lexicalState byteNum]
   [#var statesDumped = utils.newBitSet()]
   [#list lexicalState.nfaData.compositeStateTable?keys as key]
      [@DumpCompositeStatesMoves lexicalState, key, byteNum, statesDumped/]
   [/#list]
   [#list lexicalState.nfaData.allStates as state]
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

[#macro DumpCompositeStatesMoves lexicalState key byteNum statesDumped]
   [#var stateSet=lexicalState.nfaData.getStateSetFromCompositeKey(key)]
   [#var stateIndex=lexicalState.nfaData.stateIndexFromComposite(key)]
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
          case ${lexicalState.nfaData.stateIndexFromComposite(key)} :
      [#if !statesDumped.get(toBePrinted.index)&&toBePrinted.inNextOf>1]
          case ${toBePrinted.index} :
      [/#if]
              ${statesDumped.set(toBePrinted.index)!}
              [@DumpMove toBePrinted, byteNum, statesDumped/]
      [#return] 
   [/#if]
              ${toPrint}
              [#var keyState=lexicalState.nfaData.stateIndexFromComposite(key)]
              case ${keyState} :
              [#if keyState<lexicalState.indexedAllStates?size]
                 ${statesDumped.set(keyState)!}
              [/#if]
   [#if (byteNum>=0)]
         [#var partition=lexicalState.nfaData.partitionStatesSetForAscii(stateSet, byteNum)]
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
       [#var stateNames=lexicalState.nfaData.nextStatesFromKey(next.epsilonMovesString)]
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
           [#var indices=nfaState.lexicalState.nfaData.getStateSetIndicesForUse(next.epsilonMovesString)]
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
       [#var stateNames=lexicalState.nfaData.nextStatesFromKey(nfaState.next.epsilonMovesString)]
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
          [#var indices=lexicalState.nfaData.getStateSetIndicesForUse(nfaState.next.epsilonMovesString)]
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
  [#var statesForPos=lexicalState.nfaData.statesForPos]
  [#var maxKindsReqd=(1+lexicalState.maxStringIndex/64)?int]
  [#var ind=0]
  [#var maxStringIndex=lexicalState.maxStringIndex]
  [#var maxStringLength=lexicalState.maxStringLength]
  
    private int jjStartNfa_${lexicalState.name}(int pos, 
  [#list 0..(maxKindsReqd-1) as i]
       long active${i}[#if i_has_next], [#else]) {[/#if]
  [/#list]
  [#if lexicalState.mixedCase] [#--  FIXME! Currently no test coverage of any sort for this. --]
    [#if lexicalState.hasNfa()]
       return jjMoveNfa_${lexicalState.name}(${lexicalState.initStateName()}, pos+1);
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
              [#set ind = stateSetString?index_of(", ")]
			  [#set kindStr = stateSetString?substring(0, ind)]
			  [#set afterKind = stateSetString?substring(ind+2)]
			  [#set stateSetString = afterKind?substring(afterKind?index_of(",")+2)]
              [#if stateSetString = "null;"]
                        return -1;
              [#else]
                   return ${lexicalState.nfaData.addStartStateSet(stateSetString)};
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
 
