/* Copyright (c) 2008-2022 Jonathan Revusky, revusky@javacc.com
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

package com.javacc.output.java;

import com.javacc.parser.*;
import com.javacc.parser.tree.*;
import com.javacc.parser.JavaCCConstants.TokenType;
import static com.javacc.parser.JavaCCConstants.TokenType.*;

import java.util.EnumSet;

/**
 * A somewhat more sophisticated class for pretty-printing java source code.
 * Still has some rough edges though!
 * @author revusky
 */
public class JavaFormatter2 extends Node.Visitor {
     
    {this.visitUnparsedTokens = true;}

    private StringBuilder buf;
    private String indent = "    ";
    private String currentIndent = "";
    private String eol = "\n";
    private EnumSet<TokenType> alwaysPrependSpace = EnumSet.of(ASSIGN, COLON, LBRACE, HOOK, THROWS);
    private EnumSet<TokenType> alwaysAppendSpace = EnumSet.of(ASSIGN, COLON, COMMA, DO, FOR, IF, WHILE, THROWS, EXTENDS, HOOK);

    public String format(BaseNode code) {
        buf = new StringBuilder();
        visit(code);
        return buf.toString();
    }

    private void outputToken(Token tok) {
        if (buf.length() >0) {
            int prevChar = buf.codePointBefore(buf.length());
            int nextChar = tok.getImage().codePointAt(0);
            if ((Character.isJavaIdentifierPart(prevChar) || prevChar == ';') && Character.isJavaIdentifierPart(nextChar)) {
                buf.append(' ');
            }
            if (alwaysPrependSpace.contains(tok.getType())) buf.append(' ');
        }
        buf.append(tok);
        if (alwaysAppendSpace.contains(tok.getType())) buf.append(' ');
    }

    public void visit(Token tok) {
        if (tok.getType() == EOF) buf.append("\n");
        else outputToken(tok);
    }

    public void visit(Operator op) {
        switch (op.getType()) {
            case LT:
                if (op.getParent() instanceof RelationalExpression) {
                    addSpaceIfNecessary();
                    buf.append("< ");
                } else {
                    buf.append("<");
                }
                break;
            case GT:
                if (op.getParent() instanceof RelationalExpression) {
                    addSpaceIfNecessary();
                    buf.append("> ");
                } else {
                    buf.append(">");
                    if (op.nextCachedToken().getType() != GT) buf.append(' ');
                }
                break;
            default : outputToken(op);
        }
    }

    public void visit(Delimiter delimiter) {
        switch (delimiter.getType()) {
            case LBRACE : 
                outputToken(delimiter);
                if (!(delimiter.getParent() instanceof ArrayInitializer)) {
                    currentIndent += indent;
                    newLine();
                }
                break;
            case RBRACE :
                if (!(delimiter.getParent() instanceof ArrayInitializer)) {
                    newLine();
                    dedent();
                } 
                buf.append("}");
                newLine();
                break;
            case HOOK :
                if (!(delimiter.getParent() instanceof TernaryExpression)) {
                    buf.append(delimiter);
                } else {
                    outputToken(delimiter);
                }
                break;
            default : outputToken(delimiter);
        }
    }

    public void visit(MultiLineComment comment) {
        startNewLineIfNecessary();
        buf.append(indentText(comment.getImage()));
        newLine();
    }

    public void visit(SingleLineComment comment) {
        if (startsNewLine(comment)) {
            newLine();
        }
        buf.append(comment.getImage());
        buf.append(currentIndent);
    }

    public void visit(TypeDeclaration td) {
        newLine(true);
        recurse(td);
        newLine(true);
    }

    public void visit(Statement stmt) {
        if (stmt.getParent() instanceof IfStatement) {
            addSpaceIfNecessary();
        } else {
            newLine();
        }
        recurse(stmt);
        newLine();
    }

    // Add a space if the last output char was not whitespace
    private void addSpaceIfNecessary() {
        if (buf.length()==0) return;
        int lastChar = buf.codePointBefore(buf.length());
        if (!Character.isWhitespace(lastChar)) buf.append(' ');
    }

    private void dedent() {
        String finalPart = buf.substring(buf.length() - indent.length(), buf.length());
        if (finalPart.equals(indent)) {
            buf.setLength(buf.length()-4);
        }
        currentIndent = currentIndent.substring(0, currentIndent.length() - indent.length());
    }

    private boolean startsNewLine(Token t) {
        Token previousCachedToken = t.previousCachedToken();
        return previousCachedToken == null || previousCachedToken.getEndLine() != t.getBeginLine();
    }

    private String indentText(String text) {
        StringBuilder buf = new StringBuilder();
        for (String line : text.split("\n")) {
            buf.append(currentIndent);
            buf.append(line.trim());
            buf.append("\n");
        }
        return buf.toString();
    }

    public void visit(PackageDeclaration pd) {
        recurse(pd);
        newLine(true);
    }

    public void visit(ImportDeclaration id) {
        recurse(id);
        buf.append(eol);
        if (!(id.nextSibling() instanceof ImportDeclaration)) {
            buf.append(eol);
        }
    }

    public void visit(MethodDeclaration md) {
        if (!(md.previousSibling() instanceof MethodDeclaration) && !(md.previousSibling() instanceof ConstructorDeclaration)) newLine(true);
        recurse(md);
        newLine(true);
    }

    public void visit(ConstructorDeclaration cd) {
        if (!(cd.previousSibling() instanceof MethodDeclaration) && !(cd.previousSibling() instanceof ConstructorDeclaration)) newLine(true);
        recurse(cd);
        newLine(true);
    }

    public void visit(FieldDeclaration fd) {
        if (!(fd.previousSibling() instanceof FieldDeclaration)) {
            newLine();
        }
        recurse(fd);
        newLine();
    }

    public void visit(LocalVariableDeclaration lvd) {
        if (!(lvd.getParent() instanceof ForStatement)) newLine();
        recurse(lvd);
    }

    public void visit(Annotation ann) {
        if (!(ann.previousSibling() instanceof Annotation)) {
            newLine();
        }
        recurse(ann);
        newLine();
    }

    private void startNewLineIfNecessary() {
        if (buf.length() == 0) {
            return;
        }
        int lastNL = buf.lastIndexOf(eol);
        if (lastNL + eol.length() == buf.length()) {
            return;
        }
        String line = buf.substring(lastNL+ eol.length());
        if (line.trim().length() ==0) {
            buf.setLength(lastNL+eol.length());
        } else {
            buf.append(eol);
        }
    }

    private void newLine() {
        newLine(false);
    }
    
    private void newLine(boolean ensureBlankLine) {
        startNewLineIfNecessary();
        if (ensureBlankLine) {
            buf.append(eol);
        }
        buf.append(currentIndent);
    }
}
