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
import com.javacc.parser.ParseException;
import com.javacc.parser.tree.RegexpStringLiteral;

/**
 * Class to hold the data for generating the NFA's for 
 * regular expressions
 */
public class NfaData {

    final private LexicalStateData lexicalState;
    final private Grammar grammar;
    final private LexerData lexerData;
    private int[][] intermediateMatchedPos;
    private int dummyStateIndex = -1;
    private Map<String, int[]> compositeStateTable = new HashMap<>();
    private List<Map<String, long[]>> statesForPos;
    private List<Map<String, BitSet>> stateSetForPos;
    private Map<String, int[]> allNextStates = new HashMap<>();
    private List<NfaState> allStates = new ArrayList<>();

    List<NfaState> indexedAllStates = new ArrayList<>();
    Map<String, Integer> stateIndexFromComposite = new HashMap<>();
    NfaState initialState;


    NfaData(LexicalStateData lexicalState) {
        this.lexicalState = lexicalState;
        this.grammar = lexicalState.getGrammar();
        this.lexerData = grammar.getLexerData();
    }

    public Map<String, int[]> getCompositeStateTable() {
        return compositeStateTable;
    }

    public NfaState[] getStateSetFromCompositeKey(String key) {
        int[] indices = compositeStateTable.get(key);
        NfaState[] result = new NfaState[indices.length];
        for (int i = 0; i < indices.length; i++) {
            result[i] = allStates.get(indices[i]);
        }
        return result;
    }

    Map<String, int[]> getAllNextStates() {
        return allNextStates;
    }

    public int[] nextStatesFromKey(String key) {
        return allNextStates.get(key);
    }

    public int stateIndexFromComposite(String key) {
        return stateIndexFromComposite.get(key);
    }

    public NfaState getNfaState(int index) {
        return allStates.get(index);
    }

    public List<NfaState> getAllStates() {
        return allStates;
    }

    public List<Map<String, long[]>> getStatesForPos() {return statesForPos;}

    public List<Map<String, BitSet>> getStateSetForPos() {return stateSetForPos;}
    
    void generateData() {
        for (NfaState state : allStates) state.optimizeEpsilonMoves();
        for (NfaState epsilonMove : initialState.getEpsilonMoves()) {
            epsilonMove.generateCode();
        }
        if (!indexedAllStates.isEmpty()) {
            initialState.generateCode();
            initialState.generateInitMoves();
        }
        int initialOrdinal = initialState.getType() == null ? -1 : initialState.getType().getOrdinal();
        if (initialState.getType() != null && initialOrdinal != 0) {
            if (lexerData.getSkipSet().get(initialOrdinal)
                || (lexerData.getSpecialSet().get(initialOrdinal)))
                lexerData.hasSkipActions = true;
            else if (lexerData.getMoreSet().get(initialOrdinal))
                lexerData.hasMoreActions = true;
        }
        if (indexedAllStates.size() != 0 && !lexicalState.isMixedCase()) {
            generateNfaStartStates();
        }
        generateNfaStates();
        for (NfaState nfaState : allStates) {
            nfaState.generateNonAsciiMoves();
        }
        lexerData.expandStateSetSize(indexedAllStates.size());
    }

    void generateNfaStates() {
        if (indexedAllStates.isEmpty()) {
            return;
        }
        List<NfaState> v = allStates;
        allStates = new ArrayList<>();
        while (allStates.size() < indexedAllStates.size()) {
            allStates.add(null);
        }
        for (NfaState state : v) {
            if (state.getIndex() != -1 /*&& state != singlesToSkip*/)
                allStates.set(state.getIndex(), state);
        }
    }

    public int addStartStateSet(String stateSetString) {
        if (stateIndexFromComposite.containsKey(stateSetString)) {
            return stateIndexFromComposite.get(stateSetString);
        }
        int toRet = 0;
        int[] nameSet = allNextStates.get(stateSetString);
        if (nameSet.length == 1) {
            stateIndexFromComposite.put(stateSetString, nameSet[0]);
            return nameSet[0];
        }
        for (int i = 0; i < nameSet.length; i++) {
            if (nameSet[i] != -1) {
                NfaState state = indexedAllStates.get(nameSet[i]);
                state.setComposite(true);
                state.setCompositeStates(nameSet);
            }
        }
        while (toRet < nameSet.length && (indexedAllStates.get(nameSet[toRet]).getInNextOf() > 1)) {
            toRet++;
        }
        for (String key : compositeStateTable.keySet()) {
            if (!key.equals(stateSetString) && intersect(stateSetString, key)) {
                int[] other = compositeStateTable.get(key);

                while (toRet < nameSet.length
                        && (((indexedAllStates.get(nameSet[toRet])).getInNextOf() > 1) || arrayContains(other, nameSet[toRet]))) {
                    toRet++;
                }
            }
        }
        int result;
        if (toRet >= nameSet.length) {
            if (dummyStateIndex == -1) {
                result = dummyStateIndex = indexedAllStates.size();
            } else {
                result = ++dummyStateIndex;
            }
        } else {
            result = nameSet[toRet];
        }
        stateIndexFromComposite.put(stateSetString, result);
        compositeStateTable.put(stateSetString, nameSet);
        return result;
    }

    static private boolean arrayContains(int[] arr, int elem) {
        for (int i : arr) if (i==elem) return true;
        return false;
    }

    boolean intersect(String set1, String set2) {
        if (set1 == null || set2 == null)
            return false;
        int[] nameSet1 = allNextStates.get(set1);
        int[] nameSet2 = allNextStates.get(set2);
        if (nameSet1 == null || nameSet2 == null)
            return false;
        for (int i = nameSet1.length; i-- > 0;)
            for (int j = nameSet2.length; j-- > 0;)
                if (nameSet1[i] == nameSet2[j])
                    return true;
        return false;
    }

    boolean intersect(NfaState state1, NfaState state2) {
        return intersect(state1.getEpsilonMovesString(), state2.getEpsilonMovesString());
    }

    public int initStateName() {
        String s = initialState.getEpsilonMovesString();
        if (initialState.hasEpsilonMoves()) {
            return stateIndexFromComposite.get(s);
        }
        return -1;
    }
    
    String getStateSetString(List<NfaState> states) {
        if (states == null || states.size() == 0)
            return "null;";
        int[] set = new int[states.size()];
        String retVal = "{ ";
        for (int i = 0; i < states.size();) {
            int k;
            retVal += (k = (states.get(i)).getIndex()) + ", ";
            set[i] = k;
            if (i++ > 0 && i % 16 == 0)
                retVal += "\n";
        }
        retVal += "};";
        allNextStates.put(retVal, set);
        return retVal;
    }

    public int[] getStateSetIndicesForUse(String arrayString) {
        int[] set = allNextStates.get(arrayString);
        int[] result = lexerData.getTableToDump().get(arrayString);
        if (result == null) {
            result = new int[2];
            int lastIndex = lexerData.getLastIndex();
            result[0] = lastIndex;
            result[1] = lastIndex + set.length - 1;
            lexerData.setLastIndex(lastIndex + set.length);
            lexerData.getTableToDump().put(arrayString, result);
            lexerData.getOrderedStateSet().add(set);
        }
        return result;
    }

    boolean canStartNfaUsingAscii(int c) {
        assert c < 128 : "This should be impossible.";
        String epsilonMovesString = initialState.getEpsilonMovesString();
        if (epsilonMovesString == null || epsilonMovesString.equals("null;"))
            return false;
        int[] states = allNextStates.get(epsilonMovesString);
        for (int i = 0; i < states.length; i++) {
            NfaState state = indexedAllStates.get(states[i]);
            if (state.hasAsciiMove(c)) 
                return true;
        }
        return false;
    }


    void generateNfaStartStates() {
        Set<NfaState> seenStates = new HashSet<>();
        Map<String, String> stateSets = new HashMap<String, String>();
        String stateSetString = "";
        int maxKindsReqd = lexicalState.getMaxStringIndex() / 64 + 1;
        long[] actives;
        BitSet activeSet = new BitSet();
        List<NfaState> newStates = new ArrayList<>();

        statesForPos = new ArrayList<>();
        stateSetForPos = new ArrayList<>();
        for (int k = 0; k < lexicalState.getMaxStringLength(); k++) {
            statesForPos.add(null);
            stateSetForPos.add(null);
        }
        intermediateMatchedPos = new int[lexicalState.getMaxStringIndex() + 1][];
        for (RegularExpression re : lexerData.getRegularExpressions()) {
            if (!lexicalState.containsRegularExpression(re) || !(re instanceof RegexpStringLiteral)) {
                continue;
            }
            String image = re.getImage();
            int ordinal = re.getOrdinal();
            List<NfaState> oldStates = new ArrayList<NfaState>(initialState.getEpsilonMoves());
            intermediateMatchedPos[ordinal] = new int[image.length()];
            int jjmatchedPos = 0;
            int kind = Integer.MAX_VALUE;
            for (int charOffset = 0; charOffset < image.length(); charOffset++) {
                if (oldStates == null || oldStates.isEmpty()) {
                    // Here, charOffset > 0
                    jjmatchedPos = intermediateMatchedPos[ordinal][charOffset] = intermediateMatchedPos[ordinal][charOffset - 1];
                } else {
                    kind = moveFromSet(image.codePointAt(charOffset), oldStates, newStates);
                    oldStates.clear();
                    if (lexicalState.getDfaData().getStrKind(image.substring(0, charOffset + 1)) < kind) {
                        jjmatchedPos = 0;
                    } else if (kind != Integer.MAX_VALUE) {
                        jjmatchedPos = intermediateMatchedPos[ordinal][charOffset] = charOffset;
                    } 
                    else if (charOffset>0) {
                        jjmatchedPos = intermediateMatchedPos[ordinal][charOffset] = intermediateMatchedPos[ordinal][charOffset - 1];
                    }
                    stateSetString = getStateSetString(newStates);
                }
                if (kind == Integer.MAX_VALUE && (newStates == null || newStates.size() == 0))
                    continue;

                if (stateSets.get(stateSetString) == null) {
                    stateSets.put(stateSetString, stateSetString);
                    for (NfaState state : newStates) {
                        if (seenStates.contains(state)) state.incrementInNextOf();
                        else seenStates.add(state);
                    }
                } else {
                    for (NfaState state : newStates) {
                        seenStates.add(state);
                    }
                }
                List<NfaState> jjtmpStates = oldStates;
                oldStates = newStates;
                (newStates = jjtmpStates).clear();
                if (statesForPos.get(charOffset) == null) {
                    statesForPos.set(charOffset, new HashMap<>());
                    stateSetForPos.set(charOffset, new HashMap<>());
                }
                if ((actives = (statesForPos.get(charOffset).get(kind + ", " + jjmatchedPos + ", "
                        + stateSetString))) == null) {
                    actives = new long[maxKindsReqd];
                    statesForPos.get(charOffset).put(kind + ", " + jjmatchedPos + ", " + stateSetString,
                            actives);
                    stateSetForPos.get(charOffset).put(kind + ", " + jjmatchedPos + ", " + stateSetString,
                            activeSet);
                }
                actives[ordinal / 64] |= 1L << (ordinal % 64);
                activeSet.set(ordinal);
            }
        }
    }

    private static int moveFromSet(int c, List<NfaState> states, List<NfaState> newStates) {
        int result = Integer.MAX_VALUE;
        for (NfaState state : states) {
            result = Math.min(result, state.moveFrom(c, newStates));
        }
        return result;
    }

    String getLabel(int kind) {
        RegularExpression re = lexerData.getRegularExpression(kind);

        if (re instanceof RegexpStringLiteral)
            return " \"" + ParseException.addEscapes(re.getImage()) + "\"";
        else if (!re.getLabel().equals(""))
            return " <" + re.getLabel() + ">";
        else
            return " <token of kind " + kind + ">";
    }

    int getLine(int kind) {
        return lexerData.getRegularExpression(kind).getBeginLine();
    }

    int getColumn(int kind) {
        return lexerData.getRegularExpression(kind).getBeginColumn();
    }

    public int getStateSetForKind(int pos, int kind) {
        if (!lexicalState.isMixedCase() && !indexedAllStates.isEmpty()) {

            Map<String, long[]> allStateSets = statesForPos.get(pos);

            if (allStateSets == null)
                return -1;
            for (String s : allStateSets.keySet()) {
                long[] actives = allStateSets.get(s);

                s = s.substring(s.indexOf(", ") + 2);
                s = s.substring(s.indexOf(", ") + 2);

                if (s.equals("null;"))
                    continue;

                if (actives != null && (actives[kind / 64] & (1L << (kind % 64))) != 0L) {
                    return addStartStateSet(s);
                }
            }
        }
        return -1;
    }

    public boolean getDumpNfaStarts() {
        return indexedAllStates.size() != 0 
               && !lexicalState.isMixedCase() 
               && lexicalState.getMaxStringIndex() > 0;
    }

    public List<List<NfaState>> partitionStatesSetForAscii(NfaState[] states, int byteNum) {
        int[] cardinalities = new int[states.length];
        List<NfaState> original = new ArrayList<>(Arrays.asList(states));
        List<List<NfaState>> partition = new ArrayList<>();
        int cnt = 0;
        for (int i = 0; i < states.length; i++) {
            NfaState state = states[i];
            if (state.getAsciiMoves()[byteNum] != 0L) {
                int p = numberOfBitsSet(state.getAsciiMoves()[byteNum]);
                int j;
                for (j = 0; j < i; j++) {
                    if (cardinalities[j] <= p) break;
                }
                for (int k = i; k > j; k--) {
                    cardinalities[k] = cardinalities[k - 1];
                }
                cardinalities[j] = p;
                original.add(j, state);
                cnt++;
            }
        }
        original = original.subList(0, cnt);
        while (original.size() > 0) {
            NfaState state = original.get(0);
            original.remove(state);
            long bitVec = state.getAsciiMoves()[byteNum];
            List<NfaState> subSet = new ArrayList<NfaState>();
            subSet.add(state);
            for (Iterator<NfaState> it = original.iterator(); it.hasNext();) {
                NfaState otherState = it.next();
                if ((otherState.getAsciiMoves()[byteNum] & bitVec) == 0L) {
                    bitVec |= otherState.getAsciiMoves()[byteNum];
                    subSet.add(otherState);
                    it.remove();
                }
            }
            partition.add(subSet);
        }
        return partition;
    }


    static private int numberOfBitsSet(long l) {
        int ret = 0;
        for (int i = 0; i < 63; i++)
            if (((l >> i) & 1L) != 0L)
                ret++;
        return ret;
    }
}