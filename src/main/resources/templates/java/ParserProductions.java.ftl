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
 
[#macro ProductionsCode] 
   static private final int INDEFINITE = Integer.MAX_VALUE;
  //=================================
 // Start of methods for BNF Productions
 //=================================
  [#list grammar.parserProductions as production]
    [#set currentProduction = production]
    [@ParserProduction production/]
  [/#list]
[/#macro]


[#macro Phase2Code]
 [#if parserData.phase2Lookaheads?size ==0][#return][/#if]
//====================================
// Start of methods for Phase 2 Lookaheads
//====================================
  [#list parserData.phase2Lookaheads as lookahead]
     [@buildPhase2Routine lookahead.nestedExpansion/]
  [/#list]
[/#macro]

[#macro Phase3Code]
 [#if parserData.phase3Expansions?size ==0][#return][/#if]
//====================================
 // Start of methods for Phase 3 Lookaheads
 //====================================
   [#list parserData.phase3Expansions as expansion]
      [@buildPhase3Routine expansion, expansion.phase3LookaheadAmount /]
   [/#list]
[/#macro]   


[#macro ParserProduction production]
    ${production.leadingComments}
// ${production.inputSource}, line ${production.beginLine}
    final ${production.accessMod!"public"} 
    ${production.returnType}
    ${production.name}(${production.parameterList}) 
    throws ParseException
    [#list (production.throwsList.types)! as throw], ${throw}[/#list] {
     if (trace_enabled) LOGGER.info("Entering production defined on line ${production.beginLine} of ${production.inputSource}");
     if (cancelled) throw new CancellationException();
     [#-- ${production.javaCode} --]
   [@BuildCode production.expansion /]
    }   
[/#macro]

[#macro BuildCode expansion]
  // Code for ${expansion.name!"expansion"} specified on line ${expansion.beginLine} of ${expansion.inputSource}
    [#var forced=expansion.forced, nodeVarName, parseExceptionVar, production, treeNodeBehavior, buildTreeNode=false, forcedVarName, closeCondition = "true"]
    [#set treeNodeBehavior = expansion.treeNodeBehavior]
    [#if expansion.parent.class.name?ends_with("Production")]
      [#set production = expansion.parent]
      [#set forced = production.forced || forced]
    [/#if]
    [#if grammar.options.treeBuildingEnabled]
      [#set buildTreeNode = (treeNodeBehavior?is_null && production?? && !grammar.options.nodeDefaultVoid)
                        || (treeNodeBehavior?? && !treeNodeBehavior.void)]
    [/#if]
    [#if buildTreeNode]
        [@setupTreeVariables .scope /]
        [#if grammar.options.faultTolerant && forced]
        boolean ${forcedVarName} = this.tolerantParsing;
        [#else]
        boolean ${forcedVarName} = false;
        [/#if]
   	[@createNode treeNodeBehavior nodeVarName /]
          ParseException ${parseExceptionVar} = null;
         try {
    [/#if]
        [#if production??]${production.javaCode}[/#if]
        [@BuildPhase1Code expansion/]
    [#if production?? && production.returnType == "void"]
        if (trace_enabled) LOGGER.info("Exiting normally from ${production.name}");
    [/#if]
    [#if buildTreeNode]
         }
         catch (ParseException e) { 
             ${parseExceptionVar} = e;
      [#if !grammar.options.faultTolerant]
             throw e;
      [#else]             
             if (trace_enabled) LOGGER.info("We have a parse error but somehow handled it. (Or did we?)");
	    [#if production?? && production.returnType != "void"]
	       [#if production.returnType == production.nodeName]
	          [#-- We just assume that if the return type is the same as the type of the node, we want to return CURRENT_NODE.
	                This is not theoretically correct, but will probably be true about 99% of the time. Maybe REVISIT. --]
	           return ${nodeVarName};
	       [#else]
	          [#-- This is a bit screwy will not work if the return type is a primitive type --]
	           return null;
	       [/#if]
	  [/#if]
[/#if]	
         }
         finally {
[#if !grammar.options.faultTolerant]
             if (buildTree) {
                 if (${parseExceptionVar} != null) {
                     if (trace_enabled) LOGGER.warning("ParseException: " + ${parseExceptionVar}.getMessage());
                     clearNodeScope();
                 } else {
	                  ${nodeVarName}.setEndLine(current_token.getEndLine());
		              ${nodeVarName}.setEndColumn(current_token.getEndColumn());
 	                 closeNodeScope(${nodeVarName}, ${closeCondition});
                 }
             }
[#else]
             if (buildTree) {
                 if (${parseExceptionVar} == null) {
	                  ${nodeVarName}.setEndLine(current_token.getEndLine());
		              ${nodeVarName}.setEndColumn(current_token.getEndColumn());
 	                 closeNodeScope(${nodeVarName}, ${closeCondition});
                 }
	             else {
                     if (trace_enabled) LOGGER.warning("ParseException ${parseExceptionVar}: " + ${parseExceptionVar}.getMessage());
	                 ${nodeVarName}.setParseException(${parseExceptionVar});
                     if (${forcedVarName}) { 
                        Token virtualToken = insertVirtualToken(TokenType.${expansion.finalSet.firstTokenName});  
                        String message = "Inserted virtual token of type " + virtualToken.getType()
                                                  +"\non line " + virtualToken.getBeginLine()
                                                  + ", column " + virtualToken.getBeginColumn()
                                                   + " of " + token_source.getInputSource()
                                                  +"\n to complete expansion in ${currentProduction.name}\n";
                        message += ${parseExceptionVar}.getMessage(); 
                        addParsingProblem(new ParsingProblem(message, ${nodeVarName})); 
		                closeNodeScope(${nodeVarName}, true); 
		             } else {
                        closeNodeScope(${nodeVarName}, false);
                        if (trace_enabled) LOGGER.info("Rethrowing " + "${parseExceptionVar}");
		                throw ${parseExceptionVar};
		             }
	             }
        }
[/#if]  
         }       
          ${grammar.utils.popNodeVariableName()!}
    [/#if]
[/#macro]

[#--  A helper macro to set up some variables so that the BuildCode macro can be a bit more readable --]
[#macro setupTreeVariables callingScope]
    [#set nodeNumbering = nodeNumbering +1]
    [#set nodeVarName = currentProduction.name + nodeNumbering in callingScope]
    [#set forcedVarName = callingScope.nodeVarName+"forced" in callingScope]
    ${grammar.utils.pushNodeVariableName(callingScope.nodeVarName)!}
    [#set parseExceptionVar = "parseException"+nodeNumbering in callingScope]
    [#if !callingScope.treeNodeBehavior??]
        [#if grammar.options.smartNodeCreation]
           [#set treeNodeBehavior = {"name" : callingScope.production.name, "condition" : "1", "gtNode" : true, "void" :false} in callingScope]
        [#else]
           [#set treeNodeBehavior = {"name" : callingScope.production.name, "condition" : null, "gtNode" : false, "void" : false} in callingScope]
        [/#if]
     [/#if]
     [#if callingScope.treeNodeBehavior.condition?has_content]
       [#set closeCondition = callingScope.treeNodeBehavior.condition in callingScope]
       [#if callingScope.treeNodeBehavior.gtNode]
          [#set closeCondition = "nodeArity() > " + callingScope.closeCondition in callingScope]
       [/#if]
    [/#if]
[/#macro]


[#--  Boilerplate code to create the node variable --]
[#macro createNode treeNodeBehavior nodeVarName]
   [#var nodeName = NODE_PREFIX + currentProduction.name]
   [#if treeNodeBehavior?? && treeNodeBehavior.nodeName??]
      [#set nodeName = NODE_PREFIX + treeNodeBehavior.nodeName]
   [/#if]
   ${nodeName} ${nodeVarName} = null;
   if (buildTree) {
   [#if NODE_USES_PARSER]
        ${nodeVarName} = new ${nodeName}(this);
   [#else]
       ${nodeVarName} = new ${nodeName}();
   [/#if]
       Token start = getToken(1);
       ${nodeVarName}.setBeginLine(start.beginLine);
       ${nodeVarName}.setBeginColumn(start.beginColumn);
       ${nodeVarName}.setInputSource(this.getInputSource());
	    openNodeScope(${nodeVarName});
  [#if grammar.usesjjtreeOpenNodeScope]
   	   jjtreeOpenNodeScope(${nodeVarName});
  [/#if]
  [#if grammar.usesOpenNodeScopeHook]
       openNodeScopeHook(${nodeVarName});
  [/#if]
  }
[/#macro]


[#macro BuildPhase1Code expansion]
    [#var classname=expansion.class.name?split(".")?last]
    [#if classname = "CodeBlock"]
       ${expansion}
    [#elseif classname = "ExpansionSequence"]
	   [#list expansion.units as subexp]
	       [@BuildCode subexp/]
	   [/#list]        
    [#elseif classname = "NonTerminal"]
       [@BuildPhase1CodeNonTerminal expansion/]
    [#elseif expansion.isRegexp]
       [@BuildPhase1CodeRegexp expansion/]
    [#elseif classname = "TryBlock"]
       [@BuildPhase1CodeTryBlock expansion/]
    [#elseif classname = "ZeroOrOne"]
       [@BuildPhase1CodeZeroOrOne expansion/]
    [#elseif classname = "ZeroOrMore"]
       [@BuildPhase1CodeZeroOrMore expansion/]
    [#elseif classname = "OneOrMore"]
        [@BuildPhase1CodeOneOrMore expansion/]
    [#elseif classname = "ExpansionChoice"]
        [@BuildPhase1CodeChoice expansion/]
    [/#if]
[/#macro]

[#macro BuildPhase1CodeRegexp regexp]
       [#if regexp.LHS??]
          ${regexp.LHS} =  
       [/#if]
  [#if !grammar.options.faultTolerant]       
       consumeToken(TokenType.${regexp.label});
   [#else]
        consumeToken(TokenType.${regexp.label}, ${regexp.forced?string("true", "false")});
   [/#if]
[/#macro]

[#macro BuildPhase1CodeTryBlock tryblock]
   [#var nested=tryblock.nestedExpansion]
       try {
          [@BuildCode nested/]
       }
   [#list tryblock.catchBlocks as catchBlock]
       ${catchBlock}
   [/#list]
       ${tryblock.finallyBlock!}
[/#macro]

[#macro BuildPhase1CodeNonTerminal nonterminal]
   [#if !nonterminal.LHS??]
       ${nonterminal.name}(${nonterminal.args!});
   [#else]
       ${nonterminal.LHS} = ${nonterminal.name}(${nonterminal.args!});
    [/#if]
[/#macro]


[#macro BuildPhase1CodeZeroOrOne zoo]
    [#var nestedExp=zoo.nestedExpansion]
    [#var lookahead=zoo.lookahead]
    [#if lookahead.alwaysSucceeds]
       [@BuildCode nestedExp/]
    [#else]
       [#var expansionCode]
       [#set expansionCode]
          [@BuildCode nestedExp/]
       [/#set]
       [@BuildBinaryChoiceCode lookahead, expansionCode, null/]
    [/#if]
[/#macro]

[#macro BuildPhase1CodeZeroOrMore zom]
    [#var nestedExp=zom.nestedExpansion]
    [#var lookahead=zom.lookahead]
    ${zom.label}:
    while (true) {
       [@BuildBinaryChoiceCode lookahead, null, "break "+zom.label+";"/]
       [@BuildCode nestedExp/]
    }
[/#macro]

[#macro BuildPhase1CodeOneOrMore oom]
   [#var nestedExp=oom.nestedExpansion]
   [#var lookahead=oom.lookahead]
   ${oom.label}:
   while (true) {
      [@BuildCode nestedExp/]
      [@BuildBinaryChoiceCode lookahead, null, "break "+oom.label+";"/]
   }
[/#macro]

[#macro BuildPhase1CodeChoice choice]
   [#var lookaheads=[] actions=[]]
   [#var defaultAction="throw new ParseException(current_token.getNext());"]
   [#var inPhase1=false]
   [#var indentLevel=0]
   [#list choice.choices as nested]
      [#var action]
      [#set action]
         [@BuildCode nested/]
      [/#set]
      [#var la = nested.lookahead]
      [#if !la.alwaysSucceeds]
         [#set lookaheads = lookaheads+[la]]
         [#set actions = actions+[action]]
      [#else]
         [#set defaultAction = action]
         [#break]
      [/#if]
   [/#list]
   [#list lookaheads as lookahead]
      [#if lookahead.requiresPhase2Routine]
         [#if lookahead_index = 0]
            if (
            [#set indentLevel = indentLevel+1]
         [#elseif !inPhase1]
            } else if (
         [#else]
            default:
               if (
           [#if lookahead.semanticLookahead??]
               (${lookahead.semanticLookahead}) &&
           [/#if]
                [#set indentLevel = indentLevel+1]
         [/#if]
                [#var lookaheadAmount = lookahead.amount]
                [#if lookaheadAmount == 2147483647][#set lookaheadAmount = "INDEFINITE"][/#if]
                ${lookahead.nestedExpansion.phase2RoutineName}(${lookaheadAmount})
               ) { 
                   ${actions[lookahead_index]}
         [#set inPhase1 = false]
       [#elseif lookahead.amount = 1&& !lookahead.semanticLookahead?? &&!lookahead.possibleEmptyExpansion]
          [#if !inPhase1]
                 [#if lookahead_index != 0]
                 } else {
                 [/#if]
                 switch (nextTokenType()) { 
              [#set indentLevel = indentLevel+1]
          [/#if]
          [#list lookahead.firstSetTokenNames as tokenName]
                 case ${tokenName}: 
          [/#list]
                    ${actions[lookahead_index]}
                    break;
          [#set inPhase1 = true]
    [#else]
          [#if lookahead_index = 0]
             if (
             [#set indentLevel = indentLevel+1]
          [#elseif !inPhase1]
             } else if (
          [#else]
             default:
              if (
                [#set indentLevel = indentLevel+1]
          [/#if]
                  ${lookahead.semanticLookahead!}) {
                      ${actions[lookahead_index]}
          [#set inPhase1 = false]
      [/#if]
   [/#list]
      [#if lookaheads?size = 0]
           ${defaultAction}
      [#elseif !inPhase1]
           } else {
             ${defaultAction}
      [#else]
             default:
                ${defaultAction}
      [/#if]
      [#if indentLevel != 0]
         [#list 1..indentLevel as unused]
           }
         [/#list]
      [/#if]
[/#macro]

[#macro BuildBinaryChoiceCode lookahead action fallback]
   [#var emptyAction=!action?has_content]
   [#var emptyFallback=!action?has_content]
   [#var condition=lookahead.semanticLookahead!]
   [#if lookahead.requiresPhase2Routine]
      [#set condition]
        [#if lookahead.negated]![/#if]
        ${lookahead.nestedExpansion.phase2RoutineName}(${lookahead.amount})
        [#if lookahead.semanticLookahead??]
          && (${lookahead.semanticLookahead})
        [/#if]
      [/#set]
      [#set condition = condition?replace("2147483647", "INDEFINITE")]
   [#elseif lookahead.amount = 1&&!lookahead.possibleEmptyExpansion]
      [@newVar type="TokenType" init="nextTokenType()"/]
      [#set condition]
      [#list lookahead.firstSetTokenNames as tokenName]
             tokentype${newVarIndex} == TokenType.${tokenName} [#if tokenName_has_next]||[/#if]
      [/#list]
     [/#set]
   [/#if]
  [@ifelse condition, action, fallback/]
[/#macro]

[#macro ifelse condition action1 action2]
   [#if condition?is_null || condition?trim?length = 0]
      ${action1!}
   [#else]
      [#if action1?has_content]
         if (${condition}) {
            ${action1}
         }
        [#if action2?has_content]
         else {
            ${action2}
         }
        [/#if]
      [#elseif action2?has_content]
         if (!(${condition})) {
            ${action2}
         }
      [/#if]
   [/#if] 
[/#macro]

[#var parserData=grammar.parserData]
[#var nodeNumbering = 0]
[#var NODE_USES_PARSER = grammar.options.nodeUsesParser]
[#var NODE_PREFIX = grammar.options.nodePrefix]
[#var currentProduction]

[#macro buildPhase2Routine expansion]
   private boolean ${expansion.phase2RoutineName}(int maxLookahead) {
      indefiniteLookahead = (maxLookahead == INDEFINITE);
      remainingLookahead = maxLookahead; 
      lastScannedToken = currentLookaheadToken = current_token;
      try { 
            return !${expansion.phase3RoutineName}();
      }
      catch(LookaheadSuccess ls) {
          return true; 
      }
  }
[/#macro]

[#var currentPhase3Expansion]

[#macro buildPhase3Routine expansion count]
   [#if expansion.isRegexp][#return][/#if]
     private boolean ${expansion.phase3RoutineName}() {
      [@buildPhase3Code expansion, count/]
      return false;
    }
[/#macro]

[#macro buildPhase3Code expansion count]
   [#var classname=expansion.class.name?split(".")?last]
   [#if expansion.isRegexp]
      [@Phase3CodeRegexp expansion/]
   [#elseif classname = "ExpansionSequence"]
      [@Phase3CodeSequence expansion, count/]
   [#elseif classname = "ZeroOrOne"]
      [@Phase3CodeZeroOrOne expansion/]
   [#elseif classname = "ZeroOrMore"]
      [@Phase3CodeZeroOrMore expansion/]
   [#elseif classname = "OneOrMore"]
      [@Phase3CodeOneOrMore expansion/]
   [#elseif classname = "NonTerminal"]
      [@Phase3CodeNonTerminal expansion/]
   [#elseif classname = "TryBlock"]
      [@buildPhase3Code expansion.nestedExpansion, count/]
   [#elseif classname = "ExpansionChoice"]
      [@Phase3CodeChoice expansion, count/]
  [/#if]
[/#macro]

[#macro Phase3CodeChoice choice count]
   [@newVar "Token", "currentLookaheadToken"/]
  [#list choice.choices as subseq]
	  [#var lookahead=subseq.lookahead]
	  [#if lookahead.semanticLookahead??]
	    semanticLookahead = ${lookahead.semanticLookahead};
	  [/#if]
	  if (
	  [#if lookahead.semanticLookahead??]
	     !semanticLookahead || 
	  [/#if]
	  [#if subseq_has_next]
	     [@InvokePhase3Routine subseq/]) {
	        currentLookaheadToken = token${newVarIndex};
	  [#else]
	     [@InvokePhase3Routine subseq/]
	     ) 
	     return true;
	  [/#if]
  [/#list]
  [#var numBraces=choice.choices?size-1]
  [#if numBraces>0]
    [#list 1..numBraces as unused]
    }
    [/#list]
  [/#if]
[/#macro]

[#macro Phase3CodeRegexp regexp]
     if (scanToken(TokenType.${regexp.label})) return true;
[/#macro]

[#macro Phase3CodeZeroOrOne zoo]
   [@newVar type="Token" init="currentLookaheadToken"/]
   if ([@InvokePhase3Routine zoo.nestedExpansion/]) 
      currentLookaheadToken = token${newVarIndex};
[/#macro]

[#macro Phase3CodeZeroOrMore zom]
      while (true) {
         [@newVar type="Token" init="currentLookaheadToken"/]
         if ([@InvokePhase3Routine zom.nestedExpansion/]) {
             currentLookaheadToken = token${newVarIndex};
             break;
         }
      }
[/#macro]

[#macro Phase3CodeOneOrMore oom]
   if ([@InvokePhase3Routine oom.nestedExpansion/]) return true;
   while (true) {
       [@newVar type="Token" init="currentLookaheadToken"/]
       if ([@InvokePhase3Routine oom.nestedExpansion/]) {
           currentLookaheadToken = token${newVarIndex};
           break;
       }
   }
[/#macro]

[#macro Phase3CodeNonTerminal nt]
      if ([@InvokePhase3Routine nt.production.expansion/])
         return true;
[/#macro]

[#macro Phase3CodeSequence sequence count]
   [#list sequence.units as sub]
       [@buildPhase3Code sub, count/]
       [#set count = count - sub.minimumSize]
       [#if count<=0][#break][/#if]
   [/#list]
[/#macro]
    
[#macro InvokePhase3Routine expansion]
   [#if expansion.isRegexp]
       scanToken(TokenType.${expansion.label})
   [#else]
      ${expansion.phase3RoutineName}()
   [/#if]
[/#macro]

[#var newVarIndex=0]
[#macro newVar type init=null]
   [#set newVarIndex = newVarIndex+1]
   ${type} ${type?lower_case}${newVarIndex}
   [#if init??]
      = ${init}
   [/#if]
   ;
[/#macro]   

