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
    private StringBuilder charMoveBuffer = new StringBuilder();
    private StringBuilder rangeMoveBuffer = new StringBuilder();
    private NfaState stateForCase;
    private String epsilonMovesString;
    private int id;
    private RegularExpression lookingFor;
    private int usefulEpsilonMoves;
    private int nonAsciiMethod = -1;
    private boolean composite;
    private char matchSingleChar;
    private int[] nonAsciiMoveIndices;
    private BitSet asciiMoves = new BitSet();
    private NfaState next;
    private List<NfaState> epsilonMoves = new ArrayList<>();
    private int kindToPrint = Integer.MAX_VALUE;
    private List<Integer> loByteVec;
    private boolean onlyOneCharAdded, noCharsAdded=true;
    private boolean isFinal;
    private boolean dummy;
    private int index = -1;
    private int kind = Integer.MAX_VALUE;
    private int inNextOf;
    private int[] compositeStates;

    public NfaState(LexicalStateData lexicalState) {
        this.lexicalState = lexicalState;
        this.grammar = lexicalState.getGrammar();
        this.lexerData = grammar.getLexerData();
        id = lexicalState.idCnt++;
        lexicalState.allStates.add(this);
        if (lexicalState.getCurrentRegexp() != null) {
            lookingFor = lexicalState.getCurrentRegexp();
        }
    }

    public int getIndex() {
        return index;
    }

    int getKind() {return kind;}

    void setKind(int kind) {this.kind = kind;}

    RegularExpression getLookingFor() {return lookingFor;} 

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

    public NfaState getStateForCase() {
        return stateForCase;
    }

    boolean hasEpsilonMoves() {
        return usefulEpsilonMoves > 0;
    }

    public LexicalStateData getLexicalState() {
        return lexicalState;
    }

    public boolean isComposite() {
        return composite;
    }

    public boolean isDummy() {
        return dummy;
    }

    void setDummy(boolean dummy) {this.dummy = dummy;}

    public NfaState getNext() {
        return next;
    }

    public int getKindToPrint() {
        return kindToPrint;
    }

    public int getUsefulEpsilonMoves() {
        return usefulEpsilonMoves;
    }

    static private void insertInOrder(List<NfaState> stateList, NfaState stateToInsert) {
        for (ListIterator<NfaState> it = stateList.listIterator(); it.hasNext();) {
            NfaState state = it.next();
            if (state.id == stateToInsert.id) {
                return;
            }
            if (state.id > stateToInsert.id) {
                stateList.add(it.previousIndex(), stateToInsert);
                return;
            }
        }
        stateList.add(stateToInsert);
    }

    public boolean isNeeded(int byteNum) {
        boolean  hasAsciiMoves = byteNum == 0 ? asciiMoves.previousSetBit(63) >=0 : asciiMoves.nextSetBit(64) >=64; 
        return (byteNum >= 0 && hasAsciiMoves) || (byteNum < 0 && nonAsciiMethod != -1);
    }

    void addMove(NfaState newState) {
        insertInOrder(epsilonMoves, newState);
    }

    private void addASCIIMove(char c) {
        asciiMoves.set(c);
    }

    public void addChar(char c) {
        onlyOneCharAdded = noCharsAdded;
        noCharsAdded = false;

//        onlyChar++;
        matchSingleChar = c;
        if (c < 128) {// ASCII char
            addASCIIMove(c);
        } else {
            charMoveBuffer.append(c);
        }
    }

    void addRange(char left, char right) {
        noCharsAdded = onlyOneCharAdded = false;
//        onlyChar = 2;
        if (left < 128) {
            if (right < 128) {
                for (; left <= right; left++)
                    addASCIIMove(left);
                return;
            }
            for (; left < 128; left++)
                addASCIIMove(left);
        }
        rangeMoveBuffer.append(left);
        rangeMoveBuffer.append(right);
    }
    boolean closureDone = false;

    /**
     * This function computes the closure and also updates the kind so that any
     * time there is a move to this state, it can go on epsilon to a new state
     * in the epsilon moves that might have a lower kind of token number for the
     * same length.
     */
    private void epsilonClosure() {
        if (closureDone || lexicalState.mark[id]) {
            return;
        }
        lexicalState.mark[id] = true;
        // Recursively do closure
        for (NfaState state : new ArrayList<>(epsilonMoves)) {
            state.epsilonClosure();
            for (NfaState otherState : state.epsilonMoves) {
                if (otherState.usefulState() && !epsilonMoves.contains(otherState)) {
                    insertInOrder(epsilonMoves, otherState);
                    lexicalState.setDone(false);
                }
            }
            kind = Math.min(kind, state.kind);
        }
        if (hasTransitions() && !epsilonMoves.contains(this)) {
            insertInOrder(epsilonMoves, this);
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

    void mergeMoves(NfaState other) {
        // Warning : This method does not merge epsilon moves
        asciiMoves.or(other.asciiMoves);
        charMoveBuffer.append(other.charMoveBuffer);
        if (other.rangeMoveBuffer.length() > 0) {
            rangeMoveBuffer.append(other.rangeMoveBuffer);
        }
        if (other.kind < kind) {
            kind = other.kind;
        }
        if (other.kindToPrint < kindToPrint) {
            kindToPrint = other.kindToPrint;
        }
        isFinal |= other.isFinal;
    }

    // generates code (without outputting it) and returns the name used.
    void generateCode() {
        if (index != -1)
            return;
        if (getNext() != null) {
            getNext().generateCode();
            if (getNext().kind != Integer.MAX_VALUE)
                kindToPrint = getNext().kind;
        }
        if (index == -1 && hasTransitions()) {
            // stateName = lexicalState.generatedStates++;
            index = lexicalState.getGeneratedStates();
            lexicalState.indexedAllStates.add(this);
            generateNextStatesCode();
        }
    }

    void optimizeEpsilonMoves() {
        if (closureDone) return;
        // First do epsilon closure
        lexicalState.setDone(false);
        while (!lexicalState.isDone()) {
            if (lexicalState.mark == null || lexicalState.mark.length < lexicalState.allStates.size()) {
                lexicalState.mark = new boolean[lexicalState.allStates.size()];
            }
            for (int i = lexicalState.allStates.size(); i-- > 0;) {
                lexicalState.mark[i] = false;
            }
            lexicalState.setDone(true);
            epsilonClosure();
        }
        for (int i = lexicalState.allStates.size(); i-- > 0;) {
            (lexicalState.allStates.get(i)).closureDone = lexicalState.mark[(lexicalState.allStates
                    .get(i)).id];
        }
        for (Iterator<NfaState> it = epsilonMoves.iterator(); it.hasNext();) {
            NfaState state = it.next();
            if (state.hasTransitions()) {
                usefulEpsilonMoves++;
            } else {
                it.remove();
            }
        }

    }

    void generateNextStatesCode() {
        if (getNext().usefulEpsilonMoves > 0) {
            getNext().getEpsilonMovesString();
        }
    }

    public String getEpsilonMovesString() {
        if (epsilonMovesString != null) {
            return epsilonMovesString;
        }
        int[] stateNames = new int[usefulEpsilonMoves];
        int cnt = 0;
        if (usefulEpsilonMoves > 0) {
            NfaState tempState;
            epsilonMovesString = "{ ";
            for (NfaState epsilonMove : epsilonMoves) {
                if ((tempState = epsilonMove).hasTransitions()) {
                    if (tempState.index == -1)
                        tempState.generateCode();

                    lexicalState.indexedAllStates.get(tempState.index).inNextOf++;
                    stateNames[cnt] = tempState.index;
                    epsilonMovesString += tempState.index + ", ";
                    if (cnt++ > 0 && cnt % 16 == 0)
                        epsilonMovesString += "\n";
                }
            }
            epsilonMovesString += "};";
        }
        usefulEpsilonMoves = cnt;
        if (epsilonMovesString != null
                && lexicalState.allNextStates.get(epsilonMovesString) == null) {
            int[] statesToPut = new int[usefulEpsilonMoves];
            System.arraycopy(stateNames, 0, statesToPut, 0, cnt);
            lexicalState.allNextStates.put(epsilonMovesString, statesToPut);
        }
        return epsilonMovesString;
    }

    final boolean canMoveUsingChar(char c) {
//        if (onlyChar == 1)
        if (onlyOneCharAdded) 
            return c == matchSingleChar;
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
//        if (onlyChar == 1) {
        if (onlyOneCharAdded) {
            char c = matchSingleChar;
            while (c != s.charAt(i) && ++i < len)
                ;
            return i;
        }

        do {
            if (canMoveUsingChar(s.charAt(i)))
                return i;
        } while (++i < len);
        return i;
    }

    int moveFrom(char c, List<NfaState> newStates) {
        if (canMoveUsingChar(c)) {
            for (int i = getNext().epsilonMoves.size(); i-- > 0;)
                insertInOrder(newStates, getNext().epsilonMoves.get(i));

            return kindToPrint;
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
        lexicalState.addStartStateSet(epsilonMovesString);
    }

    public int OnlyOneBitSet(long l) {
        int oneSeen = -1;
        for (int i = 0; i < 64; i++)
            if (((l >> i) & 1L) != 0L) {
                if (oneSeen >= 0)
                    return -1;
                oneSeen = i;
            }

        return oneSeen;
    }


    public boolean selfLoops() {
        if (getNext() == null || getNext().epsilonMovesString == null)
            return false;

        int[] set = lexicalState.allNextStates.get(getNext().epsilonMovesString);
        return arrayContains(set, index);
    }
    
    static boolean arrayContains(int[] arr, int elem) {
        for (int i = arr.length; i-- > 0;) {
            if (arr[i] == elem) {
                return true;
            }
        }
        return false;

    }

    public boolean isNextIntersects() {
        if (selfLoops()) {
            return true;
        }
        for (NfaState state : lexicalState.allStates) {
            if (this == state || state.index == -1 || state.dummy || index == state.index
                    || (state.nonAsciiMethod == -1))
                continue;

            if (lexicalState.intersect(state.getNext().epsilonMovesString, getNext().epsilonMovesString)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMoveState(NfaState other, int byteNum) {
        if (other.composite) {
            return false;
        }
        if (this.kindToPrint != other.kindToPrint) {
            return false;
        }
        if (byteNum < 0 && this.nonAsciiMethod != other.nonAsciiMethod) {
            return false;
        }
        if (byteNum >=0 && !this.asciiMoves.equals(other.asciiMoves)) {
            return false;
        }
        if (this.getNext().epsilonMovesString == other.getNext().epsilonMovesString) {
            return true;
        }
        if (this.getNext().epsilonMovesString == null || other.getNext().epsilonMovesString == null) {
            return false;
        }
        return this.getNext().epsilonMovesString.equals(other.getNext().epsilonMovesString);
    }

    public List<NfaState> getMoveStates(int byteNum, BitSet statesAlreadyHandled) {
        List<NfaState> result = new ArrayList<NfaState>();
        for (NfaState state : lexicalState.allStates) {
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
        for (NfaState state : lexicalState.allStates) {
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

    public void setCharMoves(char[] charMoves) {
        for(char ch : charMoves) {
            if (ch != 0) charMoveBuffer.append(ch);
        }
    }

    public void setNext(NfaState next) {
        this.next = next;
    }
}
