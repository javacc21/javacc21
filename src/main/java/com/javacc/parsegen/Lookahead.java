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

import java.util.*;

import com.javacc.Grammar;
import com.javacc.lexgen.TokenSet;
import com.javacc.parser.tree.Expression;



/**
 * Describes lookahead rule for a particular expansion or expansion sequence
 * (See Sequence.java). In case this describes the lookahead rule for a single
 * expansion unit, then a sequence is created with this node as the first
 * element, and the expansion unit as the second and last element.
 */

public class Lookahead extends Expansion {
	
	List<String> firstSetNames, finalSetNames;

    public Lookahead(Grammar grammar) {
        setGrammar(grammar);
    }
    
    protected Lookahead() {}
    
    public boolean getHasDefiniteLookahead() {
        return getAmount() != 0 && getAmount() != Integer.MAX_VALUE;
    }
    
    /**
     * The names of the tokens with which this Lookahead's nested expansion can begin.
     * @return
     */

    public List<String> getFirstSetTokenNames() {
    	if (firstSetNames == null) {
    		int tokenCount = getGrammar().getLexerData().getTokenCount();
    		firstSetNames = new ArrayList<>(tokenCount);
    		BitSet firstSet = expansion.getFirstSet();
    		for (int i=0; i<tokenCount; i++) {
    			if (firstSet.get(i)) {
    			    firstSetNames.add(getGrammar().getLexerData().getTokenName(i));
    			}
    		}
        }
        return firstSetNames;
    }
    
    public List<String> getFinalSetTokenNames() {
    	return expansion.getFinalSet().getTokenNames();
    }
    
    public boolean getPossibleEmptyExpansion() {
        return expansion.isPossiblyEmpty();
    }

    public boolean getPossibleEmptyExpansionOrJavaCode() {
        return expansion.isPossiblyEmpty();
    }
  
    public Lookahead(Expansion nestedExpansion) {
        this.expansion = nestedExpansion;
        setGrammar(nestedExpansion.getGrammar());
        setAmount(getGrammar().getOptions().getLookahead());
    }

    public boolean getAlwaysSucceeds() {
        if (semanticLookahead != null) 
            return false;
        return getAmount() == 0 || expansion.isPossiblyEmpty();
    }

    /**
     * Checks whether this lookahead requires a phase2 routine to be generated.
     * If necessary, it walks the subtree recursively to figure it out.
     */

    public boolean getRequiresPhase2Routine() {
        if (getAmount() > 1)
            return true;
        if (getAmount() == 0 || expansion.isPossiblyEmpty())
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
    
    public boolean requiresPhase2Routine() {
    	return false;
    }
    
    public TokenSet getFirstSet() {return new TokenSet(getGrammar());}
    
    public TokenSet getFinalSet() {return new TokenSet(getGrammar());}
    
    public int minimumSize(int min) {
    	return 0;
    }
}

