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

import static javacc.JavaCCError.Type.*;
import javacc.parser.Node;
import javacc.parsegen.Expansion;


public class JavaCCError {

    public enum Type {
        PARSE, SEMANTIC, WARNING;
    }

    public final Type type;
    public String message;
    public Grammar grammar;
    public int beginColumn, beginLine;
    public String inputSource = "input";

    public JavaCCError(Grammar grammar, Type type, String message, Object node) {
        this.grammar = grammar;
        this.inputSource = grammar.getFilename();
        this.type = type;
        this.message = message;
        if (node instanceof Expansion) {
            Expansion fnode = (Expansion) node;
            this.beginColumn = fnode.getBeginColumn();
            this.beginLine = fnode.getBeginLine();
            this.grammar = fnode.getGrammar();
        }
        if (node instanceof Node) {
            Node n = (Node) node;
            this.beginColumn = n.getBeginColumn();
            this.beginLine = n.getBeginLine();
            this.grammar = n.getGrammar();
        }
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();
        if (type == WARNING) {
            buf.append("Warning: ");
        } else {
            buf.append("Error: ");
        }
        if (beginLine != 0) {
            buf.append("Line " + beginLine + ", Column " + beginColumn + " in " + inputSource +": ");
        }
        buf.append(message);
        return buf.toString();
    }
}
