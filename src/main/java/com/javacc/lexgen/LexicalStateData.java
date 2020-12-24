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
import com.javacc.parser.tree.CodeBlock;
import com.javacc.parser.tree.RegexpChoice;
import com.javacc.parser.tree.RegexpSpec;
import com.javacc.parser.tree.RegexpStringLiteral;
import com.javacc.parser.tree.TokenProduction;

public class LexicalStateData {

    private Grammar grammar;
    private LexerData lexerData;
    private String name;
    private DfaData dfaData;
    private NfaData nfaData;

    private List<TokenProduction> tokenProductions = new ArrayList<>();
    private Map<String, Map<String, RegularExpression>> tokenTable = new HashMap<>();

    private boolean mixed;
    private int initMatch;
    private RegularExpression currentRegexp;
    private HashSet<RegularExpression> regularExpressions = new HashSet<>();
    private String[] images;

    private BitSet marks = new BitSet();
    private boolean done;


    public LexicalStateData(Grammar grammar, String name) {
        this.grammar = grammar;
        this.lexerData = grammar.getLexerData();
        this.dfaData = new DfaData(this);
        this.nfaData = new NfaData(this);
        this.name = name;
        nfaData.initialState = new NfaState(this);
    }

    Grammar getGrammar() {
        return grammar;
    }

    NfaState getInitialState() {return nfaData.initialState;}

    RegularExpression getCurrentRegexp() {return currentRegexp;}

    public String getName() {return name;}

    public DfaData getDfaData() {return dfaData;}

    public NfaData getNfaData() {return nfaData;}

    String getImage(int i) {return images[i];}

    boolean isMarked(int i) {return marks.get(i);}

    void setMark(int i) {marks.set(i);}

    void unsetMark(int i) {marks.clear(i);}

    void clearMarks() {marks.clear();}

    void setDone(boolean done) {this.done = done;}

    boolean isDone() {return this.done;}

    public int getIndex() {return lexerData.getIndex(name);}


    void addTokenProduction(TokenProduction tokenProduction) {
        tokenProductions.add(tokenProduction);
    }

    public boolean hasNfa() {
        return !nfaData.indexedAllStates.isEmpty();
    }

    public List<NfaState> getIndexedAllStates() {
        return nfaData.indexedAllStates;
    }

    Map<String, int[]> getAllNextStates() {
        return nfaData.allNextStates;
    }

    // FIXME! There is currently no testing in place for mixed case Lexical states!
    public boolean isMixedCase() {
        return mixed;
    }

    public int getInitMatch() {
        return initMatch;
    }

    public boolean getCreateStartNfa() {
        return !mixed && nfaData.indexedAllStates.size() != 0;
    }

    public boolean containsRegularExpression(RegularExpression re) {
        return regularExpressions.contains(re);
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

    List<RegexpChoice> process() {
        images = new String[lexerData.getTokenCount()];
    	List<RegexpChoice> choices = new ArrayList<>();
        boolean isFirst = true;
        for (TokenProduction tp : tokenProductions) {
            choices.addAll(processTokenProduction(tp, isFirst));
            isFirst = false;
        }
        for (NfaState state : nfaData.allStates) state.optimizeEpsilonMoves();
        for (NfaState epsilonMove : nfaData.initialState.getEpsilonMoves()) {
            epsilonMove.generateCode();
        }
        if (nfaData.indexedAllStates.size() != 0) {
            nfaData.initialState.generateCode();
            nfaData.initialState.generateInitMoves();
        }
        if (nfaData.initialState.getKind() != Integer.MAX_VALUE && nfaData.initialState.getKind() != 0) {
            if (lexerData.getSkipSet().get(nfaData.initialState.getKind())
                || (lexerData.getSpecialSet().get(nfaData.initialState.getKind())))
                lexerData.hasSkipActions = true;
            else if (lexerData.getMoreSet().get(nfaData.initialState.getKind()))
                lexerData.hasMoreActions = true;
            if (initMatch == 0 || initMatch > nfaData.initialState.getKind()) {
                initMatch = nfaData.initialState.getKind();
                lexerData.hasEmptyMatch = true;
            }
        } else if (initMatch == 0) {
            initMatch = Integer.MAX_VALUE;
        }
        dfaData.fillSubString();
        if (nfaData.indexedAllStates.size() != 0 && !mixed) {
            nfaData.generateNfaStartStates();
        }
        lexerData.expandStateSetSize(nfaData.indexedAllStates.size());
        nfaData.generateNfaStates();
        dfaData.generateData();
        for (NfaState nfaState : nfaData.allStates) {
            nfaState.generateNonAsciiMoves();
        }
        for (Map.Entry<String, Integer> entry : nfaData.stateIndexFromComposite.entrySet()) {
//REVISIT: I don't really grok this code. What is going on?            
            int state = entry.getValue();
            if (state >= nfaData.indexedAllStates.size()) {
                if (state >= nfaData.statesForState.length) {
                    int[][] prevStatesForState = nfaData.statesForState;
                    nfaData.statesForState = new int[state+1][];
                    for (int i=0; i<prevStatesForState.length;i++) {
                        nfaData.statesForState[i] = prevStatesForState[i];
                    }
                }
                nfaData.statesForState[state] = nfaData.allNextStates.get(entry.getKey());
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
                if (dfaData.getMaxStringIndex() <= currentRegexp.getOrdinal()) {
                    dfaData.setMaxStringIndex(currentRegexp.getOrdinal() + 1);
                }
                dfaData.generate((RegexpStringLiteral) currentRegexp);
                images[currentRegexp.getOrdinal()] = ((RegexpStringLiteral) currentRegexp).getImage();
                if (!isFirst && !mixed && ignoring != ignore) {
                    mixed = true;
                }
            } else {
                if (currentRegexp instanceof RegexpChoice) {
                    choices.add((RegexpChoice) currentRegexp);
                }
                new NfaBuilder(this, ignore).buildStates(currentRegexp);
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
}
