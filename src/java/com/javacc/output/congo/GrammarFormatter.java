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
import com.javacc.core.NonTerminal;
import com.javacc.parser.*;
import static com.javacc.parser.JavaCCConstants.TokenType.*;
import com.javacc.parser.tree.*;
import com.javacc.output.java.JavaFormatter;
import java.io.IOException;
import java.io.File;
import java.nio.file.*;
import java.util.*;

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

    private Grammar grammar;
    private StringBuilder buffer = new StringBuilder();
    private boolean passWhitespaceThrough=true, inJavaCodeNT;

    private Map<String, JavacodeProduction> javacodeProductions = new HashMap<>();
    private String packageFromDecl, parserClassFromDecl;

    public GrammarFormatter(Grammar grammar) {
        this.grammar = grammar;
    }


    void visit(Options options) {
        Token firstToken = options.firstDescendantOfType(Token.class);
        boolean legacyOptionBlock = firstToken.getImage().equalsIgnoreCase("options");
        if (legacyOptionBlock) {
            for (Token t : firstToken.precedingUnparsedTokens()) {
                buffer.append(t);
            }
        } 
        else if (firstToken.previousCachedToken().getType() == MULTI_LINE_COMMENT) {
            buffer.append("\n");
        }
        for (Setting setting : options.childrenOfType(Setting.class)) {
            String key = setting.firstChildOfType(Token.class).getImage();
            if (!setting.getGrammar().isASetting(key)) continue;
            visit(setting);
        }
        if (parserClassFromDecl != null) {
            buffer.append("\nPARSER_CLASS=");
            buffer.append(parserClassFromDecl);
            buffer.append(";");
        }
        if (packageFromDecl != null) {
            buffer.append("\nPARSER_PACKAGE=");
            buffer.append(packageFromDecl);
            buffer.append(";");
        }
        if (legacyOptionBlock) {
            Token lastToken = (Token) options.getChild(options.getChildCount() -1);
            for (Token t : lastToken.precedingUnparsedTokens()) {
                buffer.append(t);
            }
        }
        buffer.append("\n");
    }

    void visit(CodeBlock block) {
        if (block.getChildCount() == 2 && block.getParent() instanceof BNFProduction) {
            return;
        }
        recurse(block);
    }

    void visit(NonTerminal nt) {
        String name = nt.getName();
        inJavaCodeNT = javacodeProductions.containsKey(name);
        if (inJavaCodeNT) buffer.append("{");
        recurse(nt);
        if (inJavaCodeNT) buffer.append(";\n}");
        inJavaCodeNT = false;
    }

    void visit(FormalParameters params) {
        if (params.getChildCount() == 2) {
            buffer.append(" ");
            return;
        }
        recurse(params);
    }

    void visit(InvocationArguments args) {
        if (args.getChildCount() == 2 && !inJavaCodeNT) return;
        recurse(args);
    }

    void visit(Token token) {
        buffer.append(token.getImage());
    }

    void visit(StringLiteral sl) {
        buffer.append(escapeNonAscii(sl.getImage()));
    }

    void visit(CharacterLiteral cl) {
        buffer.append(escapeNonAscii(cl.getImage()));
    }

    void visit(KeyWord kw) {
        if (kw.getType()==VOID 
            && kw.getParent().getParent() instanceof BNFProduction 
            && kw.getPrevious().getType() != HASH) 
        {
            return;
        }
        buffer.append(kw.getImage());
    }

    void visit(LegacyLookahead la) {
        Token firstToken = la.firstChildOfType(_LOOKAHEAD);
        for (Token t : firstToken.precedingUnparsedTokens()) visit(t);
        buffer.append("SCAN ");
        if (la.getHasExplicitNumericalAmount()) {
            buffer.append("" + la.getAmount());
        }
        if (la.hasSemanticLookahead()) {
            buffer.append("{");
            buffer.append(la.getSemanticLookahead());
            buffer.append("}");
            if (la.isSemanticLookaheadNested()) {
                buffer.append("#");
            }
            buffer.append(" ");
        }
        if (la.getNestedExpansion() != null) {
            recurse(la.getNestedExpansion());
        }
        buffer.append(" =>");
    }

    void visit(JavaCCKeyWord kw) {
        if (kw.getImage().equals("SPECIAL_TOKEN")) {
            buffer.append("UNPARSED");
            return;
        }
        buffer.append(kw.getImage());
    }

    void visit(Delimiter delim) {
        Node parent = delim.getParent();
        if (delim.getType() == LBRACE && (parent instanceof BNFProduction || parent instanceof TokenProduction)) {
            return;
        }
        else if (delim.getType() == RBRACE && (parent instanceof BNFProduction || parent instanceof TokenProduction)) {
            if (delim == parent.getChild(parent.getChildCount()-1)) {
                buffer.append(";\n");
            }
        }
        else buffer.append(delim.getImage());
    }

    void visit(Whitespace ws) {
        if (passWhitespaceThrough) buffer.append(ws.getImage());
    }

    void visit(SingleLineComment slc) {
        if (slc.getPrevious().getType() == SEMICOLON && buffer.charAt(buffer.length()-1) == '\n') {
            buffer.setLength(buffer.length()-1);
            buffer.append(" ");
        }
        buffer.append(slc.getImage());
    }

    void visit(JavacodeProduction jcp) {
        MethodDeclaration md = jcp.firstChildOfType(MethodDeclaration.class);
        buffer.append("\nINJECT PARSER_CLASS :\n{\n");
        buffer.append(new JavaFormatter().format(md,1));
        buffer.append("\n}");
    }

    protected void visit(ParserCodeDecls decls) {
        buffer.append("INJECT :\n{\n");
        CompilationUnit jcu = decls.firstChildOfType(CompilationUnit.class);
        buffer.append(new JavaFormatter().format(jcu,1));
        buffer.append("\n}\n\n");
    }

    protected void visit(TokenManagerDecls decls) {
        buffer.append("INJECT LEXER_CLASS :\n");
        ClassOrInterfaceBody coib = decls.firstChildOfType(ClassOrInterfaceBody.class);
        visit(coib);
    }

    void visit(ClassOrInterfaceBody coib) {
        buffer.append(new JavaFormatter().format(coib));
    }

    static public void main(String[] args) throws IOException {
        if (args.length == 0) usage();
        String filename = args[0];
        if (args[0].equals("convert")) {
            if (args.length == 1) usage();
            filename = args[1];
        }
        Path path = new File(filename).toPath();
        if (!Files.exists(path)) {
            System.err.println("File " + path + " does not exist!");
            System.exit(-1);
        }
        Grammar grammar = new Grammar(path.getParent(), "java", 8, false, new HashMap<>());
        Node root = grammar.parse(path, false);
        GrammarFormatter formatter = new GrammarFormatter(grammar);
        formatter.buildData();
        formatter.visit((BaseNode) root);
        System.out.println(formatter.buffer);
    }

    static void usage() {
        System.out.println("Usage: java com.javacc.output.congo.GrammarFormatter <filename>");
        System.exit(0);
    }

    private void buildData() {
        for (JavacodeProduction jp : grammar.descendantsOfType(JavacodeProduction.class)) {
            MethodDeclaration md = jp.firstChildOfType(MethodDeclaration.class);
            String name = md.getName();
            javacodeProductions.put(name, jp);
        }
        ParserCodeDecls pdecls = grammar.firstDescendantOfType(ParserCodeDecls.class);
        if (pdecls != null) {
            PackageDeclaration packageDeclaration = pdecls.firstDescendantOfType(PackageDeclaration.class);
            if (packageDeclaration != null) {
                packageFromDecl = packageDeclaration.getPackageName().toString();
            }
            parserClassFromDecl = pdecls.firstChildOfType(Identifier.class).toString();
        }
    }

    static String escapeNonAscii(String s) {
        int[] codePoints = s.codePoints().toArray();
        StringBuilder buf = new StringBuilder();
        for (int ch : codePoints) {
            if (ch < 128 && !Character.isISOControl(ch)) {
                buf.append((char) ch);
            }
            else if (ch <=0xFFFF) {
                buf.append(toEscapedUnicode(ch));
            }
            else {
                int high = Character.highSurrogate(ch);
                int low = Character.lowSurrogate(ch);
                buf.append(toEscapedUnicode(high));
                buf.append(toEscapedUnicode(low));
            }
        }
        return buf.toString();
    }

    static String toEscapedUnicode(int ch) {
        StringBuilder buf = new StringBuilder();
        buf.append("\\u");
        if (ch <= 0XF) buf.append("000");
        else if (ch <= 0xFF) buf.append("00");
        else if (ch <= 0xFFF) buf.append("0");
        buf.append(Integer.toString(ch, 16));
        return buf.toString();
    }
}