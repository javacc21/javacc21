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
/* Generated by: ${generated_by}. ${filename} */
[#if grammar.parserPackage?has_content]
package ${grammar.parserPackage};
[/#if]
[#if grammar.options.freemarkerNodes]
import freemarker.template.*;
[/#if]

public interface Node
[#if grammar.options.freemarkerNodes]
   extends TemplateNodeModel, TemplateScalarModel
[/#if] {

    /** Life-cycle hook method called after the node has been made the current
	 *  node
	 */
     void open();

  	/**
  	 * Life-cycle hook method called after all the child nodes have been
     * added.
     */
     void close();

     void setParent(Node n);

     Node getParent();

     // The following 9 methods will typically just
     // delegate straightforwardly to a List object that
     // holds the child nodes

     void addChild(Node n);

     void addChild(int i, Node n);

     Node getChild(int i);

     void setChild(int i, Node n);

     Node removeChild(int i);

     boolean removeChild(Node n);

     int indexOf(Node child);

     void clearChildren();

     int getChildCount();

     // The following 3 methods will typically delegate
     // straightforwardly to a Map<String, Object> object-s get/set/containsKey/keySet methods.

     Object getAttribute(String name);

     void setAttribute(String name, Object value);

     boolean hasAttribute(String name);

     java.util.Set<String> getAttributeNames();

     // The following ten methods are for location info.

     /**
      * @return A string that says where the input came from. Typically a file name, though
      *         it could be a URL or something else, of course.
      */
     String getInputSource();

     void setInputSource(String inputSource);

     int getBeginLine();

     int getEndLine();

     int getBeginColumn();

     int getEndColumn();

     void setBeginLine(int beginLine);

     void setEndLine(int endLine);

     void setBeginColumn(int beginColumn);

     void setEndColumn(int endColumn);

[#if grammar.options.visitor]
   [#var RETURN_TYPE = grammar.options.visitorReturnType]
   [#if !RETURN_TYPE?has_content][#set RETURN_TYPE = "void"][/#if]
   [#var DATA_TYPE = grammar.options.visitorDataType]
   [#if !DATA_TYPE?has_content][#set DATA_TYPE="Object"][/#if]
   [#var THROWS = ""]
   [#if grammar.options.visitorException?has_content][#set THROWS = "throws " + grammar.options.visitorException][/#if]
	 ${RETURN_TYPE} jjtAccept(${grammar.parserClassName}Visitor visitor, ${DATA_TYPE} data) ${THROWS};
[/#if]

}
