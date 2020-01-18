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
import javacc.parser.tree.EndOfFile;
import javacc.parser.tree.RegexpChoice;
import javacc.parser.tree.TokenProduction;

/**
 * Generate lexer.
 */
public class LexerData {
    private Grammar grammar;
    private List<LexicalState> lexicalStates = new ArrayList<LexicalState>();
    private List<RegularExpression> regularExpressions = new ArrayList<RegularExpression>();

    int stateSetSize;
    long[] toSkip;
    long[] toSpecial;
    long[] toMore;
    long[] toToken;
    int[] maxLongsReqd;
    boolean hasEmptyMatch;
    boolean hasSkipActions, hasMoreActions, hasTokenActions, hasSpecial, hasSkip, hasMore;

    private int lohiByteCount;
    private List<NfaState> nonAsciiTableForMethod = new ArrayList<NfaState>();
    private Map<String, Integer> lohiByteTable = new HashMap<String, Integer>();

    private List<String> allBitVectors = new ArrayList<String>();
    private int[] tempIndices = new int[512];
    private Map<String, int[]> tableToDump = new HashMap<String, int[]>();
    private List<int[]> orderedStateSet = new ArrayList<int[]>();
    private int lastIndex;

    public LexerData(Grammar grammar) {
        this.grammar = grammar;
        RegularExpression reof = new EndOfFile();
        reof.setGrammar(grammar);
        regularExpressions.add(reof);
        reof.setNewLexicalState(getLexicalState(grammar.getNextStateForEOF()));
    }

    public String getLexicalStateName(int index) {
        return lexicalStates.get(index).getName();
    }

    public void addLexicalState(String name) {
        lexicalStates.add(new LexicalState(grammar, name));
    }

    public LexicalState getLexicalState(String name) {
        for (LexicalState state : lexicalStates) {
            if (state.getName().equals(name)) {
                return state; 
            }
        }
        return null;
    }

    public RegularExpression getRegularExpression(int idx) {
        return regularExpressions.get(idx);
    }

    public List<RegularExpression> getRegularExpressions() {
        return regularExpressions;
    }

    public int getLexicalStateIndex(String lexicalStateName) {
        for (int i = 0; i < lexicalStates.size(); i++) {
            LexicalState state = lexicalStates.get(i);
            if (state.getName().equals(lexicalStateName)) {
                return i;
            }
        }
        return -1;
    }

    public int numLexicalStates() {
        return lexicalStates.size();
    }

    public List<LexicalState> getLexicalStates() {
        return lexicalStates;
    }

    public void addRegularExpression(RegularExpression regexp) {
        regularExpressions.add(regexp);
    }

    public int getTokenCount() {
        return regularExpressions.size();
    }

    public boolean hasActions() {
        return hasMoreActions || hasSkipActions || hasTokenActions;
    }

    public boolean getHasMore() {
        return hasMore;
    }

    public boolean getHasMoreActions() {
        return hasMoreActions;
    }

    public boolean getHasSpecial() {
        return hasSpecial;
    }

    public boolean getHasSkip() {
        return hasSkip;
    }

    public boolean getHasSkipActions() {
        return hasSkipActions;
    }

    public boolean getHasEmptyMatch() {
        return this.hasEmptyMatch;
    }

    public boolean getHasTokenActions() {
        return hasTokenActions;
    }

    public boolean hasTokenAction(int index) {
        return (toToken[index / 64] & (1L << (index % 64))) != 0L;
    }

    public boolean hasMoreAction(int index) {
        return (toMore[index / 64] & (1L << (index % 64))) != 0L;
    }

    public boolean hasSkipAction(int index) {
        return (toSkip[index / 64] & (1L << (index % 64))) != 0L;
    }

    public long[] getToSkip() {
        return toSkip;
    }

    public long[] getToMore() {
        return toMore;
    }

    public long[] getToToken() {
        return toToken;
    }

    public long[] getToSpecial() {
        return toSpecial;
    }

    public int getStateSetSize() {
        return stateSetSize;
    }

    public List<NfaState> getNonAsciiTableForMethod() {
        return nonAsciiTableForMethod;
    }

    int getLohiByteCount() {
        return lohiByteCount;
    }

    void incrementLohiByteCount() {
        ++lohiByteCount;
    }

    Map<String, Integer> getLoHiByteTable() {
        return lohiByteTable;
    }

    public List<String> getAllBitVectors() {
        return allBitVectors;
    }

    int[] getTempIndices() {
        return tempIndices;
    }

    Map<String, int[]> getTableToDump() {
        return tableToDump;
    }

    public List<int[]> getOrderedStateSet() {
        return orderedStateSet;
    }

    int getLastIndex() {
        return lastIndex;
    }

    void setLastIndex(int i) {
        lastIndex = i;
    }

    public int GetIndex(String name) {
        for (int i = 0; i < lexicalStates.size(); i++) {
            if (lexicalStates.get(i).getName().equals(name)) {
                return i;
            }

        }
        return -1;
    }

    public void start() {
        List<RegexpChoice> choices = new ArrayList<RegexpChoice>();

        for (TokenProduction tokenProduction : grammar.getAllTokenProductions()) {
            for (String lexStateName : tokenProduction.getLexStates()) {
                LexicalState lexState = getLexicalState(lexStateName);
                lexState.addTokenProduction(tokenProduction);
            }
        }
        int tokenCount = getTokenCount();
        toSkip = new long[tokenCount / 64 + 1];
        toSpecial = new long[tokenCount / 64 + 1];
        toMore = new long[tokenCount / 64 + 1];
        toToken = new long[tokenCount / 64 + 1];
        toToken[0] = 1L;
        hasTokenActions = getRegularExpression(0) != null;

        for (LexicalState lexState : lexicalStates) {
            lexState.process(choices);
        }

        for (RegexpChoice choice : choices) {
            checkUnmatchability(choice);
        }

        checkEmptyStringMatch();
    }

    void checkEmptyStringMatch() {
        int j, k, len;
        int numLexStates = lexicalStates.size();
        boolean[] seen = new boolean[numLexStates];
        boolean[] done = new boolean[numLexStates];
        String cycle;
        String reList;

        Outer: for (LexicalState ls : lexicalStates) {
            if (done[ls.getIndex()] || ls.initMatch == 0 || ls.initMatch == Integer.MAX_VALUE
                    || ls.matchAnyChar != null) {
                continue;
            }
            done[ls.getIndex()] = true;
            len = 0;
            cycle = "";
            reList = "";

            for (k = 0; k < numLexStates; k++) {
                seen[k] = false;
            }

            j = ls.getIndex();
            seen[ls.getIndex()] = true;
            cycle += ls.getName() + "-->";
            int initMatch = lexicalStates.get(j).initMatch;
            while (getRegularExpression(initMatch).getNewLexicalState() != null) {
                LexicalState newLexState = getRegularExpression(initMatch).getNewLexicalState();
                cycle += newLexState.getName();
                j = newLexState.getIndex();
                if (seen[j]) {
                    break;
                }
                cycle += "-->";
                done[j] = true;
                seen[j] = true;
                initMatch = lexicalStates.get(j).initMatch;
                if (initMatch == 0 || initMatch == Integer.MAX_VALUE || lexicalStates.get(j).matchAnyChar != null) {
                    continue Outer;
                }

                if (len != 0) {
                    reList += "; ";
                }

                reList += "line " + getRegularExpression(initMatch).getBeginLine() + ", column "
                        + getRegularExpression(initMatch).getBeginColumn();
                len++;
            }
            initMatch = lexicalStates.get(j).initMatch;
            if (getRegularExpression(initMatch).getNewLexicalState() == null) {
                cycle += getRegularExpression(initMatch).getLexicalState().getName();
            }

            k = 0;
            for (LexicalState lexState : lexicalStates) {
                if (seen[k++]) {
                    lexState.canLoop = true;
                }
            }
            initMatch = ls.initMatch;
            RegularExpression re = getRegularExpression(initMatch);
            if (len == 0) {

                grammar.addWarning(re, "Regular expression"
                        + ((re.getLabel().equals("")) ? "" : (" for " + getRegularExpression(initMatch).getLabel()))
                        + " can be matched by the empty string (\"\") in lexical state " + ls.getName()
                        + ". This can result in an endless loop of " + "empty string matches.");
            } else {

                grammar.addWarning(re, "Regular expression"
                        + ((re.getLabel().equals("")) ? "" : (" for " + re.getLabel()))
                        + " can be matched by the empty string (\"\") in lexical state " + ls.getName()
                        + ". This regular expression along with the " + "regular expressions at " + reList
                        + " forms the cycle \n   " + cycle + "\ncontaining regular expressions with empty matches."
                        + " This can result in an endless loop of empty string matches.");
            }
        }
    }
    
    static public void checkUnmatchability(RegexpChoice choice) {
        for (RegularExpression curRE : choice.getChoices()) {
            if (!(curRE).isPrivate()
                    &&
                    // curRE instanceof RJustName &&
                    curRE.getOrdinal() > 0 && curRE.getOrdinal() < choice.getOrdinal()
                    && curRE.getLexicalState() == choice.getLexicalState()) {
                choice.getGrammar().addWarning(choice,
                        "Regular Expression choice : " + curRE.getLabel() + " can never be matched as : " + choice.getLabel());
            }
        }
    }

    // Assumes l != 0L
    public int maxChar(long l) {
        for (int i = 64; i-- > 0;) {
            if ((l & (1L << i)) != 0L) {
                return (int) (char) i;
            }

        }
        return 0xffff;
    }
}
