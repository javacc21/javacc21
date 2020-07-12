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

import java.util.ArrayList;
import java.util.BitSet;
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
    
    public boolean getRequiresScanAhead() {
        Lookahead la = getLookahead();
        return la != null && la.getRequiresScanAhead();
    }
    
    public Expansion getLookaheadExpansion() {
        Lookahead la = getLookahead();
        return la == null ? this : la.getLookaheadExpansion();
    }

    public boolean getHasSyntacticLookahead() {
       return getLookaheadExpansion() != this;
    }
    
    public boolean isAlwaysSuccessful() {
        Lookahead la = getLookahead();
        return la == null ? this.isPossiblyEmpty() : la.isAlwaysSuccessful();
    }
    
    public int getLookaheadAmount() {
        Lookahead la = getLookahead();
        return la == null ? 1 : la.getAmount();
    }
    
    public boolean getHasSemanticLookahead() {
        Lookahead la = getLookahead();
        return la != null && la.hasSemanticLookahead();
    }
    
    public Expression getSemanticLookahead() {
        return getHasSemanticLookahead() ? getLookahead().getSemanticLookahead() : null;
    }
    
    public List<String> getFirstSetTokenNames() {
        return tokenSetNames(getFirstSet());
    }
    
    public List<String> getFinalSetTokenNames() {
        return tokenSetNames(getFinalSet());
    }
    
    public List<String> getFollowSetTokenNames() {
        return tokenSetNames(getFollowSet());
    }
    
    private List<String> tokenSetNames(BitSet set) {
        int tokenCount = getGrammar().getLexerData().getTokenCount();
        List<String> result = new ArrayList<>(tokenCount);
        for (int i=0; i<tokenCount; i++) {
            if (set.get(i)) {
                result.add(getGrammar().getLexerData().getTokenName(i));
            }
        }
        return result;
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
                scanRoutineName = "scan$" + prod.getName();
            } else {
                scanRoutineName = getGrammar().generateUniqueIdentifier("scan$", this);
            }
            if (this.getParent() instanceof Lookahead) {
                this.scanRoutineName = scanRoutineName.replace("scan", "check");
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
     * @return whether this expansion is (possibly) at the very end of a grammatical production
     */
    public boolean isAtProductionEnd() {
        Node parent = getParent();
        if (parent instanceof BNFProduction) {
            return true;
        }
        if (parent instanceof ExpansionSequence) {
            ExpansionSequence seq = (ExpansionSequence) parent;
            List<Expansion> siblings = seq.getUnits();
            int index = siblings.indexOf(this) + 1;
            for (int i= index; i<siblings.size();i++) {
                if (!siblings.get(i).isPossiblyEmpty()) {
                    return false;
                }
            }
        }
        return ((Expansion) parent).isAtProductionEnd();
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
    
    abstract public int minimumSize(int oldMin); 

    abstract public int getMaximumSize();
     
     /**
      * @return Does this expansion resolve to a fixed sequence of Tokens?
      */
     abstract public boolean isConcrete();
}
