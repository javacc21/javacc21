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
   for the DFA. Needless to say, this still needs some cleanup.
 --]

[#var utils = grammar.utils, lexerData=grammar.lexerData]
[#var MAX_INT=2147483647]

[#macro DumpDfaCode lexicalState]
  [#var dfaData = lexicalState.dfaData]
  [#var initState=lexicalState.nfaData.initStateName()]
  [#var maxStringLength=dfaData.maxStringLength]
  [#var maxStringIndex=dfaData.maxStringIndex]
  [#var maxStringLengthForActive=dfaData.maxStringLengthForActive]
  [#if maxStringLength = 0]
    private int jjMoveStringLiteralDfa0_${lexicalState.name}() {
    [#if lexicalState.hasNfa()]
        return jjMoveNfa_${lexicalState.name}(${initState}, 0);
    [#else]
        return 1;        
    [/#if]
    }
    [#return]
  [/#if]
  
  [#list dfaData.stringLiteralTables as table]
    [#var startNfaNeeded=false]
    [#var first = (table_index==0)]
    
    private int jjMoveStringLiteralDfa${table_index}_${lexicalState.name}
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
           [#if table_index<=dfaData.maxStringLengthForActive[j]+1]
        active${j} = active${j} & old${j};
           [/#if]
         [/#list]
        if ([@ArgsList delimiter=" | "]
         [#list 0..maxStringIndex/64 as j]
           [#if table_index<=dfaData.maxStringLengthForActive[j]+1]
            active${j}
           [/#if]
         [/#list]
         [/@ArgsList] == 0L)
         [#if !lexicalState.mixedCase&&lexicalState.hasNfa()]
            return jjStartNfa_${lexicalState.name}
            [@ArgsList]
               ${table_index-2}
               [#list 0..maxStringIndex/64 as j]
                 [#if table_index<=dfaData.maxStringLengthForActive[j]+1]
                   old${j}
                 [#else]
                   0L
                 [/#if]
               [/#list]
            [/@ArgsList];
         [#elseif lexicalState.hasNfa()]
            return jjMoveNfa_${lexicalState.name}(${initState}, ${table_index-1});
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
           jjStopStringLiteralDfa_${lexicalState.name}[@ArgsList]
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
           return jjMoveNfa_${lexicalState.name}(${initState}, ${table_index-1}); 
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
                 [#var kindToPrint=lexicalState.nfaData.getKindToPrint(j, table_index)]
                 [#if !dfaData.subString[j]]
                    [#var stateSetIndex=lexicalState.nfaData.getStateSetForKind(table_index, j)]
                    [#if stateSetIndex != -1]
                    return jjStartNfaWithStates_${lexicalState.name}(${table_index}, ${kindToPrint}, ${stateSetIndex});
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
	           return jjMoveStringLiteralDfa${table_index+1}_${lexicalState.name}[@ArgsList]
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
	           return jjMoveNfa_${lexicalState.name}(${initState}, 0);
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
            return jjMoveNfa_${lexicalState.name}(${initState}, 0);
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
            return jjStartNfa_${lexicalState.name}[@ArgsList]
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
             return jjMoveNfa_${lexicalState.name}(${initState}, ${table_index});
          [#else]
             return ${table_index+1};
          [/#if]        
       [/#if]
    [/#if]
   }
  [/#list]
[/#macro] 

[#macro SkipSingles dfaData]
    [#if dfaData.hasSinglesToSkip]
       [#var byteMask1 = utils.toHexStringL(dfaData.getSinglesToSkip(0))]
       [#var byteMask2 = utils.toHexStringL(dfaData.getSinglesToSkip(1))]
       while ((curChar < 64 && ((${byteMask1} & (1L << curChar)) != 0)) 
             || (curChar >=64 && curChar < 128 && (${byteMask2} & (1L<<(curChar-64)))!=0))
            {
               [#var debugOutput]
               [#set debugOutput]
               [#if lexerData.lexicalStates?size > 1]
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
