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

package javacc.parsegen;

import java.util.*;


import javacc.Grammar;
import javacc.MetaParseException;
import javacc.lexgen.LexerData;
import javacc.lexgen.LexicalState;
import javacc.lexgen.RegularExpression;
import javacc.parser.Node;
import javacc.parser.ParseException;
import javacc.parser.tree.*;

/**
 * Class to build up certain data structures for the parser.
 */
public class ParserData {

    private Grammar grammar;
    
    private LexerData lexerData;
    private int gensymindex = 0;
    
    private List<MatchInfo> sizeLimitedMatches;
    
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
     * methods. 
     */
    private List<Lookahead> phase2list = new ArrayList<Lookahead>();
    private List<Phase3Data> phase3list = new ArrayList<Phase3Data>();
    private Map<Expansion, Integer> phase3table = new LinkedHashMap<Expansion, Integer>();

    public ParserData(Grammar grammar) {
    	this.grammar = grammar;
    	this.lexerData = grammar.getLexerData();
    }
    
    public void buildData() throws MetaParseException {
        for (BNFProduction p : grammar.getParserProductions()) {
             visitExpansion(p.getExpansion());
        }
        for (Lookahead la : phase2list) {
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

    public List<Lookahead> getPhase2Lookaheads() {
        return phase2list;
    }
    
    public Map<Expansion, Integer> getPhase3Table() {
        return phase3table;
    }
    
    public int getPhase3ExpansionCount(Expansion exp) {
        return phase3table.get(exp);
    }
    
    List<MatchInfo> getSizeLimitedMatches() {
        return sizeLimitedMatches;
    }

    void setSizeLimitedMatches(List<MatchInfo> sizeLimitedMatches) {
        this.sizeLimitedMatches = sizeLimitedMatches;
    }

 
    private void visitExpansionChoice(ExpansionChoice choice) {
    	List<Lookahead> lookaheads = new ArrayList<Lookahead>();
    	List<ExpansionSequence> choices = choice.childrenOfType(ExpansionSequence.class);
    	for (ExpansionSequence nestedSeq : choices) {
    		visitExpansion(nestedSeq);
    		if (nestedSeq.isEmpty()) break; //REVISIT. Is this possible?
    		Lookahead lookahead = (Lookahead) nestedSeq.getChild(0);
    		if (lookahead.getAlwaysSucceeds()) break;
    		lookaheads.add(lookahead);
    	}
    	for (Lookahead lookahead : lookaheads) {
    		if (lookahead.getRequiresPhase2Routine()) {
    			// In this case lookahead is determined by the jj2 methods.
    			phase2list.add(lookahead);
    			lookahead.getNestedExpansion().setPhase2RoutineName("phase2_" + phase2list.size());
    		}
    	}
    }
    
    private void visitLookahead(Lookahead lookahead) {
        if (lookahead.getRequiresPhase2Routine()) {
            // In this case lookahead is determined by the phase2 methods.
            phase2list.add(lookahead);
		    lookahead.getNestedExpansion().setPhase2RoutineName("phase2_" + phase2list.size());
        }
    }
    
    private void visitExpansion(Expansion expansion) {
    	if (expansion instanceof ExpansionChoice) {
            visitExpansionChoice((ExpansionChoice)expansion);
        } else if (expansion instanceof ExpansionSequence) {
            ExpansionSequence expansionSequence = (ExpansionSequence) expansion;
            // We skip the first element in the following iteration since it is
            // the
            // Lookahead object.
            for (int i = 1; i < expansionSequence.getChildCount(); i++) {
                visitExpansion((Expansion) expansionSequence.getChild(i));
            }
        } else if (expansion instanceof OneOrMore) {
            OneOrMore oom = (OneOrMore) expansion;
            ++gensymindex;
            oom.setLabel("label_" + gensymindex);
            visitExpansion(oom.getNestedExpansion());
            Lookahead la = oom.getLookahead();
            if (!la.getAlwaysSucceeds()) {
                visitLookahead(la);
            }
        } else if (expansion instanceof ZeroOrMore) {
            ZeroOrMore zom = (ZeroOrMore) expansion;
            int labelIndex = ++gensymindex;
            zom.setLabel("label_" + labelIndex);
            Lookahead la = zom.getLookahead();
            if (!la.getAlwaysSucceeds()) {
                visitLookahead(la);
            }
            visitExpansion(zom.getNestedExpansion());
        } else if (expansion instanceof ZeroOrOne) {
            visitExpansion(expansion.getNestedExpansion());
            Lookahead la = expansion.getLookahead();
            if (!la.getAlwaysSucceeds()){
                visitLookahead(la);
            }
        } else if (expansion instanceof TryBlock) {
            visitExpansion(expansion.getNestedExpansion());
        }
    }

    private void generate3R(Expansion expansion, int count) {
        Expansion seq = expansion;
          if (expansion.getPhase2RoutineName() == null) {
            while (true) {
                if (seq instanceof ExpansionSequence
                        && ((ExpansionSequence) seq).getChildCount() == 2) {
                    seq = (Expansion) seq.getChild(1);
                } else if (seq instanceof NonTerminal) {
                    NonTerminal e_nrw = (NonTerminal) seq;
                    BNFProduction ntprod = grammar.getProductionByName(e_nrw.getName());
                        seq = ntprod.getExpansion();
                } else
                    break;
            }

            if (seq instanceof RegularExpression) {
                expansion.setOrdinal(((RegularExpression) seq).getOrdinal());
                return;
            }

            gensymindex++;
            expansion.setPhase3RoutineName("phase3R_" + gensymindex);
        }
        Integer amt = phase3table.get(expansion);
        if (amt == null || amt < count) {
            Phase3Data p3d = new Phase3Data(expansion, count);
            phase3list.add(p3d);
            phase3table.put(expansion, count);
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
            NonTerminal nonTerminal = (NonTerminal) e;
            BNFProduction production = nonTerminal.getProduction();
            generate3R(production.getExpansion(), amt);
        } else if (e instanceof ExpansionChoice) {
            for (Expansion sub : e.childrenOfType(Expansion.class)) {
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
                cnt -= eseq.getMinimumSize();
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

    /**
     * This class stores information to pass from phase 2 to phase 3.
     */
    private class Phase3Data {

        /*
         * This is the expansion to generate the phase3 method for.
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
    
    
    
 // This method contains various sanity checks and adjustments
  // that have been in the code forever. There is a general need
  // to clean this up because it presents a significant obstacle
  // to progress, since the original code is written in such an opaque manner that it is
   // hard to understand what it does.
       public void semanticize() throws MetaParseException {

         if (grammar.getErrorCount() != 0)
             throw new MetaParseException();

         if (grammar.getOptions().getLookahead() > 1 && !grammar.getOptions().getForceLaCheck()) {
             grammar.addWarning(null,
                             "Lookahead adequacy checking not being performed since option LOOKAHEAD "
                                     + "is more than 1.  Set option FORCE_LA_CHECK to true to force checking.");
         }


         /*
          * The following walks the entire parse tree to convert all LOOKAHEAD's
          * that are not at choice points (but at beginning of sequences) and
          * converts them to trivial choices. This way, their semantic lookahead
          * specification can be evaluated during other lookahead evaluations.
          * (Legacy code rewritten in a more simple manner, but still should REVISIT)
          */
        
         for (ExpansionSequence sequence : grammar.descendantsOfType(ExpansionSequence.class)) {
         	adjustLookahead(sequence);
         }
         
         // Check that non-terminals have all been defined.
         for (NonTerminal nt : grammar.descendantsOfType(NonTerminal.class)) {
         	BNFProduction prod = nt.getProduction();
         	if (prod == null) {
         		grammar.addSemanticError(nt, "Non-terminal " + nt.getName() + " has not been defined.");
         	} else {
         		prod.referringNonTerminals.add(nt); // REVISIT
         	}
         }
         

         /*
          * The following loop ensures that all target lexical states are
          * defined. Also piggybacking on this loop is the detection of <EOF> and
          * <name> in token productions. After reporting an error, these entries
          * are removed. Also checked are definitions on inline private regular
          * expressions. This loop works slightly differently when
          * USER_DEFINED_LEXER is set to true. In this case, <name> occurrences
          * are OK, while regular expression specs generate a warning.
          */
         for (TokenProduction tp : grammar.getAllTokenProductions()) {
             for (RegexpSpec res : tp.getRegexpSpecs()) {
                 if (res.getNextState() != null) {
                     if (lexerData.getLexicalStateIndex(res.getNextState()) == -1) {
                         grammar.addSemanticError(res.getNsTok(), "Lexical state \""
                                 + res.getNextState() + "\" has not been defined.");
                     }
                 }
                 if (tp.isExplicit() && grammar.getOptions().getUserDefinedLexer()) {
                     grammar.addWarning(res.getRegexp(),
                                        "Ignoring regular expression specification since "
                                        + "option USER_DEFINED_LEXER has been set to true.");
                 } else if (tp.isExplicit()
                         && !grammar.getOptions().getUserDefinedLexer()
                         && res.getRegexp() instanceof RegexpRef) {
                     grammar
                             .addWarning(
                                     res.getRegexp(),
                                     "Ignoring free-standing regular expression reference.  "
                                             + "If you really want this, you must give it a different label as <NEWLABEL:<"
                                             + res.getRegexp().getLabel() + ">>.");
                     tp.removeChild(res);
                 } else if (!tp.isExplicit() && res.getRegexp().isPrivate()) {
                     grammar.addSemanticError(res.getRegexp(),
                             "Private (#) regular expression cannot be defined within "
                                     + "grammar productions.");
                 }
             }
         }

         /*
          * The following loop inserts all names of regular expressions into
          * "named_tokens_table" and "ordered_named_tokens". Duplications are
          * flagged as errors.
          */
         for (TokenProduction tp : grammar.getAllTokenProductions()) {
             List<RegexpSpec> respecs = tp.getRegexpSpecs();
             for (RegexpSpec res : respecs) {
                 RegularExpression re = res.getRegexp();
                 if (!(re instanceof RegexpRef) && re.hasLabel()) {
                     String s = res.getRegexp().getLabel();
                     RegularExpression regexp = grammar.addNamedToken(s,
                             res.getRegexp());
                     if (regexp != null) {
                         grammar.addSemanticError(res.getRegexp(),
                                 "Multiply defined lexical token name \"" + s
                                         + "\".");
                     } 
                     if (lexerData.getLexicalStateIndex(s) != -1) {
                         grammar.addSemanticError(res.getRegexp(),
                                 "Lexical token name \"" + s
                                         + "\" is the same as "
                                         + "that of a lexical state.");
                     }
                 }
             }
         }

         /*
          * The following code merges multiple uses of the same string in the
          * same lexical state and produces error messages when there are
          * multiple explicit occurrences (outside the BNF) of the string in the
          * same lexical state, or when within BNF occurrences of a string are
          * duplicates of those that occur as non-TOKEN's (SKIP, MORE,
          * SPECIAL_TOKEN) or private regular expressions. While doing this, this
          * code also numbers all regular expressions (by setting their ordinal
          * values), and populates the table "names_of_tokens".
          */

         for (TokenProduction tp : grammar.getAllTokenProductions()) {
             List<RegexpSpec> respecs = tp.getRegexpSpecs();
             List<Map<String, Map<String, RegularExpression>>> table = new ArrayList<Map<String, Map<String, RegularExpression>>>();
             for (int i = 0; i < tp.getLexStates().length; i++) {
                 LexicalState lexState = lexerData.getLexicalState(tp.getLexStates()[i]);
                 table.add(lexState.getTokenTable());
             }
             for (RegexpSpec res : respecs) {
                 if (res.getRegexp() instanceof RegexpStringLiteral) {
                 	// TODO: Clean this mess up! (JR)
                     RegexpStringLiteral sl = (RegexpStringLiteral) res.getRegexp();
                     // This loop performs the checks and actions with respect to
                     // each lexical state.
                     for (int i = 0; i < table.size(); i++) {
                         // Get table of all case variants of "sl.image" into
                         // table2.
                         Map<String, RegularExpression> table2 = table.get(i)
                                 .get(sl.getImage().toUpperCase());
                         if (table2 == null) {
                             // There are no case variants of "sl.image" earlier
                             // than the current one.
                             // So go ahead and insert this item.
                             if (sl.getOrdinal() == 0) {
                                 sl.setOrdinal(lexerData.getTokenCount());
                                 lexerData.addRegularExpression(sl);
                             }
                             table2 = new HashMap<String, RegularExpression>();
                             table2.put(sl.getImage(), sl);
                             table.get(i).put(sl.getImage().toUpperCase(), table2);
                         } else if (hasIgnoreCase(table2, sl.getImage())) { // hasIgnoreCase
                                                                         // sets
                                                                         // "other"
                                                                         // if it
                                                                         // is
                                                                         // found.
                             // Since IGNORE_CASE version exists, current one is
                             // useless and bad.
                             if (!sl.tpContext.isExplicit()) {
                                 // inline BNF string is used earlier with an
                                 // IGNORE_CASE.
                                 grammar
                                         .addSemanticError(
                                                 sl,
                                                 "String \""
                                                         + sl.getImage()
                                                         + "\" can never be matched "
                                                         + "due to presence of more general (IGNORE_CASE) regular expression "
                                                         + "at line "
                                                         + other.getBeginLine()
                                                         + ", column "
                                                         + other.getBeginColumn()
                                                         + ".");
                             } else {
                                 // give the standard error message.
                                 grammar.addSemanticError(sl,
                                         "Duplicate definition of string token \""
                                                 + sl.getImage() + "\" "
                                                 + "can never be matched.");
                             }
                         } else if (sl.tpContext.getIgnoreCase()) {
                             // This has to be explicit. A warning needs to be
                             // given with respect
                             // to all previous strings.
                             String pos = "";
                             int count = 0;
                             for (RegularExpression rexp : table2.values()) {
                                 if (count != 0)
                                     pos += ",";
                                 pos += " line " + rexp.getBeginLine();
                                 count++;
                             }
                             if (count == 1) {
                                 grammar.addWarning(sl,
                                         "String with IGNORE_CASE is partially superseded by string at"
                                                 + pos + ".");
                             } else {
                                 grammar.addWarning(sl,
                                         "String with IGNORE_CASE is partially superseded by strings at"
                                                 + pos + ".");
                             }
                             // This entry is legitimate. So insert it.
                             if (sl.getOrdinal() == 0) {
                                 sl.setOrdinal(lexerData.getTokenCount());
                                 lexerData.addRegularExpression(sl);
                             }
                             table2.put(sl.getImage(), sl);
                             // The above "put" may override an existing entry
                             // (that is not IGNORE_CASE) and that's
                             // the desired behavior.
                         } else {
                             // The rest of the cases do not involve IGNORE_CASE.
                             RegularExpression re = (RegularExpression) table2
                                     .get(sl.getImage());
                             if (re == null) {
                                 if (sl.getOrdinal() == 0) {
                                     sl.setOrdinal(lexerData.getTokenCount());
                                     lexerData.addRegularExpression(sl);
                                 }
                                 table2.put(sl.getImage(), sl);
                             } else if (tp.isExplicit()) {
                                 // This is an error even if the first occurrence
                                 // was implicit.
                                 if (tp.getLexStates()[i].equals(grammar.getDefaultLexicalState())) {
                                     grammar.addSemanticError(sl,
                                             "Duplicate definition of string token \""
                                                     + sl.getImage() + "\".");
                                 } else {
                                     grammar.addSemanticError(sl,
                                             "Duplicate definition of string token \""
                                                     + sl.getImage()
                                                     + "\" in lexical state \""
                                                     + tp.getLexStates()[i] + "\".");
                                 }
                             } else if (!re.tpContext.getKind().equals("TOKEN")) {
                                 grammar
                                         .addSemanticError(
                                                 sl,
                                                 "String token \""
                                                         + sl.getImage()
                                                         + "\" has been defined as a \""
                                                         + re.tpContext.getKind()
                                                         + "\" token.");
                             } else if (re.isPrivate()) {
                                 grammar
                                         .addSemanticError(
                                                 sl,
                                                 "String token \""
                                                         + sl.getImage()
                                                         + "\" has been defined as a private regular expression.");
                             } else {
                                 // This is now a legitimate reference to an
                                 // existing RStringLiteral.
                                 // So we assign it a number and take it out of
                                 // "rexprlist".
                                 // Therefore, if all is OK (no errors), then
                                 // there will be only unequal
                                 // string literals in each lexical state. Note
                                 // that the only way
                                 // this can be legal is if this is a string
                                 // declared inline within the
                                 // BNF. Hence, it belongs to only one lexical
                                 // state - namely "DEFAULT".
                                 sl.setOrdinal(re.getOrdinal());
                                 tp.removeChild(res);
                             }
                         }
                     }
                 } else if (!(res.getRegexp() instanceof RegexpRef)) {
                     res.getRegexp().setOrdinal(lexerData.getTokenCount());
                     lexerData.addRegularExpression(res.getRegexp());
                 }
                 if (!(res.getRegexp() instanceof RegexpRef)
                         && !res.getRegexp().getLabel().equals("")) {
                     grammar.addTokenName(res.getRegexp().getOrdinal(), res.getRegexp().getLabel());
                 }
                 if (!(res.getRegexp() instanceof RegexpRef)) {
                     grammar.addRegularExpression(res.getRegexp().getOrdinal(), res.getRegexp());
                 }
             }
         }

         /*
          * The following code performs a tree walk on all regular expressions
          * attaching links to "RegexpRef"s. Error messages are given if
          * undeclared names are used, or if "RegexpRefs" refer to private
          * regular expressions or to regular expressions of any kind other than
          * TOKEN. In addition, this loop also removes top level "RJustName"s
          * from "rexprlist". This code is not executed if
          * grammar.getOptions().getUserDefinedLexer() is set to true. Instead
          * the following block of code is executed.
          */

         if (!grammar.getOptions().getUserDefinedLexer()) {
        	 List<RegexpRef> refs = grammar.descendantsOfType(RegexpRef.class);
        	 for (RegexpRef ref : refs) {
        		 String label = ref.getLabel();
        		 RegularExpression referenced = grammar.getNamedToken(label);
        		 if (referenced == null && !ref.getLabel().equals("EOF")) {
        			 grammar.addSemanticError(ref,  "Undefined lexical token name \"" + label + "\".");
        		 } else if (ref.tpContext != null && !ref.tpContext.isExplicit()) {
        			 if (referenced.isPrivate()) {
        				 grammar.addSemanticError(ref, "Token name \"" + label + "\" refers to a private (with a #) regular expression.");
        			 }   else if (!referenced.tpContext.getKind().equals("TOKEN")) {
                         grammar.addSemanticError(ref, "Token name \"" + label + "\" refers to a non-token (SKIP, MORE, IGNORE_IN_BNF) regular expression.");
        			 } 
        		 } 
        	 }
        	 for (TokenProduction tp : grammar.getAllTokenProductions()) {
        		 for (RegexpRef ref : tp.descendantsOfType(RegexpRef.class)) {
        			 RegularExpression rexp = grammar.getNamedToken(ref.getLabel());
        			 if (rexp != null) {
	        			 ref.setOrdinal(rexp.getOrdinal());
	        			 ref.setRegexp(rexp);
        			 }
        		 }
        	 }
             for (TokenProduction tp : grammar.getAllTokenProductions()) {
                 List<RegexpSpec> respecs = tp.getRegexpSpecs();
                 for (RegexpSpec res : respecs) {
                     if (res.getRegexp() instanceof RegexpRef) {
                         tp.removeChild(res);
                     }
                 }
             }
         }
         
         /*
          * The following code is executed only if
          * grammar.getOptions().getUserDefinedLexer() is set to true. This code
          * visits all top-level "RJustName"s (ignores "RJustName"s nested within
          * regular expressions). Since regular expressions are optional in this
          * case, "RJustName"s without corresponding regular expressions are
          * given ordinal values here. If "RJustName"s refer to a named regular
          * expression, their ordinal values are set to reflect this. All but one
          * "RJustName" node is removed from the lists by the end of execution of
          * this code.
          */

         if (grammar.getOptions().getUserDefinedLexer()) {
             for (TokenProduction tp : grammar.getAllTokenProductions()) {
                 List<RegexpSpec> respecs = tp.getRegexpSpecs();
                 for (RegexpSpec res : respecs) {
                     if (res.getRegexp() instanceof RegexpRef) {

                         RegexpRef jn = (RegexpRef) res.getRegexp();
                         RegularExpression rexp = grammar
                                 .getNamedToken(jn.getLabel());
                         if (rexp == null) {
                             jn.setOrdinal(lexerData.getTokenCount());
                             lexerData.addRegularExpression(jn);
                             grammar.addNamedToken(jn.getLabel(), jn);
                             grammar.addTokenName(jn.getOrdinal(),
                                     jn.getLabel());
                         } else {
                             jn.setOrdinal(rexp.getOrdinal());
                             tp.removeChild(res);
                         }
                     }
                 }
             }
         }

         /*
          * The following code is executed only if
          * grammar.getOptions().getUserDefinedLexer() is set to true. This loop
          * labels any unlabeled regular expression and prints a warning that it
          * is doing so. These labels are added to "ordered_named_tokens" so that
          * they may be generated into the ...Constants file.
          */
         if (grammar.getOptions().getUserDefinedLexer()) {
             for (TokenProduction tp : grammar.getAllTokenProductions()) {
                 List<RegexpSpec> respecs = tp.getRegexpSpecs();
                 for (RegexpSpec res : respecs) {
                     if (grammar.getTokenName(res.getRegexp().getOrdinal()) == null) {
                         grammar.addWarning(res.getRegexp(),
                                 "Unlabeled regular expression cannot be referred to by "
                                         + "user generated token manager.");
                     }
                 }
             }
         }

         if (grammar.getErrorCount() != 0)
             throw new MetaParseException();
         
           if (grammar.getErrorCount() == 0) {

        	 for (Node child : filterDescendants(grammar, (n) -> n instanceof OneOrMore || n instanceof ZeroOrMore || n instanceof ZeroOrOne)) {
        		 Expansion exp = (Expansion) child;
        		 if (exp.getNestedExpansion().isPossiblyEmpty()) {
                     grammar.addSemanticError(exp, "Expansion can be matched by empty string.");
        		 }
        	 }

             // The following code goes through the productions and adds pointers
             // to other
             // productions that it can expand to without consuming any tokens.
             // Once this is
             // done, a left-recursion check can be performed.
             for (BNFProduction prod : grammar.getParserProductions()) {
                 addLeftMost(prod, prod.getExpansion());
             }

             // Now the following loop calls a recursive walk routine that
             // searches for
             // actual left recursions. The way the algorithm is coded, once a
             // node has
             // been determined to participate in a left recursive loop, it is
             // not tried
             // in any other loop.
             for (BNFProduction prod : grammar.getParserProductions()) {
                 if (prod.walkStatus == 0) {
                     prodWalk(prod);
                 }
             }

             // Now we do a similar, but much simpler walk for the regular
             // expression part of
             // the grammar. Here we are looking for any kind of loop, not just
             // left recursions,
             // so we only need to do the equivalent of the above walk.
             // This is not done if option USER_DEFINED_LEXER is set to true.
             if (!grammar.getOptions().getUserDefinedLexer()) {
                 for (TokenProduction tp : grammar.getAllTokenProductions()) {
                     List<RegexpSpec> respecs = tp.getRegexpSpecs();
                     for (RegexpSpec res : respecs) {
                         RegularExpression rexp = res.getRegexp();
                         if (rexp.walkStatus == 0) {
                             rexp.walkStatus = -1;
                             if (rexpWalk(rexp)) {
                                 loopString = "..." + rexp.getLabel() + "... --> "
                                         + loopString;
                                 grammar.addSemanticError(rexp,
                                         "Loop in regular expression detected: \""
                                                 + loopString + "\"");
                             }
                             rexp.walkStatus = 1;
                         }
                     }
                 }
             }

             /*
              * The following code performs the lookahead ambiguity checking.
              */
             if (grammar.getErrorCount() == 0) {
                 if (grammar.getOptions().getLookahead() ==1 || grammar.getOptions().getForceLaCheck()) {
                	 for (ExpansionChoice choice : grammar.descendantsOfType(ExpansionChoice.class)) {
                		 choiceCalc(choice);
                	 }
                	 for (Node node : filterDescendants(grammar, (n) -> n instanceof OneOrMore || n instanceof ZeroOrMore || n instanceof ZeroOrOne)) {
                		 Expansion exp = (Expansion) node;
                		 if (hasImplicitLookahead(exp.getNestedExpansion())) {
                			 ebnfCalc(exp, exp.getNestedExpansion());
                		 }
                	 }
                 }
             }

         } 
         if (grammar.getErrorCount() != 0) {
             throw new MetaParseException();
         }
     }

     // Really need to REVISIT the logic of this.
     private void adjustLookahead(ExpansionSequence seq) {
         if (seq.getParent() instanceof ExpansionChoice
                 || seq.getParent() instanceof ZeroOrMore
                 || seq.getParent() instanceof OneOrMore
                 || seq.getParent() instanceof ZeroOrOne) {
             return;
         }
         Lookahead la = seq.getLookahead();
         if (!(la instanceof ExplicitLookahead)) {
             return;
         }
         // Create a singleton choice with an empty action.
         ExpansionChoice ch = new ExpansionChoice();
         ch.setGrammar(grammar);
         ch.setBeginLine(la.getBeginLine());
         ch.setBeginColumn(la.getBeginColumn());
         ExpansionSequence expansionSequence = new ExpansionSequence(grammar);
         expansionSequence.setBeginLine(la.getBeginLine());
         expansionSequence.setBeginColumn(la.getBeginColumn());
         expansionSequence.addChild(la);
         expansionSequence.setLookahead(la);
         CodeBlock codeBlock = new CodeBlock();
         codeBlock.setBeginLine(la.getBeginLine());
         codeBlock.setBeginColumn(la.getBeginColumn());
         expansionSequence.addChild(codeBlock);
         ch.addChild(expansionSequence);
         if (la.getAmount() != 0) {
             if (la.getSemanticLookahead() != null) {
                 grammar
                         .addWarning(
                                 la,
                                 "Encountered LOOKAHEAD(...) at a non-choice location.  "
                                         + "Only semantic lookahead will be considered here.");
             } else {
                 grammar
                         .addWarning(la,
                                 "Encountered LOOKAHEAD(...) at a non-choice location.  This will be ignored.");
             }
         }
         // Now we have moved the lookahead into the singleton choice.
         // Now create
         // a new dummy lookahead node to replace this one at its
         // original location.
         Lookahead lookahead = new Lookahead(grammar);
         lookahead.setBeginLine(la.getBeginLine());
         lookahead.setBeginColumn(la.getBeginColumn());
         // Now set the la_expansion field of lookahead with a dummy
         // expansion (we use EOF).
         la.setExpansion(new EndOfFile());
         lookahead.setExpansion(new EndOfFile());
         seq.setChild(0, lookahead);
         List<Expansion> newUnits = new ArrayList<Expansion>();
         newUnits.add((Expansion) seq.removeChild(0));
         newUnits.add(ch);
         newUnits.addAll(seq.getUnits());
         seq.clearChildren();
         for (Expansion exp : newUnits) {
             seq.addChild(exp);
         }
     }
     

     
     private RegularExpression other;

     // Checks to see if the "str" is superseded by another equal (except case)
     // string
     // in table.
     private boolean hasIgnoreCase(Map<String, RegularExpression> table,
             String str) {
         RegularExpression rexp;
         rexp = (RegularExpression) (table.get(str));
         if (rexp != null && !rexp.tpContext.getIgnoreCase()) {
             return false;
         }
         for (RegularExpression re : table.values()) {
             if (re.tpContext.getIgnoreCase()) {
                 other = re;
                 return true;
             }
         }
         return false;
     }

     // Updates prod.leftExpansions based on a walk of exp.
     static private void addLeftMost(BNFProduction prod, Expansion exp) {
         if (exp instanceof NonTerminal) {
             for (int i = 0; i < prod.leIndex; i++) {
                 if (prod.leftExpansions[i] == ((NonTerminal) exp).getProduction()) {
                     return;
                 }
             }
             if (prod.leIndex == prod.leftExpansions.length) {
                 BNFProduction[] newle = new BNFProduction[prod.leIndex * 2];
                 System
                         .arraycopy(prod.leftExpansions, 0, newle, 0,
                                 prod.leIndex);
                 prod.leftExpansions = newle;
             }
             prod.leftExpansions[prod.leIndex++] = ((NonTerminal) exp).getProduction();
         } else if (exp instanceof OneOrMore) {
             addLeftMost(prod, (exp.getNestedExpansion()));
         } else if (exp instanceof ZeroOrMore) {
             addLeftMost(prod, exp.getNestedExpansion());
         } else if (exp instanceof ZeroOrOne) {
             addLeftMost(prod, exp.getNestedExpansion());
         } else if (exp instanceof ExpansionChoice) {
             for (Expansion e : exp.childrenOfType(Expansion.class)) {
                 addLeftMost(prod, e);
             }
         } else if (exp instanceof ExpansionSequence) {
             for (Expansion e : exp.childrenOfType(Expansion.class)) {
                 addLeftMost(prod, e);
                 if (!exp.isPossiblyEmpty()) {
                     break;
                 }
             }
         } else if (exp instanceof TryBlock) {
             addLeftMost(prod, exp.getNestedExpansion());
         }
     }

     // The string in which the following methods store information.
     private String loopString;

     // Returns true to indicate an unraveling of a detected left recursion loop,
     // and returns false otherwise.
     private boolean prodWalk(BNFProduction prod) {
         prod.walkStatus = -1;
         for (int i = 0; i < prod.leIndex; i++) {
             if (prod.leftExpansions[i].walkStatus == -1) {
                 prod.leftExpansions[i].walkStatus = -2;
                 loopString = prod.getName() + "... --> "
                         + prod.leftExpansions[i].getName() + "...";
                 if (prod.walkStatus == -2) {
                     prod.walkStatus = 1;
                     grammar.addSemanticError(prod,
                             "Left recursion detected: \"" + loopString + "\"");
                     return false;
                 } else {
                     prod.walkStatus = 1;
                     return true;
                 }
             } else if (prod.leftExpansions[i].walkStatus == 0) {
                 if (prodWalk(prod.leftExpansions[i])) {
                     loopString = prod.getName() + "... --> " + loopString;
                     if (prod.walkStatus == -2) {
                         prod.walkStatus = 1;
                         grammar.addSemanticError(prod,
                                 "Left recursion detected: \"" + loopString
                                         + "\"");
                         return false;
                     } else {
                         prod.walkStatus = 1;
                         return true;
                     }
                 }
             }
         }
         prod.walkStatus = 1;
         return false;
     }

     // Returns true to indicate an unraveling of a detected loop,
     // and returns false otherwise.
     private boolean rexpWalk(RegularExpression rexp) {
         if (rexp instanceof RegexpRef) {
             RegexpRef jn = (RegexpRef) rexp;
             if (jn.getRegexp().walkStatus == -1) {
                 jn.getRegexp().walkStatus = -2;
                 loopString = "..." + jn.getRegexp().getLabel() + "...";
                 // Note: Only the regexpr's of RJustName nodes and the top leve
                 // regexpr's can have labels. Hence it is only in these cases
                 // that
                 // the labels are checked for to be added to the loopString.
                 return true;
             } else if (jn.getRegexp().walkStatus == 0) {
                 jn.getRegexp().walkStatus = -1;
                 if (rexpWalk(jn.getRegexp())) {
                     loopString = "..." + jn.getRegexp().getLabel() + "... --> "
                             + loopString;
                     if (jn.getRegexp().walkStatus == -2) {
                         jn.getRegexp().walkStatus = 1;
                         grammar.addSemanticError(jn.getRegexp(),
                                 "Loop in regular expression detected: \""
                                         + loopString + "\"");
                         return false;
                     } else {
                         jn.getRegexp().walkStatus = 1;
                         return true;
                     }
                 } else {
                     jn.getRegexp().walkStatus = 1;
                     return false;
                 }
             }
         } else if (rexp instanceof RegexpChoice) {
             for (RegularExpression re : ((RegexpChoice) rexp).getChoices()) {
                 if (rexpWalk(re)) {
                     return true;
                 }
             }
             return false;
         } else if (rexp instanceof RegexpSequence) {
             for (RegularExpression re : ((RegexpSequence) rexp).getUnits()) {
                 if (rexpWalk(re)) {
                     return true;
                 }
             }
             return false;
         } else if (rexp instanceof OneOrMoreRegexp) {
             return rexpWalk(((OneOrMoreRegexp) rexp).getRegexp());
         } else if (rexp instanceof ZeroOrMoreRegexp) {
             return rexpWalk(((ZeroOrMoreRegexp) rexp).getRegexp());
         } else if (rexp instanceof ZeroOrOneRegexp) {
             return rexpWalk(((ZeroOrOneRegexp) rexp).getRegexp());
         } else if (rexp instanceof RepetitionRange) {
             return rexpWalk(((RepetitionRange) rexp).getRegexp());
         }
         return false;
     }
    
     private boolean hasImplicitLookahead(Expansion exp) {
         if (!(exp instanceof ExpansionSequence)) {
             return true;
         }
         ExpansionSequence seq = (ExpansionSequence) exp;
         return (!(seq.getLookahead()  instanceof ExplicitLookahead));
     }

 	private MatchInfo overlap(List<MatchInfo> matchList1, List<MatchInfo> matchList2) {
 		for (MatchInfo match1 : matchList1) {
 			for (MatchInfo match2 : matchList2) {
 				int size = match1.firstFreeLoc;
 				MatchInfo match3 = match1;
 				if (size > match2.firstFreeLoc) {
 					size = match2.firstFreeLoc;
 					match3 = match2;
 				}
 				if (size != 0) {
 					// REVISIT. We don't have JAVACODE productions  any more!
 					// we wish to ignore empty expansions and the JAVACODE stuff
 					// here.
 					boolean diffFound = false;
 					for (int k = 0; k < size; k++) {
 						if (match1.match[k] != match2.match[k]) {
 							diffFound = true;
 							break;
 						}
 					}
 					if (!diffFound) {
 						return match3;
 					}
 				}
 			}
 		}
 		return null;
 	}

 	private String image(MatchInfo m) {
 		String ret = "";
 		for (int i = 0; i < m.firstFreeLoc; i++) {
 			if (m.match[i] == 0) {
 				ret += " <EOF>";
 			} else {
 				RegularExpression re = grammar.getRegexpForToken(m.match[i]);
 				if (re instanceof RegexpStringLiteral) {
 					ret += " \"" + ParseException.addEscapes(((RegexpStringLiteral) re).getImage()) + "\"";
 				} else if (re.getLabel() != null && !re.getLabel().equals("")) {
 					ret += " <" + re.getLabel() + ">";
 				} else {
 					ret += " <token of kind " + i + ">";
 				}
 			}
 		}
 		if (m.firstFreeLoc == 0) {
 			return "";
 		} else {
 			return ret.substring(1);
 		}
 	}

 	private void choiceCalc(ExpansionChoice ch) {
 		int first = firstChoice(ch);
 		// dbl[i] and dbr[i] are vectors of size limited matches for choice i
 		// of ch. dbl ignores matches with semantic lookaheads (when
 		// force_la_check
 		// is false), while dbr ignores semantic lookahead.
 		// List<MatchInfo>[] dbl = new List[ch.getChoices().size()];
 		// List<MatchInfo>[] dbr = new List[ch.getChoices().size()];
 		List<Expansion> choices = ch.getChoices();
 		int numChoices = choices.size();
 		List<List<MatchInfo>> dbl = new ArrayList<List<MatchInfo>>(numChoices);
 		List<List<MatchInfo>> dbr = new ArrayList<List<MatchInfo>>(numChoices);
 		for (int i = 0; i < numChoices; i++) {
 			dbl.add(null);
 			dbr.add(null);
 		}
 		int[] minLA = new int[choices.size() - 1];
 		MatchInfo[] overlapInfo = new MatchInfo[choices.size() - 1];
 		int[] other = new int[choices.size() - 1];
 		MatchInfo m;
 		//        List<MatchInfo> partialMatches;
 		boolean overlapDetected;
 		for (int la = 1; la <= grammar.getOptions().getChoiceAmbiguityCheck(); la++) {
 			grammar.setLookaheadLimit(la);
 			grammar.setConsiderSemanticLA(!grammar.getOptions().getForceLaCheck());
 			for (int i = first; i < choices.size() - 1; i++) {
 				grammar.getParserData().setSizeLimitedMatches(new ArrayList<MatchInfo>());
 				m = new MatchInfo(grammar.getLookaheadLimit());
 				m.firstFreeLoc = 0;
 				List<MatchInfo> partialMatches = new ArrayList<MatchInfo>();
 				partialMatches.add(m);
 				genFirstSet(partialMatches, choices.get(i));
 				dbl.set(i, grammar.getParserData().getSizeLimitedMatches());
 			}
 			grammar.setConsiderSemanticLA(false);
 			for (int i = first + 1; i < choices.size(); i++) {
 				grammar.getParserData().setSizeLimitedMatches(new ArrayList<MatchInfo>());
 				m = new MatchInfo(grammar.getLookaheadLimit());
 				m.firstFreeLoc = 0;
 				List<MatchInfo> partialMatches = new ArrayList<MatchInfo>();
 				partialMatches.add(m);
 				genFirstSet(partialMatches, choices.get(i));
 				dbr.set(i, grammar.getParserData().getSizeLimitedMatches());
 			}
 			if (la == 1) {
 				for (int i = first; i < choices.size() - 1; i++) {
 					Expansion exp = choices.get(i);
 					if (exp.isPossiblyEmpty()) {
 						grammar
 						.addWarning(
 								exp,
 								"This choice can expand to the empty token sequence "
 										+ "and will therefore always be taken in favor of the choices appearing later.");
 						break;
 					} 
 				}
 			}
 			overlapDetected = false;
 			for (int i = first; i < choices.size() - 1; i++) {
 				for (int j = i + 1; j < choices.size(); j++) {
 					if ((m = overlap(dbl.get(i), dbr.get(j))) != null) {
 						minLA[i] = la + 1;
 						overlapInfo[i] = m;
 						other[i] = j;
 						overlapDetected = true;
 						break;
 					}
 				}
 			}
 			if (!overlapDetected) {
 				break;
 			}
 		}
 		for (int i = first; i < choices.size() - 1; i++) {
 			if (explicitLookahead(choices.get(i)) && !grammar.getOptions().getForceLaCheck()) {
 				continue;
 			}
 			if (minLA[i] > grammar.getOptions().getChoiceAmbiguityCheck()) {
 				grammar.addWarning(null, "Choice conflict involving two expansions at");
 				System.err.print("         line " + (choices.get(i)).getBeginLine());
 				System.err.print(", column " + (choices.get(i)).getBeginColumn());
 				System.err.print(" and line " + (choices.get(other[i])).getBeginLine());
 				System.err.print(", column " + (choices.get(other[i])).getBeginColumn());
 				System.err.println(" respectively.");
 				System.err
 				.println("         A common prefix is: " + image(overlapInfo[i]));
 				System.err.println("         Consider using a lookahead of " + minLA[i] + " or more for earlier expansion.");
 			} else if (minLA[i] > 1) {
 				grammar.addWarning(null, "Choice conflict involving two expansions at");
 				System.err.print("         line " + choices.get(i).getBeginLine());
 				System.err.print(", column " + (choices.get(i)).getBeginColumn());
 				System.err.print(" and line " + (choices.get(other[i])).getBeginLine());
 				System.err.print(", column " + (choices.get(other[i])).getBeginColumn());
 				System.err.println(" respectively.");
 				System.err.println("         A common prefix is: " + image(overlapInfo[i]));
 				System.err.println("         Consider using a lookahead of " + minLA[i] + " for earlier expansion.");
 			}
 		}
 	}

 	boolean explicitLookahead(Expansion exp) {
 		if (!(exp instanceof ExpansionSequence)) {
 			return false;
 		}
 		ExpansionSequence seq = (ExpansionSequence) exp;
 		List<Expansion> es = seq.getUnits();
 		if (es.isEmpty()) {
 			//REVISIT: Look at this case carefully!
 			return false;
 		}
 		Expansion e = seq.firstChildOfType(Expansion.class);
 		return e instanceof ExplicitLookahead;
 	}

 	int firstChoice(ExpansionChoice ch) {
 		if (ch.getGrammar().getOptions().getForceLaCheck()) {
 			return 0;
 		}
 		List<Expansion> choices = ch.getChoices();
 		for (int i = 0; i < choices.size(); i++) {
 			if (!explicitLookahead(choices.get(i))) {
 				return i;
 			}
 		}
 		return choices.size();
 	}

 	private String image(Expansion exp) {
 		if (exp instanceof OneOrMore) {
 			return "(...)+";
 		} else if (exp instanceof ZeroOrMore) {
 			return "(...)*";
 		} else /* if (exp instanceof ZeroOrOne) */{
 			return "[...]";
 		}
 	}

 	void ebnfCalc(Expansion exp, Expansion nested) {
 		// exp is one of OneOrMore, ZeroOrMore, ZeroOrOne
 		MatchInfo m, m1 = null;
 		List<MatchInfo> partialMatches = new ArrayList<>();
 		int la;
 		for (la = 1; la <= grammar.getOptions().getOtherAmbiguityCheck(); la++) {
 			grammar.setLookaheadLimit(la);
 			grammar.getParserData().setSizeLimitedMatches(new ArrayList<MatchInfo>());
 			m = new MatchInfo(la);
 			m.firstFreeLoc = 0;
 			partialMatches.add(m);
 			grammar.setConsiderSemanticLA(!grammar.getOptions().getForceLaCheck());
 			genFirstSet(partialMatches, nested);
 			List<MatchInfo> first = grammar.getParserData().getSizeLimitedMatches();
 			grammar.getParserData().setSizeLimitedMatches(new ArrayList<MatchInfo>());
 			grammar.setConsiderSemanticLA(false);
 			generateFollowSet(partialMatches, exp, grammar.nextGenerationIndex());
 			List<MatchInfo> follow = grammar.getParserData().getSizeLimitedMatches();
 			if ((m = overlap(first, follow)) == null) {
 				break;
 			}
 			m1 = m;
 		}
 		if (la > grammar.getOptions().getOtherAmbiguityCheck()) {
 			grammar.addWarning(exp, "Choice conflict in " + image(exp) + " construct " + "at line "
 					+ exp.getBeginLine() + ", column " + exp.getBeginColumn() + ".");
 			System.err
 			.println("         Expansion nested within construct and expansion following construct");
 			System.err.println("         have common prefixes, one of which is: "
 					+ image(m1));
 			System.err.println("         Consider using a lookahead of " + la
 					+ " or more for nested expansion.");
 		} else if (la > 1) {
 			grammar.addWarning(exp, "Choice conflict in " + image(exp) + " construct " + "at line "
 					+ exp.getBeginLine() + ", column " + exp.getBeginColumn() + ".");
 			System.err
 			.println("         Expansion nested within construct and expansion following construct");
 			System.err.println("         have common prefixes, one of which is: " + image(m1));
 			System.err.println("         Consider using a lookahead of " + la + " for nested expansion.");
 		}
 	}
     
 	List<MatchInfo> genFirstSet(List<MatchInfo> partialMatches, Expansion exp) {
		if (exp instanceof RegularExpression) {
			int lookaheadLimit = exp.getGrammar().getLookaheadLimit();
			List<MatchInfo> retval = new ArrayList<MatchInfo>();
			for (MatchInfo partialMatch : partialMatches) {
				MatchInfo mnew = new MatchInfo(lookaheadLimit);
				for (int j = 0; j < partialMatch.firstFreeLoc; j++) {
					mnew.match[j] = partialMatch.match[j];
				}
				mnew.firstFreeLoc = partialMatch.firstFreeLoc;
				mnew.match[mnew.firstFreeLoc++] = ((RegularExpression) exp).getOrdinal();
				if (mnew.firstFreeLoc == lookaheadLimit) {
					grammar.getParserData().getSizeLimitedMatches().add(mnew);
				} else {
					retval.add(mnew);
				}
			}
			return retval;
		} else if (exp instanceof NonTerminal) {
			BNFProduction prod = ((NonTerminal) exp).getProduction();
			return genFirstSet(partialMatches, prod.getExpansion());
		} else if (exp instanceof ExpansionChoice) {
			List<MatchInfo> retval = new ArrayList<MatchInfo>();
			ExpansionChoice ch = (ExpansionChoice) exp;
			for (Expansion e : ch.getChoices()) {
				List<MatchInfo> v = genFirstSet(partialMatches, e);
				retval.addAll(v);
			}
			return retval;
		} else if (exp instanceof ExpansionSequence) {
			List<MatchInfo> v = partialMatches;
			ExpansionSequence seq = (ExpansionSequence) exp;
			for (Expansion e : seq.getUnits()) {
				v = genFirstSet(v, e);
				if (v.size() == 0)
					break;
			}
			return v;
		} else if (exp instanceof OneOrMore) {
			List<MatchInfo> retval = new ArrayList<MatchInfo>();
			List<MatchInfo> v = partialMatches;
			while (true) {
				v = genFirstSet(v, exp.getNestedExpansion());
				if (v.isEmpty())
					break;
				retval.addAll(v);
			}
			return retval;
		} else if (exp instanceof ZeroOrMore) {
			List<MatchInfo> retval = new ArrayList<MatchInfo>();
			retval.addAll(partialMatches);
			List<MatchInfo> v = partialMatches;
			while (true) {
				v = genFirstSet(v, exp.getNestedExpansion());
				if (v.size() == 0)
					break;
				retval.addAll(v);
			}
			return retval;
		} else if (exp instanceof ZeroOrOne) {
			List<MatchInfo> retval = new ArrayList<MatchInfo>();
			retval.addAll(partialMatches);
			retval.addAll(genFirstSet(partialMatches,  exp.getNestedExpansion()));
			return retval;
		} else if (exp instanceof TryBlock) {
			return genFirstSet(partialMatches, exp.getNestedExpansion());
		} else if (grammar.considerSemanticLA() && exp instanceof Lookahead
				&& ((Lookahead) exp).getSemanticLookahead() != null) {
			return new ArrayList<MatchInfo>();
		} else {
			List<MatchInfo> retval = new ArrayList<MatchInfo>();
			retval.addAll(partialMatches);
			return retval;
		}
	}

	public static <U extends Object> void listSplit(List<U> toSplit,
			List<U> mask, List<U> partInMask, List<U> rest) {
		OuterLoop: for (int i = 0; i < toSplit.size(); i++) {
			for (int j = 0; j < mask.size(); j++) {
				if (toSplit.get(i) == mask.get(j)) {
					partInMask.add(toSplit.get(i));
					continue OuterLoop;
				}
			}
			rest.add(toSplit.get(i));
		}
	}

	List<MatchInfo> generateFollowSet(List<MatchInfo> partialMatches, Expansion exp, long generation) {
		if (exp.myGeneration == generation) {
			return new ArrayList<MatchInfo>();
		}
		// System.out.println("*** Parent: " + exp.parent);
		exp.myGeneration = generation;
		if (exp.getParent() == null) {
			List<MatchInfo> retval = new ArrayList<MatchInfo>();
			retval.addAll(partialMatches);
			return retval;
		} else if (exp.getParent() instanceof BNFProduction) {
			List<NonTerminal> referringNonTerminals = ((BNFProduction) exp.getParent()).referringNonTerminals;
			List<MatchInfo> retval = new ArrayList<MatchInfo>();
			// System.out.println("1; gen: " + generation + "; exp: " + exp);
			for (NonTerminal nt : referringNonTerminals) {
				List<MatchInfo> v = generateFollowSet(partialMatches, nt, generation);
				retval.addAll(v);
			}
			return retval;
		} else if (exp.getParent() instanceof ExpansionSequence) {
			ExpansionSequence seq = (ExpansionSequence) exp.getParent();
			List<MatchInfo> v = partialMatches;
			for (int i = exp.getIndex() + 1; i < seq.getChildCount(); i++) {
				v = genFirstSet(v, (Expansion) seq.getChild(i));
				if (v.size() == 0)
					return v;
			}
			List<MatchInfo> v1 = new ArrayList<MatchInfo>();
			List<MatchInfo> v2 = new ArrayList<MatchInfo>();
			listSplit(v, partialMatches, v1, v2);
			if (v1.size() != 0) {
				// System.out.println("2; gen: " + generation + "; exp: " +
				// exp);
				v1 = generateFollowSet(v1, seq, generation);
			}
			if (v2.size() != 0) {
				// System.out.println("3; gen: " + generation + "; exp: " +
				// exp);
				v2 = generateFollowSet(v2, seq, exp.getGrammar().nextGenerationIndex());
			}
			v2.addAll(v1);
			return v2;
		} else if (exp.getParent() instanceof OneOrMore
				|| exp.getParent() instanceof ZeroOrMore) {
			List<MatchInfo> moreMatches = new ArrayList<MatchInfo>();
			moreMatches.addAll(partialMatches);
			List<MatchInfo> v = partialMatches;
			while (true) {
				v = genFirstSet(v, exp);
				if (v.size() == 0)
					break;
				moreMatches.addAll(v);
			}
			List<MatchInfo> v1 = new ArrayList<MatchInfo>();
			List<MatchInfo> v2 = new ArrayList<MatchInfo>();
			listSplit(moreMatches, partialMatches, v1, v2);
			if (v1.size() != 0) {
				// System.out.println("4; gen: " + generation + "; exp: " +
				// exp);
				v1 = generateFollowSet(v1, (Expansion) exp.getParent(), generation);
			}
			if (v2.size() != 0) {
				// System.out.println("5; gen: " + generation + "; exp: " +
				// exp);
				v2 = generateFollowSet(v2, (Expansion) exp.getParent(), grammar.nextGenerationIndex());
			}
			v2.addAll(v1);
			return v2;
		} else {
			// System.out.println("6; gen: " + generation + "; exp: " + exp);
			return generateFollowSet(partialMatches, (Expansion) exp.getParent(), generation);
		}
	}
	static final class MatchInfo {

	    int[] match;
	    int firstFreeLoc;

	    MatchInfo(int lookaheadLimit) {
	        this.match = new int[lookaheadLimit];
	    }
	}
	
	interface NodeFilter {
		 boolean accept(Node node);
	}
	
	static private List<Node> filterDescendants(Node node, NodeFilter filter) {
		List<Node> result = new ArrayList<>();
		for (Node child : node.children()) {
			if (filter.accept(child)) {
				result.add(child);
			}
			result.addAll(filterDescendants(child, filter));
		}
		return result;
	}
}
