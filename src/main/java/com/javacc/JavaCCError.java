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

package com.javacc;

import static com.javacc.JavaCCError.Type.WARNING;

import com.javacc.parser.Node;

public class JavaCCError {

	public enum Type {
		PARSE, SEMANTIC, WARNING;
	}

	private final Grammar grammar;
	private final Type type;
	private final String message;
	private final Node node;

	public JavaCCError(Grammar grammar, Type type, String message, Node node) {
		this.grammar = grammar;
		this.type = type;
		this.message = message;
		this.node = node;
	}

	public String toString() {
		StringBuilder buf = new StringBuilder();
		if (type == WARNING) {
			buf.append("Warning: ");
		} else {
			buf.append("Error: ");
		}
		if (node != null) {
			int beginLine = node.getBeginLine();
			int beginColumn = node.getBeginColumn();
			String inputSource = grammar.getInputSource();
			buf.append("Line " + beginLine + ", Column " + beginColumn + " in " + inputSource + ": ");
		}
		buf.append(message);
		return buf.toString();
	}

	public Type getType() {
		return type;
	}

	public String getMessage() {
		return message;
	}

	public Node getNode() {
		return node;
	}
}
