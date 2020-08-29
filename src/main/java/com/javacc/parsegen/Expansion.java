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

import java.util.List;

import com.javacc.Grammar;
import com.javacc.lexgen.RegularExpression;
import com.javacc.lexgen.TokenSet;
import com.javacc.parser.BaseNode;
import com.javacc.parser.Node;
import com.javacc.parser.tree.*;


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
    
    private int maxScanAhead; 
    
    void setMaxScanAhead(int maxScanAhead) {
    	this.maxScanAhead = maxScanAhead;
    }
/**
 * The number of Tokens that can be consumed in a scanahead routine    
 * (assuming that we need a scanAhead routine for this Expansion)
 */
    public int getMaxScanAhead() {
    	return this.maxScanAhead;
    }
    
    
    protected TokenSet firstSet;

    public int getIndex() {
    	return parent.indexOf(this);
	}

	public Expansion(Grammar grammar) {
        setGrammar(grammar);
    }

    public Expansion() {}

    public BNFProduction getContainingProduction() {
        return firstAncestorOfType(BNFProduction.class);
    }

    long myGeneration = 0; //REVISIT
    
    private String scanRoutineName, firstSetVarName, finalSetVarName, followSetVarName;
    
    public String getLabel() {
    	return label;
    }
    
    public final boolean hasLabel() {
        return label.length() > 0;
    }
    
    public void setLabel(String label) {
    	this.label = label;
    }
    
    public String toString() {
        String result = "[" + getSimpleName() + " on line " + getBeginLine() + ", column " + getBeginColumn();
        String inputSource = getInputSource();
        if (inputSource != null) {
            result += " of ";
            result += inputSource;
        }
        return result + "]";
    }

    public Expansion getNestedExpansion() {
        return null;
    }

    public boolean getIsRegexp() {
        return this instanceof RegularExpression;
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
    
    public boolean hasExplicitLookahead() {
        return getLookahead() != null;
    }

    private boolean scanLimit;
    private int scanLimitPlus;

    public boolean isScanLimit() {
        return scanLimit;
    }

    public void setScanLimit(boolean scanLimit) {
        this.scanLimit = scanLimit;
    }

    public int getScanLimitPlus() {
        return scanLimitPlus;
    }

    public void setScanLimitPlus(int scanLimitPlus) {
        this.scanLimitPlus = scanLimitPlus;

    }
    
    public boolean getRequiresScanAhead() {
        Lookahead la = getLookahead();
        if (la != null && la.getRequiresScanAhead()) return true;
        return getHasGlobalSemanticActions();
    } 

    public TokenSet getLookaheadFirstSet() {
        Lookahead la = getLookahead();
        return la != null ? la.getFirstSet() : getFirstSet();
    }
    
    public Expansion getLookaheadExpansion() {
        Lookahead la = getLookahead();
        Expansion exp = la == null ? null : la.getNestedExpansion();
        return exp != null ? exp : this;
    }

    public boolean getHasSyntacticLookahead() {
       return getLookaheadExpansion() != this;
    }
    
    public boolean isAlwaysSuccessful() {
        if (getHasSemanticLookahead() || getHasLookBehind() || !isPossiblyEmpty()) {
            return false;
        }
        if (firstChildOfType(Failure.class) != null) {
            return false;
        }
        Lookahead la = getLookahead();
        return la == null || la.getNestedExpansion() == null || la.getNestedExpansion().isPossiblyEmpty();
    }

    public boolean getHasGlobalSemanticActions() {
        List<CodeBlock> blocks = descendants(CodeBlock.class, cb->cb.getAppliesInLookahead());
        return !blocks.isEmpty();
    }
    
    public int getLookaheadAmount() {
        Lookahead la = getLookahead();
        if (la != null) return la.getAmount();
        return getRequiresScanAhead() ? Integer.MAX_VALUE : 1; // A bit kludgy, REVISIT
    }
    
    public boolean getHasSemanticLookahead() {
        Lookahead la = getLookahead();
        return la != null && la.hasSemanticLookahead();
    }

    public Expansion getUpToExpansion() {
        Lookahead la = getLookahead();
        return la == null ? null : la.getUpToExpansion();
    }
    
    public Expression getSemanticLookahead() {
        return getHasSemanticLookahead() ? getLookahead().getSemanticLookahead() : null;
    }

    public boolean getHasLookBehind() {
        return getLookahead() != null && getLookahead().getLookBehind() != null;
    }

    public LookBehind getLookBehind() {
        return getLookahead() != null ? getLookahead().getLookBehind() : null;
    }
    
    public boolean isNegated() {
        return getLookahead() != null && getLookahead().isNegated();
    }
    
    public void setForced(boolean forced) {this.forced = forced;}

    public boolean getForced() {return this.forced;}
    
    public String getFirstSetVarName() {
        if (firstSetVarName == null) {
            if (this.getParent() instanceof BNFProduction) {
                firstSetVarName = ((BNFProduction) getParent()).getFirstSetVarName();
            } else {
                firstSetVarName = getGrammar().generateUniqueIdentifier("first_set$", this);
            }
        }
        return firstSetVarName;
    }
    
    public String getFinalSetVarName() {
         if (finalSetVarName == null) {
             finalSetVarName = getFirstSetVarName();
             if (finalSetVarName.startsWith("first_set$")) {
                 finalSetVarName = finalSetVarName.replaceFirst("first", "final");
             } else {
                 finalSetVarName = finalSetVarName.replace("_FIRST_SET", "_FINAL_SET");
             }
         }
         return finalSetVarName;
    }
    
    public String getFollowSetVarName() {
        if (followSetVarName == null) {
            followSetVarName = getGrammar().generateUniqueIdentifier("follow_set$", this);
        }
        return followSetVarName;
    }

    public String getScanRoutineName() {
        if (scanRoutineName == null) {
            if (this.getParent() instanceof BNFProduction) {
                BNFProduction prod = (BNFProduction) getParent();
                scanRoutineName = "check$" + prod.getName();
            } else {
                scanRoutineName = getGrammar().generateUniqueIdentifier("check$", this);
            }
        }
        return scanRoutineName;
    }
   
    public int getFinalSetSize() {
	     return getFinalSet().cardinality();
    }
    
    abstract public TokenSet getFirstSet();
    
    abstract public TokenSet getFinalSet();
    
    public TokenSet getFollowSet() {
         Node parent = getParent();
         if (parent instanceof ExpansionChoice) {
             return ((ExpansionChoice) parent).getFollowSet();
         }
         if (parent instanceof ExpansionSequence) {
             ExpansionSequence sequence = (ExpansionSequence) parent;
             List<Expansion> siblings = sequence.getUnits();
             int index = siblings.indexOf(this) +1;
             TokenSet result = new TokenSet(getGrammar());
             boolean atEnd = false;
             for (int i = index; i<siblings.size(); i++) {
                  result.or(siblings.get(i).getFollowSet());
                  if (!siblings.get(i).isPossiblyEmpty()) {
                      atEnd = true; 
                      break;
                  }
             }
             if (!atEnd) {
                  result.or(sequence.getFollowSet());
             }
             return result;
         }
         if (parent instanceof OneOrMore || parent instanceof ZeroOrMore) {
             TokenSet result = new TokenSet(getGrammar());
             result.or(this.getFinalSet());
             result.or(((Expansion)parent).getFollowSet());
             return result;
         }
         if (parent instanceof Expansion) {
             return ((Expansion) parent).getFollowSet();
         }
         // REVISIT.
         return new TokenSet(getGrammar());
    }
    
    /**
     * @return whether this expansion is at the very end of 
     * the root expansion that contains it.
     */
    public boolean isAtEnd() {
        Node parent = getParent();
        if (!(parent instanceof Expansion)) {
            return true;
        }
        if (parent instanceof ExpansionSequence) {
            ExpansionSequence seq = (ExpansionSequence) parent;
            if (seq.hasExplicitLookahead()) {
                return true;
            }
            List<Expansion> siblings = seq.getUnits();
            for (int i = siblings.indexOf(this) +1; i< siblings.size();i++) {
                if (!siblings.get(i).isAlwaysSuccessful()) return false;
            }
        }
        return ((Expansion) parent).isAtEnd();
    }
    
    /**
     * @return Can this expansion be matched by the empty string.
     */
    abstract public boolean isPossiblyEmpty(); 
    
    /**
     * Returns the minimum number of tokens that can parse to this expansion.
     */
    final public int getMinimumSize() {
    	return minimumSize(Integer.MAX_VALUE);
    }

    /**
     * @return whether this Expansion is always matched by exactly one token
     */
    public final boolean isSingleToken() {
        if (firstChildOfType(Failure.class)!=null) return false; // Maybe a bit kludgy. REVISIT.
        return !isPossiblyEmpty() && getMaximumSize() == 1;
    }
    
    abstract public int minimumSize(int oldMin); 

    abstract public int getMaximumSize();
     
     /**
      * @return Does this expansion resolve to a fixed sequence of Tokens?
      */
     abstract public boolean isConcrete();

     public boolean masks(Expansion other) {
         TokenSet firstSet = this.getFirstSet();
         TokenSet otherSet = other.getFirstSet();
         TokenSet set = new TokenSet(this.getGrammar());
         set.or(firstSet);
         set.andNot(otherSet);
         return set.cardinality() == 0;
     }
 
     public TokenSet overlap(Expansion other) {
         TokenSet firstSet = this.getFirstSet();
         TokenSet otherSet = other.getFirstSet();
         TokenSet result = new TokenSet(getGrammar());
         result.or(firstSet);
         result.and(otherSet);
         return result;
     }
}
