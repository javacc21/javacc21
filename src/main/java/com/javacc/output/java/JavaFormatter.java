/* Copyright (c) 2008-2020 Jonathan Revusky, revusky@javacc.com
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

import static com.javacc.parser.JavaCCConstants.*;

import com.javacc.parser.*;
import com.javacc.parser.tree.*;
import java.util.List;

/**
 * A rather rough-and-ready class for pretty-printing java source code.
 * @author revusky
 */

public class JavaFormatter {
    
    private Token currentToken, lastToken;
    private BaseNode parent;
    private StringBuilder buf = new StringBuilder();
    private String indent = "    ";
    private String currentIndent = "";
    private String eol = "\n";
    
    public JavaFormatter() {}
    
    public String format(BaseNode code) {
        buf = new StringBuilder();
        List<Token> allTokens = Nodes.getAllTokens(code, true, true);
        checkFirstNewLine(allTokens);
        for (Token t :  allTokens) {
            if (t instanceof Whitespace) {
                continue;
            }
            lastToken = currentToken;
            currentToken = t;
            parent = (BaseNode) t.getParent();
            handleToken();
        }
        return buf.toString();
    }

    // Scans for the first instance of a line terminator 
    // and sets eol accordingly
    private void checkFirstNewLine(List<Token> tokens) {
        for (Token t: tokens) {
            if (t instanceof Whitespace) {
                String img = t.getImage();
                int idx = img.indexOf('\n');
                if (idx == 0) break;
                if (idx >1 && img.charAt(idx-1) == '\r') {
                    eol = "\r\n";
                    break;
                }
            }
        }
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
        startNewLineIfNecessary();
        buf.append(currentIndent);
    }
    
    private void handleToken() {
        switch (currentToken.getType()) {
            case LBRACE :
                handleOpenBrace();
                break;
            case RBRACE :
                handleCloseBrace();
                break;
            case COLON :
                if ((parent instanceof ConditionalOrExpression) || (parent instanceof ForStatement)) {
                    buf.append(" : ");
                } else {
                    buf.append(':');
                    newLine();
                }
                break;
            case SEMICOLON :
                buf.append(';');
                if (parent instanceof PackageDeclaration) {
                    buf.append(eol);
                    buf.append(eol);
                }
                else if (parent instanceof ForStatement) {
                	if (parent.getChild(parent.getChildCount()-1) != currentToken) {
                		buf.append(" ");
                	} else {
                		newLine();
                	}
                }
                else {
                    newLine();
                }
                break;
            case RPAREN :
                buf.append(')');
                if (parent instanceof Annotation) {
                    newLine();
                }
                break;
            case FORMAL_COMMENT :
            case MULTI_LINE_COMMENT :
                newLine();
                buf.append(currentToken);
                newLine();
                break;
            case SINGLE_LINE_COMMENT : 
                handleSingleLineComment();
                break;
            case FOR : 
            	buf.append("for ");
            	break;
            case AT :
            	newLine();
            	buf.append("@");
            	break;
            case COMMA :
                buf.append(", ");
                break;
            default:
                if (buf.length() > 0) {
                    char lastChar = buf.charAt(buf.length() -1);
                    char thisChar = currentToken.toString().charAt(0);
                    if ((Character.isJavaIdentifierPart(lastChar) || lastChar == ')' || lastChar == ']') 
                            && Character.isJavaIdentifierPart(thisChar)) {
                        buf.append(' ');
                    }
                }
                buf.append(currentToken);
                TokenType type = currentToken.getType();
                if (type == TokenType.IF || type == TokenType.WHILE || type == TokenType.GT || type == TokenType.EQ || type == TokenType.ASSIGN) {
                    buf.append(' ');
                }
                if (type == TokenType.IDENTIFIER && parent instanceof Annotation && parent.indexOf(currentToken) == parent.getChildCount()-1) {
                    newLine();
                }
        }
    }
    
    private void handleSingleLineComment() {
        if (lastToken !=null && lastToken.getEndLine() == currentToken.getBeginLine()) {
            int lastNL = buf.indexOf(eol);
            if (lastNL >=0 && buf.substring(lastNL).trim().length() == 0) {
                buf.setLength(lastNL);
            }
        }
        buf.append(currentToken);
        newLine();
    }
    
    
    private void handleOpenBrace() {
        if (parent instanceof ArrayInitializer) {
            buf.append('{');
            return;
        }
        buf.append(' ');
        buf.append('{');
        currentIndent += indent;
        newLine();
    }
    
    private void handleCloseBrace() {
        if (parent instanceof ArrayInitializer) {
            buf.append('}');
            return;
        }
        currentIndent = currentIndent.substring(0, currentIndent.length() -indent.length());
        newLine();
        buf.append('}');
        if (parent instanceof TypeDeclaration 
            || parent instanceof ConstructorDeclaration
            || parent.getParent() instanceof MethodDeclaration)
        {
            buf.append(eol);
            buf.append(eol);
        }
        newLine();
    }
}
