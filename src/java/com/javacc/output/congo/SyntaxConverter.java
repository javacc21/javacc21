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
import com.javacc.core.*;
import com.javacc.parser.*;
import static com.javacc.parser.JavaCCConstants.TokenType.*;
import com.javacc.parser.tree.*;
import com.javacc.output.java.JavaFormatter;
import java.io.IOException;
import java.io.File;
import java.nio.file.*;
import java.util.*;

/**
 * A class to convert a legacy JavaCC or JavaCC21 grammar
 * to the conventions for Congo.
 * Currently, it converts the legacy
 * PARSER_CODE_DECLS and TOKEN_MGR_DECLS to a JavaCC21/Congo 
 * INJECT. It converts the old JAVACODE thing to an INJECT as well.
 * It converts the older LOOKAHEAD construct to SCAN.
 * It gets rid of those superfluous {} that infest legacy JavaCC
 * grammars and are now unnecessary. It gets rid of superfluous
 * void return types that needed to be tacked on in legacy JavaCC grammars.
 * It converts Foo() to just Foo. It converts
 * => Expansion to Expansion =>||
 * Currently (it's a TODO) it does not convert
 * SCAN Foo Bar => Foo Bar Baz
 * to Foo Bar =>|| Baz
 * nor does it (also a TODO) replace things like:
 * Foo Foo #Foo with simply #Foo#.
 */

public class SyntaxConverter extends Node.Visitor {

    static {
        JavaCCLexer.keepWhitespace(true);
    }

    {this.visitUnparsedTokens = true;}

    private Grammar grammar;
    private StringBuilder buffer = new StringBuilder();
    private boolean passWhitespaceThrough=true;

    private Map<String, JavacodeProduction> javacodeProductions = new HashMap<>();
    private String packageFromDecl, parserClassFromDecl;

    public SyntaxConverter(Grammar grammar) {
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
        if (block.isAppliesInLookahead()) buffer.append("#");
    }

    
    void visit(RegularExpression regexp) {
        // A bit kludgy. REVISIT.
        if (regexp.getLHS() != null) {
            recurse(regexp.getLHS());
            buffer.append("=");
        }
        recurse(regexp);
    }

    void visit(NonTerminal nt) {
        String name = nt.getName();
        boolean inJavaCode = javacodeProductions.containsKey(name);
        if (inJavaCode) buffer.append("{");
        recurse(nt);
        if (inJavaCode) buffer.append(";}");
    }

    void visit(FormalParameters params) {
        if (params.getChildCount() > 2 
            || params.getParent() instanceof MethodDeclaration 
            || params.getParent() instanceof ConstructorDeclaration)
        {
               recurse(params);
        }
    }

    void visit(InvocationArguments args) {
        if (args.getChildCount() > 2 || !(args.getParent() instanceof NonTerminal)) {
            recurse(args);
        }
    }

    void visit(Token token) {
        if (token.getImage().equals("#") && buffer.charAt(buffer.length()-1) =='#') {
            // This is a kludge really.
            return;
        }
        buffer.append(token.getImage());
    }

    void visit(StringLiteral sl) {
        buffer.append(escapeNonAscii(sl.getImage()));
    }

    void visit(CharacterLiteral cl) {
        buffer.append(escapeNonAscii(cl.getImage()));
    }

    void visit(ReturnType rt) {
        if (!(rt.getParent() instanceof BNFProduction)) {
           recurse(rt);
           return;
        }
        String rtString = rt.toString();
        String productionName = ((BNFProduction) rt.getParent()).getName();
        if (productionName.equals(rtString)) {
            buffer.append("\n#");
        }
        else if (!rtString.equals("void")) {
           recurse(rt);
        }
    }

    void visit(Name name) {
        if (name.getParent() instanceof TreeBuildingAnnotation) {
            if (name.firstChildOfType(Identifier.class).getPrevious().getType() == HASH) {
                BNFProduction bnf = name.firstAncestorOfType(BNFProduction.class);
                if (bnf != null && bnf.getName().equals(name.toString())) {
                    if (buffer.substring(buffer.length()-2).equals(" #")) {
                        buffer.setLength(buffer.length()-1);
                        buffer.setCharAt(buffer.length()-1, '#');
                    }
                    return;
                }
            }
        }
        recurse(name);
    }

    void visit(ExpansionSequence seq) {
        Expansion lastExpansion = null;
        for (Expansion exp : seq.getUnits()) {
            // REVISIT
            if (!(exp instanceof CodeBlock)) lastExpansion = exp;
        }
        for (Node child : seq.children()) {
            visit(child);
            if (child instanceof Expansion) {
                // It has to be written in this annoying way because of the 
                // the tree that the JavaCCParser builds. REVISIT
                if (child == lastExpansion) {
                    if (seq.getHasExplicitLookahead()) {
                        if (seq.getLookahead().getChildCount() == 1 && !seq.getHasExplicitScanLimit()) {
                            buffer.append(" =>|| ");
                        }
                    }
                }
                TreeBuildingAnnotation tba = ((Expansion) child).getTreeNodeBehavior();
                if (tba != null) {
                    visit(tba);
                }
            }
        }
    }

    void visit(Lookahead la) {
        if (la.getChildCount() > 1) recurse(la);
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
            if (Character.isWhitespace(buffer.charAt(buffer.length()-1))) buffer.setLength(buffer.length()-1);
            return;
        }
        else if (delim.getType() == RBRACE && (parent instanceof BNFProduction || parent instanceof TokenProduction)) {
            if (delim == parent.getChild(parent.getChildCount()-1)) {
                buffer.append(";\n");
            }
        }
        else buffer.append(delim.getImage());
    }

    void visit(Identifier id) {
        if (id.getParent() instanceof BNFProduction && buffer.length() > 2) {
            char lastChar = buffer.charAt(buffer.length() -1);
            char secondLast = buffer.charAt(buffer.length() -2);
            if (lastChar == ' ' && (secondLast == '#' || secondLast == '\n')) {
                buffer.setLength(buffer.length()-1);
            }
        }
        buffer.append(id.getImage());
    }

    void visit(Whitespace ws) {
        if (passWhitespaceThrough) buffer.append(ws.getImage());
    }

    void visit(SingleLineComment slc) {
        if (slc.getPrevious() != null 
            && slc.getPrevious().getType() == SEMICOLON 
            && slc.getPrevious().getBeginLine() == slc.getBeginLine()
            && buffer.charAt(buffer.length()-1) == '\n') 
        {
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
        SyntaxConverter converter = new SyntaxConverter(grammar);
        converter.buildData();
        converter.visit((BaseNode) root);
        System.out.println(converter.buffer);
    }

    static void usage() {
        System.out.println("Usage: java -jar javacc.com convert <filename>");
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