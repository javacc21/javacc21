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

[#var ONE_TABLE=true]
[#var title="BNF for "+grammar.parserClassName]
[#escape x as (x)?html]
[#macro page]
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 3.2//EN">
<HTML>
  <HEAD>
  [#if grammar.options.CSS?has_content]
    <LINK REL="stylesheet" type="text/css" href="${grammar.options.CSS}"/>
  [/#if]
    <TITLE>${title}</TITLE>
  </HEAD>
  <BODY>
   <H1 ALIGN=CENTER>${title}</H1>
   [#nested][#t]
  </BODY>
</HTML>
[/#macro]

[#macro nonterminals]
   <H2 ALIGN=CENTER>NON-TERMINALS</H2>
   [#if ONE_TABLE]
      <TABLE>
   [/#if]
      [#nested]
   [#if ONE_TABLE]
      </TABLE>
   [/#if]
[/#macro]

[#macro tokenProductions]
  <H2 ALIGN=CENTER>TOKENS</H2>
  <TABLE>
    [#nested]
  </TABLE>
[/#macro]

[#macro tokenProduction]
  <TR><TD><PRE>[#t]
    [#nested][#t]
  </PRE></TD></TR>[#t]
[/#macro]

[#macro doRegexp re]

[/#macro]

[#macro production prod]
    [#if !ONE_TABLE]
      <TABLE ALIGN=CENTER>
      <CAPTION><STRONG>${prod.name}</STRONG></CAPTION>
    [/#if]
    <TR><TD><PRE>${prod.leadingComments}</PRE></TD></TR>
    <TR>
     <TD ALIGN=RIGHT VALIGN=BASELINE><A NAME="${utils.getID(prod.name)}">${prod.name}</A></TD>
     <TD ALIGN=CENTER VALIGN=BASELINE>::=</TD>
     <TD ALIGN=LEFT VALIGN=BASELINE>
      [#nested]
     </TD>
    </TR>
    [#if !ONE_TABLE]
      </TABLE>
      <HR>
    [/#if]
[/#macro]

[#macro nonTerminal nt]
   <A HREF="#${utils.getID(nt.name)}">${nt.name}</A>
[/#macro]


[#macro javacode prod]
  <I>java code</I>
[/#macro]

[/#escape]
