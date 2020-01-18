/* Copyright (c) 2008-2019 Jonathan Revusky, revusky@javacc.com
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

package javacc.lexgen;

import java.util.*;

import javacc.Grammar;


import static javacc.JavaCCUtils.*;

/**
 * The state of a Non-deterministic Finite Automaton.
 */
public class NfaState {

    private Grammar grammar;
    private LexerData lexerData;
    private LexicalState lexicalState;
    private char[] rangeMoves = null;
    NfaState stateForCase;
    String epsilonMovesString;
    NfaState[] epsilonMoveArray;
    private int id;
    RegularExpression lookingFor;
    private int usefulEpsilonMoves = 0;
    int nonAsciiMethod = -1;
    boolean isComposite;
    private char matchSingleChar;
    private int[] nonAsciiMoveIndices;

    long[] asciiMoves = new long[2];
    private char[] charMoves = null;
    private NfaState next;
    Vector<NfaState> epsilonMoves = new Vector<NfaState>();
    int index = -1;
    int kind = Integer.MAX_VALUE;
    int inNextOf;
    private int kindToPrint = Integer.MAX_VALUE;
    boolean dummy = false;
    int[] compositeStates = null;
    boolean isFinal = false;
    private Vector<Integer> loByteVec;
    private int round = 0;
    int onlyChar = 0;

    public NfaState(LexicalState lexicalState) {
        this.lexicalState = lexicalState;
        this.grammar = lexicalState.getGrammar();
        this.lexerData = grammar.getLexerData();
        id = lexicalState.idCnt++;
        lexicalState.allStates.add(this);
        if (lexicalState.currentRegexp != null) {
            lookingFor = lexicalState.currentRegexp;
        }
    }

    NfaState createClone() {
        NfaState copy = new NfaState(lexicalState);

        copy.isFinal = isFinal;
        copy.kind = kind;
        copy.lookingFor = lookingFor;
        copy.inNextOf = inNextOf;

        copy.mergeMoves(this);

        return copy;
    }

    public int getIndex() {
        return index;
    }

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
        return asciiMoves;
    }

    public int getInNextOf() {
        return inNextOf;
    }

    public NfaState getStateForCase() {
        return stateForCase;
    }

    boolean hasEpsilonMoves() {
        return usefulEpsilonMoves > 0;
    }

    public LexicalState getLexicalState() {
        return lexicalState;
    }

    public boolean isComposite() {
        return isComposite;
    }

    public boolean isDummy() {
        return dummy;
    }

    public NfaState getNext() {
        return next;
    }

    public int getKindToPrint() {
        return kindToPrint;
    }

    public int getUsefulEpsilonMoves() {
        return usefulEpsilonMoves;
    }

    static private void InsertInOrder(List<NfaState> v, NfaState s) {
        int j;

        for (j = 0; j < v.size(); j++)
            if (v.get(j).id > s.id)
                break;
            else if (v.get(j).id == s.id)
                return;

        v.add(j, s);
    }

    public boolean isNeeded(int byteNum) {
        return (byteNum >= 0 && asciiMoves[byteNum] != 0L) || (byteNum < 0 && nonAsciiMethod != -1);

    }

    private static char[] ExpandCharArr(char[] oldArr, int incr) {
        char[] ret = new char[oldArr.length + incr];
        System.arraycopy(oldArr, 0, ret, 0, oldArr.length);
        return ret;
    }

    void addMove(NfaState newState) {
        InsertInOrder(epsilonMoves, newState);
    }

    private void addASCIIMove(char c) {
        asciiMoves[c / 64] |= (1L << (c % 64));
    }

    public void addChar(char c) {
        onlyChar++;
        matchSingleChar = c;
        int i;
        char temp;
        char temp1;

        if ((int) c < 128) // ASCII char
        {
            addASCIIMove(c);
            return;
        }

        if (getCharMoves() == null)
            setCharMoves(new char[10]);

        int len = getCharMoves().length;

        if (getCharMoves()[len - 1] != 0) {
            setCharMoves(ExpandCharArr(getCharMoves(), 10));
            len += 10;
        }

        for (i = 0; i < len; i++)
            if (getCharMoves()[i] == 0 || getCharMoves()[i] > c)
                break;
        temp = getCharMoves()[i];
        getCharMoves()[i] = c;

        for (i++; i < len; i++) {
            if (temp == 0)
                break;

            temp1 = getCharMoves()[i];
            getCharMoves()[i] = temp;
            temp = temp1;
        }
    }

    void addRange(char left, char right) {
        onlyChar = 2;
        int i;
        char tempLeft1, tempLeft2, tempRight1, tempRight2;

        if (left < 128) {
            if (right < 128) {
                for (; left <= right; left++)
                    addASCIIMove(left);

                return;
            }

            for (; left < 128; left++)
                addASCIIMove(left);
        }
        if (rangeMoves == null)
            rangeMoves = new char[20];

        int len = rangeMoves.length;

        if (rangeMoves[len - 1] != 0) {
            rangeMoves = ExpandCharArr(rangeMoves, 20);
            len += 20;
        }

        for (i = 0; i < len; i += 2)
            if (rangeMoves[i] == 0 || (rangeMoves[i] > left)
                    || ((rangeMoves[i] == left) && (rangeMoves[i + 1] > right)))
                break;

        tempLeft1 = rangeMoves[i];
        tempRight1 = rangeMoves[i + 1];
        rangeMoves[i] = left;
        rangeMoves[i + 1] = right;

        for (i += 2; i < len; i += 2) {
            if (tempLeft1 == 0)
                break;

            tempLeft2 = rangeMoves[i];
            tempRight2 = rangeMoves[i + 1];
            rangeMoves[i] = tempLeft1;
            rangeMoves[i + 1] = tempRight1;
            tempLeft1 = tempLeft2;
            tempRight1 = tempRight2;
        }
    }

    private static boolean EqualCharArr(char[] arr1, char[] arr2) {
        if (arr1 == arr2)
            return true;

        if (arr1 != null && arr2 != null && arr1.length == arr2.length) {
            for (int i = arr1.length; i-- > 0;)
                if (arr1[i] != arr2[i])
                    return false;

            return true;
        }

        return false;
    }

    boolean closureDone = false;

    /**
     * This function computes the closure and also updates the kind so that any
     * time there is a move to this state, it can go on epsilon to a new state
     * in the epsilon moves that might have a lower kind of token number for the
     * same length.
     */

    private void epsilonClosure() {
        if (closureDone || lexicalState.mark[id])
            return;

        lexicalState.mark[id] = true;

        // Recursively do closure
        for (NfaState state : epsilonMoves) {
            state.epsilonClosure();
        }

        Enumeration<NfaState> e = epsilonMoves.elements();

        while (e.hasMoreElements()) {
            NfaState tmp = e.nextElement();

            for (int i = 0; i < tmp.epsilonMoves.size(); i++) {
                NfaState tmp1 = tmp.epsilonMoves.get(i);
                if (tmp1.usefulState() && !epsilonMoves.contains(tmp1)) {
                    InsertInOrder(epsilonMoves, tmp1);
                    lexicalState.done = false;
                }
            }

            if (kind > tmp.kind)
                kind = tmp.kind;
        }

        if (hasTransitions() && !epsilonMoves.contains(this))
            InsertInOrder(epsilonMoves, this);
    }

    private boolean usefulState() {
        return isFinal || hasTransitions();
    }

    public boolean hasTransitions() {
        return (asciiMoves[0] != 0L || asciiMoves[1] != 0L
                || (getCharMoves() != null && getCharMoves()[0] != 0) || (rangeMoves != null && rangeMoves[0] != 0));
    }

    void mergeMoves(NfaState other) {
        // Warning : This function does not merge epsilon moves
        if (asciiMoves == other.asciiMoves) {
            grammar.addSemanticError(null, "Bug in JavaCC : Please send "
                    + "a report along with the input that caused this. Thank you.");
            throw new Error();
        }

        asciiMoves[0] = asciiMoves[0] | other.asciiMoves[0];
        asciiMoves[1] = asciiMoves[1] | other.asciiMoves[1];

        if (other.getCharMoves() != null) {
            if (getCharMoves() == null)
                setCharMoves(other.getCharMoves());
            else {
                char[] tmpCharMoves = new char[getCharMoves().length + other.getCharMoves().length];
                System.arraycopy(getCharMoves(), 0, tmpCharMoves, 0, getCharMoves().length);
                setCharMoves(tmpCharMoves);

                for (char charMove : other.getCharMoves())
                    addChar(charMove);
            }
        }

        if (other.rangeMoves != null) {
            if (rangeMoves == null)
                rangeMoves = other.rangeMoves;
            else {
                char[] tmpRangeMoves = new char[rangeMoves.length + other.rangeMoves.length];
                System.arraycopy(rangeMoves, 0, tmpRangeMoves, 0, rangeMoves.length);
                rangeMoves = tmpRangeMoves;
                for (int i = 0; i < other.rangeMoves.length; i += 2)
                    addRange(other.rangeMoves[i], other.rangeMoves[i + 1]);
            }
        }

        if (other.kind < kind)
            kind = other.kind;

        if (other.kindToPrint < kindToPrint)
            kindToPrint = other.kindToPrint;

        isFinal |= other.isFinal;
    }

    NfaState createEquivState(List<NfaState> states) {
        NfaState newState = states.get(0).createClone();

        newState.setNext(new NfaState(lexicalState));

        InsertInOrder(newState.getNext().epsilonMoves, states.get(0).getNext());

        for (int i = 1; i < states.size(); i++) {
            NfaState tmp2 = states.get(i);

            if (tmp2.kind < newState.kind)
                newState.kind = tmp2.kind;

            newState.isFinal |= tmp2.isFinal;

            InsertInOrder(newState.getNext().epsilonMoves, tmp2.getNext());
        }

        return newState;
    }

    private NfaState getEquivalentRunTimeState() {
        Outer: for (int i = lexicalState.allStates.size(); i-- > 0;) {
            NfaState other = lexicalState.allStates.get(i);

            if (this != other && other.index != -1 && kindToPrint == other.kindToPrint
                    && asciiMoves[0] == other.asciiMoves[0] && asciiMoves[1] == other.asciiMoves[1]
                    && EqualCharArr(getCharMoves(), other.getCharMoves())
                    && EqualCharArr(rangeMoves, other.rangeMoves)) {
                if (getNext() == other.getNext())
                    return other;
                else if (getNext() != null && other.getNext() != null) {
                    if (getNext().epsilonMoves.size() == other.getNext().epsilonMoves.size()) {
                        for (int j = 0; j < getNext().epsilonMoves.size(); j++)
                            if (getNext().epsilonMoves.get(j) != other.getNext().epsilonMoves.get(j))
                                continue Outer;

                        return other;
                    }
                }
            }
        }

        return null;
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
            NfaState tmp = getEquivalentRunTimeState();

            if (tmp != null) {
                index = tmp.index;
                // ????
                // tmp.inNextOf += inNextOf;
                // ????
                dummy = true;
                return;
            }

            // stateName = lexicalState.generatedStates++;
            index = lexicalState.getGeneratedStates();
            lexicalState.indexedAllStates.add(this);
            generateNextStatesCode();
        }
    }

    void optimizeEpsilonMoves(boolean optReqd) {
        int i;
        // First do epsilon closure
        lexicalState.done = false;
        while (!lexicalState.done) {
            if (lexicalState.mark == null
                    || lexicalState.mark.length < lexicalState.allStates.size())
                lexicalState.mark = new boolean[lexicalState.allStates.size()];

            for (i = lexicalState.allStates.size(); i-- > 0;)
                lexicalState.mark[i] = false;

            lexicalState.done = true;
            epsilonClosure();
        }

        for (i = lexicalState.allStates.size(); i-- > 0;)
            (lexicalState.allStates.get(i)).closureDone = lexicalState.mark[(lexicalState.allStates
                    .get(i)).id];

        // Warning : The following piece of code is just an optimization.
        // in case of trouble, just remove this piece.

        boolean sometingOptimized = true;

        NfaState newState = null;
        NfaState tmp1, tmp2;
        int j;
        Vector<NfaState> equivStates = null;

        while (sometingOptimized) {
            sometingOptimized = false;
            for (i = 0; optReqd && i < epsilonMoves.size(); i++) {
                if ((tmp1 = epsilonMoves.get(i)).hasTransitions()) {
                    for (j = i + 1; j < epsilonMoves.size(); j++) {
                        if ((tmp2 = epsilonMoves.get(j)).hasTransitions()
                                && (tmp1.asciiMoves[0] == tmp2.asciiMoves[0]
                                        && tmp1.asciiMoves[1] == tmp2.asciiMoves[1]
                                        && EqualCharArr(tmp1.getCharMoves(), tmp2.getCharMoves()) && EqualCharArr(
                                        tmp1.rangeMoves, tmp2.rangeMoves))) {
                            if (equivStates == null) {
                                equivStates = new Vector<NfaState>();
                                equivStates.add(tmp1);
                            }

                            InsertInOrder(equivStates, tmp2);
                            epsilonMoves.remove(j--);
                        }
                    }
                }

                if (equivStates != null) {
                    sometingOptimized = true;
                    String tmp = "";
                    for (NfaState equivState : equivStates) {
                        tmp += String.valueOf(equivState.id) + ", ";
                    }
                    newState = lexicalState.equivStatesTable.get(tmp);

                    if (newState == null) {
                        newState = createEquivState(equivStates);
                        lexicalState.equivStatesTable.put(tmp, newState);
                    }

                    epsilonMoves.remove(i--);
                    epsilonMoves.add(newState);
                    equivStates = null;
                    newState = null;
                }
            }

            for (i = 0; i < epsilonMoves.size(); i++) {
                // if ((tmp1 = (NfaState)epsilonMoves.get(i)).next == null)
                // continue;
                tmp1 = epsilonMoves.get(i);

                for (j = i + 1; j < epsilonMoves.size(); j++) {
                    tmp2 = epsilonMoves.get(j);

                    if (tmp1.getNext() == tmp2.getNext()) {
                        if (newState == null) {
                            newState = tmp1.createClone();
                            newState.setNext(tmp1.getNext());
                            sometingOptimized = true;
                        }

                        newState.mergeMoves(tmp2);
                        epsilonMoves.remove(j--);
                    }
                }

                if (newState != null) {
                    epsilonMoves.remove(i--);
                    epsilonMoves.add(newState);
                    newState = null;
                }
            }
        }

        // End Warning

        // Generate an array of states for epsilon moves (not vector)
        if (epsilonMoves.size() > 0) {
            for (i = 0; i < epsilonMoves.size(); i++)
                // Since we are doing a closure, just epsilon moves are
                // unncessary
                if (epsilonMoves.get(i).hasTransitions())
                    usefulEpsilonMoves++;
                else
                    epsilonMoves.removeElementAt(i--);
        }
    }

    void generateNextStatesCode() {
        if (getNext().usefulEpsilonMoves > 0)
            getNext().getEpsilonMovesString();
    }

    public String getEpsilonMovesString() {
        int[] stateNames = new int[usefulEpsilonMoves];
        int cnt = 0;

        if (epsilonMovesString != null)
            return epsilonMovesString;

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
        int i;

        if (onlyChar == 1)
            return c == matchSingleChar;

        if (c < 128)
            return ((asciiMoves[c / 64] & (1L << c % 64)) != 0L);

        // Just check directly if there is a move for this char
        if (getCharMoves() != null && getCharMoves()[0] != 0) {
            for (i = 0; i < getCharMoves().length; i++) {
                if (c == getCharMoves()[i])
                    return true;
                else if (c < getCharMoves()[i] || getCharMoves()[i] == 0)
                    break;
            }
        }

        // For ranges, iterate thru the table to see if the current char
        // is in some range
        if (rangeMoves != null && rangeMoves[0] != 0)
            for (i = 0; i < rangeMoves.length; i += 2)
                if (c >= rangeMoves[i] && c <= rangeMoves[i + 1])
                    return true;
                else if (c < rangeMoves[i] || rangeMoves[i] == 0)
                    break;
        return false;
    }

    int getFirstValidPos(String s, int i, int len) {
        if (onlyChar == 1) {
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
                InsertInOrder(newStates, getNext().epsilonMoves.get(i));

            return kindToPrint;
        }

        return Integer.MAX_VALUE;
    }

    static int moveFromSetForRegEx(char c, NfaState[] states, NfaState[] newStates, int round) {
        int start = 0;
        int sz = states.length;

        for (int i = 0; i < sz; i++) {
            NfaState tmp1, tmp2;

            if ((tmp1 = states[i]) == null)
                break;

            if (tmp1.canMoveUsingChar(c)) {
                if (tmp1.kindToPrint != Integer.MAX_VALUE) {
                    newStates[start] = null;
                    return 1;
                }

                NfaState[] v = tmp1.getNext().epsilonMoveArray;
                for (int j = v.length; j-- > 0;) {
                    if ((tmp2 = v[j]).round != round) {
                        tmp2.round = round;
                        newStates[start++] = tmp2;
                    }
                }
            }
        }

        newStates[start] = null;
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
        int i, j;
        char hiByte;
        int cnt = 0;
        long[][] loBytes = new long[256][4];

        if ((getCharMoves() == null || getCharMoves()[0] == 0) && (rangeMoves == null || rangeMoves[0] == 0))
            return;

        if (getCharMoves() != null) {
            for (i = 0; i < getCharMoves().length; i++) {
                if (getCharMoves()[i] == 0)
                    break;

                hiByte = (char) (getCharMoves()[i] >> 8);
                loBytes[hiByte][(getCharMoves()[i] & 0xff) / 64] |= (1L << ((getCharMoves()[i] & 0xff) % 64));
            }
        }

        if (rangeMoves != null) {
            for (i = 0; i < rangeMoves.length; i += 2) {
                if (rangeMoves[i] == 0)
                    break;

                char c, r;

                r = (char) (rangeMoves[i + 1] & 0xff);
                hiByte = (char) (rangeMoves[i] >> 8);

                if (hiByte == (char) (rangeMoves[i + 1] >> 8)) {
                    for (c = (char) (rangeMoves[i] & 0xff); c <= r; c++)
                        loBytes[hiByte][c / 64] |= (1L << (c % 64));

                    continue;
                }

                for (c = (char) (rangeMoves[i] & 0xff); c <= 0xff; c++)
                    loBytes[hiByte][c / 64] |= (1L << (c % 64));

                while (++hiByte < (char) (rangeMoves[i + 1] >> 8)) {
                    loBytes[hiByte][0] |= 0xffffffffffffffffL;
                    loBytes[hiByte][1] |= 0xffffffffffffffffL;
                    loBytes[hiByte][2] |= 0xffffffffffffffffL;
                    loBytes[hiByte][3] |= 0xffffffffffffffffL;
                }

                for (c = 0; c <= r; c++)
                    loBytes[hiByte][c / 64] |= (1L << (c % 64));
            }
        }

        long[] common = null;
        boolean[] done = new boolean[256];

        for (i = 0; i <= 255; i++) {
            if (done[i]
                    || (done[i] = loBytes[i][0] == 0 && loBytes[i][1] == 0 && loBytes[i][2] == 0
                            && loBytes[i][3] == 0))
                continue;

            for (j = i + 1; j < 256; j++) {
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

        for (i = 0; i < 256; i++) {
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
                    loByteVec = new Vector<Integer>();

                loByteVec.add(i);
                loByteVec.add(ind);
            }
        }
        // System.out.println("");
        updateDuplicateNonAsciiMoves();
    }

    private void updateDuplicateNonAsciiMoves() {
        List<NfaState> nonAsciiTableForMethod = lexerData.getNonAsciiTableForMethod();
        for (int i = 0; i < nonAsciiTableForMethod.size(); i++) {
            NfaState tmp = nonAsciiTableForMethod.get(i);
            if (EqualLoByteVectors(loByteVec, tmp.loByteVec)
                    && EqualNonAsciiMoveIndices(nonAsciiMoveIndices, tmp.nonAsciiMoveIndices)) {
                nonAsciiMethod = i;
                return;
            }
        }

        nonAsciiMethod = nonAsciiTableForMethod.size();
        nonAsciiTableForMethod.add(this);
    }

    private static boolean EqualLoByteVectors(List<Integer> vec1, List<Integer> vec2) {
        if (vec1 == null || vec2 == null)
            return false;

        if (vec1 == vec2)
            return true;

        if (vec1.size() != vec2.size())
            return false;

        for (int i = 0; i < vec1.size(); i++) {
            if (!vec1.get(i).equals(vec2.get(i)))
                return false;
        }

        return true;
    }

    private static boolean EqualNonAsciiMoveIndices(int[] moves1, int[] moves2) {
        if (moves1 == moves2)
            return true;

        if (moves1 == null || moves2 == null)
            return false;

        if (moves1.length != moves2.length)
            return false;

        for (int i = 0; i < moves1.length; i++) {
            if (moves1[i] != moves2[i])
                return false;
        }

        return true;
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

    final void fixNextStates(int[] newSet) {
        getNext().usefulEpsilonMoves = newSet.length;
        // next.epsilonMovesString = GetStateSetString(newSet);
    }

    public boolean selfLoops() {
        if (getNext() == null || getNext().epsilonMovesString == null)
            return false;

        int[] set = lexicalState.allNextStates.get(getNext().epsilonMovesString);
        return ElemOccurs(index, set) >= 0;
    }

    public boolean getNextIntersects() {
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
        if (other.isComposite) {
            return false;
        }
        if (this.kindToPrint != other.kindToPrint) {
            return false;
        }
        if (byteNum < 0 && this.nonAsciiMethod != other.nonAsciiMethod) {
            return false;
        }
        if (byteNum >= 0 && this.asciiMoves != other.asciiMoves) {
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
            if (state.index != -1 && state.index != this.index && state.isNeeded(byteNum)
                    && ((asciiMoves[byteNum] & state.asciiMoves[byteNum]) != 0L)) {
                return false;
            }
        }
        return true;
    }

    public void setCharMoves(char[] charMoves) {
        this.charMoves = charMoves;
    }

    char[] getCharMoves() {
        return charMoves;
    }

    public void setNext(NfaState next) {
        this.next = next;
    }
}
