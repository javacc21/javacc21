[#--
/* Copyright (c) 2008-2019 Jonathan Revusky, revusky@javacc.com
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

[#var lexerData=grammar.lexerData]
[#var options=grammar.options]
[#var numLexicalStates=lexerData.lexicalStates?size]
[#var tokenCount=lexerData.tokenCount]
[#var MAX_INT=2147483647]
[#if grammar.parserPackage?has_content]
package ${grammar.parserPackage};
[/#if]

[#list grammar.parserCodeImports as import]
   ${import}
[/#list]

import java.io.Reader;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.*;

@SuppressWarnings("unused")
public class ${grammar.lexerClassName} implements ${grammar.constantsClassName} {
  private static final Logger LOGGER = Logger.getLogger("${grammar.parserClassName}");
  
[#if options.faultTolerant]  
  private InvalidToken invalidToken; 
[/#if]

void addToken(Token token) {
    [#if !options.hugeFileSupport]
       input_stream.addToken(token);
    [/#if]  
}  
    int tabSize = 8;
 [#if options.lexerUsesParser]

  public ${grammar.parserClassName} parser;
[/#if]

  int[] jjemptyLineNo = new int[${numLexicalStates}];
  int[] jjemptyColNo = new int[${numLexicalStates}];
  boolean[] jjbeenHere = new boolean[${numLexicalStates}];
  
  
  private int jjnewStateCnt;
  private int jjround;
  private int jjmatchedPos;
  private int jjmatchedKind;
  private String inputSource = "input";
  
[#if grammar.options.debugLexer]  
  private boolean trace_enabled = true;
[#else]  
  private boolean trace_enabled = false;
[/#if]
  
  private void setTracingEnabled(boolean trace_enabled) {
     this.trace_enabled = trace_enabled;
  }
  
  public String getInputSource() {
      return inputSource;
  }
  
  public void setInputSource(String inputSource) {
      this.inputSource = inputSource;
  }
   
  private LexicalState lexicalState = LexicalState.${lexerData.lexicalStates[0].name};
  
[#if numLexicalStates>1]


   void doLexicalStateSwitch(int tokenType) {
       LexicalState newLexState = newLexicalStates[tokenType];
       if (newLexState != null) {
           switchTo(newLexState);
       }
   }
   
   void doLexicalStateSwitch(TokenType tokenType) {
       doLexicalStateSwitch(tokenType.ordinal());
   }
  
  private static final LexicalState[] newLexicalStates = {
         [#list lexerData.regularExpressions as regexp]
             [#if regexp.newLexicalState?is_null]
                null,
             [#else]
                LexicalState.${regexp.newLexicalState.name},
             [/#if]
          [/#list]
  };
  
[/#if]

[#if lexerData.hasSkip || lexerData.hasMore || lexerData.hasSpecial]
      // BitSet for TOKEN
      static private BitSet tokenSet = BitSet.valueOf(new long[] {
          [#list lexerData.tokenSet.toLongArray() as long]${long}L,[/#list]
      });
[/#if]

[#if lexerData.hasSkip || lexerData.hasSpecial]
      // BitSet for SKIP
      static private BitSet skipSet = BitSet.valueOf(new long[] {
          [#list lexerData.skipSet.toLongArray() as long]${long}L,[/#list]
      });
[/#if]

[#if lexerData.hasSpecial]
      // BitSet for SPECIAL
      static private BitSet specialSet = BitSet.valueOf(new long[] {
          [#list lexerData.specialSet.toLongArray() as long]${long}L,[/#list]
      });      
[/#if]


[#if lexerData.hasMore]
      // BitSet for MORE
      static private BitSet moreSet = BitSet.valueOf(new long[] {
          [#list lexerData.moreSet.toLongArray() as long]${long}L,[/#list]
      });      
[/#if]

    private final int[] jjrounds = new int[${lexerData.stateSetSize}];
    private final int[] jjstateSet = new int[${2*lexerData.stateSetSize}];

[#if lexerData.hasActions()]
    private final StringBuilder image = new StringBuilder();
    private int matchedCharsLength;
[/#if]

    char curChar;
    
[#var tokenBuilderClass = options.hugeFileSupport?string("TokenBuilder", "FileLineMap")]

${tokenBuilderClass} input_stream;

public final void backup(int amount) {
    input_stream.backup(amount);
}

[#if !options.hugeFileSupport]
     public ${grammar.lexerClassName}(String inputSource, CharSequence chars) {
        this(inputSource, chars, LexicalState.${lexerData.lexicalStates[0].name}, 1, 1);
     }
     public ${grammar.lexerClassName}(String inputSource, CharSequence chars, LexicalState lexState, int line, int column) {
        input_stream = new ${tokenBuilderClass}(inputSource, chars, line, column);
        switchTo(lexState);
     }
     [#if options.legacyAPI]
         public ${grammar.lexerClassName}(String inputSource, CharSequence chars, int lexState, int line, int column) {
            this(inputSource, chars, LexicalState.values()[lexState],  line, column);
         }
     [/#if]
[/#if]
    public ${grammar.lexerClassName}(Reader reader) {
       this(reader, LexicalState.${lexerData.lexicalStates[0].name}, 1, 1);
    }
    public ${grammar.lexerClassName}(Reader reader, LexicalState lexState, int line, int column) {
        input_stream = new ${tokenBuilderClass}(reader, line, column);
        switchTo(lexState);
    }
     [#if options.legacyAPI]
         public ${grammar.lexerClassName}(Reader reader, int lexState, int line, int column) {
            this(reader, LexicalState.values()[lexState],  line, column);
         }
     [/#if]

    
    // Method to reinitialize the jjrounds array.
    private void ReInitRounds() {
       int i;
       jjround = 0x80000001;
       for (i = ${lexerData.stateSetSize}; i-- > 0;) 
          jjrounds[i] = 0x80000000;
    }


    /** Switch to specified lexical state. */
    public void switchTo(LexicalState lexState) {
        if (this.lexicalState != lexState) {
           if (trace_enabled) LOGGER.info("Switching from lexical state " + this.lexicalState + " to " + lexState);
        }
        this.lexicalState = lexState;
    }
[#if grammar.options.legacyAPI]
    /**
      * @deprecated Use the switchTo method.. that takes an Enum
      */
    @Deprecated
    public void SwitchTo(int lexState) {
       switchTo(LexicalState.values()[lexState]);
    }
    
    
    @Deprecated
    public void setTabSize(int  size) {this.tabSize=tabSize;}
[/#if]

 [#if grammar.options.faultTolerant]
  public Token getNextToken() {
      Token tok = null;
      do {
         tok = nextToken();
      }  while (tok instanceof InvalidToken);
      if (invalidToken != null) {
          addToken(invalidToken);
          invalidToken = null;
      }
      addToken(tok);
      return tok;
 }
[/#if]
 
  
  [#--  Need to figure out how to simplify this --]
[#if grammar.options.faultTolerant]  
  Token nextToken() {
[#else]
  public Token getNextToken() {
[/#if]    
    Token specialToken = null;
    Token matchedToken;
    int curPos = 0;

    EOFLoop :
    while (true) {
        int retval1 = input_stream.beginToken();
        curChar = (char) (retval1);
         if (retval1 == -1) { // Handle end of file
            if (trace_enabled) LOGGER.info("Returning the <EOF> token.");
			jjmatchedKind = 0;
            Token eof = jjFillToken();
            tokenLexicalActions();
[#if grammar.usesCommonTokenAction]
            CommonTokenAction(eof);
[/#if]
[#if grammar.usesTokenHook]
            eof = tokenHook(eof);
[/#if]
		    eof.specialToken = specialToken;
		    addToken(eof);
    		return eof;
       }

[#if lexerData.hasActions()]
       image.setLength(0);
       matchedCharsLength = 0;
[/#if]

[#if lexerData.hasMore]
       while (true) {
[/#if]
    [#-- this also sets up the start state of the nfa --]
[#if numLexicalStates>1]
       switch(lexicalState) {
[/#if]
    
[#list lexerData.lexicalStates as lexicalState]
    [#var singlesToSkip=lexicalState.singlesToSkip]
    [#if numLexicalStates>1]
            case ${lexicalState.name} : 
    [/#if]
    [#if singlesToSkip.hasTransitions()]
          [#if singlesToSkip.asciiMoves[0] != 0&&singlesToSkip.asciiMoves[1] != 0]
                 while ((curChar < 64 && (${utils.toHexString(singlesToSkip.asciiMoves[0])}
                  & (1L <<curChar)) != 0L) || (curChar>>6) == 1 && (${utils.toHexStringL(singlesToSkip.asciiMoves[1])} 
                  & (1L << (curChar & 077))) != 0L)
          [#elseif singlesToSkip.asciiMoves[1] = 0]
                 while (curChar <= ${lexerData.maxChar(singlesToSkip.asciiMoves[0])} 
                        && (${utils.toHexStringL(singlesToSkip.asciiMoves[0])}
                        & (1L << curChar)) != 0L) 
          [#elseif singlesToSkip.asciiMoves[0] = 0]
                 while (curChar > 63 && curChar <= ${lexerData.MaxChar(singlesToSkip.asciiMoves[1])+64}
                        && ${utils.toHexString(singlesToSkip.asciiMoves[1])}L & (1L<< (curChar&077))) != 0L)
          [/#if]
                 {
                   [#var debugOutput]
                   [#set debugOutput]
                   [#if numLexicalStates>1]
                   "<" + lexicalState + ">" + 
                   [/#if]
                   "Skipping character : " + ParseException.addEscapes(String.valueOf(curChar)) + " (" + (int) curChar + ")"
                   [/#set] 
                    if (trace_enabled) LOGGER.info(${debugOutput?trim}); 
                    curChar = (char) input_stream.beginToken();
                    if (curChar == (char) -1) {
                        continue EOFLoop;
                    }
                }
    [/#if]    
             
             
    [#if lexicalState.initMatch != MAX_INT&&lexicalState.initMatch != 0]
        if (trace_enabled) LOGGER.info("   Matched the empty string as " + tokenImage[${lexicalState.initMatch}] + " token.");
        jjmatchedKind = ${lexicalState.initMatch};
        jjmatchedPos = -1;
        curPos = 0;
    [#else]
        jjmatchedKind = 0x7FFFFFFF;
        jjmatchedPos = 0;
    [/#if]
        [#var debugOutput]
        [#set debugOutput]
            [#if numLexicalStates>1]
               "<" + lexicalState + ">" + 
            [/#if]
            "Current character : " + ParseException.addEscapes(String.valueOf(curChar)) + " (" + (int) curChar + ") " +
            "at line " + input_stream.getEndLine() + " column " + input_stream.getEndColumn()
        [/#set]
        if (trace_enabled) LOGGER.info(${debugOutput?trim}); 
        curPos = jjMoveStringLiteralDfa0${lexicalState.suffix}();
    [#if lexicalState.matchAnyChar??]
         [#if lexicalState.initMatch != MAX_INT&&lexicalState.initMatch != 0]
        if (jjmatchedPos < 0 || (jjmatchedPos == 0 && jjmatchedKind > ${lexicalState.canMatchAnyChar}))
         [#else]
        if (jjmatchedPos == 0 && jjmatchedKind > ${lexicalState.canMatchAnyChar})
        [/#if]
        {
        if (trace_enabled) LOGGER.info("    Current character matched as a " + tokenImage[${lexicalState.canMatchAnyChar}] + " token."); 
        jjmatchedKind = ${lexicalState.canMatchAnyChar};
        [#if lexicalState.initMatch != MAX_INT&&lexicalState.initMatch != 0]
        jjmatchedPos = 0;
        [/#if]
      }
    [/#if]

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
          + ParseException.addEscapes(input_stream.getSuffix(jjmatchedPos + 1)) + ") ******\n");
 
 [#if lexerData.hasSkip || lexerData.hasMore || lexerData.hasSpecial]
          if (tokenSet.get(jjmatchedKind)) {
 [/#if]

         matchedToken = jjFillToken();
 [#if grammar.usesTokenHook]
      matchedToken = tokenHook(matchedToken);
 [/#if]
 

 [#if lexerData.hasSpecial]
         matchedToken.specialToken = specialToken;
 [/#if]

 [#if lexerData.hasTokenActions]
      tokenLexicalActions();
 [/#if]

 [#if grammar.usesCommonTokenAction]
      CommonTokenAction(matchedToken);
 [/#if]
 jjmatchedKind = matchedToken.getKind();
 
 [#if numLexicalStates>1]
      if (newLexicalStates[jjmatchedKind] != null) {
          switchTo(newLexicalStates[jjmatchedKind]);
      }
 [/#if]
 addToken(matchedToken);
 return matchedToken;

      [#if lexerData.hasSkip || lexerData.hasMore || lexerData.hasSpecial]
     }


         [#if lexerData.hasSkip || lexerData.hasSpecial]
          
            [#if lexerData.hasMore]
          else if (skipSet.get(jjmatchedKind))
            [#else]
          else
            [/#if]

          {

            [#if lexerData.hasSpecial]
          
            if (specialSet.get(jjmatchedKind)) {
              matchedToken = jjFillToken();
              matchedToken.setUnparsed(true);

              if (specialToken == null) {
                specialToken = matchedToken;
              }
              else {
                 matchedToken.specialToken=specialToken;
                 specialToken.setNext(matchedToken);
                 specialToken = matchedToken;
                 addToken(specialToken);
               }

              [#if lexerData.hasSkipActions]
              tokenLexicalActions();
              [/#if]
          }
      
              [#if lexerData.hasSkipActions]
              else 
                 tokenLexicalActions();
              [/#if]
          [#elseif lexerData.hasSkipActions]
            tokenLexicalActions();
          [/#if]

          [#if numLexicalStates>1]
            if (newLexicalStates[jjmatchedKind] != null) {
               this.lexicalState = newLexicalStates[jjmatchedKind];
            }
          [/#if]

            continue EOFLoop;
          }
         [/#if]

         [#if lexerData.hasMore]
          [#if lexerData.hasMoreActions]
          tokenLexicalActions();
          [#elseif lexerData.hasSkipActions || lexerData.hasTokenActions]
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
	              "Current character : " + ParseException.addEscapes(String.valueOf(curChar)) + " (" + (int) curChar + ") " +
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
  [#if options.faultTolerant]
    if (invalidToken == null) {
       invalidToken = new InvalidToken(""+ curChar);
       invalidToken.setBeginLine(error_line);
       invalidToken.setBeginColumn(error_column);
    } else {
       invalidToken.image = invalidToken.image + curChar;
    }
  [#else]
    Token invalidToken = new InvalidToken("" + curChar);
    invalidToken.specialToken = specialToken;
    invalidToken.setBeginLine(error_line);
    invalidToken.setBeginColumn(error_column);
 [/#if]
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
   [#list 0..(tokenCount-1) as i]
      [#var regexp=lexerData.getRegularExpression(i)]
      [#var jumpOut]
      [#if lexerData.hasTokenAction(i) || lexerData.hasMoreAction(i) || lexerData.hasSkipAction(i)]
        [#var act=regexp.codeSnippet]
        [#var lexicalState=regexp.lexicalState]
        [#set jumpOut = (!act?? || !act.javaCode?has_content)&&!lexicalState.canLoop]
        [#if !jumpOut]
		  case ${i} : 
          [#if lexicalState.initMatch = i&&lexicalState.canLoop]
             [#-- Do we ever enter this block? If so, when? (JR) --]
             [#var lexicalStateIndex=lexerData.getIndex(lexicalState.name)]
              if (jjmatchedPos == -1) {
                 if (jjbeenHere[${lexerData.getIndex(lexicalState.name)}] &&
                     jjemptyLineNo[${lexicalStateIndex}] == input_stream.getBeginLine() && 
                     jjemptyColNo[${lexicalStateIndex}] == input_stream.getBeginColumn())
                          throw new RuntimeException("Error: Bailing out of infinite loop caused by repeated empty string matches " +
                             "at line " + input_stream.getBeginLine() + ", " +
                             "column " + input_stream.getBeginColumn());
                 jjemptyLineNo[${lexicalStateIndex}] = input_stream.getBeginLine();
                 jjemptyColNo[${lexicalStateIndex}] = input_stream.getBeginColumn();
                 jjbeenHere[${lexicalStateIndex}] = true;
              }              
          [/#if]
		  [#if act??&&act.javaCode?has_content]
            [#if i = 0]
              image.setLength(0); // For EOF no chars are matched
            [#else]
              image.append(input_stream.getSuffix(matchedCharsLength + jjmatchedPos + 1));
            [/#if]
		      ${act.javaCode}
		  [/#if]
        [/#if]
        [#if !jumpOut]
            break;
        [/#if]
      [/#if]
   [/#list]
           default : break;
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
        t = ${options.tokenFactory}.newToken(jjmatchedKind, curTokenImage);
    [#else]
        t = Token.newToken(jjmatchedKind, curTokenImage);
    [/#if]
        t.beginLine = beginLine;
        t.endLine = endLine;
        t.beginColumn = beginColumn;
        t.endColumn = endColumn;
        t.setInputSource(this.inputSource);
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
    
    
    protected static final int[][] kindForState =
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

  static final int[] jjnextStates = {
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

 [#if options.hugeFileSupport]
    [#embed "LegacyTokenBuilder.java.ftl"]
 [/#if]
  
}


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
            "Current character : " + ParseException.addEscapes(String.valueOf(curChar)) + " (" + (int)curChar + ") "
           + "at line " + input_stream.getEndLine() + " column " + input_stream.getEndColumn());
    [/#if]
        int kind = 0x7fffffff;
        while (true) {
            if (++jjround == 0x7fffffff) {
                ReInitRounds();
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
               ParseException.addEscapes(String.valueOf(curChar)) + " (" + (int)curChar + ") "
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
   [#set statesDumped = utils.newBitSet()]
   [#list lexicalState.compositeStateTable?keys as key]
      [@dumpCompositeStatesMoves lexicalState, key, byteNum, statesDumped/]
   [/#list]
   [@dumpMoves lexicalState, byteNum, statesDumped/]
[/#macro]

[#macro dumpMoves lexicalState byteNum statesDumped]
   [#list lexicalState.allStates as state]
      [#if state.index>=0&&!statesDumped.get(state.index)&&state.hasTransitions()]
          [#var toPrint=""]
          [#var stateForCaseHandled=false]
          [#if !state.stateForCase?is_null]
              [#set stateForCaseHandled = statesDumped.get(state.stateForCase.index) || state.inNextOf = 1]
              [#if !stateForCaseHandled]
                  [#var stateForCase=state.stateForCase]
                  ${statesDumped.set(stateForCase.index)!}
                  [#if stateForCase.isNeeded(byteNum)]
                  case ${stateForCase.index} :
                  [#else]
                    [#set toPrint = "case "+stateForCase.index+" : "]
                  [/#if]
              [/#if] 
          [/#if]
          [#if !stateForCaseHandled]
              [#if state.isNeeded(byteNum)]
                  ${toPrint}
                  ${statesDumped.set(state.index)!}
                  case ${state.index} :
                  [@dumpMove state, byteNum, statesDumped/]
              [#elseif !state.stateForCase?is_null&&toPrint = ""]
                     break;
              [/#if]
          [/#if]
      [/#if]
   [/#list]
[/#macro]

[#macro dumpMove nfaState byteNum statesDumped]
   [#var nextIntersects=nfaState.composite || nfaState.nextIntersects]
   [#var onlyState=(byteNum>=0)&&nfaState.isOnlyState(byteNum)]
   [#var lexicalState=nfaState.lexicalState]
   [#var kindToPrint=nfaState.kindToPrint]
   [#list nfaState.getMoveStates(byteNum, statesDumped) as state]
                   case ${state.index} :
   [/#list]
   [#var oneBit=0]
   [#if (byteNum>=0)]
       [#set oneBit = nfaState.OnlyOneBitSet(nfaState.asciiMoves[byteNum])]
   [/#if]
   [#if byteNum<0 || nfaState.asciiMoves[byteNum] != -1]
      [#if nfaState.next?is_null || nfaState.next.usefulEpsilonMoves<=0]
          [#var kindCheck=" && kind > "+kindToPrint]
          [#if onlyState][#set kindCheck = ""][/#if]
          [#if byteNum>=0]
             [#if oneBit != -1]
                     if (curChar == ${(64*byteNum+oneBit)} ${kindCheck})
             [#else]
                     if ((${utils.toHexStringL(nfaState.asciiMoves[byteNum])} & l) != 0L ${kindCheck})
             [/#if]
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
          [#if oneBit != -1]
                    if (curChar != ${64*byteNum+oneBit})
                          break;
          [#elseif nfaState.asciiMoves[byteNum] != -1]
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
       [#if oneBit != -1]
                    if (curChar == ${64*byteNum+oneBit})
       [#elseif nfaState.asciiMoves[byteNum] != -1]
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
   [#var toBePrinted stateForCase toPrint=""]
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
       [#if !state.stateForCase?is_null]
          [#set stateForCase = state.stateForCase]
       [/#if]
   [/#list]
   [#if stateForCase??]
        ${statesDumped.set(stateForCase.index)!}
        [#if state.stateForCase.isNeeded(byteNum)]
           case ${index} :
               [@dumpMoveForCompositeState nfaState, byteNum, false/]
           [#set toPrint = "case "+index+":"] 
        [#else]
           [#set toPrint = ""]
        [/#if]
   [/#if]
   [#if neededStates = 0]
        [#if stateForCase??&&toPrint = ""]
               break;
        [/#if]
        [#return]
   [/#if]
   [#if neededStates = 1]
          ${toPrint}
          case ${lexicalState.stateIndexFromComposite(key)} :
      [#if !statesDumped.get(toBePrinted.index)&&toBePrinted.inNextOf>1]
          case ${toBePrinted.index} :
      [/#if]
              ${statesDumped.set(toBePrinted.index)!}
              [@dumpMove toBePrinted, byteNum, statesDumped/]
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
            [#var atStart=true]
            [#list subSet as state]
              [@dumpMoveForCompositeState state, byteNum, !atStart/]
              [#set atStart = false]
            [/#list]
         [/#list]
   [#else]
         [#list stateSet as state]
            [#if state.isNeeded(byteNum)]
               [#if hasStateBlock]
                  ${statesDumped.set(state.index)!}
                  [@dumpMoveForCompositeState state, byteNum, false/]
                  [#-- ${state.dumpMoveForCompositeState(byteNum, false)} --]
               [/#if]
            [/#if]
         [/#list]
   [/#if]
                  break;
[/#macro]

[#macro dumpMoveForCompositeState nfaState byteNum elseNeeded]
   [#var nextIntersects=nfaState.nextIntersects]
   [#var kindToPrint=nfaState.kindToPrint asciiMoves=nfaState.asciiMoves loByteVec=nfaState.loByteVec next=nfaState.next lexicalState=nfaState.lexicalState]
   [#if (byteNum>=0)]
      [#if byteNum<0 || nfaState.asciiMoves[byteNum] != -1]
         [#var oneBit=nfaState.OnlyOneBitSet(asciiMoves[byteNum])]
         [#if oneBit != -1]
               [#if elseNeeded]else [/#if] if (curChar == ${64*byteNum+oneBit})
         [#else]
               [#if elseNeeded] else [/#if] if ((${utils.toHexStringL(asciiMoves[byteNum])} &l) != 0L)
         [/#if]
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
  [#var initState=lexicalState.initStateName()]
  [#var maxLen=lexicalState.maxLen]
  [#var maxStrKind=lexicalState.maxStrKind]
  [#var maxLenForActive=lexicalState.maxLenForActive]
  [#if maxLen = 0]
    private int jjMoveStringLiteralDfa0${lexicalState.suffix}() {
    [#if lexicalState.hasNfa()]
        return jjMoveNfa${lexicalState.suffix}(${initState}, 0);
    [#else]
        return 1;        
    [/#if]
    }
    [#return]
  [/#if]
  
  [#list 0..(maxLen-1) as i]
    [#var startNfaNeeded=false]
    [#var table=lexicalState.charPosKind[i]]
    
    private int jjMoveStringLiteralDfa${i}${lexicalState.suffix}
    [@ArgsList]
        [#list 0..maxStrKind/64 as j]
           [#if i != 0&&i<=maxLenForActive[j]+1&&maxLenForActive[j] != 0]
              [#if i != 1]
                 long old${j}
              [/#if]
               long active${j}
           [/#if]
        [/#list]
    [/@ArgsList] {
    [#if i != 0]
      [#if i>1]
         [#list 0..maxStrKind/64 as j]
           [#if i<=lexicalState.maxLenForActive[j]+1]
        active${j} = active${j} & old${j};
           [/#if]
         [/#list]
        if ([@ArgsList delimiter=" | "]
         [#list 0..maxStrKind/64 as j]
           [#if i<=lexicalState.maxLenForActive[j]+1]
            active${j}
           [/#if]
         [/#list]
         [/@ArgsList] == 0L)
         [#if !lexicalState.mixedCase&&lexicalState.hasNfa()]
            return jjStartNfa${lexicalState.suffix}
            [@ArgsList]
               ${i-2}
               [#list 0..maxStrKind/64 as j]
                 [#if i<=lexicalState.maxLenForActive[j]+1]
                   old${j}
                 [#else]
                   0L
                 [/#if]
               [/#list]
            [/@ArgsList];
         [#elseif lexicalState.hasNfa()]
            return jjMoveNfa${lexicalState.suffix}(${initState}, ${i-1});
         [#else]
            return ${i};
         [/#if]   
      [/#if]
      [#if grammar.options.debugLexer]
        if (trace_enabled && jjmatchedKind !=0 && jjmatchedKind != 0x7fffffff) {
            LOGGER.info("    Currently matched the first " + (jjmatchedPos + 1) + " characters as a " + tokenImage[jjmatchedKind] + " token.");
        }
        if (trace_enabled) LOGGER.info("   Possible string literal matches : { "
        [#list 0..maxStrKind/64 as vecs]
           [#if i<=maxLenForActive[vecs]]
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
              ${i-1}
           [#list 0..maxStrKind/64 as k]
              [#if (i<=maxLenForActive[k])]
                active${k}
              [#else]
                0L
              [/#if]
           [/#list][/@ArgsList];
          if (trace_enabled && jjmatchedKind != 0 && jjmatchedKind != 0x7fffffff) {
             LOGGER.info("    Currently matched the first " + (jjmatchedPos + 1) + " characters as a " + tokenImage[jjmatchedKind] + " token. ");
          }
           return ${i};
         [#elseif lexicalState.hasNfa()]
           return jjMoveNfa${lexicalState.suffix}(${initState}, ${i-1}); 
         [#else]
           return ${i};
         [/#if]
       }
    [/#if]
    [#if i != 0]
      if (trace_enabled) LOGGER.info("" + 
        [#if lexerData.lexicalStates?size != 1]
           "<${lexicalState.name}>" +
        [/#if]
        "Current character : " + ParseException.addEscapes(String.valueOf(curChar)) + " ("
        + (int) curChar + ") at line " + input_stream.getEndLine() + " column " + input_stream.getEndColumn());
    [/#if]
      switch (curChar) {
    [#list lexicalState.rearrange(table) as key]
       [#var info=table[key]]
       [#var ifGenerated=false]
	   [#var c=key[0..0]]
	   [#if lexicalState.generateDfaCase(key, info, i)]
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
	        [#list 0..maxStrKind as j]
	          [#var matchedKind=info.finalKinds[(j/64)?int]]
              [#if utils.isBitSet(matchedKind, j%64)]
                 [#if ifGenerated]
                 else if 
                 [#elseif i != 0]
                 if 
                 [/#if]
                 [#set ifGenerated = true]
                 [#if i != 0]
                   ((active${(j/64)?int} & ${utils.powerOfTwoInHex(j%64)}) != 0L) 
                 [/#if]
                 [#var kindToPrint=lexicalState.getKindToPrint(j, i)]
                 [#if !lexicalState.subString[j]]
                    [#var stateSetIndex=lexicalState.getStateSetForKind(i, j)]
                    [#if stateSetIndex != -1]
                    return jjStartNfaWithStates${lexicalState.suffix}(${i}, ${kindToPrint}, ${stateSetIndex});
                    [#else]
                    return jjStopAtPos(${i}, ${kindToPrint});
                    [/#if]
                 [#else]
                    [#if i != 0 || (lexicalState.initMatch != 0&&lexicalState.initMatch != MAX_INT)]
                     {
                    jjmatchedKind = ${kindToPrint};
                    jjmatchedPos = ${i};
                 }
                    [#else]
                    jjmatchedKind = ${kindToPrint};
                    [/#if]
                 [/#if]
              [/#if]
	        [/#list]
	      [/#if]
	      [#if info.validKindCnt != 0]
	           return jjMoveStringLiteralDfa${i+1}${lexicalState.suffix}[@ArgsList]
	              [#list 0..maxStrKind/64 as j]
	                 [#if i<=maxLenForActive[j]&&maxLenForActive[j] != 0]
	                    [#if i != 0]
	                       active${j}
	                    [/#if]
	                    ${utils.toHexStringL(info.validKinds[j])}
	                 [/#if]
	              [/#list]
	           [/@ArgsList];
	      [#else][#-- a very special case--]
	        [#if i = 0&&lexicalState.mixedCase]
	           [#if lexicalState.hasNfa()]
	           return jjMoveNfa${lexicalState.suffix}(${initState}, 0);
	           [#else]
	           return 1;
	           [/#if]
	        [#elseif i != 0][#-- No more str literals to look for --]
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
       [#if i = 0]
            return jjMoveNfa${lexicalState.suffix}(${initState}, 0);
       [#else]
            break;
          [#set startNfaNeeded = true]
       [/#if]
    [#else]
           return ${i+1};
    [/#if]
      }
    [#if i != 0]
       [#if startNfaNeeded]
          [#if !lexicalState.mixedCase&&lexicalState.hasNfa()]
            [#-- Here a string literal is successfully matched and no
                 more string literals are possible. So set the kind and t
                 state set up to and including this position for the matched
                 string. --]
            return jjStartNfa${lexicalState.suffix}[@ArgsList]
               ${i-1}
               [#list 0..maxStrKind/64 as k]
                 [#if i<=maxLenForActive[k]]
                  active${k}
                 [#else]
                   0L
                 [/#if]
               [/#list]
            [/@ArgsList];
          [#elseif lexicalState.hasNfa()]
             return jjMoveNfa${lexicalState.suffix}(${initState}, ${i});
          [#else]
             return ${i+1};
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
            "Current character : " + ParseException.addEscapes(String.valueOf(curChar)) 
            + " (" + (int)curChar + ") " + "at line " + input_stream.getEndLine() 
            + " column " + input_stream.getEndColumn());
        return jjMoveNfa${lexicalState.suffix}(state, pos+1);
   }
[/#macro]
 
[#macro DumpNfaStartStatesCode lexicalState lexicalState_index]
  [#var statesForPos=lexicalState.statesForPos]
  [#var maxKindsReqd=(1+lexicalState.maxStrKind/64)?int]
  [#var ind=0]
  [#var maxStrKind=lexicalState.maxStrKind]
  [#var maxLen=lexicalState.maxLen]
  
    private int jjStartNfa${lexicalState.suffix}(int pos, 
  [#list 0..(maxKindsReqd-1) as i]
       long active${i}[#if i_has_next], [#else]) {[/#if]
  [/#list]
  [#if lexicalState.mixedCase]
    [#if lexicalStates.generatedStates != 0]
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
  [#list 0..(maxLen-1) as i]
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
                    [#if lexicalState.subStringAtPos[i]]
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
   is just one argument per line. 
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
