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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ExpansionChoice extends Expansion {
    public List<ExpansionSequence> getChoices() {
        return childrenOfType(ExpansionSequence.class);
    }
    
    public TokenSet getFirstSet() {
         if (firstSet == null) {
            firstSet = new TokenSet(getGrammar());
            for (ExpansionSequence choice : childrenOfType(ExpansionSequence.class)) {
                firstSet.or(choice.getLookaheadExpansion().getFirstSet());
            }
         }
         return firstSet;
    }
    
    public TokenSet getFinalSet() {
        TokenSet finalSet = new TokenSet(getGrammar());
        for (ExpansionSequence choice : childrenOfType(ExpansionSequence.class)) {
            finalSet.or(choice.getFinalSet());
        }
        return finalSet;
    }
    
    
    public boolean isPossiblyEmpty() {
        return childrenOfType(ExpansionSequence.class).stream().anyMatch(choice->choice.isPossiblyEmpty());
    }
 
    public boolean isAlwaysSuccessful() {
        return childrenOfType(ExpansionSequence.class).stream().anyMatch(choice->choice.isAlwaysSuccessful());
    }
    
    public int getMinimumSize() {
        int result = Integer.MAX_VALUE;
        for (ExpansionSequence choice : childrenOfType(ExpansionSequence.class)) {
           int choiceMin = choice.getMinimumSize();
           if (choiceMin ==0) return 0;
           result = Math.min(result, choiceMin);
        }
        return result;
    }
 
    public int getMaximumSize() {
        int result = 0;
        for (ExpansionSequence exp : childrenOfType(ExpansionSequence.class)) {
            result = Math.max(result, exp.getMaximumSize());
            if (result == Integer.MAX_VALUE) break;
        }
        return result;
    }
    
    public boolean getSpecifiesLexicalStateSwitch() {
        for (ExpansionSequence choice : childrenOfType(ExpansionSequence.class)) {
            if (choice.getSpecifiesLexicalStateSwitch()) return true;
        }
        return false;
    }

    public boolean potentiallyStartsWith(String productionName, Set<String> alreadyVisited) {
        for (ExpansionSequence choice : childrenOfType(ExpansionSequence.class)) {
            if (choice.potentiallyStartsWith(productionName, alreadyVisited)) return true;
        }
        return false;
    }

    public boolean isSingleToken() {
        if (!super.isSingleToken()) return false;
        for (ExpansionSequence exp : childrenOfType(ExpansionSequence.class)) {
            if (!exp.isSingleToken()) return false;
        }
        return true;
    }


//For now we don't recurse into the various choice sub-expansions.
// If the following code is uncommented, we end up with a huge slow-down
// on the Java grammar, for example.
/*
    public boolean startsWithLexicalChange() {
        for (ExpansionSequence choice : childrenOfType(ExpansionSequence.class)) {
            if (choice.startsWithLexicalChange()) return true;
        }
        return false;
    }    

    public boolean startsWithGlobalCodeAction() {
        for (ExpansionSequence choice : childrenOfType(ExpansionSequence.class)) {
            if (choice.startsWithGlobalCodeAction()) return true;
        }
        return false;
    }
*/    
}
