package com.javacc.lexgen;

import java.util.ArrayList;
import java.util.List;

import com.javacc.Grammar;
import com.javacc.parser.Node;
import com.javacc.parser.tree.*;

public class NfaBuilder extends Node.Visitor {
    private boolean ignoreCase;
    private LexicalState lexicalState;
    private Grammar grammar;
    private Nfa nfa = null;
    
    public NfaBuilder(RegularExpression regularExpression, LexicalState lexicalState, boolean ignoreCase) {
        this.lexicalState = lexicalState;
        this.grammar = lexicalState.getGrammar();
        this.ignoreCase = ignoreCase;
        visit(regularExpression);
    }
    
    public Nfa getNfa() {return nfa;}
    
    public void visit(CharacterList charList) {
        List<CharacterRange> descriptors = charList.getDescriptors();
        if (ignoreCase) {
            descriptors = Nfa.toCaseNeutral(descriptors);
        }
        descriptors = Nfa.sortDescriptors(descriptors);
        if (charList.isNegated()) {
            descriptors =Nfa.removeNegation(descriptors);
        }
        Nfa nfa = new Nfa(lexicalState);
        NfaState startState = nfa.getStart();
        NfaState finalState = nfa.getEnd();
        for (CharacterRange cr : descriptors) {
            if (cr.isSingleChar()) {
                startState.addChar(cr.left);
            } else {
                startState.addRange(cr.left, cr.right);
            }
        }
        startState.setNext(finalState);
    }
    
    public void visit(OneOrMoreRegexp oom) {
        Nfa newNfa = new Nfa(lexicalState);
        NfaState startState = newNfa.getStart();
        NfaState finalState = newNfa.getEnd();
        visit(oom.getRegexp());
        startState.addMove(nfa.getStart());
        nfa.getEnd().addMove(nfa.getStart());
        nfa.getEnd().addMove(finalState);
        this.nfa = newNfa;
     }
    
    public void visit(RegexpChoice choice) {
        List<RegularExpression> choices = Nfa.compressCharLists(choice.getChoices());
        if (choices.size() == 1) {
            visit(choices.get(0));
        }
        Nfa newNfa = new Nfa(lexicalState);
        NfaState startState = newNfa.getStart();
        NfaState finalState = newNfa.getEnd();
        for (RegularExpression curRE : choices) {
            visit(curRE);
            startState.addMove(nfa.getStart());
            nfa.getEnd().addMove(finalState);
        }
        this.nfa = newNfa;
    }
    
    public void visit(RegexpStringLiteral stringLiteral) {
        String image = stringLiteral.getImage();
        Grammar grammar = stringLiteral.getGrammar();
        if (image.length() == 1) {
            CharacterList charList = new CharacterList();
            charList.setGrammar(grammar);
            CharacterRange cr = new CharacterRange();
            cr.setGrammar(grammar);
            cr.left = cr.right = image.charAt(0);
            charList.addChild(cr);
            visit(charList);
            return;
        }
        NfaState startState = new NfaState(lexicalState);
        NfaState theStartState = startState;
        NfaState finalState = null;
        if (image.length() == 0) {
            this.nfa = new Nfa(theStartState, theStartState);
            return;
        }
        for (int i = 0; i < image.length(); i++) {
            finalState = new NfaState(lexicalState);
            startState.setCharMoves(new char[1]);
            startState.addChar(image.charAt(i));
            if (grammar.getOptions().getIgnoreCase() || ignoreCase) {
                startState.addChar(Character.toLowerCase(image.charAt(i)));
                startState.addChar(Character.toUpperCase(image.charAt(i)));
            }
            startState.setNext(finalState);
            startState = finalState;
        }
        this.nfa = new Nfa(theStartState, finalState);
    }
    
    public void visit(ZeroOrMoreRegexp zom) {
        Nfa newNfa = new Nfa(lexicalState);
        NfaState startState = newNfa.getStart();
        NfaState finalState = newNfa.getEnd();
        visit(zom.getRegexp());
        startState.addMove(nfa.getStart());
        startState.addMove(finalState);
        nfa.getEnd().addMove(finalState);
        nfa.getEnd().addMove(nfa.getStart());
        this.nfa = newNfa;
    }
    
    public void visit(ZeroOrOneRegexp zoo) {
        Nfa newNfa = new Nfa(lexicalState);
        NfaState startState = newNfa.getStart();
        NfaState finalState = newNfa.getEnd();
        visit(zoo.getRegexp());
        startState.addMove(nfa.getStart());
        startState.addMove(finalState);
        nfa.getEnd().addMove(finalState);
        this.nfa = newNfa;
    }
    
    public void visit(RegexpSequence sequence) {
        if (sequence.getUnits().size() == 1) {
            visit(sequence.getUnits().get(0));
        }
        Nfa newNfa = new Nfa(lexicalState);
        NfaState startState = newNfa.getStart();
        NfaState finalState = newNfa.getEnd();
        Nfa prevNfa = null;
        for (RegularExpression re : sequence.getUnits())  {
            visit(re);
            if (prevNfa == null) {
                startState.addMove(nfa.getStart());
            } else {
                prevNfa.getEnd().addMove(nfa.getStart());
            }
            prevNfa = this.nfa;
        }
        nfa.getEnd().addMove(finalState);
        this.nfa = newNfa;
    }
    
    public void visit(RepetitionRange repRange) {
        List<RegularExpression> units = new ArrayList<RegularExpression>();
        RegexpSequence seq;
        int i;
        for (i = 0; i < repRange.getMin(); i++) {
            units.add(repRange.getRegexp());
        }
        if (repRange.hasMax() && repRange.getMax() == -1) // Unlimited
        {
            ZeroOrMoreRegexp zom = new ZeroOrMoreRegexp();
            zom.setGrammar(grammar);
            zom.setRegexp(repRange.getRegexp());
            units.add(zom);
        }
        while (i++ < repRange.getMax()) {
            ZeroOrOneRegexp zoo = new ZeroOrOneRegexp();
            zoo.setGrammar(grammar);
            zoo.setRegexp(repRange.getRegexp());
            units.add(zoo);
        }
        seq = new RegexpSequence();
        seq.setGrammar(grammar);
        seq.setOrdinal(Integer.MAX_VALUE);
        for (RegularExpression re : units) {
            seq.addChild(re);
        }
        visit(seq);
    }
}
