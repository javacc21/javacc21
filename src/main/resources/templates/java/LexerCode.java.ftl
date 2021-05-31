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

[#var MAX_INT=2147483647]
[#var lexerData=grammar.lexerData]
[#var multipleLexicalStates = lexerData.lexicalStates.size()>1]
[#var NFA_RANGE_THRESHOLD = 16]

[#macro BitSetFromLongArray bitSet]
      BitSet.valueOf(new long[] {
          [#list bitSet.toLongArray() as long]
             ${grammar.utils.toHexStringL(long)}
             [#if long_has_next],[/#if]
          [/#list]
      })
[/#macro]

  private int matchedPos, charsRead;
  //FIXME,should be an enum.
  private int matchedKind, kind;
  private Token matchedToken;
  private TokenType matchedType;
  private String inputSource = "input";
  private BitSet nextStates=new BitSet(), currentStates = new BitSet();
  private ToIntBiFunction<Integer,BitSet>[] nfaFunctions;
  // The starting state for the current lexical state
  private int startStateIndex;

  // A lookup of the NFA function tables for the respective lexical states
  private static EnumMap<LexicalState,ToIntBiFunction<Integer,BitSet>[]> functionTableMap = new EnumMap<>(LexicalState.class);
  // A lookup of the start state index for each lexical state
  private static EnumMap<LexicalState, Integer> startStateMap = new EnumMap<>(LexicalState.class);

  static private final BitSet tokenSet = ${BitSetFromLongArray(lexerData.tokenSet)},
                              specialSet = ${BitSetFromLongArray(lexerData.specialSet)},
                              skipSet = ${BitSetFromLongArray(lexerData.skipSet)},
                              moreSet = ${BitSetFromLongArray(lexerData.moreSet)};

  private int curChar;
    
  private Token generateEOF() {
	    this.matchedKind = 0;
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
    this.matchedKind = matchedToken.getType().ordinal();
 [#if multipleLexicalStates]
    if (newLexicalStates[this.matchedKind] != null) {
        switchTo(newLexicalStates[this.matchedKind]);
    }
 [/#if]
    matchedToken.setUnparsed(specialSet.get(this.matchedKind));
  }

  private void tokenLexicalActions() {
       switch(matchedKind) {
   [#list lexerData.regularExpressions as regexp]
        [#if regexp.codeSnippet?has_content]
		  case ${regexp.ordinal} :
		      ${regexp.codeSnippet.javaCode}
           break;
        [/#if]
   [/#list]
      }
    }

    private Token fillToken() {
        final String curTokenImage = input_stream.getImage();
        final int beginLine = input_stream.getBeginLine();
        final int beginColumn = input_stream.getBeginColumn();
        final int endLine = input_stream.getEndLine();
        final int endColumn = input_stream.getEndColumn();
    [#if grammar.settings.TOKEN_FACTORY??]
        final Token t = ${grammar.settings.TOKEN_FACTORY}.newToken(TokenType.values()[matchedKind], curTokenImage, inputSource);
    [#elseif !grammar.hugeFileSupport]
        final Token t = Token.newToken(TokenType.values()[matchedKind], curTokenImage, this);
    [#else]
        final Token t = Token.newToken(TokenType.values()[matchedKind], curTokenImage, inputSource);
    [/#if]
        t.setBeginLine(beginLine);
        t.setEndLine(endLine);
        t.setBeginColumn(beginColumn);
        t.setEndColumn(endColumn);
//        t.setInputSource(this.inputSource);
        return t;
    }

  [#--
     TODO: Merge the nextToken() method with the nfaLoop() 
     that follows. Also, need to move from using ints for the 
     token type towards actually using the type-safe Enum TokenType,
     as much as possible.
  --]
  private Token nextToken() {
    matchedToken = null;
    EOFLoop :
    while (true) {
        curChar = input_stream.beginToken();
        if (curChar == -1) {
           return generateEOF();
        }
        MORELoop : while (true) {
          this.matchedKind = 0x7FFFFFFF;
          matchedType = null;
          this.matchedPos = 0;
          charsRead = 0;
          kind = 0x7fffffff;
          nfaLoop();
          if (this.matchedKind != 0x7FFFFFFF) { 
            input_stream.backup(charsRead - this.matchedPos - 1);
            if (tokenSet.get(this.matchedKind) || specialSet.get(this.matchedKind)) {
                instantiateToken();
            }
           tokenLexicalActions();
           if (matchedToken != null) break EOFLoop;
           if (skipSet.get(this.matchedKind))
           {
      [#if multipleLexicalStates]
             if (newLexicalStates[this.matchedKind] != null) {
                this.lexicalState = newLexicalStates[this.matchedKind];
           }
      [/#if]
        continue EOFLoop;
      }
      [#if lexerData.hasMore]
        [#if multipleLexicalStates]
          doLexicalStateSwitch(this.matchedKind);
        [/#if]
      charsRead = 0;
      this.matchedKind = 0x7FFFFFFF;
      int retval = input_stream.readChar();
      if (retval >=0) {
            curChar = retval;
            continue MORELoop;
      }
     [/#if]
     }
     matchedToken = handleInvalidChar(curChar);
     break EOFLoop;
    }
   }
   return matchedToken;
  }

  private final void nfaLoop() {
      do {
          currentStates.clear();
          if (charsRead==0) {
            currentStates.set(startStateIndex);
          } else {
            currentStates.or(nextStates);
            int retval = input_stream.readChar();
            if (retval >=0) {
                curChar = retval;
            }
            else break;
          }
          nextStates.clear();
          int nextActive = currentStates.nextSetBit(0);
          while (nextActive != -1) {
            int returnedKind = nfaFunctions[nextActive].applyAsInt(curChar, nextStates);
            kind = Math.min(returnedKind, kind);
            nextActive = currentStates.nextSetBit(nextActive+1);
          } 
          if (kind != 0x7fffffff) {
              this.matchedKind = kind;
              this.matchedPos = charsRead;
              kind = 0x7fffffff;
          }
          ++charsRead;
      } while (!nextStates.isEmpty());
  }

[#--
  Outer loop to generate all the NFA (non-deterministic finite automaton)
  related code for all the various lexical states
--]    
[#list lexerData.lexicalStates as lexicalState]
  [@GenerateNfaStateCode lexicalState/]
[/#list]

static {
  [#list lexerData.lexicalStates as lexicalState]
    NFA_FUNCTIONS_${lexicalState.name}_init();
  [/#list]
}

[#--
  Generate all the NFA transition code
  for the given lexical state
--]
[#macro GenerateNfaStateCode lexicalState]
  [#list lexicalState.allStates as nfaState]
    [#if nfaState.moveRanges.size() >= NFA_RANGE_THRESHOLD]
      [@GenerateMoveArray nfaState/]
    [/#if]
    [#if nfaState.moveCodeNeeded]
      [@GenerateNfaStateMethod nfaState/]
    [/#if]
  [/#list]
  
  static private ToIntBiFunction<Integer,BitSet>[] NFA_FUNCTIONS_${lexicalState.name};

  static private void NFA_FUNCTIONS_${lexicalState.name}_init() {
    NFA_FUNCTIONS_${lexicalState.name} = (ToIntBiFunction<Integer,BitSet>[]) new ToIntBiFunction[${lexicalState.allStates.size()}];
    [#list lexicalState.allStates as state]
      [#if state.moveCodeNeeded]
        NFA_FUNCTIONS_${lexicalState.name}[${state.index}] = ${grammar.lexerClassName}::${state.methodName};
      [/#if]
    [/#list]
    functionTableMap.put(LexicalState.${lexicalState.name}, NFA_FUNCTIONS_${lexicalState.name});
    startStateMap.put(LexicalState.${lexicalState.name}, ${lexicalState.initialState.canonicalState.index});
  }
[/#macro]

[#--
   Generate the array representing the characters
   that this NfaState "accepts".
   This corresponds to the moveRanges field in 
   com.javacc.lexgen.NfaState
--]
[#macro GenerateMoveArray nfaState]
  [#var moveRanges = nfaState.moveRanges]
  [#var arrayName = nfaState.movesArrayName]
    static private int[] ${arrayName} = ${arrayName}_init();

    static private int[] ${arrayName}_init() {
        int[] result = new int[${nfaState.moveRanges.size()}];
        [#list nfaState.moveRanges as char]
          result[${char_index}] = ${char};
        [/#list]
        return result;
    }
[/#macro] 

[#--
   Generate the method that represents the transition
   (or transitions if this is a CompositeStateSet)
   that correspond to an instanceof com.javacc.lexgen.NfaState
--]
[#macro GenerateNfaStateMethod nfaState]  
  [#if !nfaState.composite]
    static int ${nfaState.methodName}(int curChar, BitSet nextStates) {
      [#if nfaState.moveRanges?size >= NFA_RANGE_THRESHOLD]
        int temp;
      [/#if]
      [@GenerateStateMove nfaState false /]
      return 0x7FFFFFFF;
    }
  [#else]
    static int ${nfaState.methodName}(int curChar, BitSet nextStates) {
      int kind = 0x7FFFFFFF, temp;
    [#var states = nfaState.orderedStates]
    [#list states as state]
      [#var jumpOut = state_has_next && state.isNonOverlapping(states.subList(state_index+1, states?size))]
      [@GenerateStateMove state true jumpOut /]
      [#if state_has_next && states[state_index+1].isNonOverlapping(states.subList(0, state_index+1))]
         else
      [/#if]
    [/#list]
      return kind;
    }
  [/#if]
[/#macro]


[#--
  Generates the code for an NFA state transition
  This is a bit messy. It takes the parameters:
  inComposite means that this state move is part of
  a CompositeStateSet. In that case, we use the jumpOut
  parameter to decide whether we can just jump out of the 
  method. (This is based on whether any of the moveRanges
  for later states overlap. If not, we can jump out. This 
  is only relevant if we are in a composite state, of course.)  
  TODO: Clean this up a bit. It's a bit messy and maybe a 
  redundant as well.
--]
[#macro GenerateStateMove nfaState inComposite jumpOut=true]
   [#var nextState = nfaState.nextState.canonicalState]
   [#var kindToPrint=(nfaState.nextState.type.ordinal)!MAX_INT]
    if ([@NfaStateCondition nfaState/]) {
   [#if nextState.composite]
         nextStates.set(${nextState.index});
   [#else]
     [#list (nextState.epsilonMoves)! as epsilonMove]
          nextStates.set(${epsilonMove.index});
     [/#list]
   [/#if]
   [#if !inComposite]
     [#if kindToPrint != MAX_INT]
      return ${kindToPrint};
     [/#if]
   [#else]
      [#if kindToPrint != MAX_INT]
          kind = Math.min(kind, ${kindToPrint});
      [/#if]
      [#if jumpOut]
          return kind;
      [/#if]
   [/#if]
   }
[/#macro]

[#--
Generate the condition part of the NFA state transition
If the size of the moveRanges vector is greater than NFA_RANGE_THRESHOLD
it uses the canned binary search routine. For the smaller moveRanges
it just generates the inline conditional expression
--]
[#macro NfaStateCondition nfaState]
    [#var moveRanges = nfaState.moveRanges]
    [#if moveRanges?size < NFA_RANGE_THRESHOLD]
      [@rangesCondition nfaState.moveRanges /]
    [#else]
      (temp = Arrays.binarySearch(${nfaState.movesArrayName}, curChar)) >=0 || temp%2 ==0
    [/#if]
[/#macro]

[#-- 
This is a recursive macro that generates the code corresponding
to the accepting condition for an NFA state. It is used
if NFA state's moveRanges array is smaller than NFA_RANGE_THRESHOLD
(which is set to 16 for now)
--]
[#macro rangesCondition moveRanges]
    [#var left = moveRanges[0], right = moveRanges[1]]
    [#var singleChar = left == right]
    [#if moveRanges?size==2]
       [#if singleChar]
          curChar == ${left}
       [#elseif left +1 == right]
          curChar == ${left} || curChar == ${right}
       [#else]
          curChar >= ${left} && curChar <= ${right}
       [/#if]
    [#else]
       curChar 
       [#if singleChar]==[#else]>=[/#if]
       ${left} 
       [#if !singleChar]
       && (curChar <= ${right} || ([@rangesCondition moveRanges[2..moveRanges?size-1]/]))
       [#else]
       || ([@rangesCondition moveRanges[2..moveRanges?size-1]/])
       [/#if]
    [/#if]
[/#macro]
