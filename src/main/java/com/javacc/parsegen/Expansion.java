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

package com.javacc.parsegen;

import com.javacc.Grammar;
import com.javacc.lexgen.RegularExpression;
import com.javacc.lexgen.TokenSet;
import com.javacc.parser.BaseNode;
import com.javacc.parser.tree.BNFProduction;
import com.javacc.parser.tree.TreeBuildingAnnotation;


/**
 * Describes expansions - entities that may occur on the right hand sides of
 * productions. This is the base class of a bunch of other more specific
 * classes.
 */

abstract public class Expansion extends BaseNode {

    private boolean forced;

    private TreeBuildingAnnotation treeNodeBehavior;

    private Lookahead lookahead;
    
    private String label = "";  
    
    private int phase3LookaheadAmount; 
    
    void setPhase3LookaheadAmount(int phase3LookaheadAmount) {
    	this.phase3LookaheadAmount = phase3LookaheadAmount;
    }
/**
 * The number of Tokens that can be consumed in a phase3 Lookahead routine    
 * (assuming that this Expansion needs phase3 lookahead)
 */
    public int getPhase3LookaheadAmount() {
    	return this.phase3LookaheadAmount;
    }
    
    
    protected TokenSet firstSet, finalSet;

    public int getIndex() {
    	return parent.indexOf(this);
	}

	public Expansion(Grammar grammar) {
        setGrammar(grammar);
    }

    public Expansion() {}

    private int ordinal= -1; // REVISIT
    
    long myGeneration = 0; //REVISIT
    
    private String phase2RoutineName, phase3RoutineName;
    
    public String getLabel() {
    	return label;
    }
    
    public final boolean hasLabel() {
        return label.length() > 0;
    }
    
    public void setLabel(String label) {
    	this.label = label;
    }
    
    private String getSimpleName() {
        String name = getClass().getName();
        return name.substring(name.lastIndexOf(".") + 1); // strip the package name
    }
    
    public String toString() {
    	return "[" + getSimpleName() + " expansion on line " + getBeginLine() + ", " + getBeginColumn() + "of " + this.getInputSource() + "]";
    }

    protected static final String eol = System.getProperty("line.separator", "\n");

    public Expansion getNestedExpansion() {
        return null;
    }

    public boolean getIsRegexp() {
        return (this instanceof RegularExpression);
    }
    
    public TreeBuildingAnnotation getTreeNodeBehavior() {
        if (treeNodeBehavior == null) {
            if (this.getParent() instanceof BNFProduction) {
                return ((BNFProduction) getParent()).getTreeNodeBehavior();
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

    public  void setLookahead(Lookahead lookahead) {
    	this.lookahead = lookahead;
    }

    public Lookahead getLookahead() {
    	return lookahead;
    }

    public void setForced(boolean forced) {this.forced = forced;}

    public boolean getForced() {return this.forced;}
    
    public int getOrdinal() {return this.ordinal;}
    

    public String getPhase2RoutineName() {
        if (phase2RoutineName == null) {
            phase2RoutineName = getGrammar().generateUniqueIdentifier("lookahead$", this);
        }
        return phase2RoutineName;
    }
    
    public String getPhase3RoutineName() {
        if (phase3RoutineName == null) {
            phase3RoutineName = getGrammar().generateUniqueIdentifier("phase3$", this);
        }
        return phase3RoutineName;
    }
   
    public int getEndSetSize() {
	     return getFinalSet().cardinality();
    }
    
    abstract public TokenSet getFirstSet();
    
    abstract public TokenSet getFinalSet();
    
    abstract public boolean isPossiblyEmpty(); 
    
    abstract public boolean requiresPhase2Routine();
    
    /*
     * Returns the minimum number of tokens that can parse to this expansion.
     */
    final public int getMinimumSize() {
    	return minimumSize(Integer.MAX_VALUE);
    }
    
     abstract public int minimumSize(int oldMin); 
    
    
}