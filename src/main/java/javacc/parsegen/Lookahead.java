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

import java.util.*;

import javacc.Grammar;
import javacc.lexgen.RegularExpression;
import javacc.parser.Nodes;
import javacc.parser.tree.ExpansionChoice;
import javacc.parser.tree.ExpansionSequence;
import javacc.parser.tree.Expression;
import javacc.parser.tree.BNFProduction;
import javacc.parser.tree.OneOrMore;
import javacc.parser.tree.ZeroOrMore;
import javacc.parser.tree.ZeroOrOne;
import javacc.parser.tree.NonTerminal;
import javacc.parser.tree.TryBlock;



/**
 * Describes lookahead rule for a particular expansion or expansion sequence
 * (See Sequence.java). In case this describes the lookahead rule for a single
 * expansion unit, then a sequence is created with this node as the first
 * element, and the expansion unit as the second and last element.
 */

public class Lookahead extends Expansion {

    private boolean[] firstSet;
    

    public Lookahead(Grammar grammar) {
        super(grammar);
    }
    
    protected Lookahead() {}
    
    boolean[] getFirstSet() {
        if (firstSet == null) {
            firstSet = new boolean[getGrammar().getLexerData().getTokenCount()];
            genFirstSet(this.expansion, firstSet);
        }
        return firstSet;
    }
    
    public List<String> getMatchingTokens() {
        List<String> result = new ArrayList<String>();
        for (int i=0; i<getFirstSet().length; i++) {
            if (firstSet[i]) {
                result.add(getGrammar().getTokenName(i));
            }
        }
        return result;
    }
    
    public boolean getPossibleEmptyExpansionOrJavaCode() {
        return Semanticizer.emptyExpansionExists(expansion) || Semanticizer.javaCodeCheck(expansion);
    }
    
    
    /**
     * Sets up the array "firstSet" above based on the Expansion argument passed
     * to it. Since this is a recursive function, it assumes that "firstSet" has
     * been reset before the first call.
     */
    private void genFirstSet(Expansion exp, boolean[] firstSet) {
        if (firstSet == null) {
            firstSet = new boolean[getGrammar().getLexerData().getTokenCount()];
        }
        if (exp instanceof RegularExpression) {
            firstSet[((RegularExpression) exp).getOrdinal()] = true;
        } else if (exp instanceof NonTerminal) {
            if (((NonTerminal) exp).prod instanceof BNFProduction) {
                genFirstSet(((BNFProduction) (((NonTerminal) exp).prod)).getExpansion(), firstSet);
            }
        } else if (exp instanceof ExpansionChoice) {
            for (Expansion sub : Nodes.childrenOfType(exp, Expansion.class)) {
                genFirstSet(sub, firstSet);
            }
        } else if (exp instanceof ExpansionSequence) {
            ExpansionSequence seq = (ExpansionSequence) exp;
            for (int i = 0; i < seq.getChildCount(); i++) {
                Expansion unit = (Expansion) seq.getChild(i);
                // Javacode productions can not have FIRST sets. Instead we
                // generate the FIRST set
                // for the preceding LOOKAHEAD (the semantic checks should have
                // made sure that
                // the LOOKAHEAD is suitable).
                if (unit instanceof NonTerminal
                        && !(((NonTerminal) unit).prod instanceof BNFProduction)) {
                    if (i > 0 && seq.getChild(i - 1) instanceof Lookahead) {
                        Lookahead la = (Lookahead) seq.getChild(i - 1);
                        genFirstSet(la.expansion, firstSet);
                    }
                } else {
                    genFirstSet((Expansion)seq.getChild(i), firstSet);
                }
                if (!Semanticizer.emptyExpansionExists((Expansion) seq.getChild(i))) {
                    break;
                }
            }
        } else if (exp instanceof OneOrMore || exp instanceof ZeroOrMore 
                   || exp instanceof ZeroOrOne || exp instanceof TryBlock) {
            genFirstSet(exp.getNestedExpansion(), firstSet);
        } 
    }
    

    public Lookahead(Expansion nestedExpansion) {
        super(nestedExpansion.getGrammar());
        this.expansion = nestedExpansion;
        setAmount(getGrammar().getOptions().getLookahead());
    }

    public boolean getAlwaysSucceeds() {
        if (semanticLookahead != null) 
            return false;
        return getAmount() == 0 || Semanticizer.javaCodeCheck(expansion)
                || Semanticizer.emptyExpansionExists(expansion);
    }

    /**
     * Checks whether this lookahead requires a phase2 routine to be generated.
     * If necessary, it walks the subtree recursively to figure it out.
     */

    public boolean getRequiresPhase2Routine() {
        if (getAmount() == 0)
            return false;
        if (getAmount() > 1)
            return true;
        if (Semanticizer.emptyExpansionExists(expansion)
                || Semanticizer.javaCodeCheck(expansion))
            return false;
        if (semanticLookahead != null)
            return true;
        return checkSubExpansionsForPhase2(expansion);
    }

    private boolean checkSubExpansionsForPhase2(Expansion exp) {
        if (exp instanceof RegularExpression) {
            return false;
        } else if (exp instanceof NonTerminal) {
            if (((NonTerminal) exp).prod instanceof BNFProduction) {
                return checkSubExpansionsForPhase2(((BNFProduction) (((NonTerminal) exp).prod)).getExpansion());
            }
        } else if (exp instanceof ExpansionChoice) {
            for (Expansion sub : Nodes.childrenOfType(exp, Expansion.class)) {
                if (checkSubExpansionsForPhase2(sub))
                    return true;
            }
        } else if (exp instanceof ExpansionSequence) {
            ExpansionSequence seq = (ExpansionSequence) exp;
            Object obj = seq.getChild(0);
            if ((obj instanceof Lookahead)
                    && (((Lookahead) obj).semanticLookahead != null)) {
                return true;
            }
            Expansion previous = null;
            for (Expansion unit : Nodes.childrenOfType(seq, Expansion.class)) {
                // For a Javacode production, we check
                // the preceding Lookahead object.
                if (unit instanceof NonTerminal
                        && !(((NonTerminal) unit).prod instanceof BNFProduction)) {
                    if (previous instanceof Lookahead) {
                        Lookahead la = (Lookahead) previous;
                        if (checkSubExpansionsForPhase2(la.expansion))
                            return true;
                    }
                } else {
                    if (checkSubExpansionsForPhase2(unit))
                        return true;

                }
                if (!Semanticizer.emptyExpansionExists(unit)) {
                    break;
                }
                previous = unit;
            }
        } else if (exp instanceof OneOrMore || exp instanceof ZeroOrMore || exp instanceof ZeroOrOne
                   || exp instanceof TryBlock) {
            return checkSubExpansionsForPhase2(exp.getNestedExpansion());
        }
        return false;
    }

    /**
     * Contains the semantic lookahead expression, may be null
     * If it is non-null, then the fields amount and la_expansion
     * are ignored.
     */
    private Expression semanticLookahead;
    
    public Expression getSemanticLookahead() {
        return semanticLookahead;
    }
    
    public void setSemanticLookahead(Expression semanticLookahead) {
        this.semanticLookahead = semanticLookahead;
    }

    /**
     * The lookahead amount. Its default value essentially gives us infinite
     * lookahead.
     */
    private int amount = Integer.MAX_VALUE;

    /**
     * The expansion used to determine whether or not to choose the
     * corresponding parse option. This expansion is parsed up to "amount" tokens
     * of lookahead or until a complete match for it is found. Usually, this is
     * the same as the expansion to be parsed.
     */
    private Expansion expansion;

    public boolean isExplicit() {
        return false;
    }
    
    public Expansion getNestedExpansion() {
        return expansion;
    }
    
    public void setExpansion(Expansion exp){
        this.expansion = exp;
    }
    
    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }
}
