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

import com.javacc.parser.Node;
import com.javacc.parser.tree.ClassOrInterfaceBodyDeclaration;
import com.javacc.parser.tree.CompilationUnit;
import com.javacc.parser.tree.FieldDeclaration;
import com.javacc.parser.tree.Identifier;

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
        for (FieldDeclaration fd : jcu.descendantsOfType(FieldDeclaration.class)) {
            if (fd.getParent() instanceof ClassOrInterfaceBodyDeclaration) {
                if (((ClassOrInterfaceBodyDeclaration) fd.getParent()).isPrivate()) {
                    List<Identifier> vars = fd.getVariableIds();
                    ids.addAll(vars);
                    for (Identifier id : vars) {
                        names.add(id.getImage());
                    }
                }
            }
        }
        Set<String> references = new HashSet<>();
        for (Identifier id : jcu.descendantsOfType(Identifier.class)) {
            String name = id.getImage();
            if (names.contains(name) && !ids.contains(id)) {
                references.add(id.getImage());
            }
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
//            System.out.println("KILROY: removing statement: " + parent.getSource());
        }
    }


}