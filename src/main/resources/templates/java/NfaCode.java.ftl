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

[#macro OutputNfaStateMoves]  
    private final int[] jjstateSet = new int[${2*lexerData.stateSetSize}];
    private int jjnewStateCnt;
    private BitSet checkedStates = new BitSet();

    private void addState(int state) {
         if (!checkedStates.get(state)) {
            jjstateSet[jjnewStateCnt++] = state;
            checkedStates.set(state);
         }
    }
    
    private void addStates(int start, int end) {
        do {
            addState(jjnextStates[start]);
        } while (start++ != end);
    }

    private void addStates(int start) {
      addState(jjnextStates[start]);
      addState(jjnextStates[start+1]);
    }
   
    private int jjStopAtPos(int pos, int kind) {
         jjmatchedKind = kind;
         jjmatchedPos = pos;
         if (trace_enabled) LOGGER.info("   No more string literal token matches are possible.");
         if (trace_enabled) LOGGER.info("   Currently matched the first " + (jjmatchedPos + 1) 
                            + " characters as a " + tokenImage[jjmatchedKind] + " token.");
         return pos + 1;
    }
    [#var needNextStep = false]

   [#list lexerData.lexicalStates as lexicalState]
       [#list lexicalState.nfaData.allStates as nfaState]
          [#if nfaState.moveRanges?size < 16]  
            private static final boolean ${nfaState.moveMethodName}(int ch) {
               [#var left, right]
               [#list nfaState.moveRanges as char]
                  [#if char_index % 2 = 0]
                     [#set left = char]
                  [#else]
                     [#set right = char]
                     [#if left = right]
                     if (ch == ${left}) return true;
                     [#else]
                       [#if left >0 ]
                     if (ch < ${left}) return false;
                       [/#if]
                     if (ch <= ${right}) return true;
                     [/#if]
                  [/#if]
               [/#list]
                     return false;
            }
           [#else]
            [#set needNextStep = true]
            [#var arrayName = nfaState.movesArrayName]

            static private int[] ${arrayName};

            static private void ${arrayName}_populate() {
               ${arrayName} = new int[${nfaState.moveRanges?size}];
               [#list nfaState.moveRanges as char]
                  ${arrayName}[${char_index}] = ${char};
               [/#list]
            }

            private static final boolean ${nfaState.moveMethodName}(int ch) {
               int idx = Arrays.binarySearch(${arrayName}, ch);
               return idx>=0 || idx%2==0;
            }
           [/#if]
       [/#list]
   [/#list]
     [#if needNextStep]
     static {
       [#list lexerData.lexicalStates as lexicalState]
        [#list lexicalState.nfaData.allStates as nfaState]
          [#if nfaState.moveRanges?size >= 16]
            ${nfaState.movesArrayName}_populate();
          [/#if]
         [/#list]
       [/#list]
     }
     [/#if]
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
            checkedStates.clear();
            if (curChar < 64) {
	            do {
	                switch (jjstateSet[--stateIndex]) {
	                    [@DumpMoves lexicalState, 0/]
	                    default : break;
	                }
	            } while (stateIndex != startsAt);
            }
            else if (curChar <128) {
	            do {
	                switch (jjstateSet[--stateIndex]) {
 	                    [@DumpMoves lexicalState, 1/]
                	     default : break;
                	}
                } while (stateIndex!= startsAt);
            }
            else {
	            do {
	                switch (jjstateSet[--stateIndex]) {
	                    [@DumpMoves lexicalState, -1/]
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

[#macro DumpMoves lexicalState byteNum]
   [#var statesDumped = utils.newBitSet()]
   [#list lexicalState.nfaData.allCompositeStateSets as stateSet]
       [#var stateIndex=lexicalState.nfaData.getStartStateIndex(stateSet)]
       [#if byteNum <0]
         [@DumpCompositeStatesMovesNonAscii lexicalState, stateSet, stateIndex, statesDumped/]
       [#else]
         [@DumpAsciiCompositeStatesMoves lexicalState, stateSet, stateIndex, byteNum, statesDumped/]
       [/#if]
   [/#list]
   [#list lexicalState.nfaData.allStates as state]
      [#if state.index>=0&&!statesDumped.get(state.index) && state.isNeeded(byteNum)]
         ${statesDumped.set(state.index)!}
          case ${state.index} :
            [@DumpMove state, statesDumped/]
      [/#if]
   [/#list]
[/#macro]

[#macro DumpCompositeStatesMovesNonAscii lexicalState stateSet stateIndex statesDumped]
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
              [@DumpMove toBePrinted, statesDumped/]
      [#return] 
   [/#if]
              case ${stateIndex} :
              [#if stateIndex<lexicalState.numNfaStates]
                 ${statesDumped.set(keyState)!}
              [/#if]
          break;
[/#macro]


[#macro DumpAsciiCompositeStatesMoves lexicalState stateSet stateIndex byteNum statesDumped]
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
              [@DumpMove toBePrinted, statesDumped/]
      [#return] 
   [/#if]
         ${statesDumped.set(stateIndex)!}
              case ${stateIndex} :
         [#var partition=lexicalState.nfaData.partitionStatesSetForAscii(stateSet, byteNum)]
         [#list partition as subSet]
           //partition
            [#list subSet as state]
             //subpartition
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
      if (${nfaState.moveMethodName}(curChar)) {
   [#if kindToPrint != MAX_INT]
      kind = Math.min(kind, ${kindToPrint});
   [/#if]
   [#if !nextState?is_null&&nextState.epsilonMoveCount>0]
       [#var stateNames = nextState.states]
       [#if stateNames?size = 1]
           addState(${stateNames[0]});
       [#elseif stateNames?size = 2 && nextIntersects]
                   addState(${stateNames[0]});
                   addState(${stateNames[1]});
       [#else]
           [#-- Note that the getStateSetIndicesForUse() method builds up a needed
                data structure lexicalState.orderedStateSet, which is used to output
                the jjnextStates vector. --]
           [#var indices=nfaState.lexicalState.nfaData.getStateSetIndicesForUse(nextState)]
           [#if nextIntersects && indices[0]+1 == indices[1]]
               addStates(${indices[0]});
           [#else]
               addStates(${indices[0]}, ${indices[1]});
           [/#if]
       [/#if]
   [/#if]
         }
[/#macro]

[#macro DumpMove nfaState statesDumped]
   [#var nextState = nfaState.nextState]
   [#var nextIntersects= nfaState.nextIntersects]
   [#var onlyState= false]
   [#var lexicalState=nfaState.lexicalState]
   [#var kindToPrint=(nfaState.nextState.type.ordinal)!MAX_INT]
   [#if nextState?is_null || nextState.epsilonMoveCount==0]
         [#var kindCheck=" && kind > "+kindToPrint]
         [#if onlyState][#set kindCheck = ""][/#if]
            if (${nfaState.moveMethodName}(curChar) ${kindCheck})
               kind = ${kindToPrint};
            break;
         [#return]
   [/#if]
   [#if kindToPrint != MAX_INT]
                if (!${nfaState.moveMethodName}(curChar))
         
                          break;
                    kind = Math.min(kind, ${kindToPrint});
   [#else]
                    if (${nfaState.moveMethodName}(curChar))
   [/#if]
   [#if !nextState?is_null&&nextState.epsilonMoveCount>0]
       [#var stateNames = nextState.states]
       [#if stateNames?size = 1]
          addState(${stateNames[0]});
       [#elseif stateNames?size = 2 && nextIntersects]
                    addState(${stateNames[0]});
                    addState(${stateNames[1]});
       [#else]
          [#var indices=lexicalState.nfaData.getStateSetIndicesForUse(nextState)]
          [#if nextIntersects && indices[0]+1 == indices[1]]
             addStates(${indices[0]});
          [#else]
             addStates(${indices[0]}, ${indices[1]});
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
       return jjMoveNfa_${lexicalState.name}(${lexicalState.nfaData.initialStateIndex}, pos+1);
    [#else]
       return pos + 1;
    [/#if]
    }
  [#else]
       int startStateIndex = jjStopStringLiteralDfa_${lexicalState.name}(pos, 
     [#list 0..(maxKindsReqd-1) as i]
        active${i}[#if i_has_next], [#else]);[/#if]
     [/#list]
       return jjMoveNfa_${lexicalState.name}(startStateIndex, pos+1);
      }
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
