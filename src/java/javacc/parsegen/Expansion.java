/* Copyright (c) 2008-2020 Jonathan Revusky, revusky@javacc.com
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

import javacc.Grammar;
import javacc.parser.BaseNode;
import javacc.lexgen.RegularExpression;
import javacc.parser.tree.TreeBuildingAnnotation;
import javacc.parser.tree.ParserProduction;



/**
 * Describes expansions - entities that may occur on the right hand sides of
 * productions. This is the base class of a bunch of other more specific
 * classes.
 */

abstract public class Expansion extends BaseNode {

    private boolean forced;

    private TreeBuildingAnnotation treeNodeBehavior;

    private Lookahead lookahead;

    public Expansion(Grammar grammar) {
        setGrammar(grammar);
    }

    public Expansion() {}

    /**
     * A reimplementing of Object.hashCode() to be deterministic. This uses the
     * line and column fields to generate an arbitrary number - we assume that
     * this method is called only after line and column are set to their actual
     * values.
     */
    public int hashCode() {
        return getBeginLine() + getBeginColumn();
    }

    /**
     * An internal name for this expansion. This is used to generate parser
     * routines.
     */
    private String internalName = "";

    /**
     * The parser routines are generated in three phases. The generation of the
     * second and third phase are on demand only, and the third phase can be
     * recursive. This variable is used to keep track of the expansions for
     * which phase 3 generations have been already added to a list so that the
     * recursion can be terminated.
     */
    boolean phase3done = false;

    /**
     * The parent of this expansion node. In case this is the top level
     * expansion of the production it is a reference to the production node
     * otherwise it is a reference to another Expansion node. In case this is
     * the top level of a lookahead expansion,then the parent is null.
     */
    private Object parentObject;

    /**
     * The ordinal of this node with respect to its parent.
     */
    public int ordinal;

    public long myGeneration = 0;

    /**
     * This flag is used for bookkeeping by the minimumSize method in class
     * ParseEngine.
     */
    public boolean inMinimumSize = false;

    private String getSimpleName() {
        String name = getClass().getName();
        return name.substring(name.lastIndexOf(".") + 1); // strip the package
                                                            // name
    }
    
    public String toString() {
        return "[" + getBeginLine() + "," + getBeginColumn() + " " + System.identityHashCode(this) + " "
                + getSimpleName() + "]";
    }

    protected static final String eol = System.getProperty("line.separator", "\n");

    public Expansion getNestedExpansion() {
        return null;
    }

    public String getInternalName() {
        return internalName;
    }

    public void setInternalName(String internalName) {
        this.internalName = internalName;
    }

    public boolean getIsRegexp() {
        return (this instanceof RegularExpression);
    }
    
    
    public Object getParentObject() {
        return parentObject != null ? parentObject : getParent();
    }
    
    public void setParentObject(Object parentObject) {
        this.parentObject = parentObject;
    }
    
    public TreeBuildingAnnotation getTreeNodeBehavior() {
        if (treeNodeBehavior == null) {
            if (this.parentObject instanceof ParserProduction) {
                return ((ParserProduction) parentObject).getTreeBuildingAnnotation();
            }
        }
        return treeNodeBehavior;
    }

    public void setTreeNodeBehavior(TreeBuildingAnnotation treeNodeBehavior) {
        if (getGrammar().getOptions().getTreeBuildingEnabled()) {
            this.treeNodeBehavior = treeNodeBehavior;
            if (treeNodeBehavior != null) {
                getGrammar().addNodeType(treeNodeBehavior.getNodeName());
            }
        }
    }

    public void setLookahead(Lookahead lookahead) {
    	this.lookahead = lookahead;
    }

    public Lookahead getLookahead() {
    	return lookahead;
    }

    public void setForced(boolean forced) {this.forced = forced;}

    public boolean isForced() {return this.forced;}
    
}
