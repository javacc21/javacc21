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

import com.javacc.parser.tree.*;

public class NonTerminal extends Expansion {
    
    private Name LHS;
    public Name getLHS() {return LHS;}
    public void setLHS(Name LHS) {this.LHS=LHS;}

    /**
     * The production this non-terminal corresponds to.
     */
    public BNFProduction getProduction() {
        return getGrammar().getProductionByName(getName());
    }

    public Expansion getNestedExpansion() {
        return getProduction().getExpansion();
    }

    public boolean getHasTokenActivation() {
        return getNestedExpansion().getHasTokenActivation();
    }

    public Lookahead getLookahead() {
        return getNestedExpansion().getLookahead();
    }

    public InvocationArguments getArgs() {
        return firstChildOfType(InvocationArguments.class);
    }

    public String getName() {
        return firstChildOfType(TokenType.IDENTIFIER).getImage();
    }
    
    /**
     * The basic logic of when we scan to the end of 
     * a NonTerminal, ignoring any nested lookahead or scan limits.
     */
    public boolean getScanToEnd() {
        if (isInsideLookahead()) return true;
        ExpansionSequence parent = (ExpansionSequence) getNonSuperfluousParent();
        if (!parent.isAtChoicePoint()) return true;
        if (parent.getHasExplicitNumericalLookahead() || parent.getHasExplicitScanLimit()) return true;
        return parent.firstNonEmpty() != this;
    }

    public TokenSet getFirstSet() {
        if (firstSet == null) {
            firstSet = getProduction().getExpansion().getFirstSet();
        }
        return firstSet;
     }
     private int reEntries;     
     public TokenSet getFinalSet() {
          ++reEntries;
          TokenSet result = reEntries == 1 ? getProduction().getExpansion().getFinalSet() : new TokenSet(getGrammar());
          --reEntries;
          return result;
     }
     
     public boolean isPossiblyEmpty() {
         return getProduction().isPossiblyEmpty();
     }

     public boolean isAlwaysSuccessful() {
         return getProduction().getExpansion().isAlwaysSuccessful();
     }
     
     private boolean inMinimumSize, inMaximumSize;
     
     public int getMinimumSize() {
         if (inMinimumSize) return Integer.MAX_VALUE;
         inMinimumSize = true;
         int result = getProduction().getExpansion().getMinimumSize();
         inMinimumSize = false;
         return result;
     }

     public int getMaximumSize() {
         if (inMaximumSize) {
             return Integer.MAX_VALUE;
         }
         inMaximumSize = true;
         int result = getProduction().getExpansion().getMaximumSize(); 
         inMaximumSize = false;
         return result;
     }
    
     
     // We don't nest into NonTerminals
     boolean getHasScanLimit() {
        Expansion exp = getNestedExpansion();
        if (exp instanceof ExpansionSequence) {
            for (Expansion sub : ((ExpansionSequence) exp).allUnits()) {
                if (sub.isScanLimit()) return true;
            }
        }
        return false;
     }

     public boolean getSpecifiesLexicalStateSwitch() {
         return getProduction().getLexicalState() != null || getNestedExpansion().getSpecifiesLexicalStateSwitch();
     }

     public boolean isSingleToken() {
        return getNestedExpansion().isSingleToken();
     }
}