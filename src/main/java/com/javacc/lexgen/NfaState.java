/* Copyright (c) 2008-2021 Jonathan Revusky, revusky@javacc.com
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

package com.javacc.lexgen;

import java.util.*;
import com.javacc.parsegen.RegularExpression;

/**
 * Class representing a single state of a Non-deterministic Finite Automaton (NFA)
 * Note that any given lexical state is implemented as an NFA (and a DFA for string literals)
 * Thus, any given NfaState object is associated with one lexical state.
 */
public class NfaState {  

    private final LexicalStateData lexicalState;
    private final NfaData nfaData;
    private RegularExpression type;
    private List<Integer> moveRanges = new ArrayList<>();
    private int index = -1;
    NfaState nextState;
    Set<NfaState> epsilonMoves = new HashSet<>();

    NfaState(LexicalStateData lexicalState) {
        this.lexicalState = lexicalState;
        nfaData = lexicalState.getNfaData();
        nfaData.allStates.add(this);
    }

    public int getIndex() {
        return index;
    }

    public String getMoveMethodName() {
        return "NFA_" + lexicalState.getName() + "_" + index;
    }

    public String getMovesArrayName() {
        return getMoveMethodName().replace("NFA_", "NFA_MOVES_");
    }

    public List<Integer> getMoveRanges() { return moveRanges; }

    public RegularExpression getType() {return type;}

    public LexicalStateData getLexicalState() {
        return lexicalState;
    }

    public NfaState getNextState() {
        return nextState;
    }

    public int getEpsilonMoveCount() {
        return epsilonMoves.size();
    }
    
    void setType(RegularExpression type) {
        this.type = type;
    }

    void addEpsilonMove(NfaState newState) {
        epsilonMoves.add(newState);
    }

    void addCharMove(int c) {
        addRange(c, c);
    }

    void addRange(int left, int right) {
        moveRanges.add(left);
        moveRanges.add(right);
    }

    private boolean closureDone;

    /**
     * This function computes the closure and also updates the type so that any
     * time there is a move to this state, it can go on epsilon to a new state
     * in the epsilon moves that might have a lower kind of token number for the
     * same length.
     */
    void doEpsilonClosure() {
        if (closureDone) return;
        closureDone = true;
        // Recursively do closure
        for (NfaState state : new ArrayList<>(epsilonMoves)) {
            state.doEpsilonClosure();
            if (type == null || (state.type != null && state.type.getOrdinal() < type.getOrdinal())) {
                type = state.type;
            } 
            for (NfaState otherState : state.epsilonMoves) {
                addEpsilonMove(otherState);
                otherState.doEpsilonClosure();
            }
        }
        addEpsilonMove(this);
        epsilonMoves.removeIf(state->state.moveRanges.isEmpty());
    }

    private boolean codeGenerated;
    
    void generateCode() {
        if (codeGenerated) return;
        codeGenerated = true;
        for (NfaState epsilonMove : epsilonMoves) {
            epsilonMove.generateCode();
        }
        if (nextState != null) {
            nextState.generateCode();
        }
        if (index == -1 && !moveRanges.isEmpty()) {
            this.index = nfaData.indexedAllStates.size();
            nfaData.indexedAllStates.put(index, this);
        }
    }

    boolean canMoveUsingChar(int c) {
        // Check whether the character c is in some range.
        int idx = Collections.binarySearch(moveRanges, c);
        return idx >=0 || idx%2 ==0;
    }
}
