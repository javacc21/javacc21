/* Copyright (c) 2008-2022 Jonathan Revusky, revusky@congocc.org
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
 *     * Neither the name Jonathan Revusky nor the names of any contributors 
 *       may be used to endorse or promote products derived from this software 
 *       without specific prior written permission.
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

package com.javacc.core.nfa;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import com.javacc.Grammar;
import com.javacc.core.RegularExpression;
import com.javacc.parser.Node;
import com.javacc.parser.tree.*;

/**
 * A Visitor object that builds an NFA from a Regular expression. 
 * This visitor object builds a lot of dummy NFA states that are 
 * effectively a kind of scaffolding that are removed in a separate stage, 
 * after doing the so-called "epsilon closure". At that point, the various
 * remaining NfaState objects (the ones that are actually used) are 
 * consolidated into CompositeStateSet objects that get expressed as
 * NFA_XXX methods in the generated code.
 * 
 * @author revusky
 */
class NfaBuilder extends Node.Visitor {

    // the starting and ending NfaState objects
    // of the last regexp that we "visited"
    private NfaState start, end;
    private boolean ignoreCase;
    private LexicalStateData lexicalState;
    final Grammar grammar;

    NfaBuilder(LexicalStateData lexicalState, boolean ignoreCase) {
        this.lexicalState = lexicalState;
        this.grammar = lexicalState.grammar;
        this.ignoreCase = ignoreCase;
    }

    /**
     * This method sets the start and end states
     * of the regexp passed in. The start state is
     * then added as an "epsilon move" to the lexical state's
     * initial state.
     */
    void buildStates(RegularExpression regularExpression) {
        visit(regularExpression);
        end.setType(regularExpression);
        lexicalState.getInitialState().addEpsilonMove(start);
    }

    void visit(CharacterList charList) {
        List<CharacterRange> ranges = orderedRanges(charList, ignoreCase);
        start = new NfaState(lexicalState);
        end = new NfaState(lexicalState);
        for (CharacterRange cr : ranges) {
            start.addRange(cr.getLeft(), cr.getRight());
        }
        start.setNextState(end);
    }

    void visit(OneOrMoreRegexp oom) {
        NfaState startState = new NfaState(lexicalState);
        NfaState finalState = new NfaState(lexicalState);
        visit(oom.getRegexp());
        startState.addEpsilonMove(this.start);
        this.end.addEpsilonMove(this.start);
        this.end.addEpsilonMove(finalState);
        this.start = startState;
        this.end = finalState;
    }

    void visit(RegexpChoice choice) {
        List<RegularExpression> choices = choice.getChoices();
        if (choices.size() == 1) {
            visit(choices.get(0));
            return;
        }
        NfaState startState = new NfaState(lexicalState);
        NfaState finalState = new NfaState(lexicalState);
        for (RegularExpression curRE : choices) {
            visit(curRE);
            startState.addEpsilonMove(this.start);
            this.end.addEpsilonMove(finalState);
        }
        this.start = startState;
        this.end = finalState;
    }

    void visit(RegexpStringLiteral stringLiteral) {
        NfaState state = end = start = new NfaState(lexicalState);
        for (int ch : stringLiteral.getImage().codePoints().toArray()) {
            state.setCharMove(ch, grammar.isIgnoreCase() || ignoreCase);
            this.end = new NfaState(lexicalState);
            state.setNextState(this.end);
            state = this.end;
        }
    }

    void visit(ZeroOrMoreRegexp zom) {
        NfaState startState = new NfaState(lexicalState);
        NfaState finalState = new NfaState(lexicalState);
        visit(zom.getRegexp());
        startState.addEpsilonMove(this.start);
        startState.addEpsilonMove(finalState);
        this.end.addEpsilonMove(finalState);
        this.end.addEpsilonMove(this.start);
        this.start = startState;
        this.end = finalState;
    }

    void visit(ZeroOrOneRegexp zoo) {
        NfaState startState = new NfaState(lexicalState);
        NfaState finalState = new NfaState(lexicalState);
        visit(zoo.getRegexp());
        startState.addEpsilonMove(this.start);
        startState.addEpsilonMove(finalState);
        this.end.addEpsilonMove(finalState);
        this.start = startState;
        this.end = finalState;
    }

    void visit(RegexpRef ref) {
        visit(ref.getRegexp());
    }

    void visit(RegexpSequence sequence) {
        if (sequence.getUnits().size() == 1) {
            visit(sequence.getUnits().get(0));
            return;
        }
        NfaState startState = new NfaState(lexicalState);
        NfaState finalState = new NfaState(lexicalState);
        NfaState prevStartState = null;
        NfaState prevEndState = null;
        for (RegularExpression re : sequence.getUnits()) {
            visit(re);
            if (prevStartState == null) {
                startState.addEpsilonMove(this.start);
            } else {
                prevEndState.addEpsilonMove(this.start);
            }
            prevStartState = this.start;
            prevEndState = this.end;
        }
        this.end.addEpsilonMove(finalState);
        this.start = startState;
        this.end = finalState;
    }

    void visit(RepetitionRange repRange) {
        List<RegularExpression> units = new ArrayList<RegularExpression>();
        for (int i=0; i < repRange.getMin(); i++) {
            units.add(repRange.getRegexp());
        }
        if (repRange.hasMax() && repRange.getMax() == -1) { // Unlimited
            ZeroOrMoreRegexp zom = new ZeroOrMoreRegexp();
            zom.setGrammar(grammar);
            zom.setRegexp(repRange.getRegexp());
            units.add(zom);
        }
        else for (int i = repRange.getMin(); i< repRange.getMax(); i++) {
            ZeroOrOneRegexp zoo = new ZeroOrOneRegexp();
            zoo.setGrammar(grammar);
            zoo.setRegexp(repRange.getRegexp());
            units.add(zoo);
        }
        visit(new RegexpSequence(grammar, units));
    }

    static private List<CharacterRange> orderedRanges(CharacterList charList, boolean caseNeutral) {
        BitSet bs = rangeListToBS(charList.getDescriptors());
        if (caseNeutral) {
            BitSet upperCaseDiffPoints = (BitSet) bs.clone();
            BitSet lowerCaseDiffPoints = (BitSet) bs.clone();
            upperCaseDiffPoints.and(upperCaseDiffSet);
            lowerCaseDiffPoints.and(lowerCaseDiffSet);
            upperCaseDiffPoints.stream().forEach(ch -> bs.set(Character.toUpperCase(ch)));
            lowerCaseDiffPoints.stream().forEach(ch -> bs.set(Character.toLowerCase(ch)));
        }
        if (charList.isNegated()) {
            bs.flip(0, 0x110000);
        }
        return bsToRangeList(bs);
    }

    // BitSet that holds which characters are not the same in lower case
    static private BitSet lowerCaseDiffSet = caseDiffSetInit(false);
    // BitSet that holds which characters are not the same in upper case
    static private BitSet upperCaseDiffSet = caseDiffSetInit(true);

    static private BitSet caseDiffSetInit(boolean upper) {
        BitSet result = new BitSet();
        for (int ch = 0; ch <= 0x16e7f; ch++) {
            int converted = upper ? Character.toUpperCase(ch) : Character.toLowerCase(ch);
            if (converted != ch) {
                result.set(ch);
            }
        }
        return result;
    }

    // Convert a list of CharacterRange's to a BitSet
    static private BitSet rangeListToBS(List<CharacterRange> ranges) {
        BitSet result = new BitSet();
        for (CharacterRange range : ranges) {
            result.set(range.getLeft(), range.getRight()+1);
        }
        return result;
    }

    //Convert a BitSet to a list of CharacterRange's
    static private List<CharacterRange> bsToRangeList(BitSet bs) {
        List<CharacterRange> result = new ArrayList<>();
        if (bs.isEmpty()) return result;
        int curPos = 0;
        while (curPos >=0) {
            int left = bs.nextSetBit(curPos);
            int right = bs.nextClearBit(left) -1;
            result.add(new CharacterRange(left, right));
            curPos = bs.nextSetBit(right+1);
        }
        return result;
    }
}
