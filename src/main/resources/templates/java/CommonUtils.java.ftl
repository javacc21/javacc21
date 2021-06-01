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

[#-- A place to put some utility routines used in various templates. Currently doesn't 
     really have much! --]

[#var TT = "TokenType."]

 [#if !grammar.legacyAPI && grammar.parserPackage?has_content]
   [#-- This is necessary because you can't do a static import from the unnamed or "default package" --]
   [#set TT=""]
 [/#if]

[#-- 
  Rewritten version of this macro to try to get around the Code too large problem.
--]
[#macro enumSet varName tokenNames]
    static private final EnumSet<TokenType> ${varName} = ${varName}_init();
    static private EnumSet<TokenType> ${varName}_init() {
       [#if tokenNames?size=0]
           return EnumSet.noneOf(TokenType.class);
       [#else]
           return EnumSet.of(
             [#list tokenNames as type]
              [#if type_index > 0],[/#if]
              ${TT}${type} 
             [/#list]
           );
       [/#if]
    }
[/#macro]

[#macro firstSetVar expansion]
    [@enumSet expansion.firstSetVarName expansion.firstSet.tokenNames /]
[/#macro]

[#macro finalSetVar expansion]
    [@enumSet expansion.finalSetVarName expansion.finalSet.tokenNames /]
[/#macro]            

[#macro followSetVar expansion]
    [@enumSet expansion.followSetVarName expansion.followSet.tokenNames/]
[/#macro]


[#var newVarIndex=0]
[#-- Just to generate a new unique variable name
  All it does is tack an integer (that is incremented)
  onto the type name, and optionally initializes it to some value--]
[#macro newVar type init=null]
   [#set newVarIndex = newVarIndex+1]
   ${type} ${type?lower_case}${newVarIndex}
   [#if init??]
      = ${init}
   [/#if]
   ;
[/#macro] 

[#macro newVarName prefix]
   ${prefix}${newID()}
[/#macro]

[#function newID]
    [#set newVarIndex = newVarIndex+1]
    [#return newVarIndex]
[/#function]

[#-- A macro to use at one's convenience to comment out a block of code --]
[#macro comment]
[#var content, lines]
[#set content][#nested/][/#set]
[#set lines = content?split("\n")]
[#list lines as line]
// ${line}
[/#list]
[/#macro]

[#function bool val]
   [#return val?string("true", "false")/]
[/#function]

[#macro HandleLexicalStateChange expansion inLookahead]
   [#if expansion.specifiedLexicalState?is_null]
      [#nested]
   [#else]
      [#var resetToken = inLookahead?string("currentLookaheadToken", "lastConsumedToken")]
      [#var prevLexicalStateVar = newVarName("previousLexicalState")]
         LexicalState ${prevLexicalStateVar} = token_source.lexicalState;
         if (token_source.lexicalState != LexicalState.${expansion.specifiedLexicalState}) {
            token_source.reset(${resetToken}, LexicalState.${expansion.specifiedLexicalState});
         } 
         try {
           [#nested/]
         }
         finally {
            if (${prevLexicalStateVar} != LexicalState.${expansion.specifiedLexicalState}) {
      [#if !grammar.hugeFileSupport && !grammar.userDefinedLexer]               
           token_source.reset(${resetToken}, ${prevLexicalStateVar});
      [#else]   
           token_source.switchTo(${prevLexicalStateVar});
      [/#if]           
            }
         }
   [/#if]
[/#macro]

[#macro BitSetFromLongArray bitSet]
      BitSet.valueOf(new long[] {
          [#list bitSet.toLongArray() as long]
             ${grammar.utils.toHexStringL(long)}
             [#if long_has_next],[/#if]
          [/#list]
      })
[/#macro]
