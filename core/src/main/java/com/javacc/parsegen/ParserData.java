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

package com.javacc.parsegen;

import com.javacc.Grammar;
import com.javacc.MetaParseException;
import com.javacc.lexgen.RegularExpression;
import com.javacc.parser.Nodes;
import com.javacc.parser.tree.Action;
import com.javacc.parser.tree.BNFProduction;
import com.javacc.parser.tree.ExpansionChoice;
import com.javacc.parser.tree.ExpansionSequence;
import com.javacc.parser.tree.NonTerminal;
import com.javacc.parser.tree.OneOrMore;
import com.javacc.parser.tree.ParserProduction;
import com.javacc.parser.tree.TryBlock;
import com.javacc.parser.tree.ZeroOrMore;
import com.javacc.parser.tree.ZeroOrOne;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generate the parser.
 */
public class ParserData {

    private StringBuffer outputBuffer;
    private Grammar grammar;
    private int gensymindex = 0;
    private boolean lookaheadNeeded;
    
    private List<int[]> tokenMasks = new ArrayList<int[]>();
    private List<BitSet> tokenMasks2 = new ArrayList<BitSet>();

    /**
     * These lists are used to maintain expansions for which code generation in
     * phase 2 and phase 3 is required. Whenever a call is generated to a phase
     * 2 or phase 3 routine, a corresponding entry is added here if it has not
     * already been added. The phase 3 routines have been optimized in version
     * 0.7pre2. Essentially only those methods (and only those portions of these
     * methods) are generated that are required. The lookahead amount is used to
     * determine this. This change requires the use of a hash table because it
     * is now possible for the same phase 3 routine to be requested multiple
     * times with different lookaheads. The hash table provides a easily
     * searchable capability to determine the previous requests. The phase 3
     * routines now are performed in a two step process - the first step gathers
     * the requests (replacing requests with lower lookaheads with those
     * requiring larger lookaheads). The second step then generates these
     * methods. This optimization and the hashtable makes it look like we do not
     * need the flag "phase3done" any more. But this has not been removed yet.
     */
    private List<Lookahead> phase2list = new ArrayList<Lookahead>();
    private List<Phase3Data> phase3list = new ArrayList<Phase3Data>();
    private Map<Expansion, Integer> phase3table = new LinkedHashMap<Expansion, Integer>();

    public ParserData(Grammar grammar) {
        this.grammar = grammar;
    }

    public String getOutput() {
        return outputBuffer.toString();
    }
    
    public List<Lookahead> getPhase2Lookaheads() {
        return phase2list;
    }
    
    public Map<Expansion, Integer> getPhase3Table() {
        return phase3table;
    }
    
    public int getPhase3ExpansionCount(Expansion exp) {
        return phase3table.get(exp);
    }
    
    public List<int[]> getTokenMaskValues() {
        return tokenMasks;
    }
    
    public boolean isLookaheadNeeded() {
        return lookaheadNeeded;
    }
    
    public void start() throws MetaParseException {
        if (grammar.getErrorCount() != 0)
            throw new MetaParseException();

        for (ParserProduction p : grammar.getBNFProductions()) {
            if (p instanceof BNFProduction) {
                visitExpansion(p.getExpansion());
            }
        }
        for (Lookahead la : phase2list) {
            if (la.getSemanticLookahead() != null) lookaheadNeeded = true;
            Expansion e = la.getNestedExpansion();
            Phase3Data p3d = new Phase3Data(e, la.getAmount());
            phase3list.add(p3d);
            phase3table.put(e, la.getAmount());
        }
        int phase3index = 0;
        while (phase3index < phase3list.size()) {
            for (; phase3index < phase3list.size(); phase3index++) {
                Phase3Data p3data = phase3list.get(phase3index);
                setupPhase3Builds(p3data.exp, p3data.count);
            }
        }
    }
    
    private void visitChoice(ExpansionChoice choice) {
        List<Lookahead> lookaheads = new ArrayList<Lookahead>();
        ExpansionSequence nestedSeq;
        List<Expansion> choices = Nodes.childrenOfType(choice, Expansion.class);
        for (int i = 0; i < choices.size(); i++) {
            nestedSeq = (ExpansionSequence) (choices.get(i));
            visitExpansion(nestedSeq);
	    if (nestedSeq.isEmpty()) {
		// TODO: REVISIT
		break;
	    }
            Lookahead l = (Lookahead) nestedSeq.getChild(0);
            if (!l.getAlwaysSucceeds()) {
                lookaheads.add(l);
            } else {
                break;
            }
        }
        int tokenCount = grammar.getLexerData().getTokenCount();
        boolean[] casedValues = new boolean[tokenCount];
        int tokenMaskSize = (tokenCount - 1) / 32 + 1;
        int[] tokenMask = null;
        BitSet tokenMask2 = null;

        boolean inPhase1 = false;
        for (Lookahead lookahead : lookaheads) {

            if (lookahead.getRequiresPhase2Routine()) {
                // In this case lookahead is determined by the jj2 methods.
                phase2list.add(lookahead);
                lookahead.getNestedExpansion().setInternalName("_" + phase2list.size());
                if (inPhase1) {
                    tokenMasks.add(tokenMask);
                    tokenMasks2.add(tokenMask2);
                }
                inPhase1 = false;
            }

            else if (lookahead.getAmount() == 1 && lookahead.getSemanticLookahead() == null
                    && !Semanticizer.emptyExpansionExists(lookahead.getNestedExpansion())
                    && !Semanticizer.javaCodeCheck(lookahead.getNestedExpansion())) {
                
                if (!inPhase1) {
                    for (int i = 0; i < grammar.getLexerData().getTokenCount(); i++) {
                        casedValues[i] = false;
                    }
                    tokenMask = new int[tokenMaskSize];
                    tokenMask2 = new BitSet(tokenMaskSize);
                }
                boolean[] firstSet = lookahead.getFirstSet();
                for (int i = 0; i < grammar.getLexerData().getTokenCount(); i++) {
                    if (firstSet[i]) {
                        if (!casedValues[i]) {
                            casedValues[i] = true;
                            int j1 = i / 32;
                            int j2 = i % 32;
                            tokenMask[j1] |= 1 << j2;
                            tokenMask2.set(i, true);
                        }
                    }
                }
                inPhase1 = true;
            }
            else {
                if (inPhase1) {
                    tokenMasks.add(tokenMask);
                    tokenMasks2.add(tokenMask2);
                }
                inPhase1 = false;
            } 
        }
        if (inPhase1) {
            tokenMasks.add(tokenMask);
            tokenMasks2.add(tokenMask2);
        }
    }
    
    private void visitLookahead(Lookahead lookahead) {
        int tokenCount = grammar.getLexerData().getTokenCount();
        int tokenMaskSize = (tokenCount - 1) / 32 + 1;
        int[] tokenMask = null;
        BitSet tokenMask2 = new BitSet(tokenCount);

        if (lookahead.getRequiresPhase2Routine()) {
            // In this case lookahead is determined by the jj2 methods.
            phase2list.add(lookahead);
            lookahead.getNestedExpansion().setInternalName("_" + phase2list.size());
        }

        else if (lookahead.getAmount() == 1 && lookahead.getSemanticLookahead() == null
                && !Semanticizer.emptyExpansionExists(lookahead.getNestedExpansion())
                && !Semanticizer.javaCodeCheck(lookahead.getNestedExpansion())) 
        {
            tokenMask = new int[tokenMaskSize];
            boolean[] firstSet = lookahead.getFirstSet();
            for (int i = 0; i < grammar.getLexerData().getTokenCount(); i++) {
                if (firstSet[i]) {
                    int j1 = i / 32;
                    int j2 = i % 32;
                    tokenMask[j1] |= 1 << j2;
                    tokenMask2.set(i, true);
                }
            }
            if (grammar.getOptions().getErrorReporting()) {
                tokenMasks.add(tokenMask);
            }
        }
    }

    private void visitExpansion(Expansion e) {
        if (e instanceof ExpansionChoice) {
            visitChoice((ExpansionChoice)e);
        } else if (e instanceof ExpansionSequence) {
            ExpansionSequence e_nrw = (ExpansionSequence) e;
            // We skip the first element in the following iteration since it is
            // the
            // Lookahead object.
            for (int i = 1; i < e_nrw.getChildCount(); i++) {
                visitExpansion((Expansion) e_nrw.getChild(i));
            }
        } else if (e instanceof OneOrMore) {
            OneOrMore oom = (OneOrMore) e;
            int labelIndex = ++gensymindex;
            oom.setLabel("label_" + labelIndex);
            visitExpansion(oom.getNestedExpansion());
            Lookahead la = oom.getLookahead();
            if (!la.getAlwaysSucceeds()) {
                visitLookahead(la);
            }
        } else if (e instanceof ZeroOrMore) {
            ZeroOrMore zom = (ZeroOrMore) e;
            int labelIndex = ++gensymindex;
            zom.setLabel("label_" + labelIndex);
            Lookahead la = zom.getLookahead();
            if (!la.getAlwaysSucceeds()) {
                visitLookahead(la);
            }
            visitExpansion(zom.getNestedExpansion());
        } else if (e instanceof ZeroOrOne) {
            visitExpansion(e.getNestedExpansion());
            Lookahead la = e.getLookahead();
            if (!la.getAlwaysSucceeds()){
                visitLookahead(la);
            }
        } else if (e instanceof TryBlock) {
            visitExpansion(e.getNestedExpansion());
        }
    }

    private void generate3R(Expansion e, int count) {
        Expansion seq = e;
        if (e.getInternalName().equals("")) {
            while (true) {
                if (seq instanceof ExpansionSequence
                        && ((ExpansionSequence) seq).getChildCount() == 2) {
                    seq = (Expansion) seq.getChild(1);
                } else if (seq instanceof NonTerminal) {
                    NonTerminal e_nrw = (NonTerminal) seq;
                    ParserProduction ntprod = grammar
                            .getProductionByLHSName(e_nrw.getName());
                    if (!(ntprod instanceof BNFProduction)) {
                        break; // nothing to do here
                    } else {
                        seq = ntprod.getExpansion();
                    }
                } else
                    break;
            }

            if (seq instanceof RegularExpression) {
                e.setInternalName("jj_scan_token("
                        + ((RegularExpression) seq).getOrdinal() + ")");
                return;
            }

            gensymindex++;
            e.setInternalName("R_" + gensymindex);
        }
        Integer amt = phase3table.get(e);
        if (amt == null || amt < count) {
            Phase3Data p3d = new Phase3Data(e, count);
            phase3list.add(p3d);
            phase3table.put(e, count);
        }
    }

    private void setupPhase3Builds(Expansion e, int amt) {
        if (e instanceof RegularExpression) {
            ; // nothing to do here
        } else if (e instanceof NonTerminal) {
            // All expansions of non-terminals have the "name" fields set. So
            // there's no need to check it below for "e_nrw" and "ntexp". In
            // fact, we rely here on the fact that the "name" fields of both
            // these
            // variables are the same.
            NonTerminal e_nrw = (NonTerminal) e;
            ParserProduction ntprod = grammar
                    .getProductionByLHSName(e_nrw.getName());
            if (!(ntprod instanceof BNFProduction)) {
                ; // nothing to do here
            } else {
                generate3R(ntprod.getExpansion(), amt);
            }
        } else if (e instanceof ExpansionChoice) {
            for (Expansion sub : Nodes.childrenOfType(e, Expansion.class)) {
                generate3R(sub, amt);
            }
        } else if (e instanceof ExpansionSequence) {
            ExpansionSequence e_nrw = (ExpansionSequence) e;
            // We skip the first element in the following iteration since it is
            // the
            // Lookahead object.
            int cnt = amt;
            for (int i = 1; i < e_nrw.getChildCount(); i++) {
                Expansion eseq = (Expansion) e_nrw.getChild(i);
                setupPhase3Builds(eseq, cnt);
                cnt -= minimumSize(eseq);
                if (cnt <= 0)
                    break;
            }
        }  
        else if (e instanceof TryBlock) {
            setupPhase3Builds(e.getNestedExpansion(), amt);
        } else if (e instanceof OneOrMore) {
            generate3R(e.getNestedExpansion(), amt);
        } else if (e instanceof ZeroOrMore) {
            generate3R(e.getNestedExpansion(), amt);
        } else if (e instanceof ZeroOrOne) {
            generate3R(e.getNestedExpansion(), amt);
        }
    }

    public int minimumSize(Expansion e) {
        return minimumSize(e, Integer.MAX_VALUE);
    }

    /*
     * Returns the minimum number of tokens that can parse to this expansion.
     */
    private int minimumSize(Expansion e, int oldMin) {
        int retval = 0; // should never be used. Will be bad if it is.
        if (e.inMinimumSize) {
            // recursive search for minimum size unnecessary.
            return Integer.MAX_VALUE;
        }
        e.inMinimumSize = true;
        if (e instanceof RegularExpression) {
            retval = 1;
        } else if (e instanceof NonTerminal) {
            NonTerminal e_nrw = (NonTerminal) e;
            ParserProduction ntprod = grammar.getProductionByLHSName(e_nrw.getName());
            if (ntprod instanceof BNFProduction) {
                Expansion ntexp = ntprod.getExpansion();
                retval = minimumSize(ntexp);
            } else {
                retval = Integer.MAX_VALUE;
                // Make caller think this is unending (for we do not go beyond
                // JAVACODE during
                // phase3 execution).
            }
        } else if (e instanceof ExpansionChoice) {
            int min = oldMin;
            Expansion nested_e;
            List<Expansion> choices = Nodes.childrenOfType(e, Expansion.class);
            for (int i = 0; min > 1 && i < choices.size(); i++) {
                nested_e = choices.get(i);
                int min1 = minimumSize(nested_e, min);
                if (min > min1)
                    min = min1;
            }
            retval = min;
        } else if (e instanceof ExpansionSequence) {
            int min = 0;
            ExpansionSequence e_nrw = (ExpansionSequence) e;
            // We skip the first element in the following iteration since it is
            // the
            // Lookahead object.
            for (int i = 1; i < e_nrw.getChildCount(); i++) {
                Expansion eseq = (Expansion) e_nrw.getChild(i);
                int mineseq = minimumSize(eseq);
                if (min == Integer.MAX_VALUE || mineseq == Integer.MAX_VALUE) {
                    min = Integer.MAX_VALUE; // Adding infinity to something
                                                // results in infinity.
                } else {
                    min += mineseq;
                    if (min > oldMin)
                        break;
                }
            }
            retval = min;
        } else if (e instanceof TryBlock) {
            retval = minimumSize(e.getNestedExpansion());
        } else if (e instanceof OneOrMore) {
            retval = minimumSize(e.getNestedExpansion());
        } else if (e instanceof ZeroOrMore) {
            retval = 0;
        } else if (e instanceof ZeroOrOne) {
            retval = 0;
        } else if (e instanceof Lookahead) {
            retval = 0;
        } else if (e instanceof Action) {
            retval = 0;
        }
        e.inMinimumSize = false;
        return retval;
    }

}

/**
 * This class stores information to pass from phase 2 to phase 3.
 */
class Phase3Data {

    /*
     * This is the expansion to generate the jj3 method for.
     */
    Expansion exp;

    /*
     * This is the number of tokens that can still be consumed. This number is
     * used to limit the number of jj3 methods generated.
     */
    int count;

    Phase3Data(Expansion e, int c) {
        exp = e;
        count = c;
    }
}
