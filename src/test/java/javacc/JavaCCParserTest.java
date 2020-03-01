/* Copyright (c) 2008-2019 Jonathan Revusky, revusky@javacc.com
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
package javacc;

import java.io.IOException;
import java.io.StringReader;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

import javacc.parser.JavaCCParser;
import javacc.parser.Node;
import javacc.parser.ParseException;
import javacc.parser.Token;
import javacc.parser.tree.Delimiter;
import javacc.parser.tree.JavaCCKeyWord;
import javacc.parser.tree.Options;
import javacc.parser.tree.TokenProduction;

/**
 * JUnit tests for JavaCCParser.
 * 
 * @author Angelo ZERR
 *
 */
public class JavaCCParserTest {

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
		Assert.assertNotNull("root should be not null", root);
		Assert.assertTrue("Node#hasChildNodes", root.hasChildNodes());
		Assert.assertEquals("Node#getChildCount", 8, root.getChildCount());

		Node firstChild = root.getFirstChild();
		Assert.assertNotNull("firstChild should be not null", firstChild);
		Assert.assertEquals("firstChild begin line", 1, firstChild.getBeginLine());
		Assert.assertEquals("firstChild begin column", 1, firstChild.getBeginColumn());
		Assert.assertEquals("firstChild end line", 5, firstChild.getEndLine());
		Assert.assertEquals("firstChild end column", 1, firstChild.getEndColumn());
		Assert.assertTrue("firstChild is an Options", firstChild instanceof Options);

		Node lastChild = root.getLastChild();
		Assert.assertNotNull("lastChild should be not null", lastChild);
		Assert.assertEquals("lastChild begin line", 100, lastChild.getBeginLine());
		Assert.assertEquals("lastChild begin column", 1, lastChild.getBeginColumn());
		Assert.assertEquals("lastChild end line", 100, lastChild.getEndLine());
		Assert.assertEquals("lastChild end column", 1, lastChild.getEndColumn());
		Assert.assertTrue("lastChild is a token", lastChild instanceof Token);
	}

	@Test
	public void nodeFindAPI() throws Exception {
		Node root = parseJSONCC();
		Assert.assertNotNull("root should be not null", root);

		// First node
		Node foundedNode = root.findNodeAt(1, 1);
		Assert.assertNotNull("foundedNode should be not null", foundedNode);
		Assert.assertTrue("foundedNode is a JavaCCKeyWord", foundedNode instanceof JavaCCKeyWord);
		Node options = root.getFirstChild();
		Assert.assertEquals("foundedNode parent is the Options", foundedNode.getParent(), options);

		// One node
		foundedNode = root.findNodeAt(9, 1);
		Assert.assertNotNull("foundedNode should be not null", foundedNode);
		Assert.assertTrue("foundedNode is a TokenProduction", foundedNode instanceof TokenProduction);
		Assert.assertTrue("foundedNode first child is a SKIP", foundedNode.getFirstChild() instanceof JavaCCKeyWord);
		Assert.assertEquals("foundedNode first child is a SKIP", "\"SKIP\"", foundedNode.getFirstChild().getNodeName());

		// last node
		foundedNode = root.findNodeAt(100, 1);
		Assert.assertNotNull("foundedNode should be not null", foundedNode);
		Assert.assertTrue("foundedNode is a Delimiter", foundedNode instanceof Delimiter);

		foundedNode = root.findNodeAt(100, 2);
		Assert.assertNull(foundedNode);

		foundedNode = root.findNodeAt(101, 1);
		Assert.assertNull(foundedNode);
	}

	private static Node parseJSONCC() throws IOException, ParseException {
		Grammar grammar = new Grammar(new JavaCCOptions(new String[] { "JSON.javacc" }));
		JavaCCParser parser = new JavaCCParser(grammar, new StringReader(JSON_JAVACC));
		parser.Root();
		Node root = parser.rootNode();
		return root;
	}

}
