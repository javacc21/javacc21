/* Copyright (c) 2020,2021 Jonathan Revusky, revusky@javacc.com
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

import java.util.*;
import com.javacc.parser.*;
import static com.javacc.parser.JavaCCConstants.TokenType;
import static com.javacc.parser.JavaCCConstants.TokenType.*;
import com.javacc.parser.tree.*;

public class JavaCodeUtils {

    // Just a holder for static methods.
    private JavaCodeUtils() {
    }

    /**
     * Adds getter/setter methods if a field is annotated with a "@Property" annotation
     */
    static public void addGetterSetters(Node root) {
        List<FieldDeclaration> fds = root.descendants(FieldDeclaration.class);
        for (FieldDeclaration fd : fds) {
            //List<Annotation> annotations  = fd.childrenOfType(Annotation.class);
            for (Annotation annotation : getAnnotations(fd)) {
                if (annotation.getName().equals("Property")) {
                    addGetterSetter(fd);
                    annotation.getParent().removeChild(annotation);
                }
            }
        }
    }

    static private List<Annotation> getAnnotations(Node node) {
        List<Annotation> result = new ArrayList<>();
        result.addAll(node.childrenOfType(Annotation.class));
        Modifiers mods = node.firstChildOfType(Modifiers.class);
        if (mods != null) {
            result.addAll(mods.childrenOfType(Annotation.class));
        }
        return result;
    }

    static private void addGetterSetter(FieldDeclaration fd) {
        Node context = fd.getParent();
        int index = context.indexOf(fd);
        String fieldType = fd.firstChildOfType(Type.class).toString();
        for (Identifier id : fd.getVariableIds()) {
            ensurePrivate(fd);
            insertGetterSetter(context, fieldType, id.getImage(), index);
        }
    }

    static private void ensurePrivate(FieldDeclaration fd) {
        List<Token> tokens = fd.childrenOfType(Token.class);
        Modifiers mods = fd.firstChildOfType(Modifiers.class);
        if (mods != null) {
            tokens.addAll(mods.childrenOfType(Token.class));
        }
        for (Token tok : tokens) {
            TokenType type = tok.getType();
            if (type == PRIVATE) {
                return; // Nothing to do!
            }
            else if (type == PROTECTED || type == PUBLIC) {
                tok.getParent().removeChild(tok);
                break;
            }
        }
        Type type = fd.firstChildOfType(Type.class);
        Token privateToken = Token.newToken(PRIVATE, "private", fd.getTokenSource());
        if (mods !=null) mods.addChild(privateToken);
        else fd.addChild(fd.indexOf(type), privateToken);
    }

    static private void insertGetterSetter(Node context, String fieldType, String fieldName, int index) {
        String getterMethodName = "get" + capitalizeFirstLetter((fieldName));
        String setterMethodName = getterMethodName.replaceFirst("g", "s");
        if (fieldType.equals("boolean")) {
            getterMethodName = getterMethodName.replaceFirst("get", "is");
        }
        String getter = "//Inserted getter for " + fieldName 
                        +"\npublic " + fieldType + " " + getterMethodName 
                        + "() {return " + fieldName + ";}";
        String setter = "//Inserted setter for " + fieldName 
                        + "\npublic void " + setterMethodName 
                        + "(" + fieldType + " " +  fieldName + ") {this." + fieldName + " = " + fieldName + ";}";
        MethodDeclaration getterMethod = null, setterMethod=null;
        getterMethod = new JavaCCParser(getter).MethodDeclaration();
        setterMethod = new JavaCCParser(setter).MethodDeclaration();
        context.addChild(index +1, setterMethod);
        context.addChild(index +1, getterMethod);
    }

    static public void removeWrongJDKElements(Node context, int target) {
        List<Annotation> annotations = context.descendants(Annotation.class, 
            a->a.getName().toLowerCase().startsWith("minjdk") || a.getName().toLowerCase().startsWith("maxjdk"));
        for (Annotation annotation : annotations) {
            boolean specifiesMax = annotation.getName().toLowerCase().startsWith("max");
            String intPart = annotation.getName().substring(6);
            int specifiedVersion = target;
            try {
                specifiedVersion = Integer.valueOf(intPart);
            }
            catch (NumberFormatException nfe) {
                //okay, do nothing here. Just leave the annotation there and let the 
                // Java compiler deal with the fact that it is wrong!
                continue;
            }
            boolean removeElement = specifiesMax ? target > specifiedVersion : target < specifiedVersion;
            Node parent = annotation.getParent();
            parent.removeChild(annotation);
            if (parent instanceof Modifiers) {
                parent = parent.getParent();
            }
            Node grandparent = parent.getParent();
            if (removeElement) {
                grandparent.removeChild(parent);
            } 
        }
    }

    /**
     * Uses the DeadCodeEliminator visitor class to get rid of
     * unused private methods and fields
     */
    static public void stripUnused(CompilationUnit jcu) {
        new DeadCodeEliminator(jcu).stripUnused();
    }

    static private final String capitalizeFirstLetter(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}