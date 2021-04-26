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
import com.javacc.parser.tree.RegexpStringLiteral;

/**
 * Class to hold the data for generating the NFA's for 
 * regular expressions. Each lexical state
 * has one NfaData object.
 */ 
public class NfaData {

    final private LexicalStateData lexicalState;
    final private Grammar grammar;
    final private LexerData lexerData;
    private int dummyStateIndex = -1;
    private Set<Set<NfaState>> allCompositeStateSets = new HashSet<>();
    private List<Map<String, BitSet>> stateSetForPos = new ArrayList<>();
    private Map<Set<NfaState>, Integer> stateIndexFromStateSet = new HashMap<>();
    Set<NfaState> allStates = new HashSet<>();
    Map<Integer, NfaState> indexedAllStates = new HashMap<>();
    NfaState initialState;

    NfaData(LexicalStateData lexicalState) {
        this.lexicalState = lexicalState;
        this.grammar = lexicalState.getGrammar();
        this.lexerData = grammar.getLexerData();
    }

    public Set<Set<NfaState>> getAllCompositeStateSets() {
        return allCompositeStateSets;
    }

    static private List<Integer> epsilonMovesStringToIntArray(String s) {
        List<Integer> result = new ArrayList<>();
        StringTokenizer st = new StringTokenizer(s, "{},;null", false);
        while (st.hasMoreTokens()) {
            result.add(Integer.valueOf(st.nextToken()));
        }
        return result;
    }

    public Collection<NfaState> getAllStates() {
        return indexedAllStates.values();
    }

    public List<Map<String, BitSet>> getStateSetForPos() {
        return stateSetForPos;
    }
    
    void generateData() {
        for (NfaState state : allStates) {
            state.doEpsilonClosure();
        }
        initialState.generateCode();
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
        allStates.removeIf(state->state.getIndex()==-1);
    }

    public int getStartStateIndex(String stateSetString) {
        return getStartStateIndex(stateSetFromString(stateSetString));
    }

    public int getStartStateIndex(Set<NfaState> states) {
        if (states.isEmpty()) return -1;
        if (stateIndexFromStateSet.containsKey(states)) {
            return stateIndexFromStateSet.get(states);
        }
        List<Integer> nameSet = new ArrayList<>();
        for (NfaState state : states) nameSet.add(state.getIndex());
        if (nameSet.size() == 1) {
            stateIndexFromStateSet.put(states, nameSet.get(0));
            return nameSet.get(0);
        }
        if (dummyStateIndex == -1) {
            dummyStateIndex = indexedAllStates.size();
        } else {
            ++dummyStateIndex;
        }
        stateIndexFromStateSet.put(states, dummyStateIndex);
        allCompositeStateSets.add(states);
        return dummyStateIndex;
    }

    public int getInitialStateIndex() {
        return getStartStateIndex(initialState.epsilonMoves);
    }
    
    static private String buildStateSetString(Collection<NfaState> states) {
        if (states.isEmpty())
            return "null;";
        String retVal = "{";
        for (NfaState state : states) {
            retVal += state.getIndex()  + ",";
        }
        retVal += "};";
        return retVal;
    }

    private Set<NfaState> stateSetFromString(String stateSetString) {
        Set<NfaState> result = new HashSet<>();
        List<Integer> indexes = epsilonMovesStringToIntArray(stateSetString);
        for (int index : indexes) {
            NfaState state = indexedAllStates.get(index);
            result.add(state);
        }
        return result;
    }

    public int[] getStateSetIndicesForUse(NfaState state) {
        int[] result = lexerData.getTableToDump().get(state.epsilonMoves);
        if (result == null) {
            result = new int[2];
            result[0] = lexerData.lastIndex;
            result[1] = lexerData.lastIndex + state.getEpsilonMoveCount() - 1;
            lexerData.lastIndex += state.getEpsilonMoveCount();
            lexerData.getTableToDump().put(state.epsilonMoves, result);
            lexerData.getOrderedStateSets().add(state.epsilonMoves);
        }
        assert result.length == 2;
        return result;
    }

    boolean canStartNfaUsing(int c) {
        return initialState.epsilonMoves.stream().anyMatch(state->state.canMoveUsingChar(c));
    }

//What a total Rube Goldberg contraption!
    void generateNfaStartStates() {
        String stateSetString = "";
        List<NfaState> newStates = new ArrayList<>();
        while(stateSetForPos.size() < lexicalState.getMaxStringLength()) {
            stateSetForPos.add(new HashMap<>());
        }
        for (RegularExpression re : lexerData.getRegularExpressions()) {
            if (!lexicalState.containsRegularExpression(re) || !(re instanceof RegexpStringLiteral)) {
                continue;
            }
            String image = re.getImage();
            int ordinal = re.getOrdinal();
            List<NfaState> oldStates = new ArrayList<>(initialState.epsilonMoves);
            int[] positions = new int[image.length()];
            int matchedPosition = 0;
            for (int charOffset = 0; charOffset < image.length(); charOffset++) {
                RegularExpression reKind = null;
                if (oldStates.isEmpty()) {
                    // Here, charOffset > 0
                    matchedPosition = positions[charOffset] = positions[charOffset - 1];
                } else {
                    int ch = image.codePointAt(charOffset);
                    reKind = moveFromSet(ch, oldStates, newStates);
                    if (ch>0xFFFF) charOffset++;
                    oldStates.clear();
                    if (reKind != null && lexicalState.getDfaData().getStrKind(image.substring(0, charOffset + 1)) < reKind.getOrdinal()) {
                        matchedPosition = 0;
                    } else if (reKind != null) {
                        matchedPosition = positions[charOffset] = charOffset;
                    } 
                    else if (charOffset>0) {
                        matchedPosition = positions[charOffset] = positions[charOffset - 1];
                    }
                    stateSetString = buildStateSetString(newStates);
                }
                if (reKind == null && newStates.isEmpty())
                    continue;
                List<NfaState> jjtmpStates = oldStates;
                oldStates = newStates;
                (newStates = jjtmpStates).clear();
                int index = reKind == null ? Integer.MAX_VALUE : reKind.getOrdinal();
                String key = index + "," + matchedPosition + "," + stateSetString;
                BitSet activeSet= stateSetForPos.get(charOffset).get(key);
                if (activeSet == null) {
                    activeSet = new BitSet();
                    stateSetForPos.get(charOffset).put(key, activeSet);
                }
                activeSet.set(ordinal);
            }
        }
    }

    private static RegularExpression moveFromSet(int c, List<NfaState> states, List<NfaState> newStates) {
        RegularExpression result = null;
        for (NfaState state : states) {
            RegularExpression re = null;
            if (state.canMoveUsingChar(c)) {
                newStates.addAll(state.nextState.epsilonMoves);
                re = state.nextState.getType();
            }
            if (result == null) result = re;
            else if (re != null && re.getOrdinal()<result.getOrdinal()) result = re;
        }
        return result;
    }

    public int getStateSetForKind(int pos, int kind) {
        if (!lexicalState.isMixedCase() && !indexedAllStates.isEmpty()) {
            Map<String, BitSet> sets = stateSetForPos.get(pos);
            for (String key: sets.keySet()) {
                BitSet activeSet = sets.get(key);
                key = key.substring(key.indexOf(",") + 1);
                key = key.substring(key.indexOf(",") + 1);
                if (key.equals("null;")) continue;
                if (activeSet.get(kind)) {
                    return getStartStateIndex(key);
                }
            }
        }
        return -1;
    }

    public boolean getDumpNfaStarts() {
        return !indexedAllStates.isEmpty()
               && !lexicalState.isMixedCase() 
               && lexicalState.getMaxStringIndex() > 0;
    }
}