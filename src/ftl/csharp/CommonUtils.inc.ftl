[#ftl strict_vars=true]
[#--
  Copyright (C) 2008-2020 Jonathan Revusky, revusky@javacc.com
  Copyright (C) 2021 Vinay Sajip, vinay_sajip@yahoo.co.uk
  All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are met:

      * Redistributions of source code must retain the above copyright
        notices, this list of conditions and the following disclaimer.
      * Redistributions in binary form must reproduce the above copyright
        notice, this list of conditions and the following disclaimer in
        the documentation and/or other materials provided with the
        distribution.
      * None of the names Jonathan Revusky, Vinay Sajip, Sun
        Microsystems, Inc. nor the names of any contributors may be
        used to endorse or promote products derived from this software
        without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
  ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
  INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
  CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
  ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
  THE POSSIBILITY OF SUCH DAMAGE.
--]

[#-- A place to put some utility routines used in various templates. Currently doesn't
     really have much! --]

[#var TT = "TokenType."]

[#macro enumSet varName tokenNames indent=0]
[#var is = ""?right_pad(indent)]
[#var size = tokenNames?size]
[#if size = 0]
${is}private static readonly HashSet<TokenType> ${varName} = Utils.GetOrMakeSet();
[#else]
${is}private static readonly HashSet<TokenType> ${varName} = Utils.GetOrMakeSet(
[#list tokenNames as type]
${is}    TokenType.${type}[#if type_has_next],[/#if]
[/#list]
${is});
[/#if]

[/#macro]

[#macro firstSetVar expansion]
    [@enumSet expansion.firstSetVarName expansion.firstSet.tokenNames 8 /]
[/#macro]

[#macro finalSetVar expansion]
    [@enumSet expansion.finalSetVarName expansion.finalSet.tokenNames 8 /]
[/#macro]

[#macro followSetVar expansion]
    [@enumSet expansion.followSetVarName expansion.followSet.tokenNames 8 /]
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
${prefix}${newID()}[#rt]
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

[#macro HandleLexicalStateChange expansion inLookahead indent]
[#var is=""?right_pad(indent)]
[#-- ${is}# DBG > HandleLexicalStateChange ${indent} ${expansion.simpleName} --]
[#if expansion.specifiedLexicalState??]
  [#var resetToken = inLookahead?string("currentLookaheadToken", "lastConsumedToken")]
  [#var prevLexicalStateVar = newVarName("previousLexicalState")]
${is}${prevLexicalStateVar} = tokenSource.LexicalState;
${is}if (tokenSource.LexicalState != LexicalState.${expansion.specifiedLexicalState}:
${is}    tokenSource.Reset(${resetToken}, LexicalState.${expansion.specifiedLexicalState})
${is}    try:
  [#nested indent + 4 /]
${is}    finally:
${is}        if ${prevLexicalStateVar} != LexicalState.${expansion.specifiedLexicalState}:
      [#if !grammar.hugeFileSupport && !grammar.userDefinedLexer]
${is}            tokenSource.Reset(${resetToken}, ${prevLexicalStateVar})
      [#else]
${is}            tokenSource.SwitchTo(${prevLexicalStateVar})
      [/#if]
[#elseif expansion.tokenActivation??]
  [#var tokenActivation = expansion.tokenActivation]
  [#var methodName = "ActivateTokenTypes"]
  [#if tokenActivation.deactivate]
    [#set methodName = "DeactivateTokenTypes"]
  [/#if]
  [#var prevActives = newVarName("previousActives")]
  [#var somethingChanged = newVarName("somethingChanged")]
${is}var ${prevActives} = new SetAdapter<TokenType>(tokenSource.ActiveTokenTypes);
${is}var ${somethingChanged} = ${methodName}(
  [#list tokenActivation.tokenNames as tokenName]
${is}    ${TT}${tokenName}[#if tokenName_has_next],[/#if]
  [/#list]
${is});
${is}try {
  [#nested indent + 4 /]
${is}}
${is}finally {
${is}    tokenSource.ActiveTokenTypes = ${prevActives};
${is}    if (${somethingChanged}) {
${is}        tokenSource.Reset(GetToken(0));
${is}        _nextTokenType = null;
${is}    }
${is}}
[#else]
  [#nested indent /]
[/#if]
[#-- ${is}# DBG < HandleLexicalStateChange ${indent} ${expansion.simpleName} --]
[/#macro]

[#-- macro BitSetFromLongArray bitSet]
      BitSet.valueOf(new long[] {
          [#list bitSet.toLongArray() as long]
             ${grammar.utils.toHexStringL(long)}
             [#if long_has_next],[/#if]
          [/#list]
      })
[/#macro --]
