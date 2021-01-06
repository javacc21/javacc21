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

package com.javacc.output.java;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.javacc.parser.*;
import static com.javacc.parser.JavaCCConstants.TokenType;
import static com.javacc.parser.JavaCCConstants.TokenType.*;
import com.javacc.parser.tree.*;

public class JavaCodeUtils {

    // Just a holder for static methods.
    private JavaCodeUtils() {
    }

    /**
     * Runs over the tree and looks for unused variables to remove. Currently it
     * just handles private class/instance level variables, not local variables This
     * was necessary because I was hitting the dreaded "Code too large" condition
     * when I was just generating all the various first/final/follow sets.
     * 
     * @param jcu
     */

    static public void removeUnusedVariables(CompilationUnit jcu) {
        Set<Identifier> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (FieldDeclaration fd : jcu.descendants(FieldDeclaration.class, 
                                     fd->fd.getParent() instanceof ClassOrInterfaceBodyDeclaration)) {
            if (((ClassOrInterfaceBodyDeclaration) fd.getParent()).isPrivate()) {
                List<Identifier> vars = fd.getVariableIds();
                ids.addAll(vars);
                for (Identifier id : vars) {
                    names.add(id.getImage());
                }
            }
        }
        Set<String> references = new HashSet<>();
        for (Identifier id : jcu.descendants(Identifier.class, 
                                    id->names.contains(id.getImage()) && !ids.contains(id))) {
            references.add(id.getImage());
        }
        for (Identifier id : ids) {
            if (!references.contains(id.getImage())) {
                removeDeclaration(id);
            }
        }
    }

    static private void removeDeclaration(Identifier id) {
        FieldDeclaration fd = id.firstAncestorOfType(FieldDeclaration.class);
        if (fd.getVariableIds().size() > 1) {
            // FIXME, TODO
        } else {
            Node parent = fd.getParent();
            Node grandparent = parent.getParent();
            grandparent.removeChild(parent);
        }
    }

    /**
     * Adds getter/setter methods if a field is annotated with a "@Property" annotation
     */
    static public void addGetterSetters(Node root) {
        List<FieldDeclaration> fds = root.descendants(FieldDeclaration.class);
        for (FieldDeclaration fd : fds) {
            List<Annotation> annotations  = fd.childrenOfType(Annotation.class);
            for (Annotation annotation : annotations) {
                if (annotation.firstChildOfType(Identifier.class).toString().equals("Property")) {
                    addGetterSetter(fd);
                    fd.removeChild(annotation);
                }
            }
        }
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
        for (Token tok : fd.childrenOfType(Token.class)) {
            TokenType type = tok.getType();
            if (type == PRIVATE) {
                return; // Nothing to do!
            }
            else if (type == PROTECTED || type == PUBLIC) {
                fd.removeChild(tok);
                break;
            }
        }
        Type type = fd.firstChildOfType(Type.class);
        Token privateToken = Token.newToken(PRIVATE, "private", fd);
        fd.addChild(fd.indexOf(type), privateToken);
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
        try {
           getterMethod = new JavaCCParser(getter).MethodDeclaration();
           setterMethod = new JavaCCParser(setter).MethodDeclaration();
        } catch (ParseException pe) {
            throw new InternalError(pe);
        }
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
            Node grandparent = parent.getParent();
            parent.removeChild(annotation);
            if (removeElement) {
                grandparent.removeChild(parent);
            } 
        }
    }

    static private final String capitalizeFirstLetter(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}