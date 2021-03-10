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
    private List<Integer> rangeMovesLeftSide = new ArrayList<>();
    private List<Integer> rangeMovesRightSide = new ArrayList<>();
    private int nonAsciiMethod = -1;
    private List<Integer> nonAsciiMoveIndices;
    private List<Integer> loByteVec = new ArrayList<>();
    private int inNextOf;

    // What a Rube Goldberg contraption this is!
    // In general, any time the index value is used, 
    // we should be using a direct reference to this NfaState object
    // And any references to the so-called epsilonMovesString 
    // usually as a map key should
    // also be a reference to the NfaState object.
    // It's as if the person who wrote this code thought that the only 
    // key->value arrangement was String->int or String->int[]
    // Totally bizarre.
    private int index = -1;
    private String epsilonMovesString;
    NfaState nextState;
    Set<NfaState> epsilonMoves = new HashSet<>();

    NfaState(LexicalStateData lexicalState) {
        this.lexicalState = lexicalState;
        nfaData = lexicalState.getNfaData();
        this.grammar = lexicalState.getGrammar();
        this.lexerData = grammar.getLexerData();
        nfaData.getAllStates().add(this);
    }

    public int getIndex() {
        return index;
    }

    public int getNonAsciiMethod() {
        return nonAsciiMethod;
    }

    public List<Integer> getNonAsciiMoveIndices() {
        return nonAsciiMoveIndices;
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

    public int getInNextOf() {
        return inNextOf;
    }

    RegularExpression getType() {return type;}

    void setType(RegularExpression type) {
        this.type = type;
    }

    boolean hasAsciiMove(int c) {
        return asciiMoves.get(c);
    }

    void incrementInNextOf() {
        this.inNextOf++;
    }

    public LexicalStateData getLexicalState() {
        return lexicalState;
    }

    public NfaState getNextState() {
        return nextState;
    }

    public int getKindToPrint() {
        return nextState.type == null ? Integer.MAX_VALUE : nextState.type.getOrdinal();
    }

    public RegularExpression getNextStateType() {
        return nextState.type;
    }

    public int getEpsilonMoveCount() {
        return epsilonMoves.size();
    }

    public boolean isNeeded(int byteNum) {
        if (byteNum < 0) {
            return isNeededNonAscii();
        }
        return byteNum == 0 ? asciiMoves.previousSetBit(63) >=0 : asciiMoves.nextSetBit(64) >=64; 
    }

    public boolean isNeededNonAscii() {
        return nonAsciiMethod != -1;
    }

    void addEpsilonMove(NfaState newState) {
        epsilonMoves.add(newState);
    }

    void addCharMove(int c) {
        addRange(c, c);
    }

    void addRange(int left, int right) {
        assert right>=left;
        for (int c = left; c <=right && c<128; c++) {
            asciiMoves.set(c);
        }
        left = Math.max(left, 128);
        if (right >= left) {
            rangeMovesLeftSide.add(left);
            rangeMovesRightSide.add(right);
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
                || !rangeMovesLeftSide.isEmpty();
    }
    
    void generateCode() {
        if (index != -1)
            return;
        if (nextState != null) {
            nextState.generateCode();
        }
        if (index == -1 && hasTransitions()) {
            this.index = lexicalState.getIndexedAllStates().size();
            lexicalState.getIndexedAllStates().add(this);
            nextState.getEpsilonMovesString();
        }
    }

    public String getEpsilonMovesString() {
        if (epsilonMovesString == null && !epsilonMoves.isEmpty()) {
            int[] stateNames = new int[getEpsilonMoveCount()];
            epsilonMovesString = "{";
            int idx =0;
            for (NfaState epsilonMove : epsilonMoves) {
                epsilonMove.generateCode();
                epsilonMove.inNextOf++;
                stateNames[idx++] = epsilonMove.index;
                epsilonMovesString += epsilonMove.index + ",";
            }
            epsilonMovesString += "};";
            nfaData.getAllNextStates().put(epsilonMovesString, stateNames);            
        }
        if (epsilonMovesString == null) epsilonMovesString = "null;";
        return epsilonMovesString;
    }

    final boolean canMoveUsingChar(int c) {
        if (c < 128) {
            return asciiMoves.get(c);
        }
        // Iterate thru the table to see if the current char
        // is in some range
        for (int i=0; i<rangeMovesLeftSide.size(); i++) {
            int left = rangeMovesLeftSide.get(i);
            int right = rangeMovesRightSide.get(i);
            if (c >= left && c <= right) 
                return true;
            else if (c < left || left == 0)
                break;
        }
        return false;
    }

    RegularExpression moveFrom(int c, List<NfaState> newStates) {
        if (canMoveUsingChar(c)) {
            newStates.addAll(nextState.epsilonMoves);
            return nextState.type;
        }
        return null;
    }

    /*
     * This function generates the bit vectors of low and hi bytes for common
     * bit vectors and returns those that are not common with anything (in
     * loBytes) and returns an array of indices that can be used to generate the
     * function names for char matching using the common bit vectors. It also
     * generates code to match a char with the common bit vectors. (Need a
     * better comment).
     * FIXME! Need to replace all the char with int
     * Also it is long overdue to rewrite this ugly legacy code anyway!
     * Im pretty sure that the following method can be written in 
     * far fewer lines!
     */
    void generateNonAsciiMoves() {
        if (rangeMovesLeftSide.isEmpty()) {
            return;
        }
        BitSet charMoves = new BitSet();
        for (int i=0; i< rangeMovesLeftSide.size(); i++) {
            int leftSide = rangeMovesLeftSide.get(i);
            int rightSide = rangeMovesRightSide.get(i);
            while (leftSide<=rightSide) {
                charMoves.set(leftSide++);
            }
        }
        BitSet superfluousSubsets = new BitSet();
        nonAsciiMoveIndices = new ArrayList<>();
        // The following 40-odd lines of code constitute a space
        // optimization. Commenting it all out produces
        // larger (redundant) XXXLexer.java files, but it all still works!
        for (int i = 0; i < 0xFF; i++) {
            BitSet commonSet = new BitSet();
            BitSet subSet = charMoves.get(256*i, 256*(i+1));
            if (subSet.isEmpty()) {
                superfluousSubsets.set(i);
            }
            if (superfluousSubsets.get(i)) {
                continue;
            }
            for (int j = i + 1; j <= 0xFF; j++) {
                if (!superfluousSubsets.get(j)) {
                    if (subSet.equals(charMoves.get(256*j, 256*(j+1)))) {
                        superfluousSubsets.set(j);
                        if (commonSet.isEmpty()) {
                            superfluousSubsets.set(i);
                            commonSet.set(i);
                        }
                        commonSet.set(j);
                    }
                }
            }
            if (!commonSet.isEmpty()) {
                String bitVector = bitSetToLong(commonSet);
                Map<BitSet, Integer> lohiByteLookup = lexerData.getLoHiByteLookup();
                List<String> allBitVectors = lexerData.getAllBitVectors();
                List<BitSet> allBitSets = lexerData.getAllBitSets();
                Integer ind = lohiByteLookup.get(commonSet);
                if (ind == null) {
                    allBitVectors.add(bitVector);
                    allBitSets.add(commonSet);

                    int lohiByteCount = lexerData.getLohiByteCount();
                    ind = lohiByteCount;
                    lohiByteLookup.put(commonSet, ind);
                    lexerData.incrementLohiByteCount();
                }
                nonAsciiMoveIndices.add(ind);
                bitVector = bitSetToLong(subSet);
                if ((ind = lohiByteLookup.get(subSet)) == null) {
                    allBitVectors.add(bitVector);
                    int lohiByteCount = lexerData.getLohiByteCount();
                    ind = lohiByteCount;
                    lohiByteLookup.put(subSet, ind);
                    lexerData.incrementLohiByteCount();
                }
                nonAsciiMoveIndices.add(ind);
            }
        }
// Up to here is just a space optimization. (Gradually coming to an understanding of this...)
// Without the space optimization, the nonAsciiMoveIndices array is empty
        for (int i = 0; i < 256; i++) {
            if (!superfluousSubsets.get(i)) {
                Map<BitSet, Integer> lohiByteLookup = lexerData.getLoHiByteLookup();
                BitSet subSet = charMoves.get(256*i, 256*(i+1));
                String longsString = bitSetToLong(subSet);
                List<String> allBitVectors = lexerData.getAllBitVectors();
                List<BitSet> allBitSets = lexerData.getAllBitSets();
                Integer ind = lohiByteLookup.get(subSet);
                if (ind == null) {
                    allBitVectors.add(longsString);
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
        updateDuplicateNonAsciiMoves();
    }

    static private String bitSetToLong(BitSet bs) {
        long[] longs = bs.toLongArray();
        longs = Arrays.copyOf(longs, 4);
        return "{0x" + Long.toHexString(longs[0]) + "L,0x"
               + Long.toHexString(longs[1]) + "L,0x"
               + Long.toHexString(longs[2]) + "L,0x"
               + Long.toHexString(longs[3]) + "L}";
    }

    private void updateDuplicateNonAsciiMoves() {
        List<NfaState> nonAsciiTableForMethod = lexerData.getNonAsciiTableForMethod();
        for (int i = 0; i < nonAsciiTableForMethod.size(); i++) {
            NfaState state = nonAsciiTableForMethod.get(i);
            if (loByteVec != null && loByteVec.equals(state.loByteVec) 
                    && nonAsciiMoveIndices != null 
                    && nonAsciiMoveIndices.equals(state.nonAsciiMoveIndices)) {
                nonAsciiMethod = i;
                return;
            }
        }
        nonAsciiMethod = nonAsciiTableForMethod.size();
        nonAsciiTableForMethod.add(this);
    }

    boolean intersects(NfaState other) {
        Set<NfaState> tempSet = new HashSet<>(epsilonMoves);
        tempSet.retainAll(other.epsilonMoves);
        return !tempSet.isEmpty();
    }
    
    public boolean isNextIntersects() {
        for (NfaState state : nfaData.getAllStates()) {
            if (this == state || state.index == -1 || index == state.index
                    || (state.nonAsciiMethod == -1))
                continue;
            if (intersects(state.nextState)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMoveState(NfaState other, int byteNum) {
        if (this.nextState.type != other.nextState.type) {
            return false;
        }
        if (byteNum < 0 && this.nonAsciiMethod != other.nonAsciiMethod) {
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
        for (NfaState state : nfaData.getAllStates()) {
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
        for (NfaState state : nfaData.getAllStates()) {
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
