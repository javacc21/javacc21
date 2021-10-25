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

public class DeadCodeEliminator extends Node.Visitor {
    private Set<String> usedNames = new HashSet<String>();
    private Set<Node> alreadyVisited = new HashSet<Node>();
    private CompilationUnit jcu;

    public DeadCodeEliminator(CompilationUnit jcu) {
        this.jcu = jcu;
    }

    public void process() {
        int previousUsedNamesSize = -1;
        // Visit the tree over and over until
        // now names are added. Then we can stop.
        while (usedNames.size() > previousUsedNamesSize) {
            previousUsedNamesSize = usedNames.size();
            visit(jcu);
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

    static public void eliminateUnused(CompilationUnit jcu) {
        DeadCodeEliminator deadCodeEliminator = new DeadCodeEliminator(jcu);
        deadCodeEliminator.process();
        Set<String> usedNames = deadCodeEliminator.usedNames;
        for (MethodDeclaration md : jcu.descendants(MethodDeclaration.class, md->!usedNames.contains(md.getName()))) {
            md.getParent().removeChild(md);
        }
        for (FieldDeclaration fd : jcu.descendants(FieldDeclaration.class, fd->shouldEliminate(fd, usedNames))) {
            fd.getParent().removeChild(fd);
        }
    }

    static boolean shouldEliminate(FieldDeclaration fd, Set<String> usedNames) {
        if (fd.firstChildOfType(PRIVATE) ==null) {
            return false;
        }
        for (VariableDeclarator vd : fd.childrenOfType(VariableDeclarator.class)) {
            if (usedNames.contains(vd.getName())) return false;
        }
        return true;
    }
}