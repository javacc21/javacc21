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
import com.javacc.parser.BaseNode;
import com.javacc.parser.Node;
import com.javacc.parser.tree.*;

/**
 * Describes expansions - entities that may occur on the right hand sides of
 * productions. This is the base class of a bunch of other more specific
 * classes.
 */

abstract public class Expansion extends BaseNode {

    /**
     * Marker interface to indicate a choice point
     */
    public interface ChoicePoint extends Node {
    }

    private TreeBuildingAnnotation treeNodeBehavior;

    private Lookahead lookahead;

    private String label = "";

    protected TokenSet firstSet;

    public int getIndex() {
        return parent.indexOf(this);
    }

    public Expansion(Grammar grammar) {
        setGrammar(grammar);
    }

    public Expansion() {
    }

    public BNFProduction getContainingProduction() {
        return firstAncestorOfType(BNFProduction.class);
    }

    private String scanRoutineName, predicateMethodName, firstSetVarName, finalSetVarName, followSetVarName;

    public String getLabel() {
        return label;
    }

    public final boolean hasLabel() {
        return label.length() > 0;
    }

    public void setLabel(String label) {
        this.label = label;
    }


    private boolean tolerantParsing;

    /**
     * If we hit a parsing error in this expansion, do we 
     * try to recover? This is only used in fault-tolerant mode, of course!
     */
    public boolean isTolerantParsing() {return tolerantParsing;}

    public void setTolerantParsing(boolean tolerantParsing) {this.tolerantParsing = tolerantParsing;}

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
        if (getGrammar().getTreeBuildingEnabled()) {
            this.treeNodeBehavior = treeNodeBehavior;
            if (treeNodeBehavior != null) {
                getGrammar().addNodeType(null, treeNodeBehavior.getNodeName());
            }
        }
    }
/*
    public boolean isAtChoicePoint() {
        Node parent = getParent();
        if (parent instanceof ChoicePoint) return true;
        if (parent instanceof BNFProduction) return true;
        if (beginsSequence() && parent.getParent() instanceof BNFProduction) return true;
        return false;
        // The expansion directly inside a BNFProduction
        // should also be treated as a choice point, I guess,
        // since a NonTerminal that represents it may
        // itself be at a choice point.
    }*/

    public boolean isAtChoicePoint() {
        Node parent = getParent();
        return parent instanceof ChoicePoint || parent instanceof BNFProduction;

    }


    public boolean beginsSequence() {
        if (getParent() instanceof ExpansionSequence) {
            ExpansionSequence seq = (ExpansionSequence) getParent();
            for (Expansion child : seq.childrenOfType(Expansion.class)) {
                if (child == this) return true;
                if (!child.isPossiblyEmpty()) return false;
            }
        }
        return false;
    }

    public boolean isInsideLookahead() {
        return firstAncestorOfType(Lookahead.class) != null;
    }

    public void setLookahead(Lookahead lookahead) {
        this.lookahead = lookahead;
    }

    public Lookahead getLookahead() {
        return lookahead;
    }

    public boolean getHasExplicitLookahead() {
        return getLookahead() != null;
    }

    public boolean getHasExplicitNumericalLookahead() {
        Lookahead la = getLookahead();
        return la != null && la.getHasExplicitNumericalAmount();
    }

    /**
     * Does this expansion have a separate lookahead expansion?
     */

    public boolean getHasSeparateSyntacticLookahead() {
        Lookahead la = getLookahead();
        return la != null && la.getNestedExpansion() != null;
    }

    /**
     * Do we do a syntactic lookahead using this expansion itself as the lookahead
     * expansion?
     */
    public boolean getHasImplicitSyntacticLookahead() {
        if (getHasSeparateSyntacticLookahead())
            return false;
        if (!this.isAtChoicePoint())
            return false;
        if (this.isAlwaysSuccessful())
            return false;
        if (getLookaheadAmount() > 1)
            return true;
        if (getHasScanLimit())
            return true;
        return false;
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
        if (la != null && la.getRequiresScanAhead())
            return true;
        // if (this.getParent() instanceof com.javacc.parser.tree.Assertion) return
        // true;
        return getHasGlobalSemanticActions();
    }

    public final boolean getRequiresPredicateMethod() {
        if (isInsideLookahead() || !isAtChoicePoint()) {
            return false;
        }
        if (getHasSeparateSyntacticLookahead() || getHasLookBehind()) {
            return true;
        }
        if (getHasImplicitSyntacticLookahead() && !isSingleToken()) {
            return true;
        }
        if (this instanceof ExpansionChoice) {
            for (Expansion choice : childrenOfType(Expansion.class)) {
                if (choice.getRequiresPredicateMethod()) return true;
            }
        }
        if (this instanceof ExpansionSequence) {
            for (Expansion exp : childrenOfType(Expansion.class)) {
                if (exp instanceof NonTerminal) {
                    NonTerminal nt = (NonTerminal) exp;
                    exp = nt.getProduction().getExpansion();
                }
                if (exp.getRequiresPredicateMethod()) return true;
                if (!exp.isPossiblyEmpty()) break;
            }
        }
        return getHasGlobalSemanticActions();
    }

    public Expansion getLookaheadExpansion() {
        Lookahead la = getLookahead();
        Expansion exp = la == null ? null : la.getNestedExpansion();
        return exp != null ? exp : this;
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
        List<CodeBlock> blocks = descendants(CodeBlock.class, cb -> cb.isAppliesInLookahead());
        return !blocks.isEmpty();
    }

    public int getLookaheadAmount() {
        Lookahead la = getLookahead();
        if (la != null)
            return la.getAmount();
        return getRequiresScanAhead() ? Integer.MAX_VALUE : 1; // A bit kludgy, REVISIT
    }

    public boolean getHasSemanticLookahead() {
        Lookahead la = getLookahead();
        return la != null && la.hasSemanticLookahead();
    }

    public boolean getHasScanLimit() {
        if (!(this instanceof ExpansionSequence))
            return false;
        for (Expansion sub : childrenOfType(Expansion.class)) {
            if (sub.isScanLimit())
                return true;
        }
        return false;
    }

    public boolean getHasInnerScanLimit() {
        if (!(this instanceof ExpansionSequence))
            return false;
        for (Expansion sub : childrenOfType(Expansion.class)) {
            if (sub instanceof NonTerminal) {
                return ((NonTerminal) sub).getProduction().getHasScanLimit();
            }
            if (sub.getMaximumSize() > 0)
                break;
        }
        return false;
    }

    public Expansion getUpToExpansion() {
        Lookahead la = getLookahead();
        return la == null ? null : la.getUpToExpansion();
    }

    /**
     * @return whether this expansion is at the very end of the root expansion that
     *         contains it.
     */
    public boolean isAtEnd() {
        Node parent = getParent();
        if (!(parent instanceof Expansion)) {
            return true;
        }
        if (parent instanceof ExpansionSequence) {
            ExpansionSequence seq = (ExpansionSequence) parent;
            if (seq.getHasExplicitLookahead()) {
                return true;
            }
            List<Expansion> siblings = seq.getUnits();
            for (int i = siblings.indexOf(this) + 1; i < siblings.size(); i++) {
                if (!siblings.get(i).isAlwaysSuccessful())
                    return false;
            }
        }
        return ((Expansion) parent).isAtEnd();
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
                scanRoutineName = prod.getLookaheadMethodName();
            } else {
                scanRoutineName = getGrammar().generateUniqueIdentifier("check$", this);
            }
        }
        return scanRoutineName;
    }

    public String getPredicateMethodName() {
        if (predicateMethodName == null) {
            predicateMethodName = getScanRoutineName().replace("check$", "scan$");
        }
        return predicateMethodName;
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
            int index = siblings.indexOf(this) + 1;
            TokenSet result = new TokenSet(getGrammar());
            boolean atEnd = false;
            for (int i = index; i < siblings.size(); i++) {
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
            result.or(((Expansion) parent).getFollowSet());
            return result;
        }
        if (parent instanceof Expansion) {
            return ((Expansion) parent).getFollowSet();
        }
        // REVISIT.
        return new TokenSet(getGrammar());
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
        if (firstChildOfType(Failure.class) != null)
            return false; // Maybe a bit kludgy. REVISIT.
        if (getHasScanLimit())
            return false;
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
