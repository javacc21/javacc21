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

package com.javacc.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.javacc.Grammar;
import com.javacc.parser.BaseNode;
import com.javacc.parser.Node;
import com.javacc.parser.tree.*;

/**
 * Describes expansions - entities that may occur on the right hand sides of
 * productions. This is the abstract base class of a bunch of other more specific
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

    public final BNFProduction getContainingProduction() {
        return firstAncestorOfType(BNFProduction.class);
    }

    private String scanRoutineName, firstSetVarName;

    public String getLabel() {
        return label;
    }

    final boolean hasLabel() {
        return label.length() > 0;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    private boolean tolerantParsing;

    /**
     * If we hit a parsing error in this expansion, do we try to recover? This is
     * only used in fault-tolerant mode, of course!
     */
    public boolean isTolerantParsing() {
        return tolerantParsing;
    }

    public void setTolerantParsing(boolean tolerantParsing) {
        this.tolerantParsing = tolerantParsing;
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
        if (getGrammar().getTreeBuildingEnabled()) {
            this.treeNodeBehavior = treeNodeBehavior;
            if (treeNodeBehavior != null) {
                getGrammar().addNodeType(null, treeNodeBehavior.getNodeName());
            }
        }
    }

    /**
     * This method is a bit hairy because of the need to deal with 
     * superfluous parentheses.
     * @return Is this expansion at a choice point?
     */
    public final boolean isAtChoicePoint() {
        if (!(this instanceof ExpansionChoice || this instanceof ExpansionSequence)) return false;
        Node parent = getParent();
        if (parent instanceof ExpansionChoice 
            || parent instanceof OneOrMore 
            || parent instanceof ZeroOrMore
            || parent instanceof ZeroOrOne 
            || parent instanceof BNFProduction) return true;
        if (!(parent instanceof ExpansionWithParentheses)) {
            return false;
        }
        ExpansionSequence grandparent = (ExpansionSequence) parent.getParent();
        return grandparent.getUnits().get(0) == parent && grandparent.isAtChoicePoint();
    }

    /**
     * @return the first ancestor that is not (directly) inside superfluous
     *         parentheses. (Yes, this is a bit hairy and I'm not 100% sure it's
     *         correct!) I really need to take a good look at all this handling of
     *         expansions inside parentheses.
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
     */
    public String getSpecifiedLexicalState() {
        Node parent = getParent();
        if (parent instanceof BNFProduction) {
            return ((BNFProduction) parent).getLexicalState();
        }
        return null;
    }

    public TokenActivation getTokenActivation() {
        return firstChildOfType(TokenActivation.class);
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
        return this.getClass() == ExpansionWithParentheses.class && firstChildOfType(ExpansionSequence.class) != null;
    }
 
    public boolean isInsideLookahead() {
        return firstAncestorOfType(Lookahead.class) != null;
    }

    public boolean getHasExplicitNumericalLookahead() {
        return false;
    }

    /**
     * Does this expansion have a separate lookahead expansion?
     */
    public boolean getHasSeparateSyntacticLookahead() {
        return false; // can only be true for an ExpansionSequence
    }

    private boolean scanLimit;
    private int scanLimitPlus;

    public final boolean isScanLimit() {
        return scanLimit;
    }

    public final void setScanLimit(boolean scanLimit) {
        this.scanLimit = scanLimit;
    }

    public final int getScanLimitPlus() {
        return scanLimitPlus;
    }

    public final void setScanLimitPlus(int scanLimitPlus) {
        this.scanLimitPlus = scanLimitPlus;

    }

    public final boolean getRequiresPredicateMethod() {
        if (isInsideLookahead() || !isAtChoicePoint()) {
            return false;
        }
        if (getLookahead() != null) {
            return true;
        }
        if (getHasImplicitSyntacticLookahead()) {
            return true;
        }
        if (getHasTokenActivation() || getSpecifiedLexicalState() != null) {
            return true;
        }
        if (getSpecifiesLexicalStateSwitch()) {
            return true;
        }
        return getHasGlobalSemanticActions();
    }

    public Lookahead getLookahead() {return null;}

    public Expansion getLookaheadExpansion() { return this; }

    boolean getHasImplicitSyntacticLookahead() {return false;}

    final boolean getHasGlobalSemanticActions() {
        List<CodeBlock> blocks = descendants(CodeBlock.class, cb -> cb.isAppliesInLookahead());
        return !blocks.isEmpty();
    }

    public int getLookaheadAmount() {
         return 1;
    }

    public boolean getHasSemanticLookahead() {
        return false;
    }

    boolean getHasScanLimit() {
        return false; // Only an ExpansionSequence or a NonTerminal can have a scan limit.
    }

    public Expression getSemanticLookahead() {
        return null;
    }

    public boolean getHasLookBehind() {
        return getLookBehind() != null;
    }

    public LookBehind getLookBehind() {
        return null;
    }

    public boolean isNegated() {
        return false;
    }

    public String getFirstSetVarName() {
        if (firstSetVarName == null) {
            if (this.getParent() instanceof BNFProduction) {
                firstSetVarName = ((BNFProduction) getParent()).getFirstSetVarName();
            } else {
                Grammar g = getGrammar();
                String prefix = g.generateIdentifierPrefix("first_set");

                firstSetVarName = g.generateUniqueIdentifier(prefix, this);
            }
        }
        return firstSetVarName;
    }

    public String getFinalSetVarName() {
        String result = getFirstSetVarName();
        String prefix = getGrammar().generateIdentifierPrefix("first_set");

        if (result.startsWith(prefix)) {
            return result.replaceFirst("first", "final");
        }
        return result.replace("_FIRST_SET", "_FINAL_SET");
    }

    public String getFollowSetVarName() {
        String result = getFirstSetVarName();
        String prefix = getGrammar().generateIdentifierPrefix("first_set");

        if (result.startsWith(prefix)) {
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
                Grammar g = getGrammar();
                String prefix = g.generateIdentifierPrefix("check");

                scanRoutineName = g.generateUniqueIdentifier(prefix, this);
            }
        }
        return scanRoutineName;
    }

    public String getPredicateMethodName() {
        Grammar g = getGrammar();
        String checkPrefix = g.generateIdentifierPrefix("check");
        String scanPrefix = g.generateIdentifierPrefix("scan");
        return getScanRoutineName().replace(checkPrefix, scanPrefix);
    }

    public String getRecoverMethodName() {
        Grammar g = getGrammar();
        String checkPrefix = g.generateIdentifierPrefix("check");
        String recoverPrefix = g.generateIdentifierPrefix("recover");
        return getScanRoutineName().replace(checkPrefix, recoverPrefix);
    }

    public String getRecoverToMethodName() {
        Grammar g = getGrammar();
        String checkPrefix = g.generateIdentifierPrefix("check");
        String recoverToPrefix = g.generateIdentifierPrefix("recover_to");
        return getScanRoutineName().replace(checkPrefix, recoverToPrefix);
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
        return false;
    };

    public boolean getHasTokenActivation() {
        return firstChildOfType(TokenActivation.class) != null;
    }

    public boolean isSingleToken() {
        // Uncomment the following line to turn off this optimization.
        // if (true) return false;
        if (isPossiblyEmpty() || getMaximumSize() > 1 || getHasScanLimit() || getSpecifiesLexicalStateSwitch())
            return false;
        if (getLookahead() != null) 
            return false;
        if (firstDescendantOfType(Failure.class) != null || firstDescendantOfType(Assertion.class) != null || firstDescendantOfType(TokenActivation.class) != null)
            return false;
        if (!descendants(Expansion.class, exp->exp.getSpecifiesLexicalStateSwitch()).isEmpty())
            return false;
        if (!descendants(Expansion.class, exp->exp.getHasLookBehind()).isEmpty())
            return false;
        if (hasNestedSemanticLookahead()) 
            return false;
        if (getHasGlobalSemanticActions())
            return false;
        return true;
    }

    public final boolean hasNestedSemanticLookahead() {
        for (ExpansionSequence expansion : descendants(ExpansionSequence.class)) {
            if (expansion.getHasSemanticLookahead() && expansion.getLookahead().isSemanticLookaheadNested()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return Can this expansion be matched by the empty string.
     */
    abstract public boolean isPossiblyEmpty();

    abstract public boolean isAlwaysSuccessful();

    /**
     * @return the minimum number of tokens that this expansion consumes.
     */
    abstract public int getMinimumSize();

    /**
     * @return the maximum number of tokens that this expansion consumes.
     */
    abstract public int getMaximumSize();

    private Expansion getPreceding() {
        Node parent = getParent();
        if (parent instanceof ExpansionSequence) {
            List<Expansion> siblings = parent.childrenOfType(Expansion.class);
            int index = siblings.indexOf(this);
            while (index > 0) {
                Expansion exp = siblings.get(index - 1);
                if (exp.getMaximumSize() > 0) {
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
            if (index < siblings.size() - 1)
                return siblings.get(index + 1);
        }
        if (parent instanceof Expansion) {
            return ((Expansion) parent).getFollowingExpansion();
        }
        return null;
    }

    public TokenSet getFollowSet() {
        TokenSet result = new TokenSet(getGrammar());
        if (isAtEndOfLoop()) {
            result.or(firstLoopAncestor().getFirstSet());
        }
        Expansion following = this;
        do {
            following = following.getFollowingExpansion();
            if (following == null) {
                result.setIncomplete(true);
                break;
            }
            result.or(following.getFirstSet());
        } while (following.isPossiblyEmpty());
        return result;
    }

    private boolean isAtEndOfLoop() {
        if (this instanceof ZeroOrMore || this instanceof OneOrMore)
            return true;
        Node parent = getParent();
        if (parent instanceof ExpansionSequence) {
            List<Expansion> siblings = parent.childrenOfType(Expansion.class);
            int index = siblings.indexOf(this);
            for (int i = index + 1; i < siblings.size(); i++) {
                if (!siblings.get(i).isPossiblyEmpty())
                    return false;
            }
        }
        if (parent instanceof Expansion) {
            return ((Expansion) parent).isAtEndOfLoop();
        }
        return false;
    }

    private Expansion firstLoopAncestor() {
        Expansion result = this;
        while (!(result instanceof ZeroOrMore || result instanceof OneOrMore)) {
            Node parent = result.getParent();
            if (parent instanceof Expansion)
                result = (Expansion) parent;
            else
                return null;
        }
        return result;
    }

    public Boolean isBeforeLexicalStateSwitch() {
        // We return a null if we don't have full info.
        Expansion following = this;
        do {
            following = following.getFollowingExpansion();
            if (following == null)
                return null;
            if (following.getSpecifiesLexicalStateSwitch())
                return true;
        } while (following.isPossiblyEmpty());
        return false;
    }

    public boolean getRequiresRecoverMethod() {
        if (isInsideLookahead()) {
            return false;
        }
        if (getContainingProduction() != null && getContainingProduction().isOnlyForLookahead()) {
            return false;
        }
        if (isTolerantParsing() || getParent() instanceof BNFProduction) {
            return true;
        }
        Expansion preceding = getPreceding();
        return preceding != null && preceding.isTolerantParsing() && !(preceding instanceof RegularExpression);
    }

    /**
     * Whether this expansion can start with a given production
     * This is the default implementation that always returns false.
     */
    public boolean potentiallyStartsWith(String productionName, Set<String> alreadyVisited) {
        return false;
    }

    final public boolean potentiallyStartsWith(String productionName) {
        return potentiallyStartsWith(productionName, new HashSet<>());
    }

    /*
     * This section indicates whether this expansion has a child name associated with it,
     * and whether that relates to a single value or a list of values.
     */
    private String childName;
    private boolean multipleChildren;

    public String getChildName() { return childName; }
    public void setChildName(String name) { childName = name; }
    public boolean isMultipleChildren() { return multipleChildren; }
    public void setMultipleChildren(boolean multiple) { multipleChildren = multiple; }
}
