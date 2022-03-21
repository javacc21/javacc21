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

import java.util.List;
import java.util.Set;

abstract public class ExpansionChoice extends Expansion {
    public List<Expansion> getChoices() {
        return childrenOfType(Expansion.class);
    }
    
    public TokenSet getFirstSet() {
         if (firstSet == null) {
            firstSet = new TokenSet(getGrammar());
            for (Expansion choice : getChoices()) {
                firstSet.or(choice.getLookaheadExpansion().getFirstSet());
            }
         }
         return firstSet;
    }
    
    public TokenSet getFinalSet() {
        TokenSet finalSet = new TokenSet(getGrammar());
        for (Expansion choice : getChoices()) {
            finalSet.or(choice.getFinalSet());
        }
        return finalSet;
    }
    
    
    public boolean isPossiblyEmpty() {
         for (Expansion e : getChoices()) {
             if (e.isPossiblyEmpty()) {
                 return true;
             }
         }
         return false;
    }
 
    public boolean isAlwaysSuccessful() {
        if (!super.isAlwaysSuccessful()) return false;
        for (Expansion choice : getChoices()) {
            if (choice.isAlwaysSuccessful()) return true;
        }
        return false;
    }
    
    public int getMinimumSize() {
        int result = Integer.MAX_VALUE;
        for (Expansion choice : getChoices()) {
           int choiceMin = choice.getMinimumSize();
           if (choiceMin ==0) return 0;
           result = Math.min(result, choiceMin);
        }
        return result;
    }
 
    public int getMaximumSize() {
        int result = 0;
        for (Expansion exp : getChoices()) {
            result = Math.max(result, exp.getMaximumSize());
            if (result == Integer.MAX_VALUE) break;
        }
        return result;
    }
    
    public boolean getSpecifiesLexicalStateSwitch() {
        for (Expansion choice : getChoices()) {
            if (choice.getSpecifiesLexicalStateSwitch()) return true;
        }
        return false;
    }

    protected boolean potentiallyStartsWith(String productionName, Set<String> alreadyVisited) {
        for (Expansion choice : getChoices()) {
            if (choice.potentiallyStartsWith(productionName, alreadyVisited)) return true;
        }
        return false;
    }
}
