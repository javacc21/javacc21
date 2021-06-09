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

package com.javacc.core;

import java.util.*;

/**
 * Class representing a single state of a Non-deterministic Finite Automaton (NFA)
 * Note that any given lexical state is implemented as an NFA.
 * Thus, any given NfaState object is associated with one lexical state.
 */
public class NfaState {  

    final LexicalStateData lexicalState;
    private RegularExpression type;
    private NfaState nextState;
    private Set<NfaState> epsilonMoves = new HashSet<>();
    int index = -1;

    // An ordered list of the ranges of characters that this 
    // NfaState "accepts". A single character is stored as a 
    // range in which the left side is the same as the right side.
    // Thus, for example, the (ASCII) characters that can start an identifier would be:
    // '$','$','A','Z','_','_',a','z'
    List<Integer> moveRanges = new ArrayList<>();

    NfaState(LexicalStateData lexicalState) {
        this.lexicalState = lexicalState;
        lexicalState.allStates.add(this);
    }

    public int getIndex() {
        return index;
    }

    public boolean isComposite() {
        return false;
    }

    public String getMovesArrayName() {
        return getMethodName().replace("NFA_", "NFA_MOVES_");
    }

    public String getMethodName() {
        String lexicalStateName = lexicalState.getName();
        if (lexicalStateName.equals("DEFAULT")) 
            return "NFA_" + index;
        return "NFA_" + lexicalStateName + "_" + index; 
    }

    public List<Integer> getMoveRanges() { return moveRanges; }

    public List<Integer> getAsciiMoveRanges() {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i<moveRanges.size(); i+=2) {
            int left = moveRanges.get(i);
            int right = moveRanges.get(i+1);
            if (left >= 128) break;
            result.add(left);
            result.add(right);
            if (right >=128) break;
        }
        return result;
    }

    public List<Integer> getNonAsciiMoveRanges() {
        return moveRanges.subList(getAsciiMoveRanges().size(), moveRanges.size());
    }

    public boolean getHasAsciiMoves() {
        return moveRanges.get(0) < 128;
    }

    public boolean getHasNonAsciiMoves() {
        return moveRanges.get(moveRanges.size()-1) >= 128;

    }

    public int getOrdinal() {
        assert !isComposite();
        return type == null ? Integer.MAX_VALUE : type.getOrdinal();
    }

    public RegularExpression getType() {return type;}

    public LexicalStateData getLexicalState() {return lexicalState;}

    public NfaState getNextState() {return nextState;}

    void setNextState(NfaState nextState) {this.nextState = nextState;}

    public Set<NfaState> getEpsilonMoves() {return epsilonMoves;}

    public NfaState getCanonicalState() {
        if (this.isComposite() || epsilonMoves.isEmpty()) return this;
        if (epsilonMoves.size() == 1) {
            return epsilonMoves.iterator().next();
        }
        return lexicalState.getCanonicalComposite(epsilonMoves);
    }

    boolean isMoveCodeNeeded() {
        if (getCanonicalState().isComposite()) return false;
        if (nextState == null) return false;
        return nextState.type != null || !nextState.epsilonMoves.isEmpty();
    }

    void setType(RegularExpression type) {
        this.type = type;
    }

    void addEpsilonMove(NfaState newState) {
        epsilonMoves.add(newState);
    }

    void addRange(int left, int right) {
        moveRanges.add(left);
        moveRanges.add(right);
    }

    void setCharMove(int c, boolean ignoreCase) {
        moveRanges.clear();
        if (!ignoreCase) {
            addRange(c, c);
        } else {//REVISIT, kinda messy
            int upper = Character.toUpperCase(c);
            int lower = Character.toLowerCase(c);
            addRange(upper, upper);
            if (upper != lower)
                addRange(lower, lower);
            if (c != upper && c!= lower)
                addRange(c, c);
            if (moveRanges.size() >2)
                Collections.sort(moveRanges);
        }
    }

    private boolean closureDone;

    /**
     * This method computes the closure and also updates the type so that any
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
            if (type == null) type = state.type;
            for (NfaState otherState : state.epsilonMoves) {
                addEpsilonMove(otherState);
                otherState.doEpsilonClosure();
            }
        }
        addEpsilonMove(this);
        epsilonMoves.removeIf(state->state.moveRanges.isEmpty());
    }

    public boolean isNonOverlapping(Collection<NfaState> states) {
        for (NfaState other : states) {
            if (this != other && intersect(this.moveRanges, other.moveRanges)) return false;
        }
        return true;
    }

    static BitSet moveRangesToBS(List<Integer> ranges) {
        BitSet result = new BitSet();
        for (int i=0; i< ranges.size(); i+=2) {
            int left = ranges.get(i);
            int right = ranges.get(i+1);
            result.set(left, right+1);
        }
        return result;
    }

    static boolean intersect(List<Integer> moves1, List<Integer> moves2) {
        BitSet bs1 = moveRangesToBS(moves1);
        BitSet bs2 = moveRangesToBS(moves2);
        return bs1.intersects(bs2);
    }
}
