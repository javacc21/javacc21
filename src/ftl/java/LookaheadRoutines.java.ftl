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

 [#-- This template generates the various lookahead/predicate routines --]

[#import "CommonUtils.java.ftl" as CU]

[#var UNLIMITED=2147483647]
[#var MULTIPLE_LEXICAL_STATE_HANDLING = grammar.lexerData.numLexicalStates>1 && !grammar.hugeFileSupport && !grammar.userDefinedLexer]
[#set MULTIPLE_LEXICAL_STATE_HANDLING = false]


[#macro Generate]
    [@firstSetVars /]
    [@followSetVars /]
    [#if grammar.choicePointExpansions?size !=0]
       [@BuildLookaheads /]
     [/#if]
[/#macro]


[#macro firstSetVars]
    //=================================
     // EnumSets that represent the various expansions' first set (i.e. the set of tokens with which the expansion can begin)
     //=================================
    [#list grammar.expansionsForFirstSet as expansion]
          [@CU.firstSetVar expansion/]
    [/#list]
[/#macro]

[#macro finalSetVars]
    //=================================
     // EnumSets that represent the various expansions' final set (i.e. the set of tokens with which the expansion can end)
     //=================================
    [#list grammar.expansionsForFinalSet as expansion]
          [@finalSetVar expansion/]
    [/#list]
[/#macro]


[#macro followSetVars]
    //=================================
     // EnumSets that represent the various expansions' follow set (i.e. the set of tokens that can immediately follow this)
     //=================================
    [#list grammar.expansionsForFollowSet as expansion]
          [@CU.followSetVar expansion/]
    [/#list]
[/#macro]


[#macro BuildLookaheads]
  private final boolean scanToken(TokenType expectedType) {
     currentLookaheadToken = nextToken(currentLookaheadToken);
     TokenType type = currentLookaheadToken.getType();
     if (type != expectedType) return lastLookaheadSucceeded = false;
     if (remainingLookahead != UNLIMITED) remainingLookahead--;
     return lastLookaheadSucceeded = true;
  }

  private final boolean scanToken(EnumSet<TokenType> types) {
     currentLookaheadToken = nextToken(currentLookaheadToken);
     TokenType type = currentLookaheadToken.getType();
     if (!types.contains(type)) return lastLookaheadSucceeded = false;
     if (remainingLookahead != UNLIMITED) remainingLookahead--;
//     if (type == upToTokenType) remainingLookahead = 0;
     return lastLookaheadSucceeded = true;
  }

//====================================
 // Lookahead Routines
 //====================================
   [#list grammar.choicePointExpansions as expansion]
      [#if expansion.parent.class.simpleName != "BNFProduction"]
        ${BuildScanRoutine(expansion)}
      [/#if]
   [/#list]
   [#list grammar.expansionsNeedingPredicate as expansion]
       ${BuildPredicateRoutine(expansion)}
   [/#list]
   [#list grammar.allLookaheads as lookahead]
      [#if lookahead.nestedExpansion??]
       ${BuildLookaheadRoutine(lookahead)}
     [/#if]
   [/#list]
   [#list grammar.allLookBehinds as lookBehind]
      ${BuildLookBehindRoutine(lookBehind)}
   [/#list]
   [#--list grammar.allAssertions as assertion]
     [#if !assertion.expansion?is_null]
      ${BuildLookaheadRoutine(assertion.expansion)}
     [/#if]
   [/#list--]
   [#list grammar.parserProductions as production]
      ${BuildProductionLookaheadMethod(production)}
   [/#list]
[/#macro]  

[#macro BuildPredicateRoutine expansion] 
  [#var lookaheadAmount = expansion.lookaheadAmount]
  [#if lookaheadAmount = 2147483647][#set lookaheadAmount = "UNLIMITED"][/#if]
  // predicate routine for expansion at: 
  // ${expansion.location}
  // BuildPredicateRoutine macro
   private final boolean ${expansion.predicateMethodName}() {
     try {
         lookaheadRoutineNesting++;
         currentLookaheadToken= lastConsumedToken;
         remainingLookahead= ${lookaheadAmount};
         hitFailure = false;
         scanToEnd = ${CU.bool(expansion.hasExplicitNumericalLookahead||expansion.hasSeparateSyntacticLookahead)};
      ${BuildPredicateCode(expansion)}
      [#if !expansion.hasSeparateSyntacticLookahead]
         ${BuildScanCode(expansion)}
      [/#if]
      return lastLookaheadSucceeded = true;
      }
      finally {
        lookaheadRoutineNesting--;
        currentLookaheadToken = null;
     }
   }
[/#macro]

[#macro BuildScanRoutine expansion]
 [#if !expansion.singleToken || expansion.requiresPredicateMethod]
  // scanahead routine for expansion at: 
  // ${expansion.location}
  // BuildScanRoutine macro
  private final boolean ${expansion.scanRoutineName}() {
    try {
       lookaheadRoutineNesting++;
   [#if !expansion.insideLookahead]
     ${BuildPredicateCode(expansion)}
   [/#if]
     ${BuildScanCode(expansion)}
      return lastLookaheadSucceeded = true;
    }
    finally {
       lookaheadRoutineNesting--;
    }
  }
 [/#if]
[/#macro]

[#-- Build the code for checking semantic lookahead, lookbehind, and/or syntactic lookahead --]
[#macro BuildPredicateCode expansion]
     // BuildPredicateCode macro
     [#if expansion.hasSemanticLookahead && expansion.lookahead.semanticLookaheadNested]
       if (!(${expansion.semanticLookahead})) return lastLookaheadSucceeded = false;
     [/#if]
     [#if expansion.hasLookBehind]
       if ([#if !expansion.lookBehind.negated]![/#if]
       ${expansion.lookBehind.routineName}()) return lastLookaheadSucceeded = false;
     [/#if]
     if (remainingLookahead <=0) return lastLookaheadSucceeded = true;
     [#if expansion.hasSeparateSyntacticLookahead]
      if (
      [#if !expansion.lookahead.negated]![/#if]
        ${expansion.lookaheadExpansion.scanRoutineName}())
        [#if expansion.lookahead.negated]
        return !(lastLookaheadSucceeded = true);
        [#else]
        return lastLookaheadSucceeded = false;
        [/#if]
      [/#if]
[/#macro]


[#--
   Generates the routine for an explicit lookahead
   that is used in a nested lookahead.
 --]
[#macro BuildLookaheadRoutine lookahead]
  [#if lookahead.nestedExpansion??]
     // lookahead routine for lookahead at: 
     // ${lookahead.location}
     private final boolean ${lookahead.nestedExpansion.scanRoutineName}() {
        int prevRemainingLookahead = remainingLookahead;
        boolean prevHitFailure = hitFailure;
        Token prevScanAheadToken = currentLookaheadToken;
        try {
          lookaheadRoutineNesting++;
          [@BuildScanCode lookahead.nestedExpansion/]
          return lastLookaheadSucceeded = !hitFailure;
        }
        finally {
           lookaheadRoutineNesting--;
           currentLookaheadToken = prevScanAheadToken;
           remainingLookahead = prevRemainingLookahead;
           hitFailure = prevHitFailure;
        }
     }
   [/#if]
[/#macro]

[#macro BuildLookBehindRoutine lookBehind]
    private final boolean ${lookBehind.routineName}() {
       ListIterator<NonTerminalCall> stackIterator = ${lookBehind.backward?string("stackIteratorBackward", "stackIteratorForward")}();
       [#list lookBehind.path as element]
          [#var elementNegated = (element[0] == "~")]
          [#if elementNegated][#set element = element?substring(1)][/#if]
          [#if element = "."]
              if (!stackIterator.hasNext()) {
                 return lastLookaheadSucceeded = false;
              }
              stackIterator.next();
          [#elseif element = "..."]
             [#if element_index = lookBehind.path?size-1]
                 [#if lookBehind.hasEndingSlash]
                      return lastLookaheadSucceeded = !stackIterator.hasNext();
                 [#else]
                      return lastLookaheadSucceeded = true;
                 [/#if]
             [#else]
                 [#var nextElement = lookBehind.path[element_index+1]]
                 [#var nextElementNegated = (nextElement[0]=="~")]
                 [#if nextElementNegated][#set nextElement=nextElement?substring(1)][/#if]
                 while (stackIterator.hasNext()) {
                    NonTerminalCall ntc = stackIterator.next();
                    [#var equalityOp = nextElementNegated?string("!=", "==")]
                    if (ntc.productionName ${equalityOp} "${nextElement}") {
                       stackIterator.previous();
                       break;
                    }
                    if (!stackIterator.hasNext()) return lastLookaheadSucceeded = false;
                 }
             [/#if]
          [#else]
             if (!stackIterator.hasNext()) return lastLookaheadSucceeded = false;
             NonTerminalCall ntc = stackIterator.next();
             [#var equalityOp = elementNegated?string("==", "!=")]
               if (ntc.productionName ${equalityOp} "${element}") return lastLookaheadSucceeded = false;
          [/#if]
       [/#list]
       [#if lookBehind.hasEndingSlash]
           return lastLookaheadSucceeded = !stackIterator.hasNext();
       [#else]
           return lastLookaheadSucceeded = true;
       [/#if]
    }
[/#macro]

[#macro BuildProductionLookaheadMethod production]
   // BuildProductionLookaheadMethod macro

   private final boolean ${production.lookaheadMethodName}() {
      [#if production.javaCode?? && production.javaCode.appliesInLookahead]
          ${production.javaCode}
       [/#if]
      ${BuildScanCode(production.expansion)}
      return lastLookaheadSucceeded = true;
   }
[/#macro]

[#--
   Macro to build the lookahead code for an expansion.
   This macro just delegates to the various sub-macros
   based on the Expansion's class name.
--]
[#macro BuildScanCode expansion]
  [#var classname=expansion.simpleName]
  [#if classname != "ExpansionSequence" && classname != "ExpansionWithParentheses"]
      if (hitFailure || remainingLookahead<=0) return lastLookaheadSucceeded =!hitFailure;
  // Lookahead Code for ${classname} specified at ${expansion.location}
  [/#if]
  [@CU.HandleLexicalStateChange expansion true]
   [#if classname = "ExpansionWithParentheses"]
      [@BuildScanCode expansion.nestedExpansion /]
   [#elseif expansion.singleToken]
      ${ScanSingleToken(expansion)}
   [#elseif classname = "Assertion"]
      ${ScanCodeAssertion(expansion)} 
   [#elseif classname = "LexicalStateSwitch"]
       ${ScanCodeLexicalStateSwitch(expansion)}
   [#elseif classname = "Failure"]
         ${ScanCodeError(expansion)}
   [#elseif classname = "TokenTypeActivation"]
         ${ScanCodeTokenActivation(expansion)}
   [#elseif classname = "ExpansionSequence"]
      ${ScanCodeSequence(expansion)}
   [#elseif classname = "ZeroOrOne"]
      [@ScanCodeZeroOrOne expansion/]
   [#elseif classname = "ZeroOrMore"]
      [@ScanCodeZeroOrMore expansion /]
   [#elseif classname = "OneOrMore"]
      [@ScanCodeOneOrMore expansion /]
   [#elseif classname = "NonTerminal"]
      [@ScanCodeNonTerminal expansion/]
   [#elseif classname = "TryBlock" || classname="AttemptBlock"]
      [@BuildScanCode expansion.nestedExpansion/]
   [#elseif classname = "ExpansionChoice"]
      [@ScanCodeChoice expansion /]
   [#elseif classname = "CodeBlock"]
      [#if expansion.appliesInLookahead]
         ${expansion}
      [/#if]
   [/#if]
  [/@CU.HandleLexicalStateChange]
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
[#macro ScanCodeSequence sequence]
   [#list sequence.units as sub]
       [@BuildScanCode sub/]
       [#if sub.scanLimit]
          if (!scanToEnd && lookaheadRoutineNesting <= 1) {
             remainingLookahead = ${sub.scanLimitPlus};
          }
       [/#if]
   [/#list]
[/#macro]

[#--
  Generates the lookahead code for a non-terminal.
  It (trivially) just delegates to the code for 
  checking the production's nested expansion 
--]
[#macro ScanCodeNonTerminal nt]
      pushOntoLookaheadStack("${nt.containingProduction.name}", "${nt.inputSource?j_string}", ${nt.beginLine}, ${nt.beginColumn});
      [#var prevProductionVarName = "prevProduction" + CU.newID()]
      String ${prevProductionVarName} = currentLookaheadProduction;
      currentLookaheadProduction = "${nt.production.name}";
      scanToEnd = ${CU.bool(nt.scanToEnd)};
      try {
          if (!${nt.production.lookaheadMethodName}()) return lastLookaheadSucceeded = false;
      }
      finally {
          popLookaheadStack();
          currentLookaheadProduction = ${prevProductionVarName};
      }
[/#macro]

[#macro ScanSingleToken expansion]
    [#var firstSet = expansion.firstSet.tokenNames]
    [#if firstSet?size = 1]
      if (!scanToken(${CU.TT}${firstSet[0]})) return lastLookaheadSucceeded = false;
    [#else]
      if (!scanToken(${expansion.firstSetVarName})) return lastLookaheadSucceeded = false;
    [/#if]
[/#macro]    

[#macro ScanCodeAssertion assertion]
   TODO!!!
[/#macro]

[#macro ScanCodeError expansion]
    if (true) {
      hitFailure = true;
      return lastLookaheadSucceeded = false;
    }
[/#macro]

[#macro ScanCodeTokenActivation activation]
    [#if activation.deactivate]
       deactivateTokenTypes(
    [#else]
       activateTokenTypes(
    [/#if]
    [#list activation.tokenNames as name]
       ${CU.TT}${name} [#if name_has_next],[/#if]
    [/#list]
       );
[/#macro]]

[#macro ScanCodeChoice choice]
   [@CU.newVar "Token", "currentLookaheadToken"/]
   int remainingLookahead${CU.newVarIndex} = remainingLookahead;
   boolean hitFailure${CU.newVarIndex} = hitFailure;
  [#list choice.choices as subseq]
     if (!${CheckExpansion(subseq)}) {
     currentLookaheadToken = token${CU.newVarIndex};
     remainingLookahead=remainingLookahead${CU.newVarIndex};
     hitFailure = hitFailure${CU.newVarIndex};
     [#if !subseq_has_next]
        return lastLookaheadSucceeded = false;
     [/#if]
  [/#list]
  [#list 1..choice.choices?size as unused] } [/#list]
[/#macro]

[#macro ScanCodeZeroOrOne zoo]
   [@CU.newVar type="Token" init="currentLookaheadToken"/]
   if (!${CheckExpansion(zoo.nestedExpansion)}) 
      currentLookaheadToken = token${CU.newVarIndex};
[/#macro]

[#-- 
  Generates lookahead code for a ZeroOrMore construct]
--]
[#macro ScanCodeZeroOrMore zom]
      while (remainingLookahead > 0 && !hitFailure) {
      [@CU.newVar type="Token" init="currentLookaheadToken"/]
         if (!${CheckExpansion(zom.nestedExpansion)}) {
             currentLookaheadToken = token${CU.newVarIndex};
             break;
         }
      }
[/#macro]

[#--
   Generates lookahead code for a OneOrMore construct
   It generates the code for checking a single occurrence
   and then the same code as a ZeroOrMore
--]
[#macro ScanCodeOneOrMore oom]
   if (!${CheckExpansion(oom.nestedExpansion)}) {
      return lastLookaheadSucceeded = false;
   }
   [@ScanCodeZeroOrMore oom /]
[/#macro]


[#macro CheckExpansion expansion]
   [#if expansion.singleToken && !expansion.requiresPredicateMethod]
     [#if expansion.firstSet.tokenNames?size = 1]
      scanToken(${CU.TT}${expansion.firstSet.tokenNames[0]})
     [#else]
      scanToken(${expansion.firstSetVarName})
     [/#if]
   [#else]
      ${expansion.scanRoutineName}()
   [/#if]
[/#macro]


