[#ftl strict_vars=true]
[#--
  Copyright (C) 2008-2020 Jonathan Revusky, revusky@javacc.com
  Copyright (C) 2021-2022 Vinay Sajip, vinay_sajip@yahoo.co.uk
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

 [#-- This template generates the various lookahead/predicate routines --]

[#import "common_utils.inc.ftl" as CU]

[#var UNLIMITED=2147483647]
[#-- var MULTIPLE_LEXICAL_STATE_HANDLING = grammar.lexerData.numLexicalStates > 1 --]
[#var MULTIPLE_LEXICAL_STATE_HANDLING = false]


[#macro Generate]
    [@firstSetVars /]
    [@followSetVars /]
    [#if grammar.choicePointExpansions?size !=0]
       [@BuildLookaheads 4 /]
     [/#if]
[/#macro]


[#macro firstSetVars]
    # ==================================================================
    # EnumSets that represent the various expansions' first set (i.e. the set of tokens with which the expansion can begin)
    # ==================================================================
    [#list grammar.expansionsForFirstSet as expansion]
          [@CU.firstSetVar expansion/]
    [/#list]
[/#macro]

[#macro finalSetVars]
    # ==================================================================
    # EnumSets that represent the various expansions' final set (i.e. the set of tokens with which the expansion can end)
    # ==================================================================
    [#list grammar.expansionsForFinalSet as expansion]
          [@finalSetVar expansion/]
    [/#list]
[/#macro]


[#macro followSetVars]
    # ==================================================================
    # EnumSets that represent the various expansions' follow set (i.e. the set of tokens that can immediately follow this)
    # ==================================================================
    [#list grammar.expansionsForFollowSet as expansion]
          [@CU.followSetVar expansion/]
    [/#list]
[/#macro]

[#--
  scan_token tends to be a big source of time spent in the parser,
  so we try to optimize it into two versions if optimize_scan_token is
  true - one for one type and one for many.
 --]
[#var optimize_scan_token = true]

[#macro BuildLookaheads indent]
[#if !optimize_scan_token]
    def scan_token(self, expected_type_or_types):
        is_set = isinstance(expected_type_or_types, (set, frozenset))
        peeked_token = self.next_token(self.current_lookahead_token)
        tt = peeked_token.type
        if not is_set:
            no_match = tt != expected_type_or_types
        else:
            no_match = tt not in expected_type_or_types
        if no_match:
            return False
        if self.remaining_lookahead != UNLIMITED:
            self.remaining_lookahead -= 1
        self.current_lookahead_token = peeked_token
        return True

[#else]
    def scan_token_one(self, expected_type):
        peeked_token = self.next_token(self.current_lookahead_token)
        tt = peeked_token.type
        if tt != expected_type:
            return False
        if self.remaining_lookahead != UNLIMITED:
            self.remaining_lookahead -= 1
        self.current_lookahead_token = peeked_token
        return True

    def scan_token_many(self, expected_types):
        peeked_token = self.next_token(self.current_lookahead_token)
        tt = peeked_token.type
        if tt not in expected_types:
            return False
        if self.remaining_lookahead != UNLIMITED:
            self.remaining_lookahead -= 1
        self.current_lookahead_token = peeked_token
        return True

[/#if]
# ====================================
# Lookahead Routines
# ====================================
   [#list grammar.choicePointExpansions as expansion]
      [#if expansion.parent.class.simpleName != "BNFProduction"]
${BuildScanRoutine(expansion, indent)}
      [/#if]
   [/#list]
   [#list grammar.assertionExpansions as expansion]
${BuildAssertionRoutine(expansion, indent)}
   [/#list]   [#list grammar.expansionsNeedingPredicate as expansion]
${BuildPredicateRoutine(expansion)}
   [/#list]
   [#list grammar.allLookaheads as lookahead]
      [#if lookahead.nestedExpansion??]
${BuildLookaheadRoutine(lookahead, indent)}
     [/#if]
   [/#list]
   [#list grammar.allLookBehinds as lookBehind]
${BuildLookBehindRoutine(lookBehind, indent)}
   [/#list]
   [#list grammar.parserProductions as production]
${BuildProductionLookaheadMethod(production, indent)}
   [/#list]
[/#macro]

[#macro BuildPredicateRoutine expansion indent]
  [#var lookaheadAmount = expansion.lookaheadAmount]
  [#if lookaheadAmount = 2147483647][#set lookaheadAmount = "UNLIMITED"][/#if]
    # predicate routine for expansion at:
    # ${expansion.location}
    # BuildPredicateRoutine macro
    def ${expansion.predicateMethodName}(self):
        try:
            self.lookahead_routine_nesting += 1
            self.current_lookahead_token = self.last_consumed_token
            self.remaining_lookahead = ${lookaheadAmount}
            self.hit_failure = False
            self.scan_to_end = ${CU.bool(expansion.hasExplicitNumericalLookahead || expansion.hasSeparateSyntacticLookahead)}
${BuildPredicateCode(expansion, 12)}
      [#if !expansion.hasSeparateSyntacticLookahead]
${BuildScanCode(expansion, 12)}
      [/#if]
            return True
        finally:
            self.lookahead_routine_nesting -= 1
            self.current_lookahead_token = None
[/#macro]

[#macro BuildScanRoutine expansion indent]
[#var is=""?right_pad(indent)]
[#-- ${is}# DBG > BuildScanRoutine ${indent} --]
 [#if !expansion.singleToken || expansion.requiresPredicateMethod]
${is}# scanahead routine for expansion at:
${is}# ${expansion.location}
${is}# BuildScanRoutine macro
${is}def ${expansion.scanRoutineName}(self):
${is}    # import pdb; pdb.set_trace()
${is}    try:
${is}        self.lookahead_routine_nesting += 1
   [#if !expansion.insideLookahead]
${BuildPredicateCode(expansion, indent + 8)}
   [/#if]
${BuildScanCode(expansion, indent + 8)}
${is}        return True
${is}    finally:
${is}        self.lookahead_routine_nesting -= 1
 [/#if]
[#-- ${is}# DBG < BuildScanRoutine ${indent} --]
[/#macro]

[#macro BuildAssertionRoutine expansion indent]
[#var is=""?right_pad(indent)]
[#-- ${is}# DBG > BuildAssertionRoutine ${indent} --]
${is}# scanahead routine for assertion at:
${is}# ${expansion.parent.location}
${is}# BuildAssertionRoutine macro
${is}def ${expansion.scanRoutineName}(self):
${is}    # import pdb; pdb.set_trace()
[#var storeCurrentLookaheadVar = CU.newVarName("currentLookahead")]
${is}    self.remaining_lookahead = UNLIMITED
${is}    self.scan_to_end = True
${is}    ${storeCurrentLookaheadVar} = self.current_lookahead_token
${is}    if self.current_lookahead_token is None:
${is}        self.current_lookahead_token = self.last_consumed_token
${is}    try:
${is}        self.lookahead_routine_nesting += 1
${BuildScanCode(expansion, indent + 8)}
${is}        return True
${is}    finally:
${is}        self.lookahead_routine_nesting -= 1
${is}        self.current_lookahead_token = ${storeCurrentLookaheadVar}
[#-- ${is}# DBG < BuildAssertionRoutine ${indent} --]
[/#macro]

[#-- Build the code for checking semantic lookahead, lookbehind, and/or syntactic lookahead --]
[#macro BuildPredicateCode expansion indent]
[#var is=""?right_pad(indent)]
[#-- ${is}# DBG > BuildPredicateCode ${indent} --]
[#if expansion.hasSemanticLookahead && (expansion.lookahead.semanticLookaheadNested || expansion.containingProduction.onlyForLookahead)]
${is}if not (${grammar.utils.translateExpression(expansion.semanticLookahead)}):
${is}    return False
[/#if]
[#if expansion.hasLookBehind]
${is}if [#if !expansion.lookBehind.negated]not [/#if]self.${expansion.lookBehind.routineName}():
${is}    return False
[/#if]
${is}if self.remaining_lookahead <= 0:
${is}    return True
[#if expansion.hasSeparateSyntacticLookahead]
${is}if [#if !expansion.lookahead.negated]not [/#if]self.${expansion.lookaheadExpansion.scanRoutineName}():
  [#if expansion.lookahead.negated]
${is}    return False
  [#else]
${is}    return False
  [/#if]
[/#if]
[#-- ${is}# DBG < BuildPredicateCode ${indent} --]
[/#macro]


[#--
   Generates the routine for an explicit lookahead
   that is used in a nested lookahead.
 --]
[#macro BuildLookaheadRoutine lookahead indent]
[#var is=""?right_pad(indent)]
[#-- ${is}# DBG > BuildLookaheadRoutine ${indent} --]
[#if lookahead.nestedExpansion??]
${is}# lookahead routine for lookahead at:
${is}# ${lookahead.location}
${is}def ${lookahead.nestedExpansion.scanRoutineName}(self):
${is}    prev_remaining_lookahead = self.remaining_lookahead
${is}    prev_hit_failure = self.hit_failure
${is}    prev_scanahead_token = self.current_lookahead_token
${is}    try:
${is}        self.lookahead_routine_nesting += 1
${BuildScanCode(lookahead.nestedExpansion, indent + 8)}
${is}        return not self.hit_failure
${is}    finally:
${is}        self.lookahead_routine_nesting -= 1
${is}        self.current_lookahead_token = prev_scanahead_token
${is}        self.remaining_lookahead = prev_remaining_lookahead
${is}        self.hit_failure = prev_hit_failure
[/#if]
[#-- ${is}# DBG < BuildLookaheadRoutine ${indent} --]
[/#macro]

[#macro BuildLookBehindRoutine lookBehind indent]
[#var is=""?right_pad(indent)]
[#-- ${is}# DBG > BuildLookBehindRoutine ${indent} --]
${is}# Look behind
${is}def ${lookBehind.routineName}(self):
${is}    stack_iterator = self.${lookBehind.backward?string("stack_iterator_backward", "stack_iterator_forward")}()
[#list lookBehind.path as element]
  [#var elementNegated = (element[0] == "~")]
  [#if elementNegated][#set element = element?substring(1)][/#if]
  [#if element = "."]
${is}    if not stack_iterator.has_next:
${is}        return False
${is}    stack_iterator.next
  [#elseif element = "..."]
    [#if element_index = lookBehind.path?size-1]
      [#if lookBehind.hasEndingSlash]
${is}    return not stack_iterator.has_next
      [#else]
${is}    return True
      [/#if]
    [#else]
      [#var nextElement = lookBehind.path[element_index+1]]
      [#var nextElementNegated = (nextElement[0]=="~")]
      [#if nextElementNegated][#set nextElement=nextElement?substring(1)][/#if]
${is}    while stack_iterator.has_next:
${is}        ntc = stack_iterator.next
      [#var equalityOp = nextElementNegated?string("!=", "==")]
${is}        if ntc.production_name ${equalityOp} "${nextElement}":
${is}            stack_iterator.previous
${is}            break
${is}        if not stack_iterator.has_next:
${is}            return False
    [/#if]
  [#else]
${is}    if not stack_iterator.has_next:
${is}        return False
${is}    ntc = stack_iterator.next
     [#var equalityOp = elementNegated?string("==", "!=")]
${is}    if ntc.production_name ${equalityOp} "${element}":
${is}        return False
  [/#if]
[/#list]
[#if lookBehind.hasEndingSlash]
${is}    return not stack_iterator.has_next
[#else]
${is}    return True
[/#if]
[#-- ${is}# DBG < BuildLookBehindRoutine ${indent} --]
[/#macro]

[#macro BuildProductionLookaheadMethod production indent]
[#var is=""?right_pad(indent)]
[#--     # DBG > BuildProductionLookaheadMethod ${indent} --]
    # BuildProductionLookaheadMethod
    def ${production.lookaheadMethodName}(self):
        # import pdb; pdb.set_trace()
[#if production.javaCode?? && production.javaCode.appliesInLookahead]
${grammar.utils.translateCodeBlock(production.javaCode, 8)}
[/#if]
${BuildScanCode(production.expansion, 8)}
        return True

[#--     # DBG < BuildProductionLookaheadMethod ${indent} --]
[/#macro]

[#--
   Macro to build the lookahead code for an expansion.
   This macro just delegates to the various sub-macros
   based on the Expansion's class name.
--]
[#macro BuildScanCode expansion indent]
[#var is=""?right_pad(indent)]
[#-- ${is}# DBG > BuildScanCode ${indent} ${expansion.simpleName} --]
  [#var classname=expansion.simpleName]
  [#if classname != "ExpansionSequence" && classname != "ExpansionWithParentheses"]
${is}if self.hit_failure or self.remaining_lookahead <= 0:
${is}    return not self.hit_failure
${is}# Lookahead Code for ${classname} specified at ${expansion.location}
  [/#if]
  [@CU.HandleLexicalStateChange expansion true indent; indent]
  [#--
${is}# Building scan code for: ${classname}
${is}# at: ${expansion.location}
  --]
   [#if classname = "ExpansionWithParentheses"]
      [@BuildScanCode expansion.nestedExpansion indent /]
   [#elseif expansion.isRegexp]
${ScanSingleToken(expansion, indent)}
   [#elseif classname = "Assertion"]
${ScanCodeAssertion(expansion, indent)}
   [#elseif classname = "LexicalStateSwitch"]
       ${ScanCodeLexicalStateSwitch(expansion)}
   [#elseif classname = "Failure"]
${ScanCodeError(expansion, indent)}
   [#elseif classname = "UncacheTokens"]
${is}self.uncache_tokens()
   [#elseif classname = "ExpansionSequence"]
${ScanCodeSequence(expansion, indent)}
   [#elseif classname = "ZeroOrOne"]
${ScanCodeZeroOrOne(expansion, indent)}
   [#elseif classname = "ZeroOrMore"]
${ScanCodeZeroOrMore(expansion, indent)}
   [#elseif classname = "OneOrMore"]
${ScanCodeOneOrMore(expansion, indent)}
   [#elseif classname = "NonTerminal"]
      [@ScanCodeNonTerminal expansion indent /]
   [#elseif classname = "TryBlock" || classname="AttemptBlock"]
      [@BuildScanCode expansion.nestedExpansion indent /]
   [#elseif classname = "ExpansionChoice"]
${ScanCodeChoice(expansion, indent)}
   [#elseif classname = "CodeBlock"]
      [#if expansion.appliesInLookahead || expansion.containingProduction.onlyForLookahead]
${grammar.utils.translateCodeBlock(expansion, indent)}
      [/#if]
   [/#if]
  [/@CU.HandleLexicalStateChange]
[#-- ${is}# DBG < BuildScanCode ${indent} ${expansion.simpleName} --]
[/#macro]

[#--
   Generates the lookahead code for an ExpansionSequence.
   In legacy JavaCC there was some quite complicated logic so as
   not to generate unnecessary code. They actually had a longstanding bug
   there, which was the topic of this blog post: https://javacc.com/2020/10/28/a-bugs-life/
   I very much doubt that this kind of space optimization is worth
   the candle nowadays and it just really complicated the code. Also, the ability
   to scan to the end of an expansion strike me as quite useful in general,
   particularly for fault-tolerant.
--]
[#macro ScanCodeSequence sequence indent]
[#var is=""?right_pad(indent)]
[#-- ${is}# DBG > ScanCodeSequence ${indent} --]
   [#list sequence.units as sub]
       [@BuildScanCode sub indent /]
       [#if sub.scanLimit]
${is}if not self.scan_to_end and self.lookahead_routine_nesting <= 1:
${is}    self.remaining_lookahead = ${sub.scanLimitPlus}
       [/#if]
   [/#list]
[#-- ${is}# DBG < ScanCodeSequence ${indent} --]
[/#macro]

[#--
  Generates the lookahead code for a non-terminal.
  It (trivially) just delegates to the code for
  checking the production's nested expansion
--]
[#macro ScanCodeNonTerminal nt indent]
[#var is=""?right_pad(indent)]
${is}self.push_onto_lookahead_stack('${nt.containingProduction.name}', '${nt.inputSource?j_string}', ${nt.beginLine}, ${nt.beginColumn})
      [#var prevScanToEndVarName = "scan_to_end" + CU.newID()]
${is}${prevScanToEndVarName} = self.scan_to_end
${is}self.current_lookahead_production = '${nt.production.name}'
${is}self.scan_to_end = ${CU.bool(nt.scanToEnd)}
${is}try:
${is}    if not self.${nt.production.lookaheadMethodName}():
${is}        return False
${is}finally:
${is}    self.pop_lookahead_stack()
${is}    self.scanToEnd = ${prevScanToEndVarName}
[/#macro]

[#macro ScanSingleToken expansion indent]
[#var is=""?right_pad(indent)]
[#var firstSet = expansion.firstSet.tokenNames]
[#-- ${is}# DBG > ScanSingleToken ${indent} --]
[#if firstSet?size = 1]
[#if optimize_scan_token]
${is}if not self.scan_token_one(${firstSet[0]}):
[#else]
${is}if not self.scan_token(${firstSet[0]}):
[/#if]
${is}    return False
[#else]
[#if optimize_scan_token]
${is}if not self.scan_token_many(self.${expansion.firstSetVarName}):
[#else]
${is}if not self.scan_token(self.${expansion.firstSetVarName}):
[/#if]
${is}    return False
[/#if]
[#-- ${is}# DBG < ScanSingleToken ${indent} --]
[/#macro]

[#macro ScanCodeAssertion assertion indent]
[#var is=""?right_pad(indent)]
[#-- ${is}# DBG > ScanCodeAssertion ${indent} --]
[#if assertion.assertionExpression?? && (assertion.semanticLookaheadNested || assertion.containingProduction.onlyForLookahead)]
${is}if not (${grammar.utils.translateExpression(assertion.assertionExpression)}):
${is}    self.hit_failure = True
${is}    return False
[/#if]
[#if assertion.expansion??]
${is}if [#if !assertion.expansionNegated]not [/#if]self.${assertion.expansion.scanRoutineName}():
${is}    self.hit_failure = True
${is}    return False
[/#if]
[#-- ${is}# DBG < ScanCodeAssertion ${indent} --]
[/#macro]

[#macro ScanCodeError expansion indent]
[#var is=""?right_pad(indent)]
[#-- ${is}# DBG > ScanCodeError ${indent} --]
${is}self.hit_failure = True
${is}return False
[#-- ${is}# DBG < ScanCodeError ${indent} --]
[/#macro]

[#macro ScanCodeChoice choice indent]
[#var is=""?right_pad(indent)]
[#-- ${is}# DBG > ScanCodeChoice ${indent} --]
${is}${CU.newVarName("token")} = self.current_lookahead_token
${is}remaining_lookahead${CU.newVarIndex} = self.remaining_lookahead
${is}hit_failure${CU.newVarIndex} = self.hit_failure
  [#list choice.choices as subseq]
${is}if not (${CheckExpansion(subseq)}):
${is}    self.current_lookahead_token = token${CU.newVarIndex}
${is}    self.remaining_lookahead = remaining_lookahead${CU.newVarIndex}
${is}    self.hit_failure = hit_failure${CU.newVarIndex}
     [#if !subseq_has_next]
${is}    return False
     [/#if]
[#-- bump up the indentation, as the items in the list are recursive
     levels
--]
[#set is = is + "    "]
  [/#list]
[#-- ${is}# DBG < ScanCodeChoice ${indent} --]
[/#macro]

[#macro ScanCodeZeroOrOne zoo indent]
[#var is=""?right_pad(indent)]
[#-- ${is}# DBG > ScanCodeZeroOrOne ${indent} --]
${is}${CU.newVarName("token")} = self.current_lookahead_token
${is}if not (${CheckExpansion(zoo.nestedExpansion)}):
${is}    self.current_lookahead_token = token${CU.newVarIndex}
[#-- ${is}# DBG < ScanCodeZeroOrOne ${indent} --]
[/#macro]

[#--
  Generates lookahead code for a ZeroOrMore construct]
--]
[#macro ScanCodeZeroOrMore zom indent]
[#var is=""?right_pad(indent)]
[#-- ${is}# DBG > ScanCodeZeroOrMore ${indent} --]
${is}while self.remaining_lookahead > 0 and not self.hit_failure:
${is}    ${CU.newVarName("token")} = self.current_lookahead_token
${is}    if not (${CheckExpansion(zom.nestedExpansion)}):
${is}        self.current_lookahead_token = token${CU.newVarIndex}
${is}        break
[#-- ${is}# DBG < ScanCodeZeroOrMore ${indent} --]
[/#macro]

[#--
   Generates lookahead code for a OneOrMore construct
   It generates the code for checking a single occurrence
   and then the same code as a ZeroOrMore
--]
[#macro ScanCodeOneOrMore oom indent]
[#var is=""?right_pad(indent)]
[#-- ${is}# DBG > ScanCodeOneOrMore ${indent} --]
${is}if not (${CheckExpansion(oom.nestedExpansion)}):
${is}    return False
[@ScanCodeZeroOrMore oom indent /]
[#-- ${is}# DBG < ScanCodeOneOrMore ${indent} --]
[/#macro]


[#macro CheckExpansion expansion]
   [#if expansion.singleToken && !expansion.requiresPredicateMethod]
     [#if expansion.firstSet.tokenNames?size = 1]
       [#if optimize_scan_token]
      self.scan_token_one(${expansion.firstSet.tokenNames[0]})[#t]
       [#else]
      self.scan_token(${expansion.firstSet.tokenNames[0]})[#t]
       [/#if]
     [#else]
       [#if optimize_scan_token]
      self.scan_token_many(self.${expansion.firstSetVarName})[#t]
       [#else]
      self.scan_token(self.${expansion.firstSetVarName})[#t]
       [/#if]
     [/#if]
   [#else]
      self.${expansion.scanRoutineName}()[#t]
   [/#if]
[/#macro]


