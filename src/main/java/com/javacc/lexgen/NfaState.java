/* Copyright (c) 2008-2020 Jonathan Revusky, revusky@javacc.com
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
 * The state of a Non-deterministic Finite Automaton.
 */
public class NfaState {

    private Grammar grammar;
    private LexerData lexerData;
    private LexicalStateData lexicalState;
    private RegularExpression type;
    private List<NfaState> epsilonMoves = new ArrayList<>();
    private BitSet asciiMoves = new BitSet();
    private StringBuilder charMoveBuffer = new StringBuilder();
    private StringBuilder rangeMoveBuffer = new StringBuilder();
    private String epsilonMovesString;
    private NfaState next;
    final private int id;
    private boolean isFinal;
    private int usefulEpsilonMoveCount;
    private int nonAsciiMethod = -1;
    private boolean composite;
    private int[] nonAsciiMoveIndices;
    private List<Integer> loByteVec;
    private int index = -1;
    private int inNextOf;
    private int[] compositeStates;

    NfaState(LexicalStateData lexicalState) {
        this.lexicalState = lexicalState;
        this.grammar = lexicalState.getGrammar();
        this.lexerData = grammar.getLexerData();
        id = lexicalState.getNfaData().getAllStates().size();
        lexicalState.getNfaData().getAllStates().add(this);
    }

    public int getIndex() {
        return index;
    }

    RegularExpression getType() {return type;}

    void setType(RegularExpression type) {
        this.type = type;
    }

    void setFinal(boolean b) {
        this.isFinal = b;
    }

    void setComposite(boolean composite) {this.composite = composite;}

    int[] getCompositeStates() {return this.compositeStates;}

    void setCompositeStates(int[] compositeStates) {this.compositeStates = compositeStates;}

    public int getNonAsciiMethod() {
        return nonAsciiMethod;
    }

    public int[] getNonAsciiMoveIndices() {
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

    List<NfaState> getEpsilonMoves() {
        // REVISIT: The following line does not seem necessary, but I have it there
        // to replicate legacy behavior just in case.
        Collections.sort(epsilonMoves, (state1, state2) -> state1.id-state2.id);
        return epsilonMoves;
    }
    
    boolean hasAsciiMove(char c) {
        return asciiMoves.get(c);
    }

    public int getInNextOf() {
        return inNextOf;
    }

    void incrementInNextOf() {
        this.inNextOf++;
    }

    boolean hasEpsilonMoves() {
        return usefulEpsilonMoveCount > 0;
    }

    public LexicalStateData getLexicalState() {
        return lexicalState;
    }

    public boolean isComposite() {
        return composite;
    }

    public NfaState getNext() {
        return next;
    }

    void setNext(NfaState next) {
        this.next = next;
    }

    public int getKindToPrint() {
        return next.type == null ? Integer.MAX_VALUE : next.type.getOrdinal();
    }

    public int getUsefulEpsilonMoves() {
        return usefulEpsilonMoveCount;
    }

    public boolean isNeeded(int byteNum) {
        boolean  hasAsciiMoves = byteNum == 0 ? asciiMoves.previousSetBit(63) >=0 : asciiMoves.nextSetBit(64) >=64; 
        return (byteNum >= 0 && hasAsciiMoves) || (byteNum < 0 && nonAsciiMethod != -1);
    }

    void addEpsilonMove(NfaState newState) {
        if (!epsilonMoves.contains(newState)) epsilonMoves.add(newState);
    }

    void addCharMove(char c) {
        if (c < 128) {// ASCII char
            asciiMoves.set(c);
        } else {
            charMoveBuffer.append(c);
        }
    }

    void addRange(char left, char right) {
        for (char c = left; c <=right && c<128; c++) {
            asciiMoves.set(c);
        }
        left = (char) Math.max(left, 128);
        right = (char) Math.max(right, 128);
        if (right>left) {
            rangeMoveBuffer.append(left);
            rangeMoveBuffer.append(right);
        }
    }

    boolean closureDone = false;

    /**
     * This function computes the closure and also updates the kind so that any
     * time there is a move to this state, it can go on epsilon to a new state
     * in the epsilon moves that might have a lower kind of token number for the
     * same length.
     */
    private void epsilonClosure(Set<LexicalStateData> processedLexicalStates, Set<NfaState> visitedStates) {
        if (closureDone || visitedStates.contains(this)) {
            return;
        }
        visitedStates.add(this);
        // Recursively do closure
        for (NfaState state : new ArrayList<>(epsilonMoves)) {
            state.epsilonClosure(processedLexicalStates, visitedStates);
            for (NfaState otherState : state.epsilonMoves) {
                if (otherState.usefulState() && !epsilonMoves.contains(otherState)) {
                    addEpsilonMove(otherState);
                    processedLexicalStates.remove(lexicalState);
                } 
            }
            if (type == null || (state.type != null && state.type.getOrdinal() < type.getOrdinal())) {
                type = state.type;
            } 
        }
        if (hasTransitions() && !epsilonMoves.contains(this)) {
            addEpsilonMove(this);
        }
    }

    private boolean usefulState() {
        return isFinal || hasTransitions();
    }

    public boolean hasTransitions() {
        return asciiMoves.cardinality() >0
                || charMoveBuffer.length()>0
                || rangeMoveBuffer.length()>0;
    }
    
    void generateCode() {
        if (index != -1)
            return;
        if (next != null) {
            next.generateCode();
        }
        if (index == -1 && hasTransitions()) {
            this.index = lexicalState.getIndexedAllStates().size();
            lexicalState.getIndexedAllStates().add(this);
            generateNextStatesCode();
        }
    }

    void optimizeEpsilonMoves() {
        if (closureDone) return;
        // First do epsilon closure
        Set<LexicalStateData> processedLexicalStates = new HashSet<>();
        Set<NfaState> visitedStates = new HashSet<>();
        while (!processedLexicalStates.contains(lexicalState)) {
            processedLexicalStates.add(lexicalState);
            epsilonClosure(processedLexicalStates, visitedStates);
        }
        for (NfaState state : lexicalState.getNfaData().getAllStates()) {
            state.closureDone = visitedStates.contains(state);
        }
        for (Iterator<NfaState> it = epsilonMoves.iterator(); it.hasNext();) {
            NfaState state = it.next();
            if (state.hasTransitions()) {
                usefulEpsilonMoveCount++;
            } else {
                it.remove();
            }
        }
    }

    void generateNextStatesCode() {
        if (next.usefulEpsilonMoveCount > 0) {
            next.generateEpsilonMovesString();
        }
    }

    private void generateEpsilonMovesString() {
        int[] stateNames = new int[usefulEpsilonMoveCount];
        if (usefulEpsilonMoveCount > 0) {
            usefulEpsilonMoveCount = 0;
            epsilonMovesString = "{ ";
            for (NfaState epsilonMove : epsilonMoves) {
                if (epsilonMove.hasTransitions()) {
                    if (epsilonMove.index == -1)
                        epsilonMove.generateCode();

                    lexicalState.getIndexedAllStates().get(epsilonMove.index).inNextOf++;
                    stateNames[usefulEpsilonMoveCount] = epsilonMove.index;
                    epsilonMovesString += epsilonMove.index + ", ";
                    if (usefulEpsilonMoveCount++ > 0 && usefulEpsilonMoveCount % 16 == 0)
                        epsilonMovesString += "\n";
                }
            }
            epsilonMovesString += "};";
        }
        if (epsilonMovesString != null) {
            lexicalState.getNfaData().getAllNextStates().put(epsilonMovesString, stateNames);
        }
    }

    public String getEpsilonMovesString() {
        if (epsilonMovesString == null) {
            generateEpsilonMovesString();
        }
        return epsilonMovesString;
    }

    final boolean canMoveUsingChar(char c) {
        if (c < 128) {
            return asciiMoves.get(c);
        }
        // Just check directly if there is a move for this char
        if (charMoveBuffer.indexOf(Character.toString(c)) >=0) {
            return true;
        }
        // For ranges, iterate thru the table to see if the current char
        // is in some range
        for (int i = 0; i < rangeMoveBuffer.length(); i += 2) {
            char left = rangeMoveBuffer.charAt(i);
            char right = rangeMoveBuffer.charAt(i+1);
            if (c >= left && c <= right) 
                return true;
            else if (c < left || left == 0)
                break;
        }
        return false;
    }

    int getFirstValidPos(String s, int i, int len) {
        do {
            if (canMoveUsingChar(s.charAt(i)))
                return i;
        } while (++i < len);
        return i;
    }

    int moveFrom(char c, List<NfaState> newStates) {
        if (canMoveUsingChar(c)) {
            for (int i = next.epsilonMoves.size(); i-- > 0;) {
                newStates.add(next.epsilonMoves.get(i));
            }
            return getKindToPrint();
        }
        return Integer.MAX_VALUE;
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
        char hiByte;
        int cnt = 0;
        long[][] loBytes = new long[256][4];
        if ((charMoveBuffer.length() == 0) && rangeMoveBuffer.length() == 0)
            return;
        for (char ch : charMoveBuffer.toString().toCharArray()) {
            hiByte = (char) (ch >> 8);
            loBytes[hiByte][(ch & 0xFF)/64] |= (1L << ((ch & 0xFF) %64));
        }
        for (int i = 0; i < rangeMoveBuffer.length(); i += 2) {
            char r = (char) (rangeMoveBuffer.charAt(i + 1) & 0xff);
            hiByte = (char) (rangeMoveBuffer.charAt(i) >> 8);
            if (hiByte == (char) (rangeMoveBuffer.charAt(i + 1) >> 8)) {
                for (char c = (char) (rangeMoveBuffer.charAt(i) & 0xff); c <= r; c++) {
                    loBytes[hiByte][c / 64] |= (1L << (c % 64));
                }
                continue;
            }
            for (char c = (char) (rangeMoveBuffer.charAt(i) & 0xff); c <= 0xff; c++) {
                loBytes[hiByte][c / 64] |= (1L << (c % 64));
            }
            while (++hiByte < (char) (rangeMoveBuffer.charAt(i + 1) >> 8)) {
                loBytes[hiByte][0] |= 0xffffffffffffffffL;
                loBytes[hiByte][1] |= 0xffffffffffffffffL;
                loBytes[hiByte][2] |= 0xffffffffffffffffL;
                loBytes[hiByte][3] |= 0xffffffffffffffffL;
            }
            for (char c = 0; c <= r; c++) {
                loBytes[hiByte][c / 64] |= (1L << (c % 64));
            }
        }

        long[] common = null;
        boolean[] done = new boolean[256];
        for (int i = 0; i <= 255; i++) {
            if (done[i]
                    || (done[i] = loBytes[i][0] == 0 && loBytes[i][1] == 0 && loBytes[i][2] == 0
                            && loBytes[i][3] == 0))
                continue;

            for (int j = i + 1; j < 256; j++) {
                if (done[j])
                    continue;

                if (loBytes[i][0] == loBytes[j][0] && loBytes[i][1] == loBytes[j][1]
                        && loBytes[i][2] == loBytes[j][2] && loBytes[i][3] == loBytes[j][3]) {
                    done[j] = true;
                    if (common == null) {
                        done[i] = true;
                        common = new long[4];
                        common[i / 64] |= (1L << (i % 64));
                    }

                    common[j / 64] |= (1L << (j % 64));
                }
            }

            if (common != null) {
                Integer ind;
                String bitVector = "{\n   0x" + Long.toHexString(common[0]) + "L, " + "0x"
                        + Long.toHexString(common[1]) + "L, " + "0x" + Long.toHexString(common[2])
                        + "L, " + "0x" + Long.toHexString(common[3]) + "L}";
                Map<String, Integer> lohiByteTable = lexerData.getLoHiByteTable();
                List<String> allBitVectors = lexerData.getAllBitVectors();
                if ((ind = lohiByteTable.get(bitVector)) == null) {
                    allBitVectors.add(bitVector);

                    int lohiByteCount = lexerData.getLohiByteCount();
                    lohiByteTable.put(bitVector, ind = lohiByteCount);
                    lexerData.incrementLohiByteCount();
                }
                int[] tmpIndices = lexerData.getTempIndices();
                tmpIndices[cnt++] = ind;

                bitVector = "{\n   0x" + Long.toHexString(loBytes[i][0]) + "L, " + "0x"
                        + Long.toHexString(loBytes[i][1]) + "L, " + "0x"
                        + Long.toHexString(loBytes[i][2]) + "L, " + "0x"
                        + Long.toHexString(loBytes[i][3]) + "L}";
                if ((ind = lohiByteTable.get(bitVector)) == null) {
                    allBitVectors.add(bitVector);

                    int lohiByteCount = lexerData.getLohiByteCount();
                    lohiByteTable.put(bitVector, ind = lohiByteCount);
                    lexerData.incrementLohiByteCount();
                }

                tmpIndices[cnt++] = ind;

                common = null;
            }
        }

        nonAsciiMoveIndices = new int[cnt];
        System.arraycopy(lexerData.getTempIndices(), 0, nonAsciiMoveIndices, 0, cnt);

        for (int i = 0; i < 256; i++) {
            if (done[i])
                loBytes[i] = null;
            else {
                // System.out.print(i + ", ");
                String tmp;
                Integer ind;
                Map<String, Integer> lohiByteTable = lexerData.getLoHiByteTable();
                tmp = "{\n   0x" + Long.toHexString(loBytes[i][0]) + "L, " + "0x"
                        + Long.toHexString(loBytes[i][1]) + "L, " + "0x"
                        + Long.toHexString(loBytes[i][2]) + "L, " + "0x"
                        + Long.toHexString(loBytes[i][3]) + "L\n}";

                List<String> allBitVectors = lexerData.getAllBitVectors();

                if ((ind = lohiByteTable.get(tmp)) == null) {
                    allBitVectors.add(tmp);

                    int lohiByteCount = lexerData.getLohiByteCount();
                    lohiByteTable.put(tmp, ind = lohiByteCount);
                    lexerData.incrementLohiByteCount();
                }

                if (loByteVec == null)
                    loByteVec = new ArrayList<>();

                loByteVec.add(i);
                loByteVec.add(ind);
            }
        }
        updateDuplicateNonAsciiMoves();
    }

    private void updateDuplicateNonAsciiMoves() {
        List<NfaState> nonAsciiTableForMethod = lexerData.getNonAsciiTableForMethod();
        for (int i = 0; i < nonAsciiTableForMethod.size(); i++) {
            NfaState state = nonAsciiTableForMethod.get(i);
            if (loByteVec != null && loByteVec.equals(state.loByteVec) 
                    && nonAsciiMoveIndices != null 
                    && Arrays.equals(nonAsciiMoveIndices, state.nonAsciiMoveIndices)) {
                nonAsciiMethod = i;
                return;
            }
        }
        nonAsciiMethod = nonAsciiTableForMethod.size();
        nonAsciiTableForMethod.add(this);
    }

    void generateInitMoves() {
        getEpsilonMovesString();
        if (epsilonMovesString == null)
            epsilonMovesString = "null;";
        lexicalState.getNfaData().addStartStateSet(epsilonMovesString);
    }
    
    public boolean isNextIntersects() {
        for (NfaState state : lexicalState.getNfaData().getAllStates()) {
            if (this == state || state.index == -1 || index == state.index
                    || (state.nonAsciiMethod == -1))
                continue;

            if (lexicalState.getNfaData().intersect(state.next.epsilonMovesString, next.epsilonMovesString)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMoveState(NfaState other, int byteNum) {
        if (other.composite) {
            return false;
        }
        if (this.next.type != other.next.type) {
            return false;
        }
        if (byteNum < 0 && this.nonAsciiMethod != other.nonAsciiMethod) {
            return false;
        }
        if (byteNum >=0 && !this.asciiMoves.equals(other.asciiMoves)) {
            return false;
        }
        if (this.next.epsilonMovesString == null || other.next.epsilonMovesString == null) {
            return false;
        }
        return this.next.epsilonMovesString.equals(other.next.epsilonMovesString);
    }

    public List<NfaState> getMoveStates(int byteNum, BitSet statesAlreadyHandled) {
        List<NfaState> result = new ArrayList<NfaState>();
        for (NfaState state : lexicalState.getNfaData().getAllStates()) {
            if (!statesAlreadyHandled.get(state.index) && isMoveState(state, byteNum)) {
                statesAlreadyHandled.set(state.index);
                result.add(state);
            }
        }
        return result;
    }

    /**
     * @param byteNum
     *            either 0 or 1
     */
    public boolean isOnlyState(int byteNum) {
        for (NfaState state : lexicalState.getNfaData().getAllStates()) {
            BitSet bs = new BitSet();
            bs.or(asciiMoves);
            bs.and(state.asciiMoves);
            boolean intersects = bs.cardinality() > 0;
            if (intersects) {
                intersects = byteNum == 0 ? bs.previousSetBit(63) >=0 : bs.nextSetBit(64) >=0;
            }
            if (state.index != -1 && state.index != this.index && state.isNeeded(byteNum) && intersects) {
                return false;
            }
        }
        return true;
    }
 
}
