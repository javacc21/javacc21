/* Copyright (c) 2020 Jonathan Revusky, revusky@javacc.com
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

package com.javacc.output.lint;

import com.javacc.*;
import com.javacc.parsegen.Expansion;
import com.javacc.parser.*;
import com.javacc.parser.tree.*;
import static com.javacc.parser.JavaCCConstants.TokenType.*;

import java.io.*;
//import java.nio.charset.Charset;
//import java.nio.file.Files;


 public class SyntaxConverter extends Node.Visitor {

     private StringBuilder outputBuffer = new StringBuilder();
     private Grammar grammar = new Grammar();

     static public void main(String args[]) throws Exception {
         String location = args[0].equalsIgnoreCase("convert") ? args[1] : args[0];
         SyntaxConverter converter = new SyntaxConverter(location);
         System.out.print(converter.outputBuffer);
     }

     public SyntaxConverter(String location) throws IOException, ParseException{
/*
        File file = new File(location);
        String content = new String(Files.readAllBytes(file.toPath()),Charset.forName("UTF-8"));
        JavaCCParser parser = new JavaCCParser(grammar, file.getCanonicalFile().getName(), content);
        parser.setEnterIncludes(false);
        parser.setUnparsedTokensAreNodes(true);
        System.out.println("Parsing grammar file " + location + " . . .");
        GrammarFile root = parser.Root();
        System.out.print(root.getSource());
        visit(root);*/
        Node root = grammar.parse(location, false);
        System.out.print(root);
     }

     public void visit(Lookahead la) {
         if (la.getSource().trim().startsWith("LOOKAHEAD")) {
             int amount = la.getAmount();
             boolean explicitAmount = amount != Integer.MAX_VALUE;
             Expansion expansion = la.getNestedExpansion();
             Expression semanticLookahead = la.getSemanticLookahead();
             if (!explicitAmount && semanticLookahead == null && expansion == null) {
                 outputBuffer.append("=> ");
             }
             else {
                 outputBuffer.append("SCAN ");
                 if (explicitAmount) {
                     outputBuffer.append("" + amount);
                     outputBuffer.append(" ");
                 }
                 if (semanticLookahead != null) {
                     outputBuffer.append("{");
                     outputBuffer.append(semanticLookahead.getSource());
                     outputBuffer.append("}");
                 }
                 if (expansion != null) {
                     outputBuffer.append(expansion.getSource());
                 }
                 outputBuffer.append("=> ");
             }
         } else {
             recurse(la);
         }
     }

     public void visit(BNFProduction bnf) {
         for (Token t : bnf.childrenOfType(Token.class)) {
             if (t.getType() == VOID || t.getType() == PUBLIC || t.getType() == LBRACE) {
                 bnf.removeChild(t);
             }
             if (t.getType() == RBRACE) {
                 t.setImage(";");
             }
         }
         FormalParameters params = bnf.firstChildOfType(FormalParameters.class);
         if (params != null && params.firstChildOfType(FormalParameter.class) == null) {
             bnf.removeChild(params);
         }
     }

     public void visit(NonTerminal nt) {
         InvocationArguments args = nt.getArgs();
         if (args != null && args.firstChildOfType(Expression.class) == null) {
             nt.removeChild(args);
         }
         recurse(nt);
     }

     public void visit(CodeInjection injection) {
         for (Token t : injection.childrenOfType(Token.class)) {
             if (t.getType() == LPAREN || t.getType() == RPAREN || t.getType() == LBRACE || t.getType() == RBRACE) {
                 injection.removeChild(t);
             }
         }
     }

     public void visit(Options options) {
         for (Token t : options.childrenOfType(Token.class)) {
            if (t.getType() == LBRACE || t.getType() == RBRACE) {
                options.removeChild(t);
            }
         }
         recurse(options);
     }

     public void visit(TokenProduction tp) {
         Token lastToken = (Token) tp.getChild(tp.getChildCount() -1);
         if (lastToken.getType() == RBRACE) {
             lastToken.setImage(";");
             for (Token t : tp.childrenOfType(Token.class)) {
                if (t.getType() == LBRACE) {
                    tp.removeChild(t);
                    break;
                }
             }
         }
         recurse(tp);
     }

     public void visit(Token t) {
         outputBuffer.append(t.getImage());
     }
 }