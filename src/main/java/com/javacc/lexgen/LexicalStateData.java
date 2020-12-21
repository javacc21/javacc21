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
import com.javacc.parser.tree.CodeBlock;
import com.javacc.parser.tree.RegexpChoice;
import com.javacc.parser.tree.RegexpSpec;
import com.javacc.parser.tree.RegexpStringLiteral;
import com.javacc.parser.tree.TokenProduction;

public class LexicalStateData {

    private Grammar grammar;
    private LexerData lexerData;
    private String name;

    private Map<String, int[]> compositeStateTable = new HashMap<>();
    private Map<String, Integer> stateIndexFromComposite = new HashMap<>();
    private List<TokenProduction> tokenProductions = new ArrayList<>();
    private NfaState initialState;
    private Map<String, Map<String, RegularExpression>> tokenTable = new HashMap<>();

    private NfaState singlesToSkip;
    private boolean mixed;
    private int initMatch;
    private RegularExpression currentRegexp;
    private HashSet<RegularExpression> regularExpressions = new HashSet<>();

    private int maxStringIndex, maxStringLength;
    private List<Map<String, KindInfo>> stringLiteralTables = new ArrayList<>();

    // with single char keys;
    private int[] maxStringLengthForActive = new int[100]; // 6400 tokens
    private int[][] intermediateKinds;
    private int[][] intermediateMatchedPos;
    private boolean subString[];
    private boolean[] subStringAtPos;
    private List<Map<String, long[]>> statesForPos;
    private String[] images;
    private int[] kindsForStates;
    private int[][] statesForState;
    private List<NfaState> allStates = new ArrayList<>();
    private List<NfaState> indexedAllStates = new ArrayList<>();
    private Map<String, int[]> allNextStates = new HashMap<>();
    private int dummyStateIndex = -1;
    private BitSet marks = new BitSet();
    private boolean done;

    public LexicalStateData(Grammar grammar, String name) {
        this.grammar = grammar;
        this.lexerData = grammar.getLexerData();
        this.name = name;
        singlesToSkip = new NfaState(this);
        singlesToSkip.setDummy(true);
        initialState = new NfaState(this);
    }

    Grammar getGrammar() {
        return grammar;
    }

    NfaState getInitialState() {return initialState;}

    RegularExpression getCurrentRegexp() {return currentRegexp;}

    public String getName() {
        return name;
    }

    boolean isMarked(int i) {
        return marks.get(i);
    }

    void setMark(int i) {
        marks.set(i);
    }

    void unsetMark(int i) {
        marks.clear(i);
    }

    void clearMarks() {
        marks.clear();
    }

    void setDone(boolean done) {this.done = done;}

    boolean isDone() {return this.done;}

    public int getIndex() {
        return lexerData.getIndex(name);
    }

    public int getMaxStringIndex() {
        return maxStringIndex;
    }

    public List<Map<String, KindInfo>> getStringLiteralTables() {
        return stringLiteralTables;
    }

    public int getMaxStringLength() {
        return maxStringLength;
    }

    void addTokenProduction(TokenProduction tokenProduction) {
        tokenProductions.add(tokenProduction);
    }

    public List<Map<String, long[]>> getStatesForPos() {
        return statesForPos;
    }

    public boolean hasNfa() {
        return !indexedAllStates.isEmpty();
    }

    public List<NfaState> getIndexedAllStates() {
        return indexedAllStates;
    }

    Map<String, int[]> getAllNextStates() {
        return allNextStates;
    }

    public NfaState getSinglesToSkip() {
        return this.singlesToSkip;
    }

    // FIXME! There is currently no testing in place for mixed case Lexical states!
    public boolean isMixedCase() {
        return mixed;
    }

    public int getInitMatch() {
        return initMatch;
    }

    public boolean[] getSubStringAtPos() {
        return subStringAtPos;
    }

    public int getMaxStringLengthForActive(int i) {
        return maxStringLengthForActive[i];
    }

    public boolean[] getSubString() {
        return this.subString;
    }

    public String getSuffix() {
        return name.equals("DEFAULT") ? "" : "_" + name;
    }

    public int[] getKindsForStates() {
        return kindsForStates;
    }

    public int[][] getStatesForState() {
        return statesForState;
    }

    public boolean getCreateStartNfa() {
        return !mixed && indexedAllStates.size() != 0;
    }

    public boolean containsRegularExpression(RegularExpression re) {
        return regularExpressions.contains(re);
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

    /**
     * This is a two-level symbol table that contains all simple tokens (those
     * that are defined using a single string (with or without a label). The
     * index to the first level hashtable is the string of the simple token
     * converted to upper case, and this maps to a second level hashtable. This
     * second level hashtable contains the actual string of the simple token and
     * maps it to its RegularExpression.
     */
    public Map<String, Map<String, RegularExpression>> getTokenTable() {
        return tokenTable;
    }

    public boolean getDumpNfaStarts() {
        return indexedAllStates.size() != 0 && !mixed && maxStringIndex > 0;
    }

    List<RegexpChoice> process() {
        images = new String[lexerData.getTokenCount()];
    	List<RegexpChoice> choices = new ArrayList<>();
        boolean isFirst = true;
        for (TokenProduction tp : tokenProductions) {
            choices.addAll(processTokenProduction(tp, isFirst));
            isFirst = false;
        }
        for (NfaState state : allStates) state.optimizeEpsilonMoves();
        for (NfaState epsilonMove : initialState.getEpsilonMoves()) {
            epsilonMove.generateCode();
        }
        if (indexedAllStates.size() != 0) {
            initialState.generateCode();
            initialState.generateInitMoves();
        }
        if (initialState.getKind() != Integer.MAX_VALUE && initialState.getKind() != 0) {
            if (lexerData.getSkipSet().get(initialState.getKind())
                || (lexerData.getSpecialSet().get(initialState.getKind())))
                lexerData.hasSkipActions = true;
            else if (lexerData.getMoreSet().get(initialState.getKind()))
                lexerData.hasMoreActions = true;
            if (initMatch == 0 || initMatch > initialState.getKind()) {
                initMatch = initialState.getKind();
                lexerData.hasEmptyMatch = true;
            }
        } else if (initMatch == 0) {
            initMatch = Integer.MAX_VALUE;
        }
        fillSubString();
        if (indexedAllStates.size() != 0 && !mixed) {
            generateNfaStartStates();
        }
        lexerData.expandStateSetSize(indexedAllStates.size());
        generateNfaStates();
        generateDfaData();
        for (NfaState nfaState : allStates) {
            nfaState.generateNonAsciiMoves();
        }
        for (Map.Entry<String, Integer> entry : stateIndexFromComposite.entrySet()) {
//REVISIT: I don't really grok this code. What is going on?            
            int state = entry.getValue();
            if (state >= indexedAllStates.size()) {
                if (state >= statesForState.length) {
                    int[][] prevStatesForState = statesForState;
                    statesForState = new int[state+1][];
                    for (int i=0; i<prevStatesForState.length;i++) {
                        statesForState[i] = prevStatesForState[i];
                    }
                }
                statesForState[state] = allNextStates.get(entry.getKey());
            }
        }
        return choices;
    }

    List<RegexpChoice> processTokenProduction(TokenProduction tp, boolean isFirst) {
        boolean ignoring = false;
        boolean ignore = tp.getIgnoreCase() || grammar.getOptions().getIgnoreCase();
        if (isFirst) {
            ignoring = ignore;
        }
        List<RegexpChoice> choices = new ArrayList<>();
        for (RegexpSpec respec : tp.getRegexpSpecs()) {
            currentRegexp = respec.getRegexp();
            regularExpressions.add(currentRegexp);
            currentRegexp.setIgnoreCase(ignore);
            if (currentRegexp.isPrivate()) {
                continue;
            }
            if (currentRegexp instanceof RegexpStringLiteral
                    && !((RegexpStringLiteral) currentRegexp).getImage().equals("")) {
                if (maxStringIndex <= currentRegexp.getOrdinal()) {
                    maxStringIndex = currentRegexp.getOrdinal() + 1;
                }
                generateDfa((RegexpStringLiteral) currentRegexp);
                if (!isFirst && !mixed && ignoring != ignore) {
                    mixed = true;
                }
            } else {
                if (currentRegexp instanceof RegexpChoice) {
                    choices.add((RegexpChoice) currentRegexp);
                }
                new NfaBuilder(currentRegexp, this, ignore).buildStates(currentRegexp);
            }
            if (respec.getNextState() != null && !respec.getNextState().equals(this.name))
                currentRegexp.setNewLexicalState(lexerData.getLexicalState(respec.getNextState()));

            if (respec.getCodeSnippet() != null && !respec.getCodeSnippet().isEmpty()) {
                currentRegexp.setCodeSnippet(respec.getCodeSnippet());
            }
            CodeBlock tokenAction = currentRegexp.getCodeSnippet();
            String kind = tp.getKind();
            if (kind.equals("SPECIAL_TOKEN")) {
                if (tokenAction != null || currentRegexp.getNewLexicalState() != null) {
                    lexerData.hasSkipActions = true;
                }
                lexerData.hasSpecial = true;
                lexerData.getSpecialSet().set(currentRegexp.getOrdinal());
                lexerData.getSkipSet().set(currentRegexp.getOrdinal());
                currentRegexp.setUnparsedToken();
            }
            else if (kind.equals("SKIP")) {
                lexerData.hasSkipActions |= (tokenAction != null);
                lexerData.hasSkip = true;
                lexerData.getSkipSet().set(currentRegexp.getOrdinal());
                currentRegexp.setSkip();
            }
            else if (kind.equals("MORE")) {
                lexerData.hasMoreActions |= tokenAction != null;
                lexerData.hasMore = true;
                lexerData.getMoreSet().set(currentRegexp.getOrdinal());
                currentRegexp.setMore();
            }
            else {
                lexerData.getTokenSet().set(currentRegexp.getOrdinal());
                currentRegexp.setRegularToken();
            }
        }
        return choices;

    }

    /**
     * Used for top level string literals.
     */
    private void generateDfa(final RegexpStringLiteral rsLiteral) {
        final int ordinal = rsLiteral.getOrdinal();
        final String stringLiteral = rsLiteral.getImage();
        final int stringLength = stringLiteral.length();
        this.maxStringLength = Math.max(stringLength, maxStringLength);
        this.maxStringIndex = Math.max(maxStringIndex, ordinal+1);
        while (stringLiteralTables.size() < stringLength) {
            stringLiteralTables.add(new HashMap<>());
        }
        for (int i = 0; i < stringLength; i++) {
            final char c = stringLiteral.charAt(i);
            String s = Character.toString(c);
            if (grammar.getOptions().getIgnoreCase()) {
                s = s.toLowerCase(Locale.ROOT);
            }
            Map<String, KindInfo> table = stringLiteralTables.get(i);
            if (!table.containsKey(s)) {
                table.put(s, new KindInfo(grammar));
            }
            KindInfo info = table.get(s);
            if (!grammar.getOptions().getIgnoreCase() && rsLiteral.getIgnoreCase()) {
                table.put(s.toLowerCase(Locale.ROOT), info);
                table.put(s.toUpperCase(Locale.ROOT), info);
            }
            if (i + 1 == stringLength) {
                info.insertFinalKind(ordinal);
            }
            else {
                info.insertValidKind(ordinal);
            }
        }
        maxStringLengthForActive[ordinal/64] = Math.max(maxStringLengthForActive[ordinal/64], stringLength - 1);
        images[ordinal] = stringLiteral;
    }

    public int getStateSetForKind(int pos, int kind) {
        if (!isMixedCase() && !indexedAllStates.isEmpty()) {

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

    String getLabel(int kind) {
        RegularExpression re = lexerData.getRegularExpression(kind);

        if (re instanceof RegexpStringLiteral)
            return " \"" + ParseException.addEscapes(((RegexpStringLiteral) re).getImage()) + "\"";
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

    void fillSubString() {
        subString = new boolean[maxStringIndex + 1];
        subStringAtPos = new boolean[maxStringLength];

        for (int i = 0; i < maxStringIndex; i++) {
            RegularExpression re = lexerData.getRegularExpression(i);
            subString[i] = false;

            if (images[i] == null || !this.containsRegularExpression(re)) {
                continue;
            }

            if (isMixedCase()) {
                // We will not optimize for mixed case
                subString[i] = true;
                subStringAtPos[images[i].length() - 1] = true;
                continue;
            }

            for (int j = 0; j < maxStringIndex; j++) {
                RegularExpression re2 = lexerData.getRegularExpression(j);
                if (j != i && this.containsRegularExpression(re2) && images[j] != null) {
                    if (images[j].indexOf(images[i]) == 0) {
                        subString[i] = true;
                        subStringAtPos[images[i].length() - 1] = true;
                        break;
                    } else if (grammar.getOptions().getIgnoreCase()
                            && images[j].toLowerCase().startsWith(images[i].toLowerCase())) {
                        subString[i] = true;
                        subStringAtPos[images[i].length() - 1] = true;
                        break;
                    }
                }
            }
        }
    }

    static public List<String> rearrange(Map<String, KindInfo> table) {
    	List<String> result = new ArrayList<>(table.keySet());
    	Collections.sort(result);
    	return result;
    }

    public int getKindToPrint(int kind, int index) {
        if (intermediateKinds != null && intermediateKinds[kind] != null
                && intermediateKinds[kind][index] < kind && intermediateMatchedPos != null
                && intermediateMatchedPos[kind][index] == index) {
            grammar.addWarning(null, " \"" + ParseException.addEscapes(images[kind])
                    + "\" cannot be matched as a string literal token " + "at line "
                    + getLine(kind) + ", column " + getColumn(kind) + ". It will be matched as "
                    + getLabel(intermediateKinds[kind][index]) + ".");
            return intermediateKinds[kind][index];
        } 
        return kind;
    }

    final int getStrKind(String str) {
        for (int i = 0; i < maxStringIndex; i++) {
            RegularExpression re = lexerData.getRegularExpression(i);
            if (!this.containsRegularExpression(re))
                continue;

            if (images[i] != null && images[i].equals(str))
                return i;
        }

        return Integer.MAX_VALUE;
    }

    public boolean generateDfaCase(String key, KindInfo info, int index) {
        char firstChar = key.charAt(0);
        for (int kind = 0; kind<maxStringIndex; kind++) {
        	if (index == 0 && firstChar < 128 && info.getFinalKindCnt() !=0
        			&& (indexedAllStates.size() == 0 || !canStartNfaUsingAscii(firstChar))) {
        			if (info.isFinalKind(kind) && !subString[kind]) {
        				if ((intermediateKinds != null && intermediateKinds[(kind)] != null
        						&& intermediateKinds[kind][index] < kind
        						&& intermediateMatchedPos != null && intermediateMatchedPos[kind][index] == index))
        					break;
                        else if (lexerData.getSkipSet().get(kind)
        				        && !lexerData.getSpecialSet().get(kind)
        						&& lexerData.getRegularExpression(kind).getCodeSnippet() == null
        						&& lexerData.getRegularExpression(kind).getNewLexicalState() == null) {
        					singlesToSkip.addChar(firstChar);
        					singlesToSkip.setKind(kind);

        					if (grammar.getOptions().getIgnoreCase()) {
        						if (firstChar != Character.toUpperCase(firstChar)) {
        							singlesToSkip.addChar(firstChar);
        							singlesToSkip.setKind(kind);
        						}

        						if (firstChar != Character.toLowerCase(firstChar)) {
        							singlesToSkip.addChar(firstChar);
        							singlesToSkip.setKind(kind);
        						}
        					}
        					return false;
        				}
        			}
        	}
        }
        return true;
    }

    void generateNfaStartStates() {
        boolean[] seen = new boolean[indexedAllStates.size()];
        Map<String, String> stateSets = new HashMap<String, String>();
        String stateSetString = "";
        int maxKindsReqd = maxStringIndex / 64 + 1;
        long[] actives;
        List<NfaState> newStates = new ArrayList<>();

        statesForPos = new ArrayList<Map<String, long[]>>(maxStringLength);
        for (int k = 0; k < maxStringLength; k++)
            statesForPos.add(null);
        intermediateKinds = new int[maxStringIndex + 1][];
        intermediateMatchedPos = new int[maxStringIndex + 1][];
        for (int i = 0; i < maxStringIndex; i++) {
            RegularExpression re = lexerData.getRegularExpression(i);
            if (!this.containsRegularExpression(re)) {
                continue;
            }
            String image = images[i];
            if (image == null || image.length() < 1) {
                continue;
            }
            List<NfaState> oldStates = new ArrayList<NfaState>(initialState.getEpsilonMoves());
            intermediateKinds[i] = new int[image.length()];
            intermediateMatchedPos[i] = new int[image.length()];
            int jjmatchedPos = 0;
            int kind = Integer.MAX_VALUE;

            for (int j = 0; j < image.length(); j++) {
                if (oldStates == null || oldStates.size() <= 0) {
                    // Here, j > 0
                    kind = intermediateKinds[i][j] = intermediateKinds[i][j - 1];
                    jjmatchedPos = intermediateMatchedPos[i][j] = intermediateMatchedPos[i][j - 1];
                } else {
                    kind = MoveFromSet(image.charAt(j), oldStates, newStates);
                    oldStates.clear();

                    if (getStrKind(image.substring(0, j + 1)) < kind) {
                        intermediateKinds[i][j] = kind = Integer.MAX_VALUE;
                        jjmatchedPos = 0;
                    } else if (kind != Integer.MAX_VALUE) {
                        intermediateKinds[i][j] = kind;
                        jjmatchedPos = intermediateMatchedPos[i][j] = j;
                    } else if (j == 0)
                        kind = intermediateKinds[i][j] = Integer.MAX_VALUE;
                    else {
                        kind = intermediateKinds[i][j] = intermediateKinds[i][j - 1];
                        jjmatchedPos = intermediateMatchedPos[i][j] = intermediateMatchedPos[i][j - 1];
                    }
                    stateSetString = getStateSetString(newStates);
                }
                if (kind == Integer.MAX_VALUE && (newStates == null || newStates.size() == 0))
                    continue;
                if (stateSets.get(stateSetString) == null) {
                    stateSets.put(stateSetString, stateSetString);
                    for (NfaState state : newStates) {
                        if (seen[state.getIndex()]) state.incrementInNextOf();
                        else seen[state.getIndex()] = true;
                    }
                } else {
                    for (NfaState state : newStates) {
                        seen[state.getIndex()] = true;
                    }
                }
                List<NfaState> jjtmpStates = oldStates;
                oldStates = newStates;
                (newStates = jjtmpStates).clear();
                if (statesForPos.get(j) == null) {
                    statesForPos.set(j, new HashMap<String, long[]>());
                }
                if ((actives = (statesForPos.get(j).get(kind + ", " + jjmatchedPos + ", "
                        + stateSetString))) == null) {
                    actives = new long[maxKindsReqd];
                    statesForPos.get(j).put(kind + ", " + jjmatchedPos + ", " + stateSetString,
                            actives);
                }
                actives[i / 64] |= 1L << (i % 64);
            }
        }
    }

    private boolean canStartNfaUsingAscii(char c) {
        assert c < 128 : "This should be impossible.";
        String s = initialState.getEpsilonMovesString();
        if (s == null || s.equals("null;"))
            return false;
        int[] states = allNextStates.get(s);
        for (int i = 0; i < states.length; i++) {
            NfaState tmp = indexedAllStates.get(states[i]);
            if (tmp.hasAsciiMove(c)) 
                return true;
        }
        return false;
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
                NfaState st = indexedAllStates.get(nameSet[i]);
                st.setComposite(true);
                st.setCompositeStates(nameSet);
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

    public boolean intersect(String set1, String set2) {
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

    public boolean intersect(NfaState state1, NfaState state2) {
        return intersect(state1.getEpsilonMovesString(), state2.getEpsilonMovesString());
    }

    void computeClosures() {
        for (NfaState state : allStates) {
            state.optimizeEpsilonMoves();
        }
    }

    private void generateNfaStates() {
        if (indexedAllStates.isEmpty()) {
            return;
        }
        List<NfaState> v = allStates;
        allStates = new ArrayList<>();
        while (allStates.size() < indexedAllStates.size()) {
            allStates.add(null);
        }
        for (NfaState state : v) {
            if (state.getIndex() != -1 && !state.isDummy())
                allStates.set(state.getIndex(), state);
        }
        for (NfaState nfaState : allStates) {
            if (nfaState.getLexicalState() != this || !nfaState.hasTransitions() || nfaState.isDummy()
                    || nfaState.getIndex() == -1)
                continue;
            if (kindsForStates == null) {
                kindsForStates = new int[indexedAllStates.size()];
                statesForState = new int[Math.max(indexedAllStates.size(), dummyStateIndex + 1)][];
            }
            kindsForStates[nfaState.getIndex()] = nfaState.getLookingFor().getOrdinal();
            statesForState[nfaState.getIndex()] = nfaState.getCompositeStates();
        }
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

    public int initStateName() {
        String s = initialState.getEpsilonMovesString();
        if (initialState.hasEpsilonMoves()) {
            return stateIndexFromComposite.get(s);
        }
        return -1;
    }

    static private int numberOfBitsSet(long l) {
        int ret = 0;
        for (int i = 0; i < 63; i++)
            if (((l >> i) & 1L) != 0L)
                ret++;
        return ret;
    }

    private static int MoveFromSet(char c, List<NfaState> states, List<NfaState> newStates) {
        int tmp;
        int retVal = Integer.MAX_VALUE;

        for (int i = states.size(); i-- > 0;)
            if (retVal > (tmp = states.get(i).moveFrom(c, newStates)))
                retVal = tmp;

        return retVal;
    }

    private void generateDfaData() {
        for (int i = 0; i < maxStringLength; i++) {
            Map<String, KindInfo> tab = stringLiteralTables.get(i);
            for (String key : tab.keySet()) {
                KindInfo info = tab.get(key);
                if (generateDfaCase(key, info, i)) {
                    if (info.getFinalKindCnt() != 0) {
                        for (int j = 0; j < maxStringIndex; j++) {
                        	if (info.isFinalKind(j) && !subString[j]) {
                        		getStateSetForKind(i, j); // <-- needs to be called!
                        	}
                        }
                    }
                }
            }
        }
    }

    static private boolean arrayContains(int[] arr, int elem) {
        for (int i : arr) if (i==elem) return true;
        return false;
    }
}
