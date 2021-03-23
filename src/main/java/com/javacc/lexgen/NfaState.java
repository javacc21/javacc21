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

import com.javacc.Grammar;
import com.javacc.parsegen.RegularExpression;

/**
 * Class representing a single state of a Non-deterministic Finite Automaton (NFA)
 * Note that any given lexical state is implemented as an NFA (and a DFA for string literals)
 * Thus, any given NfaState object is associated with one lexical state.
 */
public class NfaState {  

    private final Grammar grammar;
    private final LexerData lexerData;
    private final LexicalStateData lexicalState;
    private final NfaData nfaData;
    private RegularExpression type;
    private BitSet asciiMoves = new BitSet();
    private List<Integer> moveRanges = new ArrayList<>();
    private List<Integer> loByteVec = new ArrayList<>();

    private int index = -1;
    private boolean nonAscii;
    NfaState nextState;
    Set<NfaState> epsilonMoves = new HashSet<>();

    NfaState(LexicalStateData lexicalState) {
        this.lexicalState = lexicalState;
        nfaData = lexicalState.getNfaData();
        this.grammar = lexicalState.getGrammar();
        this.lexerData = grammar.getLexerData();
        nfaData.allStates.add(this);
    }

    public int getIndex() {
        return index;
    }

    public String getMoveMethodName() {
        return "NFA_" + lexicalState.getName() + "_" + index;
    }

    public boolean isNonAscii() {
        return nonAscii;
    }

    public List<Integer> getLoByteVec() {
        return loByteVec;
    }

    public long[] getAsciiMoves() {
        long[] ll = asciiMoves.toLongArray();
        if (ll.length !=2) {
            ll = Arrays.copyOf(ll, 2);
        }
        return ll;
    }

    public BitSet getAsciiMoveSet() {
        return asciiMoves;
    }

    public List<Integer> getMoveRanges() {
        return moveRanges;
    }

    public RegularExpression getType() {return type;}

    void setType(RegularExpression type) {
        this.type = type;
    }

    public LexicalStateData getLexicalState() {
        return lexicalState;
    }

    public NfaState getNextState() {
        return nextState;
    }

    public int getEpsilonMoveCount() {
        return epsilonMoves.size();
    }

    public boolean isNeeded(int byteNum) {
        assert byteNum == 0 || byteNum ==1;
        return byteNum == 0 ? asciiMoves.previousSetBit(63) >=0 : asciiMoves.nextSetBit(64) >=64; 
    }

    void addEpsilonMove(NfaState newState) {
        epsilonMoves.add(newState);
    }

    void addCharMove(int c) {
        addRange(c, c);
    }

    void addRange(int left, int right) {
        assert right>=left;
        assert right<=0x10FFFF;
        if (left < 128) asciiMoves.set(left, Math.min(right+1, 128));
        left = Math.max(left, 128);
        if (right >= left) {
            moveRanges.add(left);
            moveRanges.add(right);
        }
    }

    private boolean closureDone = false;

    /**
     * This function computes the closure and also updates the kind so that any
     * time there is a move to this state, it can go on epsilon to a new state
     * in the epsilon moves that might have a lower kind of token number for the
     * same length.
     */
    void doEpsilonClosure() {
        if (closureDone) {
            return;
        }
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
        epsilonMoves.removeIf(state->!state.hasTransitions());
    }

    private boolean hasTransitions() {
        return !asciiMoves.isEmpty()
                || !moveRanges.isEmpty();
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
        if (index == -1 && hasTransitions()) {
            this.index = nfaData.indexedAllStates.size();
            nfaData.indexedAllStates.put(index, this);
        }
    }

    public int[] getStates() {
        int[] result = new int[epsilonMoves.size()];
        int index = 0;
        for (NfaState state : epsilonMoves) {
            result[index++] = state.index;
        }
        return result;
    }

    boolean canMoveUsingChar(int c) {
        if (c < 128) {
            return asciiMoves.get(c);
        }
        // Iterate thru the table to see if the current char
        // is in some range
        for (int i = 0; i< moveRanges.size(); i=i+2) {
            int left = moveRanges.get(i);
            int right = moveRanges.get(i+1);
            if (c>=left && c<=right) return true;
            if (c<left) return false;
        }
        return false;
    }

    /*
     * This function generates the bit vectors of low and hi bytes for common
     * bit vectors and returns those that are not common with anything (in
     * loBytes) and returns an array of indices that can be used to generate the
     * function names for char matching using the common bit vectors. It also
     * generates code to match a char with the common bit vectors. (Need a
     * better comment).
     */
    void generateNonAsciiMoves() {
        if (moveRanges.isEmpty()) {
            return;
        }
        this.nonAscii = true;
        BitSet charMoves = new BitSet();
        for (int i=0; i< moveRanges.size(); i+=2) {
            int leftSide = moveRanges.get(i);
            int rightSide = moveRanges.get(i+1);
            while (leftSide<=rightSide) {
                charMoves.set(leftSide++);
            }
        }
        for (int i = 0; i < 256; i++) {
            Map<BitSet, Integer> lohiByteLookup = lexerData.getLoHiByteLookup();
            BitSet subSet = charMoves.get(256*i, 256*(i+1));
            List<BitSet> allBitSets = lexerData.getAllBitSets();
            Integer ind = lohiByteLookup.get(subSet);
            if (ind == null) {
                allBitSets.add(subSet);
                int lohiByteCount = lexerData.getLohiByteCount();
                ind = lohiByteCount;
                lohiByteLookup.put(subSet, ind);
                lexerData.incrementLohiByteCount();
            }
            loByteVec.add(i);
            loByteVec.add(ind);
        }
    }

    public boolean isNextIntersects() {
        for (NfaState state : nfaData.allStates) {
            if (this == state || !state.hasTransitions() || !state.isNonAscii())
                continue;
            if (!Collections.disjoint(epsilonMoves, state.nextState.epsilonMoves)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMoveState(NfaState other, int byteNum) {
        if (this.nextState.type != other.nextState.type) {
            return false;
        }
        if (byteNum < 0) {
            return false;
        }
        if (byteNum >=0 && !this.asciiMoves.equals(other.asciiMoves)) {
            return false;
        }
        if (this.nextState.epsilonMoves.isEmpty() || other.nextState.epsilonMoves.isEmpty()) {
            return false;
        }
        return this.nextState.epsilonMoves.equals(other.nextState.epsilonMoves);
    }

    public List<NfaState> getMoveStates(int byteNum, BitSet statesAlreadyHandled) {
        List<NfaState> result = new ArrayList<NfaState>();
        for (NfaState state : nfaData.allStates) {
            if (!statesAlreadyHandled.get(state.index) && isMoveState(state, byteNum)) {
                statesAlreadyHandled.set(state.index);
                result.add(state);
            }
        }
        return result;
    }

    /**
     * @param byteNum either 0 or 1
     */
    public boolean isOnlyState(int byteNum) {
        for (NfaState state : nfaData.allStates) {
            BitSet bs = new BitSet();
            bs.or(asciiMoves);
            bs.and(state.asciiMoves);
            boolean intersects = byteNum == 0 ? bs.previousSetBit(63) >=0 : bs.nextSetBit(64) >=0;
            if (state.index != -1 && state.index != this.index && state.isNeeded(byteNum) && intersects) {
                return false;
            }
        }
        return true;
    }
}
