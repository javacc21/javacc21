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
     Token peekedToken = nextToken(currentLookaheadToken);
     TokenType type = peekedToken.getType();
     if (type != expectedType) return false;
     if (remainingLookahead != UNLIMITED) remainingLookahead--;
     currentLookaheadToken = peekedToken;
     return true;
  }

  private final boolean scanToken(EnumSet<TokenType> types) {
     Token peekedToken = nextToken(currentLookaheadToken);
     TokenType type = peekedToken.getType();
     if (!types.contains(type)) return false;
     if (remainingLookahead != UNLIMITED) remainingLookahead--;
//     if (type == upToTokenType) remainingLookahead = 0;
     currentLookaheadToken = peekedToken;
     return true;
  }

//====================================
 // Lookahead Routines
 //====================================
   [#list grammar.choicePointExpansions as expansion]
      [#if expansion.parent.class.simpleName != "BNFProduction"]
        ${BuildScanRoutine(expansion)}
      [/#if]
   [/#list]
   [#list grammar.assertionExpansions as expansion]
      ${BuildAssertionRoutine(expansion)}
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
   [#list grammar.parserProductions as production]
      ${BuildProductionLookaheadMethod(production)}
   [/#list]
[/#macro]  

[#macro BuildPredicateRoutine expansion] 
  [#var lookaheadAmount = expansion.lookaheadAmount]
  [#if lookaheadAmount = 2147483647][#set lookaheadAmount = "UNLIMITED"][/#if]
  // BuildPredicateRoutine: expansion at ${expansion.location}
   private final boolean ${expansion.predicateMethodName}() {
     [#var prevNonTerminalNestingVarName = "nonTerminalNesting" + CU.newID(),
           prevCurrentLookaheadTokenVarName = "currentLookaheadToken"+ CU.newID(),
           prevLookaheadRoutineNestingVarName = "lookaheadRoutineNesting" + CU.newID()
     ]
     int ${prevNonTerminalNestingVarName} = nonTerminalNesting;
     int ${prevLookaheadRoutineNestingVarName} = lookaheadRoutineNesting;
     Token ${prevCurrentLookaheadTokenVarName} = currentLookaheadToken;
     try {
         lookaheadRoutineNesting++;
         remainingLookahead= ${lookaheadAmount};
         if (currentLookaheadToken == null) currentLookaheadToken= lastConsumedToken;
         hitFailure = false;
         scanToEnd = false;
      ${BuildPredicateCode(expansion)}
      [#if !expansion.hasSeparateSyntacticLookahead]
         ${BuildScanCode(expansion)}
      [/#if]
      return true;
      }
      finally {
        lookaheadRoutineNesting = ${prevLookaheadRoutineNestingVarName};
        currentLookaheadToken = ${prevCurrentLookaheadTokenVarName};
        nonTerminalNesting = ${prevNonTerminalNestingVarName};
     }
   }
[/#macro]

[#macro BuildScanRoutine expansion]
 [#if !expansion.singleToken]
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
      return true;
    }
    finally {
       lookaheadRoutineNesting--;
    }
  }
 [/#if]
[/#macro]

[#macro BuildAssertionRoutine expansion]
  // scanahead routine for assertion at: 
  // ${expansion.parent.location}
  // BuildAssertionRoutine macro
    private final boolean ${expansion.scanRoutineName}() {
     [#var storeCurrentLookaheadVar = CU.newVarName("currentLookahead")]
       remainingLookahead = UNLIMITED;
       scanToEnd=true;
       Token ${storeCurrentLookaheadVar} = currentLookaheadToken;
       if (currentLookaheadToken == null) {
          currentLookaheadToken = lastConsumedToken;
       }
    try {
      lookaheadRoutineNesting++;
      ${BuildScanCode(expansion)}
      return true;
    }
    finally {
       lookaheadRoutineNesting--;
       currentLookaheadToken = ${storeCurrentLookaheadVar};
    }
  }
[/#macro]


[#-- Build the code for checking semantic lookahead, lookbehind, and/or syntactic lookahead --]
[#macro BuildPredicateCode expansion]
     // BuildPredicateCode macro
     [#if expansion.hasSemanticLookahead && (expansion.lookahead.semanticLookaheadNested || expansion.containingProduction.onlyForLookahead)]
       if (!(${expansion.semanticLookahead})) return false;
     [/#if]
     [#if expansion.hasLookBehind]
       if ([#if !expansion.lookBehind.negated]![/#if]
       ${expansion.lookBehind.routineName}()) return false;
     [/#if]
     if (remainingLookahead <=0) return true;
     [#if expansion.hasSeparateSyntacticLookahead]
      if (
      [#if !expansion.lookahead.negated]![/#if]
        ${expansion.lookaheadExpansion.scanRoutineName}()) return false;
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
          return !hitFailure;
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
       NonTerminalCall ntc = null;
       [#list lookBehind.path as element]
          [#var elementNegated = (element[0] == "~")]
          [#if elementNegated][#set element = element?substring(1)][/#if]
          [#if element = "."]
              if (!stackIterator.hasNext()) {
                 return false;
              }
              stackIterator.next();
          [#elseif element = "..."]
             [#if element_index = lookBehind.path?size-1]
                 [#if lookBehind.hasEndingSlash]
                      return !stackIterator.hasNext();
                 [#else]
                      return true;
                 [/#if]
             [#else]
                 [#var nextElement = lookBehind.path[element_index+1]]
                 [#var nextElementNegated = (nextElement[0]=="~")]
                 [#if nextElementNegated][#set nextElement=nextElement?substring(1)][/#if]
                 while (stackIterator.hasNext()) {
                    ntc = stackIterator.next();
                    [#var equalityOp = nextElementNegated?string("!=", "==")]
                    if (ntc.productionName ${equalityOp} "${nextElement}") {
                       stackIterator.previous();
                       break;
                    }
                    if (!stackIterator.hasNext()) return false;
                 }
             [/#if]
          [#else]
             if (!stackIterator.hasNext()) return false;
             ntc = stackIterator.next();
             [#var equalityOp = elementNegated?string("==", "!=")]
               if (ntc.productionName ${equalityOp} "${element}") return false;
          [/#if]
       [/#list]
       [#if lookBehind.hasEndingSlash]
           return !stackIterator.hasNext();
       [#else]
           return true;
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
      return true;
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
      if (hitFailure || remainingLookahead<=0) return !hitFailure;
  // Lookahead Code for ${classname} specified at ${expansion.location}
  [/#if]
  [@CU.HandleLexicalStateChange expansion true]
   [#--
   // Building scan code for: ${classname}
   // at: ${expansion.location}
   --]
   [#if classname = "ExpansionWithParentheses"]
      [@BuildScanCode expansion.nestedExpansion /]
   [#elseif expansion.singleToken]
      [#if !expansion.isRegexp]
   // Applying single-token optimization for expansion of type ${classname}
   // ${expansion.location}
      [/#if]
      ${ScanSingleToken(expansion)}
   [#elseif classname = "Assertion"]
      ${ScanCodeAssertion(expansion)} 
   [#elseif classname = "LexicalStateSwitch"]
       ${ScanCodeLexicalStateSwitch(expansion)}
   [#elseif classname = "Failure"]
         ${ScanCodeError(expansion)}
   [#elseif classname = "UncacheTokens"]
         uncacheTokens();
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
      [#if expansion.appliesInLookahead || expansion.insideLookahead || expansion.containingProduction.onlyForLookahead]
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
          if (!scanToEnd && lookaheadRoutineNesting <= 1 && nonTerminalNesting <=1) {
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
      // NonTerminal ${nt.name} at ${nt.location}
      pushOntoLookaheadStack("${nt.containingProduction.name}", "${nt.inputSource?j_string}", ${nt.beginLine}, ${nt.beginColumn});
      [#var prevScanToEndVarName = "prevScanToEnd" + CU.newID()]
      boolean ${prevScanToEndVarName} = scanToEnd;
      currentLookaheadProduction = "${nt.production.name}";
      scanToEnd = ${CU.bool(nt.scanToEnd)};
      ++nonTerminalNesting;
      try {
          if (!${nt.production.lookaheadMethodName}()) return false;
      }
      finally {
          popLookaheadStack();
          scanToEnd = ${prevScanToEndVarName};
          --nonTerminalNesting;
      }
[/#macro]

[#macro ScanSingleToken expansion]
    [#var firstSet = expansion.firstSet.tokenNames]
    [#if firstSet?size = 1]
      if (!scanToken(${CU.TT}${firstSet[0]})) return false;
    [#else]
      if (!scanToken(${expansion.firstSetVarName})) return false;
    [/#if]
[/#macro]    

[#macro ScanCodeAssertion assertion]
   [#if assertion.assertionExpression?? && 
        (assertion.insideLookahead || assertion.semanticLookaheadNested || assertion.containingProduction.onlyForLookahead)]
      if (!(${assertion.assertionExpression})) {
         hitFailure = true;
         return false;
      }
   [/#if]
   [#if assertion.expansion??]
      if ([#if !assertion.expansionNegated]![/#if] 
         ${assertion.expansion.scanRoutineName}()
      ) {
        hitFailure = true;
        return false;
      }
   [/#if]
[/#macro]

[#macro ScanCodeError expansion]
    if (true) {
      hitFailure = true;
      return false;
    }
[/#macro]

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
        return false;
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
      return false;
   }
   [@ScanCodeZeroOrMore oom /]
[/#macro]


[#macro CheckExpansion expansion]
   [#if expansion.singleToken]
     [#if expansion.firstSet.tokenNames?size = 1]
      scanToken(${CU.TT}${expansion.firstSet.tokenNames[0]})
     [#else]
      scanToken(${expansion.firstSetVarName})
     [/#if]
   [#else]
      ${expansion.scanRoutineName}()
   [/#if]
[/#macro]
