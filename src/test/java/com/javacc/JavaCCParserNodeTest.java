/* Copyright (c) 2008-2020 Jonathan Revusky, revusky@javacc.com
 * Copyright (c) 2006, Sun Microsystems Inc.
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
 *       nor the names of any contributors may be used to endorse or promote
 *       products derived from this software without specific prior written
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
package com.javacc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.javacc.parser.JavaCCParser;
import com.javacc.parser.Node;
import com.javacc.parser.ParseException;
import com.javacc.parser.Token;
import com.javacc.parser.tree.Delimiter;
import com.javacc.parser.tree.JavaCCKeyWord;
import com.javacc.parser.tree.Options;
import com.javacc.parser.tree.TokenProduction;

/**
 * Tests for {@link JavaCCParser} node API.
 * 
 * @author Angelo ZERR
 *
 */
public class JavaCCParserNodeTest {

	private static final String JSON_JAVACC = "options {\r\n" + //
			"  PARSER_PACKAGE=\"json\";\r\n" + //
			"  NODE_PACKAGE=\"json.ast\";\r\n" + //
			"  DEFAULT_LEXICAL_STATE=\"JSON\";\r\n" + //
			"}\r\n" + //
			"\r\n" + //
			"SKIP :\r\n" + //
			"{\r\n" + //
			"  <WHITESPACE : (\" \"| \"\\t\"| \"\\n\"| \"\\r\")+> \r\n" + //
			"}\r\n" + //
			"\r\n" + //
			"// Delimiters\r\n" + //
			"TOKEN #Delimiter :\r\n" + //
			"{\r\n" + //
			"    <COLON : \":\">\r\n" + //
			"    |\r\n" + //
			"    <COMMA : \",\">\r\n" + //
			"    |\r\n" + //
			"    <OPEN_BRACKET : \"[\">\r\n" + //
			"    |\r\n" + //
			"    <CLOSE_BRACKET : \"]\">\r\n" + //
			"    |\r\n" + //
			"    <OPEN_BRACE : \"{\" >\r\n" + //
			"    |\r\n" + //
			"    <CLOSE_BRACE : \"}\">\r\n" + //
			"}\r\n" + //
			"\r\n" + //
			"// Literals\r\n" + //
			"TOKEN #Literal :\r\n" + //
			"{\r\n" + //
			"    <TRUE: \"true\"> #BooleanLiteral\r\n" + //
			"    |\r\n" + //
			"    <FALSE: \"false\"> #BooleanLiteral\r\n" + //
			"    |\r\n" + //
			"    <NULL: \"null\"> #NullLiteral\r\n" + //
			"    |\r\n" + //
			"    <#ESCAPE1 : \"\\\\\" ([\"\\\\\", \"\\\"\", \"/\",\"b\",\"f\",\"n\",\"r\",\"t\"])>\r\n" + //
			"    |\r\n" + //
			"    <#ESCAPE2 : \"\\\\u\" ([\"0\"-\"9\", \"a\"-\"f\", \"A\"-\"F\"]) {4}>\r\n" + //
			"    |\r\n" + //
			"    <#REGULAR_CHAR : ~[\"\\u0000\"-\"\\u001F\",\"\\\"\",\"\\\\\"]>\r\n" + //
			"    |\r\n" + //
			"    <STRING_LITERAL : \"\\\"\" (<REGULAR_CHAR>|<ESCAPE2>|<ESCAPE1>)* \"\\\"\"> #StringLiteral\r\n" + //
			"    |\r\n" + //
			"    <#ZERO : \"0\">\r\n" + //
			"    |\r\n" + //
			"    <#NON_ZERO : ([\"1\"-\"9\"])([\"0\"-\"9\"])*>\r\n" + //
			"    |\r\n" + //
			"    <#FRACTION : \".\" ([\"0\"-\"9\"])+>\r\n" + //
			"    |\r\n" + //
			"    <#EXPONENT : [\"E\",\"e\"][\"+\",\"-\"]([\"1\"-\"9\"])+>\r\n" + //
			"    |\r\n" + //
			"    <NUMBER : (\"-\")?(<ZERO>|<NON_ZERO>)(<FRACTION>)?(<EXPONENT>)?> #NumberLiteral\r\n" + //
			"}\r\n" + //
			"\r\n" + //
			"void Array() : {}\r\n" + //
			"{\r\n" + //
			"\r\n" + //
			"    <OPEN_BRACKET>\r\n" + //
			"    [\r\n" + //
			"      Value() \r\n" + //
			"      (\r\n" + //
			"        <COMMA>\r\n" + //
			"        Value()\r\n" + //
			"      )*\r\n" + //
			"    ]\r\n" + //
			"    <CLOSE_BRACKET>\r\n" + //
			"}\r\n" + //
			"\r\n" + //
			"void Value() : {}\r\n" + //
			"{\r\n" + //
			"   (\r\n" + //
			"    <TRUE>\r\n" + //
			"    |\r\n" + //
			"    <FALSE>\r\n" + //
			"    |\r\n" + //
			"    <NULL>\r\n" + //
			"    |\r\n" + //
			"    <STRING_LITERAL>\r\n" + //
			"    |\r\n" + //
			"    <NUMBER>\r\n" + //
			"    |\r\n" + //
			"    Array()\r\n" + //
			"    |\r\n" + //
			"    JSONObject()\r\n" + //
			"   )\r\n" + //
			"}\r\n" + //
			"\r\n" + //
			"void JSONObject() : {}\r\n" + //
			"{\r\n" + //
			"    <OPEN_BRACE>\r\n" + //
			"    [\r\n" + //
			"       <STRING_LITERAL> <COLON> Value()\r\n" + //
			"       (\r\n" + //
			"         <COMMA>\r\n" + //
			"        <STRING_LITERAL><COLON>Value()\r\n" + //
			"       )*\r\n" + //
			"    ]\r\n" + //
			"    <CLOSE_BRACE>\r\n" + //
			"}";

	@Test
	public void nodeHierarchyAPI() throws Exception {
		Node root = parseJSONCC();
		assertNotNull(root, "root should be not null");
		assertTrue(root.hasChildNodes(), "Node#hasChildNodes");
		assertEquals(8, root.getChildCount(), "Node#getChildCount");

		Node firstChild = root.getFirstChild();
		assertNotNull(firstChild, "firstChild should be not null");
		assertEquals(1, firstChild.getBeginLine(), "firstChild begin line");
		assertEquals(1, firstChild.getBeginColumn(), "firstChild begin column");
		assertEquals(5, firstChild.getEndLine(), "firstChild end line");
		assertEquals(1, firstChild.getEndColumn(), "firstChild end column");
		assertTrue(firstChild instanceof Options, "firstChild is an Options");

		Node lastChild = root.getLastChild();
		assertNotNull(lastChild, "lastChild should be not null");
		assertEquals(100, lastChild.getBeginLine(), "lastChild begin line");
		assertEquals(2, lastChild.getBeginColumn(), "lastChild begin column");
		assertEquals(100, lastChild.getEndLine(), "lastChild end line");
		assertEquals(1, lastChild.getEndColumn(), "lastChild end column");
		assertTrue(lastChild instanceof Token, "lastChild is a token");
	}

	@Test
	public void nodeFindAPI() throws Exception {
		Node root = parseJSONCC();
		assertNotNull(root, "root should be not null");

		// First node
		Node foundedNode = root.findNodeAt(1, 1);
		assertNotNull(foundedNode, "foundedNode should be not null");
		assertTrue(foundedNode instanceof JavaCCKeyWord, "foundedNode is a JavaCCKeyWord");
		Node options = root.getFirstChild();
		assertEquals(foundedNode.getParent(), options, "foundedNode parent is the Options");

		// One node
		foundedNode = root.findNodeAt(9, 1);
		assertNotNull(foundedNode, "foundedNode should be not null");
		assertTrue(foundedNode instanceof TokenProduction, "foundedNode is a TokenProduction");
		assertTrue(foundedNode.getFirstChild() instanceof JavaCCKeyWord, "foundedNode first child is a SKIP");
		assertEquals("_SKIP", foundedNode.getFirstChild().getNodeName(), "foundedNode first child is a SKIP");

		// last node
		foundedNode = root.findNodeAt(100, 1);
		assertNotNull(foundedNode, "foundedNode should be not null");
		assertTrue(foundedNode instanceof Delimiter, "foundedNode is a Delimiter");

		foundedNode = root.findNodeAt(100, 2);
		assertNull(foundedNode);

		foundedNode = root.findNodeAt(101, 1);
		assertNull(foundedNode);
	}

	private static Node parseJSONCC() throws IOException, ParseException {
		return JavaCCAssert.parseGrammar("JSON.javacc", JSON_JAVACC);
	}

}
