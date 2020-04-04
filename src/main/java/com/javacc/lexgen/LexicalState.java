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

package com.javacc.lexgen;

import java.util.*;

import com.javacc.Grammar;
import com.javacc.parser.ParseException;
import com.javacc.parser.tree.CharacterList;
import com.javacc.parser.tree.CodeBlock;
import com.javacc.parser.tree.RegexpChoice;
import com.javacc.parser.tree.RegexpSpec;
import com.javacc.parser.tree.RegexpStringLiteral;
import com.javacc.parser.tree.TokenProduction;

public class LexicalState {

    private Grammar grammar;
    private LexerData lexerData;
    private String name;
    private int index;
    private String suffix;

    int idCnt = 0;
    int dummyStateIndex = -1;
    boolean done;
    boolean mark[];
    Vector<NfaState> allStates = new Vector<>();
    List<NfaState> indexedAllStates = new ArrayList<>();
    Map<String, NfaState> equivStatesTable = new HashMap<>();
    Map<String, int[]> allNextStates = new Hashtable<>();
    private Hashtable<String, int[]> compositeStateTable = new Hashtable<>();
    private Hashtable<String, Integer> stateIndexFromComposite = new Hashtable<>();
    private Hashtable<String, int[]> stateSetsToFix = new Hashtable<>();
    private List<TokenProduction> tokenProductions = new ArrayList<>();
    private NfaState initialState;
    private Map<String, Map<String, RegularExpression>> tokenTable = new HashMap<>();

    private NfaState singlesToSkip;
    private boolean mixed;
    boolean canLoop;
    private boolean canReachOnMore;
    RegularExpression matchAnyChar;
    int initMatch;
    RegularExpression currentRegexp;
    private HashSet<RegularExpression> regularExpressions = new HashSet<>();

    private int maxStrKind, maxLen;
    private List<Map<String, KindInfo>> charPosKind = new ArrayList<>();

    // with single char keys;
    private int[] maxLenForActive = new int[100]; // 6400 tokens
    int[][] intermediateKinds;
    int[][] intermediateMatchedPos;

    private boolean subString[];
    boolean[] subStringAtPos;
    private List<Map<String, long[]>> statesForPos;
    private String[] images;
    private int[] kindsForStates;
    private int[][] statesForState;

    public LexicalState(Grammar grammar, String name) {
        this.grammar = grammar;
        this.lexerData = grammar.getLexerData();
        this.name = name;
        singlesToSkip = new NfaState(this);
        singlesToSkip.dummy = true;
        initialState = new NfaState(this);
    }

    Grammar getGrammar() {
        return grammar;
    }

    public String getName() {
        return name;
    }

    public int getIndex() {
        return index;
    }

    public int getMaxStrKind() {
        return maxStrKind;
    }

    public List<Map<String, KindInfo>> getCharPosKind() {
        return charPosKind;
    }

    public int getMaxLen() {
        return maxLen;
    }

    void addTokenProduction(TokenProduction tokenProduction) {
        tokenProductions.add(tokenProduction);
    }

    List<TokenProduction> getTokenProductions() {
        return tokenProductions;
    }

    public List<Map<String, long[]>> getStatesForPos() {
        return statesForPos;
    }

    public boolean hasNfa() {
        return indexedAllStates.size() != 0;
    }

    public int getGeneratedStates() {
        return indexedAllStates.size();
    }

    public List<NfaState> getIndexedAllStates() {
        return indexedAllStates;
    }

    public NfaState getSinglesToSkip() {
        return this.singlesToSkip;
    }

    void setMixedCase(boolean mixedCase) {
        this.mixed = mixedCase;
    }

    public boolean isMixedCase() {
        return mixed;
    }

    public boolean getCanLoop() {
        return canLoop;
    }

    public int getCanMatchAnyChar() {
        return matchAnyChar == null ? -1 : matchAnyChar.getOrdinal();
    }

    public RegularExpression getMatchAnyChar() {
        return matchAnyChar;
    }

    public int getInitMatch() {
        return initMatch;
    }

    public boolean[] getSubStringAtPos() {
        return subStringAtPos;
    }

    NfaState getInitialState() {
        return initialState;
    }
    
    public int getMaxLenForActive(int i) {
        return maxLenForActive[i];
    }

    public boolean[] getSubString() {
        return this.subString;
    }

    public int[][] getIntermediateKinds() {
        return intermediateKinds;
    }

    public int[][] getIntermediateMatchedPos() {
        return intermediateMatchedPos;
    }

    public String getSuffix() {
        return suffix;
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

    boolean containsRegularExpression(RegularExpression re) {
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
        return indexedAllStates.size() != 0 && !mixed && maxStrKind > 0;
    }

    void process(List<RegexpChoice> choices) {
        images = new String[lexerData.getTokenCount()];
        this.index = lexerData.getIndex(this.name);
        suffix = "_" + name;
        if (suffix.equals("_DEFAULT")) {
            suffix = "";
        }

        boolean isFirst = true;
        for (TokenProduction tp : tokenProductions) {
            processTokenProduction(choices, tp, isFirst);
            isFirst = false;
        }

        // Generate a static block for initializing the nfa transitions
        computeClosures();

        for (NfaState epsilonMove : initialState.epsilonMoves) {
            epsilonMove.generateCode();
        }

        if (indexedAllStates.size() != 0) {
            initialState.generateCode();
            initialState.generateInitMoves();
        }

        if (initialState.kind != Integer.MAX_VALUE && initialState.kind != 0) {
            if ((lexerData.toSkip[initialState.kind / 64] & (1L << initialState.kind)) != 0L
                    || (lexerData.toSpecial[initialState.kind / 64] & (1L << initialState.kind)) != 0L)
                lexerData.hasSkipActions = true;
            else if ((lexerData.toMore[initialState.kind / 64] & (1L << initialState.kind)) != 0L)
                lexerData.hasMoreActions = true;
            else
                lexerData.hasTokenActions = true;
            if (initMatch == 0 || initMatch > initialState.kind) {
                initMatch = initialState.kind;
                lexerData.hasEmptyMatch = true;
            }
        } else if (initMatch == 0) {
            initMatch = Integer.MAX_VALUE;
        }
        fillSubString();
        if (indexedAllStates.size() != 0 && !mixed) {
            generateNfaStartStates();
        }
        if (lexerData.stateSetSize < indexedAllStates.size())
            lexerData.stateSetSize = indexedAllStates.size();
        generateNfaStates();
        setupStateSetsForKinds();
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
        if (!stateSetsToFix.isEmpty()) {
            fixStateSets();
        }
    }

    void processTokenProduction(List<RegexpChoice> choices, TokenProduction tp, boolean isFirst) {
        boolean ignoring = false;
        boolean ignore = tp.getIgnoreCase() || grammar.getOptions().getIgnoreCase();
        if (isFirst) {
            ignoring = ignore;
        }
        for (RegexpSpec respec : tp.getRegexpSpecs()) {
            currentRegexp = respec.getRegexp();
            regularExpressions.add(currentRegexp);
            currentRegexp.setIgnoreCase(ignore);
            if (currentRegexp.isPrivate()) {
                continue;
            }
            if (currentRegexp instanceof RegexpStringLiteral
                    && !((RegexpStringLiteral) currentRegexp).getImage().equals("")) {
                if (maxStrKind <= currentRegexp.getOrdinal()) {
                    maxStrKind = currentRegexp.getOrdinal() + 1;
                }
                generateDfa((RegexpStringLiteral) currentRegexp);
                if (!isFirst && !mixed && ignoring != ignore) {
                    mixed = true;
                }
            } else if (currentRegexp.canMatchAnyChar()) {
                if (matchAnyChar == null || matchAnyChar.getOrdinal() > currentRegexp.getOrdinal())
                    matchAnyChar = currentRegexp;
            } else {
                if (currentRegexp instanceof RegexpChoice) {
                    choices.add((RegexpChoice) currentRegexp);
                }
                Nfa nfa = Nfa.buildNfa(currentRegexp, this, ignore);
                nfa.getEnd().isFinal = true;
                nfa.getEnd().kind = currentRegexp.getOrdinal();
                initialState.addMove(nfa.getStart());
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
                lexerData.toSpecial[currentRegexp.getOrdinal() / 64] |= 1L << (currentRegexp
                        .getOrdinal() % 64);
                lexerData.toSkip[currentRegexp.getOrdinal() / 64] |= 1L << (currentRegexp
                        .getOrdinal() % 64);
                currentRegexp.setSpecialToken();
            }
            else if (kind.equals("SKIP")) {
                lexerData.hasSkipActions |= (tokenAction != null);
                lexerData.hasSkip = true;
                lexerData.toSkip[currentRegexp.getOrdinal() / 64] |= 1L << (currentRegexp
                        .getOrdinal() % 64);
                currentRegexp.setSkip();
            }
            else if (kind.equals("MORE")) {
                lexerData.hasMoreActions |= tokenAction != null;
                lexerData.hasMore = true;
                lexerData.toMore[currentRegexp.getOrdinal() / 64] |= 1L << (currentRegexp
                        .getOrdinal() % 64);
                currentRegexp.setMore();

                if (currentRegexp.getNewLexicalState() != null) {
                    LexicalState ls = currentRegexp.getNewLexicalState();
                    ls.canReachOnMore = true;
                } else {
                    canReachOnMore = true;
                }
            }
            else {
                lexerData.hasTokenActions |= (tokenAction != null);
                lexerData.toToken[currentRegexp.getOrdinal() / 64] |= 1L << (currentRegexp
                        .getOrdinal() % 64);
                currentRegexp.setRegularToken();
            }
        }

    }

    /**
     * Used for top level string literals.
     */
    private void generateDfa(RegexpStringLiteral stringLiteral) {
        String s;
        Map<String, KindInfo> temp;
        KindInfo info;

        if (maxStrKind <= stringLiteral.getOrdinal()) {
            maxStrKind = stringLiteral.getOrdinal() + 1;
        }

        int imageLength = stringLiteral.getImage().length();
        if (imageLength > maxLen) {
            maxLen = imageLength;
        }

        for (int i = 0; i < imageLength; i++) {
            char c = stringLiteral.getImage().charAt(i);
            if (grammar.getOptions().getIgnoreCase()) {
                s = "" + Character.toLowerCase(c);
            } else {
                s = "" + c;
            }
            if (i >= charPosKind.size()) // Kludge, but OK
                charPosKind.add(temp = new HashMap<String, KindInfo>());
            else
                temp = charPosKind.get(i);

            if ((info = (KindInfo) temp.get(s)) == null)
                temp.put(s, info = new KindInfo(lexerData.getTokenCount()));

            if (i + 1 == imageLength)
                info.InsertFinalKind(stringLiteral.getOrdinal());
            else
                info.InsertValidKind(stringLiteral.getOrdinal());

            if (!grammar.getOptions().getIgnoreCase() && stringLiteral.getIgnoreCase()
                    && c != Character.toLowerCase(c)) {
                s = ("" + stringLiteral.getImage().charAt(i)).toLowerCase();

                if (i >= charPosKind.size()) // Kludge, but OK
                    charPosKind.add(temp = new HashMap<String, KindInfo>());
                else
                    temp = charPosKind.get(i);

                if ((info = (KindInfo) temp.get(s)) == null)
                    temp.put(s, info = new KindInfo(lexerData.getTokenCount()));

                if (i + 1 == imageLength)
                    info.InsertFinalKind(stringLiteral.getOrdinal());
                else
                    info.InsertValidKind(stringLiteral.getOrdinal());
            }

            if (!grammar.getOptions().getIgnoreCase() && stringLiteral.getIgnoreCase()
                    && c != Character.toUpperCase(c)) {
                s = ("" + stringLiteral.getImage().charAt(i)).toUpperCase();

                if (i >= charPosKind.size()) // Kludge, but OK
                    charPosKind.add(temp = new HashMap<String, KindInfo>());
                else
                    temp = charPosKind.get(i);

                if ((info = (KindInfo) temp.get(s)) == null)
                    temp.put(s, info = new KindInfo(lexerData.getTokenCount()));

                if (i + 1 == imageLength)
                    info.InsertFinalKind(stringLiteral.getOrdinal());
                else
                    info.InsertValidKind(stringLiteral.getOrdinal());
            }
        }

        maxLenForActive[stringLiteral.getOrdinal() / 64] = Math.max(maxLenForActive[stringLiteral
                .getOrdinal() / 64], imageLength - 1);
        images[stringLiteral.getOrdinal()] = stringLiteral.getImage();
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

    /**
     * Returns true if s1 starts with s2 (ignoring case for each character).
     */
    static private boolean StartsWithIgnoreCase(String s1, String s2) {
        if (s1.length() < s2.length())
            return false;

        for (int i = 0; i < s2.length(); i++) {
            char c1 = s1.charAt(i), c2 = s2.charAt(i);

            if (c1 != c2 && Character.toLowerCase(c2) != c1 && Character.toUpperCase(c2) != c1)
                return false;
        }

        return true;
    }

    void fillSubString() {
        subString = new boolean[maxStrKind + 1];
        subStringAtPos = new boolean[maxLen];

        for (int i = 0; i < maxStrKind; i++) {
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

            for (int j = 0; j < maxStrKind; j++) {
                RegularExpression re2 = lexerData.getRegularExpression(j);
                if (j != i && this.containsRegularExpression(re2) && images[j] != null) {
                    if (images[j].indexOf(images[i]) == 0) {
                        subString[i] = true;
                        subStringAtPos[images[i].length() - 1] = true;
                        break;
                    } else if (grammar.getOptions().getIgnoreCase()
                            && StartsWithIgnoreCase(images[j], images[i])) {
                        subString[i] = true;
                        subStringAtPos[images[i].length() - 1] = true;
                        break;
                    }
                }
            }
        }
    }

    static public String[] rearrange(Map<String, KindInfo> tab) {
        String[] ret = new String[tab.size()];
        int cnt = 0;
        for (String s : tab.keySet()) {
            int i = 0, j;
            char c = s.charAt(0);
            while (i < cnt && ret[i].charAt(0) < c) {
                i++;
            }
            if (i < cnt) {
                for (j = cnt - 1; j >= i; j--) {
                    ret[j + 1] = ret[j];
                }
            }
            ret[i] = s;
            cnt++;
        }
        return ret;
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
        } else if (index == 0 && matchAnyChar != null && matchAnyChar.getOrdinal() < kind) {
            grammar.addWarning(null, " \"" + ParseException.addEscapes(images[kind])
                    + "\" cannot be matched as a string literal token " + "at line "
                    + getLine(kind) + ", column " + getColumn(kind) + ". It will be matched as "
                    + matchAnyChar.getLabel() + ".");
            return matchAnyChar.getOrdinal();
        }
        return kind;
    }

    final int getStrKind(String str) {
        for (int i = 0; i < maxStrKind; i++) {
            RegularExpression re = lexerData.getRegularExpression(i);
            if (!this.containsRegularExpression(re))
                continue;

            if (images[i] != null && images[i].equals(str))
                return i;
        }

        return Integer.MAX_VALUE;
    }

    public boolean generateDfaCase(String key, KindInfo info, int index) {
        int maxLongsReqd = 1 + maxStrKind / 64;
        char c = key.charAt(0);
        int kind;
        int j, k;
        for (j = 0; j < maxLongsReqd; j++)
            if (info.finalKinds[j] != 0L)
                break;

        if (index == 0 && c < 128 && info.finalKindCnt != 0
                && (indexedAllStates.size() == 0 || !canStartNfaUsingAscii(c))) {

            for (k = 0; k < 64; k++) {
                if ((info.finalKinds[j] & (1L << k)) != 0L && !subString[kind = (j * 64 + k)]) {
                    if ((intermediateKinds != null && intermediateKinds[(j * 64 + k)] != null
                            && intermediateKinds[(j * 64 + k)][index] < (j * 64 + k)
                            && intermediateMatchedPos != null && intermediateMatchedPos[(j * 64 + k)][index] == index)
                            || (matchAnyChar != null && matchAnyChar.getOrdinal() < (j * 64 + k)))
                        break;
                    else if ((lexerData.toSkip[kind / 64] & (1L << (kind % 64))) != 0L
                            && (lexerData.toSpecial[kind / 64] & (1L << (kind % 64))) == 0L
                            && lexerData.getRegularExpression(kind).getCodeSnippet() == null
                            && lexerData.getRegularExpression(kind).getNewLexicalState() == null) {
                        singlesToSkip.addChar(c);
                        singlesToSkip.kind = kind;

                        if (grammar.getOptions().getIgnoreCase()) {
                            if (c != Character.toUpperCase(c)) {
                                singlesToSkip.addChar(c);
                                singlesToSkip.kind = kind;
                            }

                            if (c != Character.toLowerCase(c)) {
                                singlesToSkip.addChar(c);
                                singlesToSkip.kind = kind;
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
        int i, j, kind, jjmatchedPos = 0;
        int maxKindsReqd = maxStrKind / 64 + 1;
        long[] actives;
        List<NfaState> newStates = new ArrayList<NfaState>();
        List<NfaState> jjtmpStates;

        statesForPos = new ArrayList<Map<String, long[]>>(maxLen);
        for (int k = 0; k < maxLen; k++)
            statesForPos.add(null);
        intermediateKinds = new int[maxStrKind + 1][];
        intermediateMatchedPos = new int[maxStrKind + 1][];
        for (i = 0; i < maxStrKind; i++) {
            RegularExpression re = lexerData.getRegularExpression(i);
            if (!this.containsRegularExpression(re)) {
                continue;
            }

            String image = images[i];

            if (image == null || image.length() < 1) {
                continue;
            }
            List<NfaState> oldStates = new ArrayList<NfaState>(initialState.epsilonMoves);
            intermediateKinds[i] = new int[image.length()];
            intermediateMatchedPos[i] = new int[image.length()];
            jjmatchedPos = 0;
            kind = Integer.MAX_VALUE;

            for (j = 0; j < image.length(); j++) {
                if (oldStates == null || oldStates.size() <= 0) {
                    // Here, j > 0
                    kind = intermediateKinds[i][j] = intermediateKinds[i][j - 1];
                    jjmatchedPos = intermediateMatchedPos[i][j] = intermediateMatchedPos[i][j - 1];
                } else {
                    kind = MoveFromSet(image.charAt(j), oldStates, newStates);
                    oldStates.clear();

                    if (j == 0 && kind != Integer.MAX_VALUE && matchAnyChar != null
                            && kind > matchAnyChar.getOrdinal())
                        kind = matchAnyChar.getOrdinal();

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

                int p;
                if (stateSets.get(stateSetString) == null) {
                    stateSets.put(stateSetString, stateSetString);
                    for (p = 0; p < newStates.size(); p++) {
                        if (seen[newStates.get(p).index])
                            newStates.get(p).inNextOf++;
                        else
                            seen[newStates.get(p).index] = true;
                    }
                } else {
                    for (p = 0; p < newStates.size(); p++)
                        seen[newStates.get(p).index] = true;
                }

                jjtmpStates = oldStates;
                oldStates = newStates;
                (newStates = jjtmpStates).clear();

                if (statesForPos.get(j) == null)
                    statesForPos.set(j, new HashMap<String, long[]>());

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

            if ((tmp.asciiMoves[c / 64] & (1L << c % 64)) != 0L)
                return true;
        }

        return false;
    }

    public int addStartStateSet(String stateSetString) {
        if (stateIndexFromComposite.contains(stateSetString)) {
            return stateIndexFromComposite.get(stateSetString);
        }
        int toRet = 0;
        int[] nameSet = (int[]) allNextStates.get(stateSetString);

        if (nameSet.length == 1) {
            stateIndexFromComposite.put(stateSetString, nameSet[0]);
            return nameSet[0];
        }

        for (int i = 0; i < nameSet.length; i++) {
            if (nameSet[i] == -1)
                continue;

            NfaState st = indexedAllStates.get(nameSet[i]);
            st.isComposite = true;
            st.compositeStates = nameSet;
        }

        while (toRet < nameSet.length && (indexedAllStates.get(nameSet[toRet]).inNextOf > 1))
            toRet++;

        Enumeration<String> e = compositeStateTable.keys();
        String s;
        while (e.hasMoreElements()) {
            s = e.nextElement();
            if (!s.equals(stateSetString) && intersect(stateSetString, s)) {
                int[] other = compositeStateTable.get(s);

                while (toRet < nameSet.length
                        && (((indexedAllStates.get(nameSet[toRet])).inNextOf > 1) || NfaState.arrayContains(other, nameSet[toRet]))) {
                    toRet++;
                }
            }
        }
        
        int tmp;

        if (toRet >= nameSet.length) {
            if (dummyStateIndex == -1) {
                tmp = dummyStateIndex = indexedAllStates.size();
            } else {
                tmp = ++dummyStateIndex;
            }
        } else {
            tmp = nameSet[toRet];
        }
        stateIndexFromComposite.put(stateSetString, tmp);
        compositeStateTable.put(stateSetString, nameSet);
        return tmp;
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
            retVal += (k = ((NfaState) states.get(i)).index) + ", ";
            set[i] = k;

            if (i++ > 0 && i % 16 == 0)
                retVal += "\n";
        }

        retVal += "};";
        allNextStates.put(retVal, set);
        return retVal;
    }

    private void fixStateSets() {
        HashMap<String, int[]> fixedSets = new HashMap<String, int[]>();
        int[] tmp = new int[indexedAllStates.size()];
        for (Map.Entry<String, int[]> entry : stateSetsToFix.entrySet()) {
            int cnt = 0;
            for (int state : entry.getValue()) {
                if (state != -1)
                    tmp[cnt++] = state;
            }
            int[] fixed = new int[cnt];
            System.arraycopy(tmp, 0, fixed, 0, cnt);
            fixedSets.put(entry.getKey(), fixed);
            allNextStates.put(entry.getKey(), fixed);
        }
        for (NfaState state : allStates) {
            if (state.getNext() != null && state.getNext().hasEpsilonMoves()) {
                int[] newSet = fixedSets.get(state.getNext().epsilonMovesString);
                if (newSet != null) {
                    state.fixNextStates(newSet);
                }
            }
        }
    }

    public boolean intersect(String set1, String set2) {
        if (set1 == null || set2 == null)
            return false;

        int[] nameSet1 = allNextStates.get(set1);
        int[] nameSet2 = allNextStates.get(set2);

        if (nameSet1 == null || nameSet2 == null)
            return false;

        if (nameSet1 == nameSet2)
            return true;

        for (int i = nameSet1.length; i-- > 0;)
            for (int j = nameSet2.length; j-- > 0;)
                if (nameSet1[i] == nameSet2[j])
                    return true;

        return false;
    }

    public boolean intersect(NfaState state1, NfaState state2) {
        return intersect(state1.epsilonMovesString, state2.epsilonMovesString);
    }

    void computeClosures() {
        for (int i = allStates.size(); i-- > 0;) {
            NfaState tmp = allStates.get(i);

            if (!tmp.closureDone)
                tmp.optimizeEpsilonMoves(true);
        }
        for (int i = 0; i < allStates.size(); i++) {
            NfaState state = allStates.get(i);
            if (!state.closureDone)
                state.optimizeEpsilonMoves(false);
        }

        for (int i = 0; i < allStates.size(); i++) {
            NfaState tmp = allStates.get(i);
            tmp.epsilonMoveArray = new NfaState[tmp.epsilonMoves.size()];
            ((Vector<NfaState>)tmp.epsilonMoves).copyInto(tmp.epsilonMoveArray);
//            tmp.epsilonMoves.toArray(tmp.epsilonMoveArray);
        }
    }

    void generateNfaStates() {
        if (indexedAllStates.isEmpty()) {
            return;
        }
        Vector<NfaState> v = allStates;
        allStates = new Vector<NfaState>();
        allStates.setSize(indexedAllStates.size());

        for (NfaState state : v) {
            if (state.index != -1 && !state.dummy)
                allStates.set(state.index, state);
        }
        for (NfaState nfaState : allStates) {
            if (nfaState.getLexicalState() != this || !nfaState.hasTransitions() || nfaState.dummy
                    || nfaState.index == -1)
                continue;
            if (kindsForStates == null) {
                kindsForStates = new int[indexedAllStates.size()];
                statesForState = new int[Math.max(indexedAllStates.size(), dummyStateIndex + 1)][];
            }
            kindsForStates[nfaState.index] = nfaState.lookingFor.getOrdinal();
            statesForState[nfaState.index] = nfaState.compositeStates;
        }
    }

    public List<List<NfaState>> partitionStatesSetForAscii(NfaState[] states, int byteNum) {
        int[] cardinalities = new int[states.length];
        Vector<NfaState> original = new Vector<>();
        Vector<List<NfaState>> partition = new Vector<>();
        NfaState tmp;

        original.setSize(states.length);
        int cnt = 0;
        for (int i = 0; i < states.length; i++) {
            tmp = states[i];

            if (tmp.asciiMoves[byteNum] != 0L) {
                int j;
                int p = numberOfBitsSet(tmp.asciiMoves[byteNum]);

                for (j = 0; j < i; j++)
                    if (cardinalities[j] <= p)
                        break;

                for (int k = i; k > j; k--)
                    cardinalities[k] = cardinalities[k - 1];

                cardinalities[j] = p;

                original.insertElementAt(tmp, j);
                cnt++;
            }
        }

        original.setSize(cnt);

        while (original.size() > 0) {
            tmp = (NfaState) original.get(0);
            original.removeElement(tmp);

            long bitVec = tmp.asciiMoves[byteNum];
            List<NfaState> subSet = new Vector<NfaState>();
            subSet.add(tmp);

            for (int j = 0; j < original.size(); j++) {
                NfaState tmp1 = (NfaState) original.get(j);

                if ((tmp1.asciiMoves[byteNum] & bitVec) == 0L) {
                    bitVec |= tmp1.asciiMoves[byteNum];
                    subSet.add(tmp1);
                    original.removeElementAt(j--);
                }
            }

            partition.addElement(subSet);
        }

        return partition;
    }

    public int initStateName() {
        String s = initialState.getEpsilonMovesString();

        if (initialState.hasEpsilonMoves())
            return stateIndexFromComposite.get(s);
        return -1;
    }

    static private int numberOfBitsSet(long l) {
        int ret = 0;
        for (int i = 0; i < 63; i++)
            if (((l >> i) & 1L) != 0L)
                ret++;

        return ret;
    }

    static int MoveFromSet(char c, List<NfaState> states, List<NfaState> newStates) {
        int tmp;
        int retVal = Integer.MAX_VALUE;

        for (int i = states.size(); i-- > 0;)
            if (retVal > (tmp = states.get(i).moveFrom(c, newStates)))
                retVal = tmp;

        return retVal;
    }

    private void setupStateSetsForKinds() {
        for (int i = 0; i < maxLen; i++) {
            Map<String, KindInfo> tab = charPosKind.get(i);
            for (String key : tab.keySet()) {
                KindInfo info = (KindInfo) tab.get(key);
                if (generateDfaCase(key, info, i)) {
                    if (info.finalKindCnt != 0) {
                        for (int j = 0; j < maxStrKind; j++) {
                            long matchedKind = info.finalKinds[j / 64];
                            if (((matchedKind & (1L << j % 64)) != 0L) && !subString[j]) {
                                getStateSetForKind(i, j); // <-- needs to be
                                                            // called!
                            }
                        }
                    }
                }
            }
        }
    }

    static public boolean canMatchAnyChar(CharacterList cl) {
        // Return true only if it is ~[]
        return cl.isNegated() && (cl.getDescriptors().isEmpty());
    }
}
