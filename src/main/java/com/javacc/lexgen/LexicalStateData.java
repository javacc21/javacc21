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
    private Map<String, RegularExpression> caseSensitiveTokenTable = new HashMap<>();
    private Map<String, RegularExpression> caseInsensitiveTokenTable = new HashMap<>();

    private boolean mixedCase;
    private HashSet<RegularExpression> regularExpressions = new HashSet<>();

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

    public String getName() {return name;}

    public DfaData getDfaData() {return dfaData;}

    public NfaData getNfaData() {return nfaData;}

    public int getIndex() {return lexerData.getIndex(name);}

    public int getMaxStringLength() {
        int result = 0;
        for (RegularExpression re : regularExpressions) {
            int length = re.getImage() == null ? 0 : re.getImage().length();
            result = Math.max(result, length);
        }
        return result;
    }
    
    public int getMaxStringIndex() {
        int result =0;
        for (RegularExpression re: regularExpressions) {
            if (re instanceof RegexpStringLiteral  && re.getImage().length()>0) 
                result = Math.max(result,re.getOrdinal()+1);
        }
        return result;
    }

    void addTokenProduction(TokenProduction tokenProduction) {
        tokenProductions.add(tokenProduction);
    }

    public boolean hasNfa() {
        return !nfaData.indexedAllStates.isEmpty();
    }

    public int getNumStates() {
        return nfaData.indexedAllStates.size();
    }

    // FIXME! There is currently no testing in place for mixed case Lexical states!
    public boolean isMixedCase() {
        return mixedCase;
    }

    public boolean getCreateStartNfa() {
        return !mixedCase && !nfaData.indexedAllStates.isEmpty();
    }

    public boolean containsRegularExpression(RegularExpression re) {
        return regularExpressions.contains(re);
    }

    public void addStringLiteral(RegexpStringLiteral re) {
        if (re.getIgnoreCase()) {
            caseInsensitiveTokenTable.put(re.getImage().toUpperCase(), re);
        } else {
            caseSensitiveTokenTable.put(re.getImage(), re);
        }
    }

    public RegularExpression getStringLiteral(String image) {
        RegularExpression result = caseSensitiveTokenTable.get(image);
        if (result == null) {
            result = caseInsensitiveTokenTable.get(image.toUpperCase());
        }
        return result;
    }

    List<RegexpChoice> process() {
    	List<RegexpChoice> choices = new ArrayList<>();
        boolean isFirst = true;
        for (TokenProduction tp : tokenProductions) {
            choices.addAll(processTokenProduction(tp, isFirst));
            isFirst = false;
        }
        dfaData.generateData();
        nfaData.generateData();
        return choices;
    }

    List<RegexpChoice> processTokenProduction(TokenProduction tp, boolean isFirst) {
        boolean ignoring = false;
        boolean ignore = tp.isIgnoreCase() || grammar.isIgnoreCase();//REVISIT
        if (isFirst) {
            ignoring = ignore;
        }
        List<RegexpChoice> choices = new ArrayList<>();
        for (RegexpSpec respec : tp.getRegexpSpecs()) {
            RegularExpression currentRegexp = respec.getRegexp();
            regularExpressions.add(currentRegexp);
//            currentRegexp.setIgnoreCase(ignore);
            if (currentRegexp.isPrivate()) {
                continue;
            }
            if (currentRegexp instanceof RegexpStringLiteral
                    && !((RegexpStringLiteral) currentRegexp).getImage().equals("")) {
                dfaData.generate((RegexpStringLiteral) currentRegexp);
                if (!isFirst && ignoring != ignore) {
                    mixedCase = true;
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
                if (currentRegexp.getOrdinal() >0) {
                    lexerData.getSpecialSet().set(currentRegexp.getOrdinal());
                    lexerData.getSkipSet().set(currentRegexp.getOrdinal());
                }
                currentRegexp.setUnparsedToken();
            }
            else if (kind.equals("SKIP")) {
                lexerData.hasSkipActions |= (tokenAction != null);
                lexerData.hasSkip = true;
                lexerData.getSkipSet().set(currentRegexp.getOrdinal());
                currentRegexp.setSkip();
            }
            else if (kind.equals("MORE") && currentRegexp.getOrdinal()>0) { // REVISIT
                lexerData.hasMoreActions |= tokenAction != null;
                lexerData.hasMore = true;
                lexerData.getMoreSet().set(currentRegexp.getOrdinal());
                currentRegexp.setMore();
            }
            else if (currentRegexp.getOrdinal() >0) { // REVISIT
                lexerData.getTokenSet().set(currentRegexp.getOrdinal());
                currentRegexp.setRegularToken();
            }
        }
        return choices;
    }
}
