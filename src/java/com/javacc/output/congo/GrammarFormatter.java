/* Copyright (c) 2022 Jonathan Revusky, revusky@congocc.org
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
 *     * Neither the name Jonathan Revusky nor the names of any contributors 
 *       may be used to endorse or promote products derived from this software 
 *       without specific prior written permission.
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

package com.javacc.output.congo;

import com.javacc.Grammar;
import com.javacc.core.BNFProduction;
import com.javacc.parser.*;
import static com.javacc.parser.JavaCCConstants.TokenType.*;
import com.javacc.parser.tree.*;
import com.javacc.output.java.JavaFormatter;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

/**
 * A class to format/lint/cleanup a JavaCC grammar
 * Basically, convert it to the conventions for CongoCC
 * TODO
 */

public class GrammarFormatter extends Node.Visitor {

    static {
        JavaCCLexer.keepWhitespace(true);
    }

    {this.visitUnparsedTokens = true;}

    private StringBuilder buffer = new StringBuilder();

    protected void visit(Options options) {
        Token firstToken = options.firstDescendantOfType(Token.class);
        boolean legacyOptionBlock = firstToken.getImage().equalsIgnoreCase("options");
        if (legacyOptionBlock) {
            for (Token t : firstToken.precedingUnparsedTokens()) {
                buffer.append(t);
            }
        }
        for (Setting setting : options.childrenOfType(Setting.class)) {
            visit(setting);
        }
        if (legacyOptionBlock) {
            Token lastToken = (Token) options.getChild(options.getChildCount() -1);
            for (Token t : lastToken.precedingUnparsedTokens()) {
                buffer.append(t);
            }
        }
    }

/*    protected void visit(TokenProduction tp) {
        for (Node child : tp.children()) {
            buffer.append("KILROY: " + child.getClass().getSimpleName()+"\n");
        }
        recurse(tp);
    }

    protected void visit(RegexpSpec respec) {
        buffer.append("\nKILROY1\n");
        for (Node child : respec.children()) {
            buffer.append(child.getClass().getSimpleName());
        }
        buffer.append("\nKILROY2\n");
        recurse(respec);
    }*/

    protected void visit(BNFProduction prod) {
    }

    protected void visit(Token tok) {
        if (tok.getParent() instanceof TokenProduction) {
            if (tok.getType() == LBRACE) return;
            if (tok.getType() == RBRACE) {
                buffer.append(";");
                return;
            }
        }
        buffer.append(tok.getImage());
    }

    static public void main(String[] args) throws IOException {
        //if (args.length == 0) usage();
        String filename = "/Users/revusky/projects/javacc/examples/json/JSON.javacc";
        if (args.length > 0) filename = args[0];
        Path path = new File(filename).toPath();
        if (!Files.exists(path)) {
            System.err.println("File " + path + " does not exist!");
            System.exit(-1);
        }
        Grammar grammar = new Grammar(path.getParent(), "java", 8, false, new HashMap<>());
        Node root = grammar.parse(path, false);
        GrammarFormatter formatter = new GrammarFormatter();
        formatter.visit((BaseNode) root);
        System.out.println("===========");
        System.out.println(formatter.buffer);
    }

    static void usage() {
        System.out.println("Usage: java com.javacc.output.congo.GrammarFormatter <filename>");
        System.exit(0);
    }
}