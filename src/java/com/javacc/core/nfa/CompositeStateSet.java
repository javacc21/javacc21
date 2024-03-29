/* Copyright (c) 2021-2022 Jonathan Revusky, revusky@congocc.com
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

package com.javacc.core.nfa;

import java.util.*;

/**
 * A class representing a composite state set, i.e. 
 * a set of one or more NfaState objects.
 * Each instance of this class ends up being implemented 
 * in the generated code as an instance of the 
 * XXXNfaData::NfaFunction functional interface. 
 */
public class CompositeStateSet {
    private Set<NfaState> states = new HashSet<>();
    final LexicalStateData lexicalState;
    int index=-1; 

    CompositeStateSet(Set<NfaState> states, LexicalStateData lsd) {
        this.states = new HashSet<>(states);
        this.lexicalState = lsd;
    }

    public int getIndex() {return index;}

    void setIndex(int index) {this.index = index;}

    public String getMethodName() {
        String lexicalStateName = lexicalState.getName();
        if (lexicalStateName.equals("DEFAULT")) 
            return "NFA_" + index;
        return "NFA_" + lexicalStateName + "_" + index; 
    }

    public boolean equals(Object other) {
        return (other instanceof CompositeStateSet)
               && ((CompositeStateSet)other).states.equals(this.states);
    }

    /**
     * We return the NFA states in this composite 
     * in order (decreasing) of the ordinal of the nextState's 
     * ordinal, i.e. in increasing order of its priority in
     * terms of pattern matching.
     * @return sorted list of states
     */
    public List<NfaState> getOrderedStates() {
        ArrayList<NfaState> result = new ArrayList<>(states);
        Collections.sort(result, CompositeStateSet::nfaComparator);
        return result;    
    }

    static private int nfaComparator(NfaState state1, NfaState state2) {
        int result = getOrdinal(state2.getNextState()) - getOrdinal(state1.getNextState());
        if (result == 0)
           result = (state1.getMoveRanges().get(0) - state2.getMoveRanges().get(0));
        if (result == 0)
           result = (state1.getMoveRanges().get(1) - state2.getMoveRanges().get(1));
        if (result ==0)
           result = state2.getMoveRanges().size() - state2.getMoveRanges().size();
        return result;
    }    
    
    static private int getOrdinal(NfaState state) {
        return state.getType() == null ? Integer.MAX_VALUE : state.getType().getOrdinal();
    }

    // Recursive method to figure out which composite state sets are actually used.
    // We invoke this on a lexical state's initial state. 
    void findWhatIsUsed(Set<CompositeStateSet> alreadyVisited, Set<CompositeStateSet> usedStates) {
        if (alreadyVisited.contains(this)) return;
        alreadyVisited.add(this);
        if (states.isEmpty()) return;
        usedStates.add(this);
        for (NfaState state : states) {
            state.getNextState().getComposite().findWhatIsUsed(alreadyVisited, usedStates);
        }
    }
}