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

  private int jjmatchedPos;
  //FIXME,should be an enum.
  private int jjmatchedKind;
  private TokenType matchedType;
  private String inputSource = "input";
  private final int[] jjstateSet = new int[${2*lexerData.stateSetSize}];
  private int jjnewStateCnt;
  private BitSet checkedStates = new BitSet();

  static private final BitSet tokenSet = ${BitSetFromLongArray(lexerData.tokenSet)},
                                specialSet = ${BitSetFromLongArray(lexerData.specialSet)},
                                skipSet = ${BitSetFromLongArray(lexerData.skipSet)},
                                moreSet = ${BitSetFromLongArray(lexerData.moreSet)};

  
  private final StringBuilder image = new StringBuilder();
  private int curChar, matchedCharsLength;
    
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
[#if multipleLexicalStates]
       switch(lexicalState) {
[/#if]
    
[#list lexerData.lexicalStates as lexicalState]
    [#if multipleLexicalStates]
            case ${lexicalState.name} : 
    [/#if]
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
    curPos = jjMoveNfa_${lexicalState.name}(${lexicalState.numNfaStates}, 0);
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
        final String curTokenImage = input_stream.getImage();
        final int beginLine = input_stream.getBeginLine();
        final int beginColumn = input_stream.getBeginColumn();
        final int endLine = input_stream.getEndLine();
        final int endColumn = input_stream.getEndColumn();
    [#if grammar.settings.TOKEN_FACTORY??]
        final Token t = ${grammar.settings.TOKEN_FACTORY}.newToken(TokenType.values()[jjmatchedKind], curTokenImage, inputSource);
    [#elseif !grammar.hugeFileSupport]
        final Token t = Token.newToken(TokenType.values()[jjmatchedKind], curTokenImage, this);
    [#else]
        final Token t = Token.newToken(TokenType.values()[jjmatchedKind], curTokenImage, inputSource);
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

  [#var needNextStep = false]

  [#list lexerData.lexicalStates as lexicalState]
      [#list lexicalState.allStates as nfaState]
        [#if nfaState.singleChar]
           private static final boolean ${nfaState.moveMethodName} (int ch) {
             return ch == ${nfaState.moveRanges[0]};
           }
        [#elseif nfaState.moveRanges.size() == 2]
           private static final boolean ${nfaState.moveMethodName} (int ch) {
             return ch >= ${nfaState.moveRanges[0]} && ch <= ${nfaState.moveRanges[1]};
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
          [#set needNextStep = true]
          [#var arrayName = nfaState.movesArrayName]

          static private int[] ${arrayName};

          static private void ${arrayName}_populate() {
              ${arrayName} = new int[${nfaState.moveRanges.size()}];
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
      [#list lexicalState.allStates as nfaState]
        [#if nfaState.moveRanges.size() >= 16]
          ${nfaState.movesArrayName}_populate();
        [/#if]
        [/#list]
      [/#list]
    }
    [/#if]

  private final void addStates(int[] stateSet) {
      for (int i=0; i< stateSet.length; i++) {
         if (!checkedStates.get(stateSet[i])) {
             jjstateSet[jjnewStateCnt++] = stateSet[i];
             checkedStates.set(stateSet[i]);
         }
      }
  }
  
[#macro DumpMoveNfa lexicalState]
    private int jjMoveNfa_${lexicalState.name}(int startState, int curPos) {
        int startsAt = 0;
        jjnewStateCnt = ${lexicalState.numNfaStates};
        int stateIndex=1;
        jjstateSet[0] = startState;
        int kind = 0x7fffffff;
        while (true) {
          checkedStates.clear();
	         do {
	             switch (jjstateSet[--stateIndex]) {
	                 [@DumpMoves lexicalState/]
                     default : break;
                }
            } while(stateIndex != startsAt);
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
            if (stateIndex == startsAt) {
              return curPos;
            }
            int retval = input_stream.readChar();
            if (retval >=0) {
                 curChar = retval;
            }
            else  {
                return curPos;
            }
            if (trace_enabled) LOGGER.info("" + 
            [#if multipleLexicalStates]
               "<" + lexicalState + ">" + 
            [/#if]
               [#-- REVISIT --]
               addEscapes(String.valueOf(curChar)) + " (" + curChar + ") "
              + "at line " + input_stream.getEndLine() + " column " + input_stream.getEndColumn());
        }
    }
[/#macro]

[#macro DumpMoves lexicalState]
  //DumpMoves macro for lexicalState ${lexicalState.name}
      case ${lexicalState.numNfaStates} :
        [#list lexicalState.initialState.epsilonMoves as state]
             [@DumpMove state /]
        [/#list]
          break;
   [#list lexicalState.allStates as state]
       case ${state.index} :
         [@DumpMove state /]
         break;
   [/#list]
[/#macro]

[#macro DumpMove nfaState]
   [#var statesToAdd = (nfaState.nextState.epsilonMoves.size())!0]
   [#var kindToPrint=(nfaState.nextState.type.ordinal)!MAX_INT]
   [#if statesToAdd == 0 && kindToPrint == MAX_INT][#return][/#if]
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
