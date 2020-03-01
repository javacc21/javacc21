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

package javacc.output.java;

import java.io.*;
import javacc.parser.*;
import javacc.parser.tree.*;
import static javacc.parser.JavaCCConstants.*;

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
    
    public JavaFormatter() {}
    
    public String format(Reader input) throws IOException, ParseException {
        JavaCCParser parser = new JavaCCParser(input);
        CompilationUnit cu = parser.CompilationUnit();
        input.close();
        return format(cu);
    }
    
    public String format(BaseNode code) {
        buf = new StringBuilder();
        for (Token t :  Nodes.getAllTokens(code, true, true)) {
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
    
    private void startNewLineIfNecessary() {
        if (buf.length() == 0) {
            return;
        }
        int lastNL = buf.lastIndexOf("\n");
        if (lastNL +1 == buf.length()) {
            return;
        }
        String line = buf.substring(lastNL+1);
        if (line.trim().length() ==0) {
            buf.setLength(lastNL+1);
        } else {
            buf.append("\n");
        }
    }
    
    private void newLine() {
        startNewLineIfNecessary();
        buf.append(currentIndent);
    }
    
    private void handleToken() {
        switch (currentToken.getId()) {
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
                if (parent instanceof ForStatement) {
                    buf.append(' ');
                }
                if (parent instanceof PackageDeclaration) {
                    buf.append("\n\n");
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
                if (currentToken.getId() == IF || currentToken.getId() == WHILE) {
                    buf.append(' ');
                }
        }
    }
    
    private void handleSingleLineComment() {
        if (lastToken !=null && lastToken.getEndLine() == currentToken.getBeginLine()) {
            int lastNL = buf.indexOf("\n");
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
            buf.append("\n\n");
        }
        newLine();
    }
}
