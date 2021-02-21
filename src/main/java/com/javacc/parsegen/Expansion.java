/* Copyright (c) 2008-2021 Jonathan Revusky, revusky@javacc.com
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

    private TreeBuildingAnnotation treeNodeBehavior;

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

    private String scanRoutineName, firstSetVarName;

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

    public boolean isAtChoicePoint() {
        //Node parent = getNonSuperfluousParent();
        Node parent = getParent();
        return parent instanceof ExpansionChoice
            || parent instanceof OneOrMore
            || parent instanceof ZeroOrMore
            || parent instanceof ZeroOrOne
            || parent instanceof BNFProduction;
    }

    /**
     * @return the first ancestor that is not (directly) inside
     * superfluous parentheses. (Yes, this is a bit hairy and I'm not 100% sure it's correct!) 
     */

    public Node getNonSuperfluousParent() {
        Node parent = getParent();
        if (!(parent instanceof Expansion) || !((Expansion) parent).superfluousParentheses()) {
            return parent;
        }
        ExpansionSequence grandparent = (ExpansionSequence) parent.getParent();
        return grandparent.getNonSuperfluousParent();
    }

    /**
     * @return the lexical state to switch into to parse this expansion.
     * At the moment this can only be specified at the production level.
     */
    public String getSpecifiedLexicalState() {
        Node parent = getParent();
        if (parent instanceof BNFProduction) {
            return ((BNFProduction) parent).getLexicalState();
        }
        return null;
    }

    private CodeBlock customErrorRecoveryBlock;

    public CodeBlock getCustomErrorRecoveryBlock() {
        return customErrorRecoveryBlock;
    }

    public void setCustomErrorRecoveryBlock(CodeBlock customErrorRecoveryBlock) {
        this.customErrorRecoveryBlock = customErrorRecoveryBlock;
    }

    /**
     * Is this expansion superfluous parentheses?
     */
    public final boolean superfluousParentheses() {
        return this.getClass() == ExpansionWithParentheses.class 
               && firstChildOfType(ExpansionSequence.class) != null;
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

    public Lookahead getLookahead() {
        return null;
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
        if (!this.isAtChoicePoint())
            return false;
        if (getHasSeparateSyntacticLookahead())
            return false;
        if (this.isAlwaysSuccessful())
            return false;
        if (getHasExplicitNumericalLookahead() && getLookaheadAmount() <=1 )
            return false;
        if (getHasScanLimit()) {
            return true;
        }
        if (getMaximumSize() <=1) {
            return false;
        }
        Lookahead la = getLookahead();
        return la != null && la.getAmount()>1;
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
        if (getHasSeparateSyntacticLookahead() || getHasLookBehind() || getSpecifiedLexicalState()!=null) {
            return true;
        }
        if (getHasImplicitSyntacticLookahead() && !isSingleToken()) {
            return true;
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
        return false; //Only an ExpansionSequence can have a scan limit.
    }

    public boolean getHasInnerScanLimit() {
        return false;
    }

    public Expansion getUpToExpansion() {
        Lookahead la = getLookahead();
        return la == null ? null : la.getUpToExpansion();
    }

    /**
     * @return whether this expansion is at the very end of the root expansion that
     *         contains it.
     *//*
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
    }*/

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
        String result = getFirstSetVarName();
        if (result.startsWith("first_set$")) {
            return result.replaceFirst("first", "final");
        }
        return result.replace("_FIRST_SET", "_FINAL_SET");
    }

    public String getFollowSetVarName() {
        String result = getFirstSetVarName();
        if (result.startsWith("first_set$")) {
            return result.replaceFirst("first", "follow");
        } 
        return result.replace("_FIRST_SET", "_FOLLOW_SET");
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
        return getScanRoutineName().replace("check$", "scan$");
    }

    public String getRecoverMethodName() {
        return getScanRoutineName().replace("check$", "recover$");
    }

    public int getFinalSetSize() {
        return getFinalSet().cardinality();
    }

    abstract public TokenSet getFirstSet();

    abstract public TokenSet getFinalSet();

    public boolean getHasFullFollowSet() {
        return !getFollowSet().isIncomplete();
    }

    public boolean getSpecifiesLexicalStateSwitch() {
        return getSpecifiedLexicalState() != null;
    };

    /**
     * @return Can this expansion be matched by the empty string.
     */
    abstract public boolean isPossiblyEmpty();

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
    /**
     * @return the minimum number of tokens that this expansion 
     * consumes.
     */
    abstract public int getMinimumSize();

    /**
     * @return the maximum number of tokens that this expansion consumes.
     */
    abstract public int getMaximumSize();

    /**
     * @return Does this expansion resolve to a fixed sequence of Tokens?
     */
    abstract public boolean isConcrete();

    private Expansion getPreceding() {
        Node parent = getParent();
        if (parent instanceof ExpansionSequence) {
            List<Expansion> siblings = parent.childrenOfType(Expansion.class);
            int index = siblings.indexOf(this);
            while (index >0) {
                Expansion exp = siblings.get(index-1);
                if (exp.getMaximumSize()>0) {
                    return exp;
                }
                index--;
            }
        }
        return null;
    }

    public Expansion getFollowingExpansion() {
        Node parent = getParent();
        if (parent instanceof ExpansionSequence) {
            List<Expansion> siblings = parent.childrenOfType(Expansion.class);
            int index = siblings.indexOf(this);
            if (index < siblings.size()-1) return siblings.get(index+1);
        }
        if (parent instanceof Expansion) {
            return ((Expansion)parent).getFollowingExpansion();
        }
        return null;
    }

    public TokenSet getFollowSet() {
        TokenSet result = new TokenSet(getGrammar());
        Expansion following = this;
        do {
            following = following.getFollowingExpansion();
            if (following == null) {
                result.setIncomplete(true);
                break;
            }
            result.or(following.getFirstSet());
        } while (following.isPossiblyEmpty());
        if (this instanceof ZeroOrMore || this instanceof OneOrMore) {
            result.or(this.getFirstSet());
        }
        return result;
    }

    public Boolean isBeforeLexicalStateSwitch() {
        // We return a null if we don't have full info.
        Expansion following = this;
        do {
            following = following.getFollowingExpansion();
            if (following == null) return null;
            if (following.getSpecifiesLexicalStateSwitch()) return true;
        } while (following.isPossiblyEmpty());
        return false;
    }

    public boolean getRequiresRecoverMethod() {
        if (isInsideLookahead()) {
            return false;
        }
        if (isTolerantParsing() || getParent() instanceof BNFProduction) {
            return true;
        }
        Expansion preceding = getPreceding();
        return preceding != null && preceding.isTolerantParsing() && !(preceding instanceof RegularExpression);
    }
}
