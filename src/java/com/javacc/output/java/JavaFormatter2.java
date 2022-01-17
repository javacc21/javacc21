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

import com.javacc.parser.JavaCCConstants.TokenType;
import static com.javacc.parser.JavaCCConstants.TokenType.*;

import com.javacc.parser.*;
import com.javacc.parser.tree.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A somewhat more sophisticated class for pretty-printing java source code.
 * Still has some rough edges though!
 * @author revusky
 */
public class JavaFormatter2 extends Node.Visitor {
    
    private StringBuilder buf = new StringBuilder();
    private String indent = "    ";
    private String currentIndent = "";
    private String eol = "\n";

    private void visitPrecedingTokens(Token tok) {
        if (tok.isUnparsed()) return;
        for (Token t : precedingUnparsedTokens(tok)) {
            if (t instanceof Whitespace) continue;
            visit((Node) t);
        }
    }

    public void visit(Identifier id) {
        visitPrecedingTokens(id);
        Token previousCachedToken = id.previousCachedToken();
        if (previousCachedToken != null) {
            if (previousCachedToken instanceof KeyWord || previousCachedToken instanceof Identifier) {
                addSpaceIfNecessary(false);
            }
        }
        buf.append(id);
    }

    public void visit(KeyWord keyword) {
        visitPrecedingTokens(keyword);
        TokenType type = keyword.getType();
        addSpaceIfNecessary(type == THROWS);
        buf.append(keyword);
        if (type == IF || type == DO || type == WHILE) {
            buf.append(' ');
        }
    }

    public void visit(TypeDeclaration td) {
        newLine(true);
        recurse(td);
        newLine(true);
    }

    public void visit(Token tok) {
        visitPrecedingTokens(tok);
        if (tok.getType() == EOF) buf.append("\n");
        else {
            int firstChar = tok.getImage().codePointAt(0);
            if (Character.isJavaIdentifierPart(firstChar)) {
                addSpaceIfNecessary(false);
            }
            buf.append(tok);
        }
    }

    public void visit(Operator op) {
        visitPrecedingTokens(op);
        switch (op.getType()) {
            case LT:
                if (op.getParent() instanceof RelationalExpression) {
                    addSpaceIfNecessary(true);
                    buf.append("< ");
                } else {
                    buf.append("<");
                }
                break;
            case GT:
                if (op.getParent() instanceof RelationalExpression) {
                    addSpaceIfNecessary(true);
                    buf.append("> ");
                } else {
                    buf.append(">");
                    if (op.nextCachedToken().getType() != GT) buf.append(' ');
                }
                break;
            default : buf.append(op);
        }
    }

    public void visit(Delimiter delimiter) {
        visitPrecedingTokens(delimiter);
        switch (delimiter.getType()) {
            case COLON :
                addSpaceIfNecessary(true);
                buf.append(": ");
                break;
            case COMMA : 
                buf.append(", "); 
                break;
            case LBRACE : 
                addSpaceIfNecessary(true); 
                buf.append("{");
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
            default : buf.append(delimiter);
        }
    }

    public void visit(Statement stmt) {
        if (stmt.getParent() instanceof IfStatement) {
            addSpaceIfNecessary(true);
        } else {
            newLine();
        }
        recurse(stmt);
        newLine();
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

    private void addSpaceIfNecessary(boolean afterNonWhitespace) {
        if (buf.length()==0) return;
        int lastChar = buf.codePointBefore(buf.length());
        if (afterNonWhitespace) {
            if (!Character.isWhitespace(buf.charAt(buf.length()-1))) buf.append(' ');
        } else {
            if (Character.isJavaIdentifierPart(lastChar)) buf.append(' ');
        }

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
        buf.append(eol);
        buf.append(eol);
    }

    public void visit(ImportDeclaration id) {
        recurse(id);
        buf.append(eol);
        if (!(id.nextSibling() instanceof ImportDeclaration)) {
            buf.append(eol);
        }
    }

    public void visit(MethodDeclaration md) {
        if (!(md.previousSibling() instanceof MethodDeclaration)) newLine(true);
        recurse(md);
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

    private List<Token> precedingUnparsedTokens(Token tok) {
        ArrayList<Token> result = new ArrayList<>();
        for (Iterator<Token> it = tok.precedingTokens(); it.hasNext();) {
            Token next = it.next();
            if (!next.isUnparsed()) break;
            result.add(next);
        }
        Collections.reverse(result);
        return result;
    }

    public String format(BaseNode code) {
        visit(code);
        return buf.toString();
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
