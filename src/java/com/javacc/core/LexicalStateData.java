/* Copyright (c) 2008-2021 Jonathan Revusky, revusky@congocc.com
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

package com.javacc.core;

import java.util.*;

import com.javacc.Grammar;
import com.javacc.parser.tree.RegexpChoice;
import com.javacc.parser.tree.RegexpSpec;
import com.javacc.parser.tree.RegexpStringLiteral;
import com.javacc.parser.tree.TokenProduction;

public class LexicalStateData {

    final Grammar grammar;
    private String name;

    private List<TokenProduction> tokenProductions = new ArrayList<>();

    private List<CompositeStateSet> compositeSets;
    private List<NfaState> simpleStates;
    private Map<Set<NfaState>, CompositeStateSet> canonicalSetLookup = new HashMap<>();

    private Map<String, RegularExpression> caseSensitiveTokenTable = new HashMap<>();
    private Map<String, RegularExpression> caseInsensitiveTokenTable = new HashMap<>();

    private HashSet<RegularExpression> regularExpressions = new HashSet<>();

    private NfaState initialState;

    private Set<NfaState> allStates = new HashSet<>();
    
    public LexicalStateData(Grammar grammar, String name) {
        this.grammar = grammar;
        this.name = name;
        initialState = new NfaState(this);
    }

    public List<CompositeStateSet> getCanonicalSets() {
        return compositeSets;
    }

    public String getName() {return name;}

    public List<NfaState> getAllNfaStates() {
        return simpleStates;
    }

    void addState(NfaState state) {
        allStates.add(state);
    }

    boolean isEmpty() {
        return regularExpressions.isEmpty();
    }

    NfaState getInitialState() {return initialState;}

    void addTokenProduction(TokenProduction tokenProduction) {
        tokenProductions.add(tokenProduction);
    }

    boolean containsRegularExpression(RegularExpression re) {
        return regularExpressions.contains(re);
    }

    void addStringLiteral(RegexpStringLiteral re) {
        if (re.getIgnoreCase()) {
            caseInsensitiveTokenTable.put(re.getImage().toUpperCase(), re);
        } else {
            caseSensitiveTokenTable.put(re.getImage(), re);
        }
    }

    RegularExpression getStringLiteral(String image) {
        RegularExpression result = caseSensitiveTokenTable.get(image);
        if (result == null) {
            result = caseInsensitiveTokenTable.get(image.toUpperCase());
        }
        return result;
    }

    CompositeStateSet getCanonicalComposite(Set<NfaState> stateSet) {
        CompositeStateSet result = canonicalSetLookup.get(stateSet);
        if (result == null) {
            result = new CompositeStateSet(stateSet, this);
            canonicalSetLookup.put(stateSet, result);
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
        generateData();
        return choices;
    }

    private void generateData() {
        for (NfaState state: allStates) {
            state.doEpsilonClosure();
        }
        // Get rid of dummy states.
        allStates.removeIf(state->!state.isMoveCodeNeeded());
        simpleStates = new ArrayList<>(allStates);
        for (int i = 0; i<simpleStates.size();i++) {
            simpleStates.get(i).setMovesArrayName(i);
        }
        Set<CompositeStateSet> allComposites = new HashSet<>();
        CompositeStateSet initialComposite = initialState.getComposite();
        initialComposite.findWhatIsUsed(new HashSet<>(), allComposites);
        this.compositeSets = new ArrayList<>(allComposites);
        // Make sure the initial state is the first in the list.
        int indexInList = compositeSets.indexOf(initialComposite);
        Collections.swap(compositeSets, indexInList, 0);
        // Set the index on the various composites
        for (int i =0; i< compositeSets.size();i++) {
            compositeSets.get(i).setIndex(i);
        }
    }

    List<RegexpChoice> processTokenProduction(TokenProduction tp, boolean isFirst) {
        boolean ignore = tp.isIgnoreCase() || grammar.isIgnoreCase();//REVISIT
        List<RegexpChoice> choices = new ArrayList<>();
        for (RegexpSpec regexpSpec : tp.getRegexpSpecs()) {
            RegularExpression currentRegexp = regexpSpec.getRegexp();
            if (currentRegexp.isPrivate()) {
                continue;
            }
            regularExpressions.add(currentRegexp);
            if (currentRegexp instanceof RegexpChoice) {
                choices.add((RegexpChoice) currentRegexp);
            }
            new NfaBuilder(this, ignore).buildStates(currentRegexp);
            if (regexpSpec.getNextState() != null && !regexpSpec.getNextState().equals(this.name))
                currentRegexp.setNewLexicalState(grammar.getLexerData().getLexicalState(regexpSpec.getNextState()));

            if (regexpSpec.getCodeSnippet() != null && !regexpSpec.getCodeSnippet().isEmpty()) {
                currentRegexp.setCodeSnippet(regexpSpec.getCodeSnippet());
            }
        }
        return choices;
    }
}
