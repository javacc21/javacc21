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

  private int matchedPos, charsRead;
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

  private int curChar, pendingMoreChars;
    
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
    EOFLoop :
    while (true) {
        curChar = input_stream.beginToken();
        if (curChar == -1) {
           return generateEOF();
        }
        pendingMoreChars = 0;
       MORELoop : while (true) {
       switch(lexicalState) {
[#list lexerData.lexicalStates as lexicalState]
            case ${lexicalState.name} : 
                this.matchedKind = 0x7FFFFFFF;
                matchedType = null;
                this.matchedPos = 0;
                if (!checkMemoization(memoizationCache_${lexicalState.name})) {
                  moveNfa_${lexicalState.name}();
                }
                break;
[/#list]
      }
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
          pendingMoreChars += this.matchedPos + 1;
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

    private static final int MAX_LENGTH_TO_MEMOIZE = 32;
    static private Integer PARTIAL_MATCH = 0xFFFFFFFF;

    private boolean checkMemoization(Map<String, Integer> cache) {
[#if !grammar.hugeFileSupport]      
      StringBuilder buf = new StringBuilder();
      buf.appendCodePoint(curChar);
      int codePointsRead = 0;
      String key = null;
      Integer value = null;
      int previousPosition = input_stream.getBufferPosition();
      while (buf.length() < MAX_LENGTH_TO_MEMOIZE) {
        key = buf.toString();
        value = cache.get(key);
        if (value == null) {
            break;
        }
        else if (value == PARTIAL_MATCH) {
           int ch = input_stream.readChar();
           if (ch >=0) {
             codePointsRead++;
             buf.appendCodePoint(ch);
           } 
           else break;
        } 
        else {
          matchedPos = (int) (value % 1000);
          matchedKind = (int) (value/1000);
          charsRead = codePointsRead+1;
          return true;
        }
      }
      backup(codePointsRead); 
[/#if]      
      return false;
    }
  [#var NFA_RANGE_THRESHOLD = 16]

  [#list lexerData.lexicalStates as lexicalState]
      [#list lexicalState.allStates as nfaState]
        [#var moveRanges = nfaState.moveRanges]
        [#if moveRanges.size() >= NFA_RANGE_THRESHOLD]
          [#var arrayName = nfaState.movesArrayName]
          static private int[] ${arrayName} = ${arrayName}_init();

          static private int[] ${arrayName}_init() {
              int[] result = new int[${nfaState.moveRanges.size()}];
              [#list nfaState.moveRanges as char]
                result[${char_index}] = ${char};
              [/#list]
              return result;
          }
        [/#if]
      [/#list]
    static private Map<String,Integer> memoizationCache_${lexicalState.name} = new Hashtable<>();
  [/#list]

[#macro DumpMoveNfa lexicalState]
    private final void moveNfa_${lexicalState.name}() {
        charsRead = 0;
        int kind = 0x7fffffff;
        nextStates = new BitSet();
        while (true) {
            int temp = 0;
            currentStates = nextStates;
            nextStates = new BitSet();
            int nextActive = -1;
            if (charsRead == 0) {
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
                this.matchedPos = charsRead;
                kind = 0x7fffffff;
            }
            ++charsRead;
            if (nextStates.isEmpty()) {
              if (matchedKind != 0x7FFFFFFF && pendingMoreChars == 0) {
                int value = matchedKind * 1000 + matchedPos;
                String image = input_stream.getImage();
                if (image.length() <= MAX_LENGTH_TO_MEMOIZE) {
                    memoizationCache_${lexicalState.name}.put(image, value);
                    for (int i = image.length()-1; i>=0 ; i--) {
                       Integer previous = memoizationCache_${lexicalState.name}.put(image.substring(0, i), PARTIAL_MATCH);
                       if (previous != null) break;
                    }
                }
              }
              break;
            }
            int retval = input_stream.readChar();
            if (retval >=0) {
                 curChar = retval;
            }
            else break;
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
   [#var kindToPrint=(nfaState.nextState.type.ordinal)!MAX_INT]
    if ([@nfaStateCondition nfaState/]) {
   [#if kindToPrint != MAX_INT]
       kind = Math.min(kind, ${kindToPrint});
   [/#if]
   [#list (nfaState.nextState.epsilonMoves)! as epsilonMove]
          nextStates.set(${epsilonMove.index});
   [/#list]
   }
[/#macro]

[#macro nfaStateCondition nfaState]
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

[#list lexerData.lexicalStates as lexicalState]
   [@DumpMoveNfa lexicalState/]
[/#list]

[#macro BitSetFromLongArray bitSet]
      BitSet.valueOf(new long[] {
          [#list bitSet.toLongArray() as long]
             ${grammar.utils.toHexStringL(long)}
             [#if long_has_next],[/#if]
          [/#list]
      })
[/#macro]
