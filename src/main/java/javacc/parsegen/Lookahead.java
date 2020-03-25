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
import javacc.parser.tree.Expression;



/**
 * Describes lookahead rule for a particular expansion or expansion sequence
 * (See Sequence.java). In case this describes the lookahead rule for a single
 * expansion unit, then a sequence is created with this node as the first
 * element, and the expansion unit as the second and last element.
 */

public class Lookahead extends Expansion {

    private boolean[] firstSet;
    

    public Lookahead(Grammar grammar) {
        setGrammar(grammar);
    }
    
    protected Lookahead() {}
    
    private boolean[] getFirstSet() {
        if (firstSet == null) {
            firstSet = new boolean[getGrammar().getLexerData().getTokenCount()];
            expansion.genFirstSet(firstSet);
        }
        return firstSet;
    }
    
    public List<String> getFirstSetTokenNames() {
        List<String> result = new ArrayList<String>();
        for (int i=0; i<getFirstSet().length; i++) {
            if (firstSet[i]) {
                result.add(getGrammar().getTokenName(i));
            }
        }
        return result;
    }
    
    public boolean getPossibleEmptyExpansionOrJavaCode() {
        return expansion.isPossiblyEmpty() || expansion.javaCodeCheck();
    }
    
    public Lookahead(Expansion nestedExpansion) {
        this.expansion = nestedExpansion;
        setGrammar(nestedExpansion.getGrammar());
        setAmount(getGrammar().getOptions().getLookahead());
    }

    public boolean getAlwaysSucceeds() {
        if (semanticLookahead != null) 
            return false;
        return getAmount() == 0 || expansion.javaCodeCheck()
                || expansion.isPossiblyEmpty();
    }

    /**
     * Checks whether this lookahead requires a phase2 routine to be generated.
     * If necessary, it walks the subtree recursively to figure it out.
     */

    public boolean getRequiresPhase2Routine() {
        if (getAmount() > 1)
            return true;
        if (getAmount() == 0 || expansion.isPossiblyEmpty() || expansion.javaCodeCheck())
            return false;
        return semanticLookahead != null || expansion.requiresPhase2Routine();
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
    
    public boolean hasSemanticLookahead() {
    	return semanticLookahead != null;
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

    // The following 5 little methods should disappear when we 
    // finally see our way through to having Lookahead not extend Expansion anymore.
    
    public boolean isPossiblyEmpty() {
    	return true;
    }
    
    public boolean javaCodeCheck() {
    	return false;
    }
    
    public boolean requiresPhase2Routine() {
    	return false;
    }
    
    public void genFirstSet(boolean[] firstSet ) {
    	//A bit screwy since Lookahead extends Expansion but
    	// is ignored in the recursive walk to build the first set.
    }
    
    public int minimumSize(int min) {
    	return 0;
    }
}

