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

 [#var MAX_INT=2147483647]
 [#var lexerData=grammar.lexerData]
 [#var multipleLexicalStates = lexerData.lexicalStates.size()>1]

  private int matchedPos;
  //FIXME,should be an enum.
  private int matchedKind;
  private Token matchedToken;
  private TokenType matchedType;
  private String inputSource = "input";
  private BitSet nextStates=new BitSet(), currentStates = new BitSet();

  static private final BitSet tokenSet = ${BitSetFromLongArray(lexerData.tokenSet)},
                                specialSet = ${BitSetFromLongArray(lexerData.specialSet)},
                                skipSet = ${BitSetFromLongArray(lexerData.skipSet)},
                                moreSet = ${BitSetFromLongArray(lexerData.moreSet)};

  private final StringBuilder image = new StringBuilder();
  private int curChar, matchedCharsLength;
    
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
  
  private Token nextToken() {
    matchedToken = null;
    int curPos = 0;
    EOFLoop :
    while (true) {
        curChar = input_stream.beginToken();
        if (curChar == -1) {
           return generateEOF();
        }
       image.setLength(0);
       matchedCharsLength = 0;
       MORELoop : while (true) {
       switch(lexicalState) {
[#list lexerData.lexicalStates as lexicalState]
            case ${lexicalState.name} : 
                this.matchedKind = 0x7FFFFFFF;
                matchedType = null;
                this.matchedPos = 0;
                curPos = moveNfa_${lexicalState.name}();
                break;
[/#list]
      }
   if (this.matchedKind != 0x7FFFFFFF) { 
      handleMatch(curPos);
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
          [#if !lexerData.hasMoreActions]
             matchedCharsLength += this.matchedPos + 1;
		     [/#if]
         [#if multipleLexicalStates]
             doLexicalStateSwitch(this.matchedKind);
         [/#if]
          curPos = 0;
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

  private void handleMatch(int curPos) {
      matchedToken = null;
      input_stream.backup(curPos - this.matchedPos - 1);
      if (tokenSet.get(this.matchedKind) || specialSet.get(this.matchedKind)) {
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
  }

  private void tokenLexicalActions() {
       switch(matchedKind) {
   [#list lexerData.regularExpressions as regexp]
        [#if regexp.codeSnippet?has_content]
		  case ${regexp.ordinal} :
            [#if regexp.ordinal = 0]
              image.setLength(0); // For EOF no chars are matched
            [#else]
              image.append(input_stream.getSuffix(matchedCharsLength + this.matchedPos + 1));
            [/#if]
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
     [#if false]
        t.setLexicalState(lexicalState);
     [/#if]        
        return t;
    }

  [#list lexerData.lexicalStates as lexicalState]
      [#list lexicalState.allStates as nfaState]
        [#if nfaState.moveRanges.size() == 2]
           private static final boolean ${nfaState.moveMethodName} (int ch) {
             [#if nfaState.moveRanges[0] == nfaState.moveRanges[1]]
                return ch == ${nfaState.moveRanges[0]};
             [#else]
                return ch >= ${nfaState.moveRanges[0]} && ch <= ${nfaState.moveRanges[1]};
             [/#if]
           }
        [#elseif nfaState.moveRanges.size() < 16]  
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
          [#var arrayName = nfaState.movesArrayName]
          static private int[] ${arrayName} = ${arrayName}_init();

          static private int[] ${arrayName}_init() {
              int[] result = new int[${nfaState.moveRanges.size()}];
              [#list nfaState.moveRanges as char]
                result[${char_index}] = ${char};
              [/#list]
              return result;
          }

          private static final boolean ${nfaState.moveMethodName}(int ch) {
              int idx = Arrays.binarySearch(${arrayName}, ch);
              return idx>=0 || idx%2==0;
          }
          [/#if]
      [/#list]
  [/#list]

  private final void addStates(int[] set) {
      for (int i=0; i< set.length; i++) {
         nextStates.set(set[i]);
      }
  }
  
[#macro DumpMoveNfa lexicalState]
    private int moveNfa_${lexicalState.name}() {
        int curPos = 0;
        int kind = 0x7fffffff;
        nextStates = new BitSet();
        while (true) {
            currentStates = nextStates;
            nextStates = new BitSet();
            int nextActive = -1;
            if (curPos == 0) {
              [#list lexicalState.initialState.epsilonMoves as state]
                  [@DumpMove state /]
              [/#list]
            }
	          else do {
              nextActive = currentStates.nextSetBit(nextActive+1);
              if (nextActive != -1) {
                switch(nextActive) {
                  [@DumpMoves lexicalState/]
                  default : break;
                }
              }
            } while (nextActive != -1);
            if (kind != 0x7fffffff) {
                this.matchedKind = kind;
                this.matchedPos = curPos;
                kind = 0x7fffffff;
            }
            ++curPos;
            if (nextStates.isEmpty()) {
              return curPos;
            }
            int retval = input_stream.readChar();
            if (retval >=0) {
                 curChar = retval;
            }
            else  {
                return curPos;
            }
        }
    }
[/#macro]

[#macro DumpMoves lexicalState]
  //DumpMoves macro for lexicalState ${lexicalState.name}
   [#list lexicalState.allStates as state]
     [#if NeedDumpMove(state)]
       case ${state.index} :
         [@DumpMove state /]
         break;
     [/#if]
   [/#list]
[/#macro]

[#function NeedDumpMove nfaState]
   [#var statesToAdd = (nfaState.nextState.epsilonMoves.size())!0]
   [#var kindToPrint=(nfaState.nextState.type.ordinal)!MAX_INT]
   [#return statesToAdd>0 || kindToPrint!=MAX_INT]
[/#function]

[#macro DumpMove nfaState]
   [#if !NeedDumpMove(nfaState)][#return][/#if]
   [#var statesToAdd = (nfaState.nextState.epsilonMoves.size())!0]
   [#var kindToPrint=(nfaState.nextState.type.ordinal)!MAX_INT]
    if (${nfaState.moveMethodName}(curChar)) {
   [#if kindToPrint != MAX_INT]
       kind = Math.min(kind, ${kindToPrint});
   [/#if]
      [#if statesToAdd >0]
       addStates(nextStates_${nfaState.lexicalState.name}_${nfaState.index});
      [/#if]
   }
[/#macro]

[#list lexerData.lexicalStates as lexicalState]
   [@OutputNextStates lexicalState/] 
   [@DumpMoveNfa lexicalState/]
[/#list]

[#macro OutputNextStates lexicalState]
   [#list lexicalState.allStates as state]
       static private final int[] nextStates_${lexicalState.name}_${state.index} = {
        [#var nextStateEpsilonMoves = (state.nextState.epsilonMoves)![]]
        [#list nextStateEpsilonMoves as epsilonMove]
            ${epsilonMove.index}
            [#if epsilonMove_has_next],[/#if]
        [/#list]
      };
   [/#list]
[/#macro]

[#macro BitSetFromLongArray bitSet]
      BitSet.valueOf(new long[] {
          [#list bitSet.toLongArray() as long]
             ${grammar.utils.toHexStringL(long)}
             [#if long_has_next],[/#if]
          [/#list]
      })
[/#macro]
