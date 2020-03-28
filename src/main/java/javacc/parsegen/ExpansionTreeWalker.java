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

package javacc.parsegen;

import javacc.parser.tree.ExpansionChoice;
import javacc.parser.tree.ExpansionSequence;
import javacc.parser.tree.RegexpChoice;
import javacc.parser.tree.RegexpSequence;
import javacc.parser.tree.RepetitionRange;
import javacc.parser.tree.ZeroOrMoreRegexp;
import javacc.parser.tree.ZeroOrOneRegexp;
import javacc.parser.tree.OneOrMoreRegexp;
import javacc.parser.tree.OneOrMore;
import javacc.parser.tree.ZeroOrMore;
import javacc.parser.tree.ZeroOrOne;
import javacc.parser.tree.TryBlock;

/**
 * A set of routines that walk down the Expansion tree in various ways.
 */
final class ExpansionTreeWalker {
    private ExpansionTreeWalker() {
    }

    /**
     * Visits the nodes of the tree rooted at "node" in pre-order. i.e., it
     * executes opObj.action first and then visits the children.
     */
    static void preOrderWalk(Expansion node, TreeWalkerOp opObj) {
        opObj.action(node);
        if (opObj.goDeeper(node)) {
            if (node instanceof ExpansionChoice) {
                for (Expansion exp : node.childrenOfType(Expansion.class)) {

                    preOrderWalk(exp, opObj);
                }
            } else if (node instanceof ExpansionSequence) {
                for (Expansion exp : node.childrenOfType(Expansion.class)) {
                    preOrderWalk(exp, opObj);
                }
            } else if (node instanceof OneOrMore || node instanceof ZeroOrMore || node instanceof ZeroOrOne
                    || node instanceof TryBlock) {
                preOrderWalk(node.getNestedExpansion(), opObj);
            } else if (node instanceof Lookahead) {
                Expansion nested_e = ((Lookahead) node).getNestedExpansion();
                if (!(nested_e instanceof ExpansionSequence && (Expansion) (((ExpansionSequence) nested_e).getLookahead()) == node)) {
                    preOrderWalk(nested_e, opObj);
                }
            } else if (node instanceof RegexpChoice) {
                for (Expansion exp : ((RegexpChoice) node).getChoices()) {
                    preOrderWalk(exp, opObj);
                }
            } else if (node instanceof RegexpSequence) {
                for (Expansion exp : ((RegexpSequence) node).getUnits()) {
                    preOrderWalk(exp, opObj);
                }
            } else if (node instanceof OneOrMoreRegexp) {
                preOrderWalk(((OneOrMoreRegexp) node).getRegexp(), opObj);
            } else if (node instanceof ZeroOrMoreRegexp) {
                preOrderWalk(((ZeroOrMoreRegexp) node).getRegexp(), opObj);
            } else if (node instanceof ZeroOrOneRegexp) {
                preOrderWalk(((ZeroOrOneRegexp) node).getRegexp(), opObj);
            } else if (node instanceof RepetitionRange) {
                preOrderWalk(((RepetitionRange) node).getRegexp(), opObj);
            }
        }
    }

    /**
     * Visits the nodes of the tree rooted at "node" in post-order. i.e., it
     * visits the children first and then executes opObj.action.
     */
    static void postOrderWalk(Expansion node, TreeWalkerOp opObj) {
        if (opObj.goDeeper(node)) {
            if (node instanceof ExpansionChoice) {
                for (Expansion exp : node.childrenOfType(Expansion.class)) {
                    postOrderWalk(exp, opObj);
                }
            } else if (node instanceof ExpansionSequence) {
                for (Expansion exp : node.childrenOfType(Expansion.class)) {
                	postOrderWalk(exp, opObj);
                }
            } else if (node instanceof OneOrMore) {
                postOrderWalk(node.getNestedExpansion(), opObj);
            } else if (node instanceof ZeroOrMore) {
                postOrderWalk(node.getNestedExpansion(), opObj);
            } else if (node instanceof ZeroOrOne) {
                postOrderWalk(node.getNestedExpansion(), opObj);
            } else if (node instanceof Lookahead) {
                Expansion nested_e = ((Lookahead) node).getNestedExpansion();
                if (!(nested_e instanceof ExpansionSequence && nested_e.getChild(0) == node)) {
                    postOrderWalk(nested_e, opObj);
                }
            } else if (node instanceof TryBlock) {
                postOrderWalk(node.getNestedExpansion(), opObj);
            } else if (node instanceof RegexpChoice) {
                for (Expansion exp : ((RegexpChoice) node).getChoices()) {
                    postOrderWalk(exp, opObj);
                }
            } else if (node instanceof RegexpSequence) {
                for (Expansion exp : ((RegexpSequence) node).getUnits()) {
                    postOrderWalk(exp, opObj);
                }
            } else if (node instanceof OneOrMoreRegexp) {
                postOrderWalk(((OneOrMoreRegexp) node).getRegexp(), opObj);
            } else if (node instanceof ZeroOrMoreRegexp) {
                postOrderWalk(((ZeroOrMoreRegexp) node).getRegexp(), opObj);
            } else if (node instanceof ZeroOrOneRegexp) {
                postOrderWalk(((ZeroOrOneRegexp) node).getRegexp(), opObj);
            } else if (node instanceof RepetitionRange) {
                postOrderWalk(((RepetitionRange) node).getRegexp(), opObj);
            }
        }
        opObj.action(node);
    }

}
