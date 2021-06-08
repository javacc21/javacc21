/* Copyright (c) 2021 Jonathan Revusky, revusky@javacc.com
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

public class CompositeStateSet extends NfaState {

    private Set<NfaState> states = new HashSet<>(); 

    CompositeStateSet(LexicalStateData lsd) {
        super(lsd);
    }

    CompositeStateSet(Set<NfaState> states) {
        this(states.iterator().next().getLexicalState());
        this.states = new HashSet<>(states);
    }

    public boolean isComposite() {
        return true;
    }

    public boolean isMoveCodeNeeded() {
//        return states.stream().anyMatch(NfaState::isMoveCodeNeeded);
        return true;
    }


    public String getMethodName() {
        return super.getMethodName().replace("NFA_", "NFA_COMPOSITE_");
    }

    public boolean equals(Object other) {
        return (other instanceof CompositeStateSet)
               && ((CompositeStateSet)other).states.equals(this.states);
    }

    /**
     * We return the NFA states in this composite 
     * in order (decreasing) of the ordinal of the nextState
     * @return sorted list of states
     */
    public List<NfaState> getOrderedStates() {
        ArrayList<NfaState> result = new ArrayList<>(states);
        Collections.sort(result, 
                         (state1, state2) -> state2.getNextState().getOrdinal()-state1.getNextState().getOrdinal());
        return result;    
    }

    /**
     * @return the ordinal of the highest priority match
     */
    public int getOrdinal() {
        int result = Integer.MAX_VALUE;
        for (NfaState state : states) {
            if (state.getType() !=null)
                result = Math.min(result, state.getOrdinal());
        }
        return result;
    }
}