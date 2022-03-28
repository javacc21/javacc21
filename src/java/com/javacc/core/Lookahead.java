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

import com.javacc.parser.BaseNode;
import com.javacc.parser.tree.*;

public class Lookahead extends BaseNode {
    private Name LHS;
    private Expansion expansion, nestedExpansion, upToExpansion;
    private boolean negated, semanticLookaheadNested;
    private Expression semanticLookahead;

    public Name getLHS() {return LHS;}

    public void setLHS(Name LHS) {this.LHS = LHS;}

    public Expansion getExpansion() {return expansion;}

    public void setExpansion(Expansion expansion) {this.expansion=expansion;}

    public Expansion getNestedExpansion() {return nestedExpansion;}

    public void setNestedExpansion(Expansion nestedExpansion) {this.nestedExpansion = nestedExpansion;}

    public Expression getSemanticLookahead() {return semanticLookahead;}

    public void setSemanticLookahead(Expression semanticLookahead) {this.semanticLookahead = semanticLookahead;}

    public boolean isSemanticLookaheadNested() {return semanticLookaheadNested;}

    public void setSemanticLookaheadNested(boolean semanticLookaheadNested) {this.semanticLookaheadNested = semanticLookaheadNested;}

    public boolean isNegated() {return negated;}

    public void setNegated(boolean negated) {this.negated = negated;}

    public Expansion getUpToExpansion() { return upToExpansion;}

    public void setUpToExpansion(Expansion upToExpansion) {this.upToExpansion = upToExpansion;}


    public boolean isAlwaysSuccessful() {
        return !hasSemanticLookahead() && (getAmount() == 0 || getLookaheadExpansion().isPossiblyEmpty()); 
    }

    public boolean getRequiresScanAhead() {
        return !getLookaheadExpansion().isPossiblyEmpty() || isSemanticLookaheadNested();
//        return !getLookaheadExpansion().isPossiblyEmpty() && getAmount() > 1;
//          return !isAlwaysSuccessful() && getAmount() >1;
    }

    public boolean hasSemanticLookahead() {
        return getSemanticLookahead() != null;
    }
    
    public Expansion getLookaheadExpansion() {
        Expansion result = getNestedExpansion();
        if (result != null) {
            return result;
        }
        return expansion;
    }

    public boolean getHasExplicitNumericalAmount() {
        return firstChildOfType(TokenType.INTEGER_LITERAL) != null;
    }

    public int getAmount() {
        IntegerLiteral it = firstChildOfType(IntegerLiteral.class);
        if (it!=null) return it.getValue();
        if (this instanceof LegacyLookahead) {
            if (getNestedExpansion() == null && hasSemanticLookahead()) return 0;
        }
        return Integer.MAX_VALUE;
    }

    public LookBehind getLookBehind() {
        return firstChildOfType(LookBehind.class);
    }

}