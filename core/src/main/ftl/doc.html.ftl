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

[#-- In principle, you could replace html32.ftl
     with another library to customize the HTML output --]
[#import "html32.ftl" as html]
[#escape x as (x)?html]
[@html.page]

[@html.nonterminals]
 [#list grammar.BNFProductions as production]
  [#var classname=production.class.name?split(".")?last]
  [@html.production production]
     [#if classname = "ParserProduction"]
       [@html.javacode production/]
     [#else]
       [@expansion production.expansion/]
     [/#if]
  [/@]
 [/#list]
[/@]
<HR>
[@html.tokenProductions]
[#list grammar.allTokenProductions as tp]
 [#if tp.explicit]
   [@tokenProduction tp/]
 [/#if]
[/#list]
[/@]

[/@]

[#macro expansion exp]
   [#var classname=exp.class.name?split(".")?last]
     [#if classname = "Action" || classname?ends_with("Lookahead")]
        [#--Do nothing--]
     [#elseif classname = "ExpansionChoice"]
       [#list exp.choices as choice]
          [@expansion choice/][#t]
             [#if choice_has_next]
               |[#t]
             [/#if]
       [/#list]
     [#elseif classname = "NonTerminal"]
       [@html.nonTerminal exp/]
     [#elseif classname = "ExpansionSequence"]
        [#list exp.units as unit]
           [#var needsParen=unit.class.name?ends_with("Sequence") || unit.class.name?ends_with("Choice")]
           [#if needsParen]([#t][/#if]
             [@expansion unit/] [#t]
           [#if needsParen])[#t][/#if] 
        [/#list]
     [#elseif classname = "TryBlock"]
       [#if exp.nestedExpansion.class.name?ends_with("Choice")]
        ([@expansion exp.nestedExpansion/])
       [#else]
        [@expansion exp.nestedExpansion/]
       [/#if]
     [#elseif classname = "OneOrMore"]
       ([@expansion exp.nestedExpansion/])+
     [#elseif classname = "ZeroOrMore"]
       ([@expansion exp.nestedExpansion/])*
     [#elseif classname = "ZeroOrOne"]
       ([@expansion exp.nestedExpansion/])?
     [#else]
       [#-- must be a regexp --]
       ${exp.emit}
     [/#if]
[/#macro]

[#macro tokenProduction tp]
 [@html.tokenProduction]
  &lt;[#t]
  [#list tp.lexStates as lexState]
     ${lexState}[#if lexState_has_next], [/#if][#t]
  [/#list]
  &gt;[#t]
  [#lt] ${tp.kind}[#if tp.ignoreCase][IGNORE_CASE][/#if] : { 
  [#list tp.regexpSpecs as respec]
    [#var re=respec.regexp]
    [#if !re.private]
       ${re.emit}[#if re.nextState??] : ${re.nextState}[/#if][#lt]
       [#if respec_has_next]|[/#if][#t]
    [/#if]
  [/#list]
}

 [/@]
[/#macro]
[/#escape]
