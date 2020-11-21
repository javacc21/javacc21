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

[#var currentLookaheadExpansion]
[#var TT = "TokenType.", UNLIMITED=2147483647]

 [#if !grammar.options.legacyAPI && grammar.parserPackage?has_content]
   [#-- This is necessary because you can't do a static import from the unnamed or "default package" --]
   [#set TT=""]
 [/#if]

[#macro Generate]
    [#if grammar.choicePointExpansions?size !=0]
       [@BuildLookaheads /]
     [/#if]
[/#macro]


[#macro BuildLookaheads]
  private final boolean scanToken(TokenType expectedType) {
     if (hitFailure) return false;
     if (remainingLookahead <=0) return true;
     currentLookaheadToken = nextToken(currentLookaheadToken);
     TokenType type = currentLookaheadToken.getType();
     if (type != expectedType) return false;
     if (remainingLookahead != Integer.MAX_VALUE) remainingLookahead--;
//     if (type == upToTokenType) remainingLookahead = 0;
     return true;
  }

  private final boolean scanToken(EnumSet<TokenType> types) {
     if (hitFailure) return false;
     if (remainingLookahead <=0) return true;
     currentLookaheadToken = nextToken(currentLookaheadToken);
     TokenType type = currentLookaheadToken.getType();
     if (!types.contains(type)) return false;
     if (remainingLookahead != Integer.MAX_VALUE) remainingLookahead--;
//     if (type == upToTokenType) remainingLookahead = 0;
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
   [#list grammar.expansionsNeedingPredicate as expansion]
       ${BuildPredicateRoutine(expansion)}
   [/#list]
   [#list grammar.allLookaheads as lookahead]
[#--       ${firstSetVar(lookahead)} --]
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
   private final boolean ${expansion.predicateMethodName}() {
     try {
         currentLookaheadToken= currentToken;
         remainingLookahead= ${lookaheadAmount};
         hitFailure = false;
      [#if expansion.hasScanLimit || expansion.hasInnerScanLimit]
         stopAtScanLimit= ${bool(!expansion.hasExplicitNumericalLookahead && !expansion.hasSeparateSyntacticLookahead)};
      [/#if]
      ${BuildPredicateCode(expansion)}
      [#if !expansion.hasSeparateSyntacticLookahead]
         ${BuildScanCode(expansion)}
      [/#if]
      return true;
      }
     finally {
        currentLookaheadToken = null;
     }
   }
[/#macro]



[#macro BuildScanRoutine expansion]
 [#if !expansion.singleToken || expansion.requiresPredicateMethod]
  private final boolean ${expansion.scanRoutineName}() {
   [#if !expansion.insideLookahead]
     if (hitFailure) return false;
     if (remainingLookahead <=0) return true;
     ${BuildPredicateCode(expansion)}
   [/#if]
     ${BuildScanCode(expansion)}
      return true;
  }
 [/#if]
[/#macro]

[#-- Build the code for checking semantic lookahead, lookbehind, and/or syntactic lookahead --]
[#macro BuildPredicateCode expansion]
     [#if expansion.hasSemanticLookahead && expansion.lookahead.semanticLookaheadNested]
       if (!(${expansion.semanticLookahead})) return false;
     [/#if]
     [#if expansion.hasLookBehind]
       if (!${expansion.lookBehind.routineName}()) return false;
     [/#if]
     [#if expansion.hasSeparateSyntacticLookahead]
      if (
      [#if !expansion.lookahead.negated]![/#if]
        ${expansion.lookaheadExpansion.scanRoutineName}())
        return false;
      [/#if]
[/#macro]


[#--
   Generates the routine for an explicit lookahead
   that is used in a nested lookahead.
 --]
[#macro BuildLookaheadRoutine lookahead]
  [#if lookahead.nestedExpansion??]
     private final boolean ${lookahead.nestedExpansion.scanRoutineName}() {
        int prevRemainingLookahead = remainingLookahead;
        boolean prevHitFailure = hitFailure;
        Token prevScanAheadToken = currentLookaheadToken;
        try {
          [@BuildScanCode lookahead.nestedExpansion/]
          return !hitFailure;
        }
        finally {
           currentLookaheadToken = prevScanAheadToken;
           remainingLookahead = prevRemainingLookahead;
           hitFailure = prevHitFailure;
        }
     }
   [/#if]
[/#macro]

[#macro BuildLookaheadRoutine2 expansion]
     private final boolean ${expansion.scanRoutineName}() {
        int prevRemainingLookahead = remainingLookahead;
        boolean prevHitFailure = hitFailure;
        Token prevScanAheadToken = currentLookaheadToken;
        currentLookaheadToken = currentToken;
        try {
          [@BuildScanCode lookahead.nestedExpansion/]
          return !hitFailure;
        }
        finally {
           currentLookaheadToken = prevScanAheadToken;
           remainingLookahead = prevRemainingLookahead;
           hitFailure = prevHitFailure;
        }
     }
[/#macro]


[#macro BuildLookBehindRoutine lookBehind]
    private final boolean ${lookBehind.routineName}() {
       Iterator<NonTerminalCall> stackIterator = ${lookBehind.backward?string("stackIteratorBackward", "stackIteratorForward")}();
       boolean foundProduction = false;
       [#var justSawEllipsis = false]
       [#list lookBehind.path as element]
          [#var elementNegated = (element[0] == "~")]
          [#if elementNegated][#set element = element[1..]][/#if]
          [#if element == "..."]
             [#set justSawEllipsis = true]
          [#elseif element = "."]
             [#set justSawEllipsis = false]
             if (!stackIterator.hasNext()) {
                return ${bool(lookBehind.negated)};
             }
             stackIterator.next();
         [#else]
             [#var exclam = elementNegated?string("!", "")]
             [#if justSawEllipsis]
               foundProduction = false;
               while (stackIterator.hasNext() && !foundProduction) {
                  NonTerminalCall ntc = stackIterator.next();
                  if (${exclam}ntc.productionName.equals("${element}")) {
                     foundProduction = true;
                  }
               }
               if (!foundProduction) {
                  return ${bool(lookBehind.negated)};
               }
           [#else]
               [#var exclam = elementNegated?string("", "!")]
               if (!stackIterator.hasNext()) {
                  return ${bool(lookBehind.negated)};
               } else {
                  NonTerminalCall ntc = stackIterator.next();
                  if (${exclam}ntc.productionName.equals("${element}")) {
                     return ${bool(lookBehind.negated)};
                  }
               }
           [/#if]
           [#set justSawEllipsis = false] 
         [/#if]
       [/#list]
       [#if lookBehind.hasEndingSlash]
           return [#if !lookBehind.negated]![/#if]stackIterator.hasNext();
       [#else]
           return ${bool(!lookBehind.negated)};
       [/#if]
    }
[/#macro]

[#macro BuildProductionLookaheadMethod production]
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
  [#set currentLookaheadExpansion = expansion]
  [#var classname=expansion.simpleName]
  [#if classname != "ExpansionSequence"]
  // Lookahead Code for ${classname} specified on line ${expansion.beginLine} of ${expansion.inputSource}
  [/#if]
  [#if expansion.singleToken]
     ${ScanSingleToken(expansion)}
   [#elseif classname = "Assertion"]
      ${ScanCodeAssertion(expansion)} 
   [#elseif classname = "LexicalStateSwitch"]
      ${ScanCodeLexicalStateSwitch(expansion)}
   [#elseif classname = "Failure"]
      ${ScanCodeError(expansion)}
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
[/#macro]

[#--
   Generates the lookahead code for an ExpansionSequence
   The count parameter is not being used right now. The original purpose
   was to specify the maximum number of tokens 
   we need to lookahead, so we don't generate unnecessary code. However, this
   kind of space optimization is probably not worth the candle and makes things
   complicated. So it is currently disabled. (May REVISIT later.)
--]
[#macro ScanCodeSequence sequence]
   [#list sequence.units as sub]
       [@BuildScanCode sub/]
       [#if sub.scanLimit]
          if (hitFailure) return false;
          if (stopAtScanLimit && lookaheadStack.size() <= 1) {
         [#if sub.scanLimitPlus >0]
             remainingLookahead = ${sub.scanLimitPlus};
         [#else]
             return true;
         [/#if]
          }
       [/#if]
       [#--set count = count - sub.minimumSize]
       [#if count<=0][#break][/#if--]
   [/#list]
[/#macro]

[#--
  Generates the lookahead code for a non-terminal.
  It (trivially) just delegates to the code for 
  checking the production's nested expansion 
--]
[#macro ScanCodeNonTerminal nt]
      pushOntoLookaheadStack("${nt.containingProduction.name}", "${nt.inputSource}", ${nt.beginLine}, ${nt.beginColumn});
      [#set newVarIndex = newVarIndex +1]
      [#var prevProductionVarName = "prevProduction" + newVarIndex]
      String ${prevProductionVarName} = currentLookaheadProduction;
      currentLookaheadProduction = "${nt.production.name}";
      [#if nt.ignoreUpToHere && nt.production.expansion.hasScanLimit]
         stopAtScanLimit = false;
      [/#if]
      try {
          if (!${nt.production.lookaheadMethodName}()) return false;
      }
      finally {
          popLookaheadStack();
          currentLookaheadProduction = ${prevProductionVarName};
      }
[/#macro]





[#macro ScanSingleToken expansion]
    [#var firstSet = expansion.firstSet.tokenNames]
    [#if firstSet?size = 1]
      if (!scanToken(${TT}${firstSet[0]})) return false;
    [#else]
      if (!scanToken(${expansion.firstSetVarName})) return false;
    [/#if]
[/#macro]    

[#macro ScanCodeLexicalStateSwitch switch]
   token_source.switchTo(LexicalState.${switch.lexicalStateName});
[/#macro]

[#macro ScanCodeAssertion assertion]
    
[/#macro]

[#macro ScanCodeError expansion]
    hitFailure = true;
[/#macro]

[#macro ScanCodeChoice choice]
   [@newVar "Token", "currentLookaheadToken"/]
   int remainingLookahead${newVarIndex} = remainingLookahead;
   boolean hitFailure${newVarIndex} = hitFailure;
  [#list choice.choices as subseq]
     if (!${CheckExpansion(subseq)}) {
     [#if subseq_has_next]
        currentLookaheadToken = token${newVarIndex};
        remainingLookahead = remainingLookahead${newVarIndex};
        hitFailure = hitFailure${newVarIndex};
     [#else]
        return false;
     [/#if]
  [/#list]
  [#list 1..choice.choices?size as unused] } [/#list]
[/#macro]

[#macro ScanCodeZeroOrOne zoo]
   [@newVar type="Token" init="currentLookaheadToken"/]
   if (!${CheckExpansion(zoo.nestedExpansion)}) 
      currentLookaheadToken = token${newVarIndex};
[/#macro]

[#-- 
  Generates lookahead code for a ZeroOrMore construct]
--]
[#macro ScanCodeZeroOrMore zom]
      while (remainingLookahead > 0 && !hitFailure) {
      [@newVar type="Token" init="currentLookaheadToken"/]
         if (!${CheckExpansion(zom.nestedExpansion)}) {
             currentLookaheadToken = token${newVarIndex};
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
   [#if expansion.singleToken && !expansion.requiresPredicateMethod]
     [#if expansion.firstSet.tokenNames?size = 1]
      scanToken(${TT}${expansion.firstSet.tokenNames[0]})
     [#else]
      scanToken(${expansion.firstSetVarName})
     [/#if]
   [#else]
      ${expansion.scanRoutineName}()
   [/#if]
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

[#function bool val]
   [#return val?string("true", "false")/]
[/#function]