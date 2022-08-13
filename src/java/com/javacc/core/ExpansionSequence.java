/* Copyright (c) 2022 Jonathan Revusky, revusky@congocc.org
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

import java.util.*;
import com.javacc.parser.Node;
import com.javacc.parser.tree.*;

public class ExpansionSequence extends Expansion {

    /**
     * @return a List that includes child expansions that are
     *         inside of superfluous parentheses.
     */
    List<Expansion> allUnits() {
        List<Expansion> result = new ArrayList<>();
        for (Expansion unit : childrenOfType(Expansion.class)) {
            result.add(unit);
            if (unit.superfluousParentheses()) {
                result.addAll(unit.firstChildOfType(ExpansionSequence.class).allUnits());
            } 
        }
        return result;
    }

    Expansion firstNonEmpty() {
        for (Expansion unit : childrenOfType(Expansion.class)) {
            if (unit instanceof ExpansionWithParentheses
                    && ((ExpansionWithParentheses) unit).superfluousParentheses()) {
                unit = unit.firstChildOfType(ExpansionSequence.class).firstNonEmpty();
                if (unit != null)
                    return unit;
            } else if (!unit.isPossiblyEmpty())
                return unit;
        }
        return null;
    }


    @Override
    public boolean isAlwaysSuccessful() {
        if (!isPossiblyEmpty()) return false;
        if (allUnits().stream().anyMatch(unit->unit instanceof Assertion || unit instanceof Failure)) return false;
        Lookahead la = getLookahead();
        if (la != null) {
            if (la.getSemanticLookahead() != null || la.getLookBehind() != null || la.getNestedExpansion() != null) {
                return false;
            }
            return la.getAmount() == 0;
        }
        for (Expansion exp : childrenOfType(Expansion.class)) {
            if (!exp.isAlwaysSuccessful()) return false;
        }
        return true;
    }

    public TokenSet getFirstSet() {
        if (firstSet == null) {
            firstSet = new TokenSet(getGrammar());
            for (Expansion child : childrenOfType(Expansion.class)) {
                firstSet.or(child.getFirstSet());
                if (!child.isPossiblyEmpty()) {
                    break;
                }
            }
        }
        return firstSet;
    }

    public TokenSet getFinalSet() {
        TokenSet finalSet = new TokenSet(getGrammar());
        List<Expansion> children = childrenOfType(Expansion.class);
        Collections.reverse(children);
        for (Expansion child : children) {
            finalSet.or(child.getFinalSet());
            if (!child.isPossiblyEmpty()) {
                break;
            }
        }
        return finalSet;
    }

    private boolean getRequiresScanAhead() {
        if (this.getHasExplicitScanLimit()) return true;
        for (Expansion unit : allUnits()) {
            if (unit instanceof NonTerminal) {
                NonTerminal nt = (NonTerminal) unit;
                if (nt.getHasScanLimit())
                    return true;
                if (nt.getProduction().getHasExplicitLookahead())
                    return true;
            }
            if (unit.getMaximumSize()>0) break;
        }
        Lookahead la = getLookahead();
        return la != null && la.getRequiresScanAhead();
    }

    private Lookahead lookahead;

    public void setLookahead(Lookahead lookahead) {
        this.lookahead = lookahead;
    }

    @Override
    public Lookahead getLookahead() {
        if (lookahead != null)
            return lookahead;
        for (Expansion unit : childrenOfType(Expansion.class)) {
            if (unit instanceof NonTerminal) {
                NonTerminal nt = (NonTerminal) unit;
                return nt.getLookahead();
            }
            if (unit.superfluousParentheses()) {
                ExpansionSequence seq = unit.firstChildOfType(ExpansionSequence.class);
                if (seq != null) {
                    return seq.getLookahead();
                }
            }
            if (unit.getMaximumSize() > 0)
                break;
        }
        return null;
    }

    public boolean getHasExplicitLookahead() {
        return lookahead != null;
    }

    public boolean isPossiblyEmpty() {
        for (Expansion e : childrenOfType(Expansion.class)) {
            if (!e.isPossiblyEmpty()) {
                return false;
            }
        }
        return true;
    }

    public int getMinimumSize() {
        int result = 0;
        for (Expansion unit : childrenOfType(Expansion.class)) {
            int minUnit = unit.getMinimumSize();
            if (minUnit == Integer.MAX_VALUE)
                return Integer.MAX_VALUE;
            result += minUnit;
        }
        return result;
    }

    public int getMaximumSize() {
        int result = 0;
        for (Expansion exp : childrenOfType(Expansion.class)) {
            int max = exp.getMaximumSize();
            if (max == Integer.MAX_VALUE)
                return Integer.MAX_VALUE;
            result += max;
        }
        return result;
    }

    /**
     * @return whether we have a scan limit, including an implicit one inside a
     *         nested NonTerminal. However, the NonTerminal has to 
     *         start the sequence and we don't nest within the NonTerminal. I think
     *         that gets too confusing.
     */
    @Override
    public boolean getHasScanLimit() {
        boolean atStart = true;
        for (Expansion unit : allUnits()) {
            if (unit.isScanLimit())
                return true;
            if (atStart && unit instanceof NonTerminal) {
                atStart = false;
                if (unit.getHasScanLimit())
                    return true;
            }
            if (unit.getMaximumSize() > 0)
                atStart = false;
        }
        return false;
    }

    /**
     * @return whether we have an <em>explicit</em> scan limit,
     *         i.e. <em>not including</em> one that is inside a NonTerminal
     *         expansion.
     */
    @Override
    public boolean getHasExplicitScanLimit() {
        return allUnits().stream().anyMatch(Expansion::isScanLimit);
    }

    public List<Expansion> getUnits() {
        return childrenOfType(Expansion.class);
    }

    @Override
    public boolean potentiallyStartsWith(String productionName, Set<String> alreadyVisited) {
        boolean result = false;
        for (Expansion unit : childrenOfType(Expansion.class)) {
            if (unit.potentiallyStartsWith(productionName, alreadyVisited)) result = true;
            if (!unit.isPossiblyEmpty()) break;
        }
        return result;
    }

    @Override
    public int getLookaheadAmount() {
        if (getHasExplicitScanLimit()) return Integer.MAX_VALUE;
        Lookahead la = getLookahead();
        if (la != null)
            return la.getAmount();
        return getRequiresScanAhead() ? Integer.MAX_VALUE : 1; // A bit kludgy, REVISIT 
    }

    /**
     * Does this expansion have a separate lookahead expansion?
     */
    public boolean getHasSeparateSyntacticLookahead() {
        Lookahead la = getLookahead();
        return la != null && la.getNestedExpansion() != null;
    }

    public Expansion getLookaheadExpansion() {
        Lookahead la = getLookahead();
        Expansion exp = la == null ? null : la.getNestedExpansion();
        return exp != null ? exp : this;
    }

    public boolean isNegated() {
        return getLookahead() != null && getLookahead().isNegated();
    }

    public LookBehind getLookBehind() {
        Lookahead la = getLookahead();
        return la == null ? null : la.getLookBehind();
    }

    public final Expression getSemanticLookahead() {
        Lookahead la = getLookahead();
        return la == null ? null : la.getSemanticLookahead();
    }

    public boolean getHasExplicitNumericalLookahead() {
        return lookahead != null && lookahead.getHasExplicitNumericalAmount();
    }

    public boolean getHasSemanticLookahead() {
        Lookahead la = getLookahead();
        return la != null && la.hasSemanticLookahead();
    }
    
   /**
     * Do we do a syntactic lookahead using this expansion itself as the lookahead
     * expansion?
     */
    boolean getHasImplicitSyntacticLookahead() {
        if (!this.isAtChoicePoint())
            return false;
        if (getHasSeparateSyntacticLookahead())
            return false;
        if (this.isAlwaysSuccessful())
            return false;
        if (getHasScanLimit()) {
            return true;
        }
        if (getHasExplicitNumericalLookahead() && getLookaheadAmount() ==0)
            return false;
// REVISIT.            
//        if (getMaximumSize() <= 1) {
//            return false;
//        }
        return getLookahead() != null;
    }

    public boolean isSingleToken() {
        if (!super.isSingleToken()) return false;
        for (Expansion exp : childrenOfType(Expansion.class)) {
            // This is mostly in order to recurse into any NonTerminals 
            // in the expansion sequence.
            if (exp.getMaximumSize() == 1 && !exp.isSingleToken()) return false;
        }
        return true;
    }

    public boolean startsWithLexicalChange() {
        Node parent = getParent();
        if (parent instanceof BNFProduction) {
            if (((BNFProduction) parent).getLexicalState() != null) {
                return true;
            }
        }
        for (Expansion exp : childrenOfType(Expansion.class)) {
            if (exp.startsWithLexicalChange()) return true;
            if (!exp.isPossiblyEmpty()) break;
        }
        return false;
    }

    public boolean startsWithGlobalCodeAction() {
        for (Expansion exp : childrenOfType(Expansion.class)) {
            if (exp.startsWithGlobalCodeAction()) return true;
            if (!exp.isPossiblyEmpty()) break;
        }
        return false;
    }
}