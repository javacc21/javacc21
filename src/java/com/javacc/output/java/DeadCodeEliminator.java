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
import com.javacc.parser.JavaCCConstants.TokenType;
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
public class DeadCodeEliminator extends Node.Visitor {
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
        for (FieldDeclaration fd : jcu.descendants(FieldDeclaration.class, fd->fd.firstChildOfType(PRIVATE)!=null)) {
            stripUnusedVars(fd);
        }
    }

    public void visit(MethodDeclaration md) {
        if (alreadyVisited.contains(md)) return;
        if (md.firstChildOfType(PRIVATE) == null || usedNames.contains(md.getName())) {
            usedNames.add(md.getName());
            for (Identifier id : md.descendants(Identifier.class)) {
                usedNames.add(id.getImage());
            }
            alreadyVisited.add(md);
        }
    }

    public void visit(VariableDeclarator vd) {
        if (alreadyVisited.contains(vd)) return;
        boolean isPrivate = vd.getParent().firstChildOfType(PRIVATE) != null;
        if (!isPrivate || usedNames.contains(vd.getName())) {
            for (Identifier id : vd.descendants(Identifier.class)) {
                usedNames.add(id.getImage());
            }
            alreadyVisited.add(vd);
        }
    }

    public void visit(Initializer init) {
        if (alreadyVisited.contains(init)) return;
        for (Identifier id : init.descendants(Identifier.class)) {
            usedNames.add(id.getImage());
        }
        alreadyVisited.add(init);
    }

    public void visit(ConstructorDeclaration cd) {
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
        boolean removedSomething = false;
        for (VariableDeclarator vd : fd.childrenOfType(VariableDeclarator.class)) {
            if (!usedNames.contains(vd.getName())) {
                fd.removeChild(vd);
                removedSomething = true;
            }
        }
        if (removedSomething) {
            if (fd.firstChildOfType(VariableDeclarator.class) != null) {
                removeExtraCommas(fd);
            }
            else {
                fd.getParent().removeChild(fd);
            }
        }
    }

    // A mop-up operation if we've eliminated some (but not all) 
    // of the variables in a FieldDeclaration
    static private void removeExtraCommas(FieldDeclaration fd) {
        List<Token> toBeRemoved = new ArrayList<Token>();
        for (int i=0; i< fd.getChildCount()-1; i++) {
            Node current = fd.getChild(i);
            Node next = fd.getChild(i+1);
            if (current instanceof Token && next instanceof Token) {
                Token curToken = (Token) current;
                TokenType nextTokenType = ((Token) next).getType();
                if (curToken.getType() == COMMA && (nextTokenType == COMMA || nextTokenType == SEMICOLON))
                    toBeRemoved.add((Token) current);
            }
        }
        for (Token comma : toBeRemoved) {
            fd.removeChild(comma);
        }
    }
}