/* Copyright (c) 2021 Jonathan Revusky, revusky@javacc.com
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
import com.javacc.parser.tree.*;
import static com.javacc.parser.JavaCCConstants.TokenType.*;

/**
 * A visitor that eliminates unused code.
 * It is not absolutely correct, in the sense of catching all 
 * unused methods or fields, but works for our purposes.
 * For example, it does not take account overloaded methods, so
 * if the method name is referenced somewhere, it is assumed to be used.
 * However, it might be a reference to a method with the same name
 * with different arguments.
 * Also variable names can be in a sense overloaded by being defined
 * in inner classes, but we don't bother about that either.
 */
class DeadCodeEliminator extends Node.Visitor {
    private Set<String> usedNames = new HashSet<String>();
    private Set<Node> alreadyVisited = new HashSet<Node>();
    private CompilationUnit jcu;

    DeadCodeEliminator(CompilationUnit jcu) {
        this.jcu = jcu;
    }

    void stripUnused() {
        int previousUsedNamesSize = -1;
        // Visit the tree over and over until
        // nothing is added to usedNames. Then we can stop.
        while (usedNames.size() > previousUsedNamesSize) {
            previousUsedNamesSize = usedNames.size();
            visit(jcu);
        }
        // If the name of the method is not in usedNames, we delete it.
        for (MethodDeclaration md : jcu.descendants(MethodDeclaration.class, md->!usedNames.contains(md.getName()))) {
            md.getParent().removeChild(md);
        }
        // We go through all the private FieldDeclarations and get rid of any variables that 
        // are not in usedNames
        for (FieldDeclaration fd : jcu.descendants(FieldDeclaration.class, fd->isPrivate(fd))) {
            stripUnusedVars(fd);
        }

        // With the remaining field declarations, we add any type names to usedNames
        // so that we don't remove imports that refer to them. 
        for (FieldDeclaration fd : jcu.descendants(FieldDeclaration.class)) {
            for (Identifier id : fd.descendantsOfType(Identifier.class)) {
        // In Foo.Bar.Baz it is only the Foo
        // that needs to be added to usedNames, for example.
                if (id.getPrevious().getType() != DOT) {
                   usedNames.add(id.getImage());
                }
            }
        }

        // Now get rid of unused imports.
        for (ImportDeclaration imp : jcu.childrenOfType(ImportDeclaration.class)) {
            if (imp.firstChildOfType(STAR) == null) {
                List<Identifier> names = imp.descendantsOfType(Identifier.class);
                Identifier name = names.get(names.size()-1);
                if (!usedNames.contains(name.getImage())) {
                    jcu.removeChild(imp);
                    //System.out.println("Removing: " + imp.getAsString());
                }
            }
        }
    }

    private boolean isPrivate(Node node) {
        if (node.firstChildOfType(PRIVATE) != null) return true;
        Modifiers mods = node.firstChildOfType(Modifiers.class);
        return mods == null ? false : mods.firstChildOfType(PRIVATE) != null;
    }

    void visit(MethodDeclaration md) {
        if (alreadyVisited.contains(md)) return;
        if (!isPrivate(md) || usedNames.contains(md.getName())) {
            md.descendants(Identifier.class).stream().forEach(id->usedNames.add(id.getImage()));
            alreadyVisited.add(md);
        }
    }

    void visit(VariableDeclarator vd) {
        if (alreadyVisited.contains(vd)) return;
        if (!isPrivate(vd.getParent()) || usedNames.contains(vd.getName())) {
            for (Identifier id : vd.descendants(Identifier.class)) {
                usedNames.add(id.getImage());
            }
            alreadyVisited.add(vd);
        }
    }

    void visit(Initializer init) {
        if (alreadyVisited.contains(init)) return;
        for (Identifier id : init.descendants(Identifier.class)) {
            usedNames.add(id.getImage());
        }
        alreadyVisited.add(init);
    }

    void visit(ConstructorDeclaration cd) {
        if (alreadyVisited.contains(cd)) return;
        for (Identifier id : cd.descendants(Identifier.class)) {
            usedNames.add(id.getImage());
        }
        alreadyVisited.add(cd);
    }

    // Get rid of any variable declarations where the variable name 
    // is not in usedNames. The only complicated case is if the field
    // has more than one variable declaration comma-separated
    private void stripUnusedVars(FieldDeclaration fd) {
        Set<Node> toBeRemoved = new HashSet<Node>();
        for (VariableDeclarator vd : fd.childrenOfType(VariableDeclarator.class)) {
            if (!usedNames.contains(vd.getName())) {
                toBeRemoved.add(vd);
                int index = fd.indexOf(vd);
                Node prev = fd.getChild(index-1);
                Node next = fd.getChild(index+1);
                if (prev instanceof Token && ((Token)prev).getType()==COMMA) {
                    toBeRemoved.add(prev);
                }
                else if (next instanceof Token && ((Token)next).getType() == COMMA) {
                    toBeRemoved.add(next);
                }
            }
        }
        for (Node n : toBeRemoved) {
            fd.removeChild(n);
        }
        if (fd.firstChildOfType(VariableDeclarator.class) == null) {
            fd.getParent().removeChild(fd);
        }
    }
}