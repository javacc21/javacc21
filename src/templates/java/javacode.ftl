[#ftl strict_vars=true]
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

[#var parserData=grammar.parserData]
[#var tokenMaskIndex=0]
[#var nodeNumbering = 0]
[#var jj3_expansion]
[#var NODE_USES_PARSER = grammar.options.nodeUsesParser]
[#var NODE_PREFIX = grammar.options.nodePrefix]
[#var currentProduction]

[#macro buildPhase2Routine expansion]
  private boolean jj_2${expansion.internalName}(int xla) { 
      jj_la = xla; 
      jj_lastpos = jj_scanpos = current_token;
      try { 
          return !jj_3${expansion.internalName}(); 
      }
      catch(LookaheadSuccess ls) {
          return true; 
      }
      [#if grammar.options.errorReporting]
      finally {
          jj_save(${(expansion.internalName?substring(1)?number-1)}, xla);
      }
      [/#if]
  }
[/#macro]

[#macro BNFProduction prod]
    ${prod.leadingComments}
// ${prod.inputSource}, line ${prod.beginLine}
    final ${prod.accessMod!"public"} 
    ${prod.returnType}
    ${prod.name}(${prod.parameterList}) 
    throws ParseException
    [#list (prod.throwsList.types)! as throw], ${throw}[/#list] {
    [#if grammar.options.debugParser]
         trace_call("${prod.name}");
         try {
    [/#if]
         ${prod.javaCode}
    [@BuildCode prod.expansion/]
    [#if grammar.options.debugParser]
         } finally {
             trace_return("${prod.name}");
         }
    [/#if]
    }   
[/#macro]

[#macro BuildCode expansion]
    [#var nodeVarName, hitExceptionVar, production, treeNodeBehavior, buildTreeNode=false]
    [#if expansion?is_null]
      [#set production = currentProduction]
      [#set treeNodeBehavior = production.treeBuildingAnnotation]
// ${production.inputSource}, line ${production.beginLine}
    [#else]
       [#if expansion.inputSource??]
         // ${expansion.inputSource}, line ${expansion.beginLine}
       [/#if]
      [#set treeNodeBehavior = expansion.treeNodeBehavior]
      [#if expansion.parentObject.class.name?ends_with("Production")]
        [#set production = expansion.parentObject]
      [/#if]
    [/#if]
    [#if grammar.options.treeBuildingEnabled]
      [#set buildTreeNode = (treeNodeBehavior?is_null && production?? && !grammar.options.nodeDefaultVoid)
                        || (treeNodeBehavior?? && !treeNodeBehavior.void)]
    [/#if]
    [#if buildTreeNode]
        [#set nodeNumbering = nodeNumbering+1]
        [#set nodeVarName = "node" + nodeNumbering]
        [#set hitExceptionVar = "hitException" + nodeNumbering]
    	 ${grammar.pushNodeVariableName(nodeVarName)!}
    	[@createNode treeNodeBehavior nodeVarName /]
         boolean ${hitExceptionVar} = false;
         try {
    [/#if]
    [#if expansion?is_null]
        ${currentProduction.javaCode}
    [#else]
        [@BuildPhase1Code expansion/]
    [/#if]
    [#if production?? && expansion?? && production.returnType]
         throw new Error("Missing return statement in function");
    [/#if]
    [#if buildTreeNode]
       [#var closeCondition = "true"]
       [#if !treeNodeBehavior??]
         [#if grammar.options.smartNodeCreation]
            [#set treeNodeBehavior = {"name" : production.name, "condition" : "1", "gtNode" : true, "void" :false}]
         [#else]
            [#set treeNodeBehavior = {"name" : production.name, "condition" : null, "gtNode" : false, "void" : false}]
         [/#if]
       [/#if]
       [#if treeNodeBehavior.condition?has_content]
          [#set closeCondition = treeNodeBehavior.condition]
          [#if treeNodeBehavior.gtNode]
             [#set closeCondition = "nodeArity() > " + closeCondition]
          [/#if]
       [/#if]
         }
         catch (Exception e${nodeNumbering}) {
            ${hitExceptionVar} = false;
         [#list ["ParseException", "RuntimeException"] + (currentProduction.throwsList.types)! as etype]
            if (e${nodeNumbering} instanceof ${etype}) throw (${etype}) e${nodeNumbering};
         [/#list]
            throw new RuntimeException(e${nodeNumbering});
         }
         finally {
            if (buildTree) {
                if (!${hitExceptionVar}) {
                    closeNodeScope(${nodeVarName}, ${closeCondition});
         [#if grammar.usesjjtreeCloseNodeScope]
                    jjtreeCloseNodeScope(${nodeVarName});
         [/#if]
         [#if grammar.usesCloseNodeScopeHook]
                    closeNodeScopeHook(${nodeVarName});
         [/#if]
                    Token jjtEndToken = getToken(0);
                    ${nodeVarName}.setEndLine(jjtEndToken.endLine);
                    ${nodeVarName}.setEndColumn(jjtEndToken.endColumn);
                } else {
                    clearNodeScope();
                    mark = marks.remove(marks.size()-1);
                }
            }
          ${grammar.popNodeVariableName()!}
         }
    [/#if]
[/#macro]

[#macro createNode treeNodeBehavior nodeVarName]
   [#var nodeName = NODE_PREFIX + currentProduction.name]
   [#if treeNodeBehavior??]
      [#set nodeName = NODE_PREFIX + treeNodeBehavior.nodeName]
   [/#if]
   [#-- var constName = nodeName?substring(NODE_PREFIX?length)?upper_case --]
         ${nodeName} ${nodeVarName} = null;
   if (buildTree) {
   [#if NODE_USES_PARSER]
        ${nodeVarName} = new ${nodeName}(this);
   [#else]
       ${nodeVarName} = new ${nodeName}();
   [/#if]

       Token jjtStartToken = getToken(1);
       ${nodeVarName}.setBeginLine(jjtStartToken.beginLine);
       ${nodeVarName}.setBeginColumn(jjtStartToken.beginColumn);
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
    [#if classname = "Action"]
       ${expansion.javaCode!"{}"}
    [#elseif classname = "ExpansionSequence"]
	   [#list expansion.units as subexp]
	     [#if subexp_index != 0]
	       [@BuildCode subexp/]
	     [/#if]
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
    [#else]
        [@BuildPhase1CodeChoice expansion/]
    [/#if]
[/#macro]

[#macro BuildPhase1CodeRegexp regexp]
       [#if regexp.LHS??]
          ${regexp.LHS} =  
       [/#if]
       consumeToken(${regexp.label});
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

[#macro JavaCodeProduction jprod]
   ${jprod.leadingComments}
   ${jprod.accessMod!} ${jprod.returnType} ${jprod.name}(
   ${jprod.parameterList}) throws ParseException
   [#list (jprod.throwsList.types)! as throw], ${throw}[/#list] {
   [#if grammar.options.debugParser]
    trace_call("${jprod.name}");
    try {
   [/#if]
   [@BuildCode null /]
   [#if grammar.options.debugParser]
    }
    finally {
        trace_return("${jprod.name}");
    }
   [/#if]
 } 
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
   [#var defaultAction="consumeToken(-1);
throw new ParseException();"]
   [#var inPhase1=false]
   [#var indentLevel=0]
   [#var casesHandled=[]]
   [#list choice.choices as nested]
      [#var action]
      [#set action]
         [@BuildCode nested/]
      [/#set]
      [#-- var la=nested.units[0] --]
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
            [#if grammar.options.errorReporting]
                jj_la1[${tokenMaskIndex}] = jj_gen;
               [#set tokenMaskIndex = tokenMaskIndex+1]
            [/#if]
               if (
               [#set indentLevel = indentLevel+1]
         [/#if]
                jj_2${lookahead.nestedExpansion.internalName}(${lookahead.amount})
         [#if lookahead.semanticLookaheadA??]
                && (${lookahead.semanticLookahead})
         [/#if]
                ) { 
                   ${actions[lookahead_index]}
         [#set inPhase1 = false]
       [#elseif lookahead.amount = 1&& !lookahead.semanticLookahead?? &&!lookahead.possibleEmptyExpansionOrJavaCode]
          [#if !inPhase1]
                 [#if lookahead_index != 0]
                 } else {
                 [/#if]
                 [#set casesHandled = ","]
                 switch (nextTokenKind()) { 
              [#set indentLevel = indentLevel+1]
          [/#if]
          [#list lookahead.matchingTokens as token]
               [#if !casesHandled?contains(","+token+",")]
                 case ${token}: 
                 [#set casesHandled = casesHandled+token+","]
               [/#if]
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
               [#if grammar.options.errorReporting]
                 jj_la1[${tokenMaskIndex}] = jj_gen;
                 [#set tokenMaskIndex = tokenMaskIndex+1]
              [/#if]
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
          [#if grammar.options.errorReporting]
                jj_la1[${tokenMaskIndex}] = jj_gen;
                 [#set tokenMaskIndex = tokenMaskIndex+1]
          [/#if]
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
        jj_2${lookahead.nestedExpansion.internalName}(${lookahead.amount})
        [#if lookahead.semanticLookahead??]
          && (${lookahead.semanticLookahead})
        [/#if]
      [/#set]
   [#elseif lookahead.amount = 1&&!lookahead.possibleEmptyExpansionOrJavaCode]
      [@newVar type="int" init="nextTokenKind()"/]
      [#set condition]
      [#list lookahead.matchingTokens as token]
             int${newVarIndex} == ${token} [#if token_has_next]|| [/#if]
      [/#list]
     [/#set]
      [#if grammar.options.errorReporting]
         [#set fallback = "
               jj_la1[${tokenMaskIndex}] = jj_gen;
               "+fallback!]
               [#set tokenMaskIndex = tokenMaskIndex+1]
      [/#if]
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

[#macro buildPhase3Routine expansion count]
   [#if expansion.internalName?starts_with("jj_scan_token")][#return][/#if]
   private boolean jj_3${expansion.internalName}() {
        [#if grammar.options.debugLookahead&&expansion.parentObject.class.name?ends_with("Production")]
           [#if grammar.options.errorReporting]
      if (!rescan) 
           [/#if]
      trace_call("${expansion.parentObject.name} (LOOKING AHEAD...)");
            [#set jj3_expansion = expansion]
       [#else]
            [#set jj3_expansion = null]
        [/#if]
      [@buildPhase3Code expansion, count/]
      [@genReturn false/]
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
  [#if choice.choices?size != 1]
    [@newVar "Token", "jj_scanpos"/]
  [/#if]
  [#list choice.choices as subseq]
	  [#var lookahead=subseq.units[0]]
	  [#if lookahead.semanticLookahead??]
	    jj_lookingAhead = true;
	    jj_semLA = ${lookahead.semanticLookahead};
	    jj_lookingAhead = false;
	  [/#if]
	  if (
	  [#if lookahead.semanticLookahead??]
	     !jj_semLA || 
	  [/#if]
	  [#if subseq_has_next]
	     [@genjj_3Call subseq/]) {
	        jj_scanpos = token${newVarIndex};
	  [#else]
	     [@genjj_3Call subseq/]) [@genReturn true/]
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
  [#var label=regexp.label]
  [#if !label?has_content]
     [#set label = grammar.getTokenName(regexp.ordinal)]
  [/#if]
    if (jj_scan_token(${label})) [@genReturn true/] 
[/#macro]

[#macro Phase3CodeZeroOrOne zoo]
   [@newVar type="Token" init="jj_scanpos"/]
   if ([@genjj_3Call zoo.nestedExpansion/]) 
      jj_scanpos = token${newVarIndex};
[/#macro]

[#macro Phase3CodeZeroOrMore zom]
      while (true) {
         [@newVar type="Token" init="jj_scanpos"/]
         if ([@genjj_3Call zom.nestedExpansion/]) {
             jj_scanpos = token${newVarIndex};
             break;
         }
      }
[/#macro]

[#macro Phase3CodeOneOrMore oom]
   if ([@genjj_3Call oom.nestedExpansion/]) [@genReturn true/]
   while (true) {
       [@newVar type="Token" init="jj_scanpos"/]
       if ([@genjj_3Call oom.nestedExpansion/]) {
           jj_scanpos = token${newVarIndex};
           break;
       }
   }
[/#macro]

[#macro Phase3CodeNonTerminal nt]
   [#var ntprod=grammar.getProductionByLHSName(nt.name)]
   [#if ntprod.class.name?ends_with("ParserProduction")]
     if (true) {
         jj_la = 0; 
         jj_scanpos = jj_lastpos;
         [@genReturn false/]
     }
   [#else]
        if ([@genjj_3Call ntprod.expansion/])
           [@genReturn true/]
   [/#if]
[/#macro]

[#macro Phase3CodeSequence sequence count]
   [#list sequence.units as sub]
      [#if sub_index != 0]
         [@buildPhase3Code sub, count/]
         [#set count = count-parserData.minimumSize(sub)]
         [#if count<=0][#break][/#if]
      [/#if]
   [/#list]
[/#macro]
    
[#macro genjj_3Call expansion]
   [#if expansion.internalName?starts_with("jj_scan_token")]
     ${expansion.internalName}
   [#else]
     jj_3${expansion.internalName}()
   [/#if]
[/#macro]

[#macro genReturn bool]
    [#var retval=bool?string("true", "false")]
    [#if grammar.options.debugLookahead&&!jj3_expansion?is_null]
       [#var tracecode]
       [#set tracecode]
 trace_return("${jj3_expansion.parentObject.name} (LOOKAHEAD ${bool?string("FAILED", "SUCCEEDED")}");
       [/#set]
       [#if grammar.options.errorReporting] 
         [#set tracecode = "if (!rescan) "+tracecode]
       [/#if]
  { ${tracecode} return {bool?string("true", "false")};
              return "return " + retval + ";";
    [#else]
  return ${retval};
    [/#if]
[/#macro]

[#var newVarIndex=0]
[#macro newVar type init=null]
   [#set newVarIndex = newVarIndex+1]
   ${type} ${type?lower_case}${newVarIndex}
   [#if init??]
      = ${init};
   [/#if]
   ;
[/#macro]   
    
