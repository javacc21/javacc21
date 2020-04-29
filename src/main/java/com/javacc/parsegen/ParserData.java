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

package com.javacc.parsegen;

import java.util.*;

import com.javacc.Grammar;
import com.javacc.lexgen.LexerData;
import com.javacc.lexgen.LexicalStateData;
import com.javacc.lexgen.RegularExpression;
import com.javacc.parser.*;
import com.javacc.parser.tree.*;

/**
 * This class holds the remains of all the most icky legacy code that is used to build up the data
 * structure for the parser. The near-term (or possibly mid-term) goal is to refactor and clean it 
 * all up (JR).
 */
public class ParserData {

    private Grammar grammar;

    private LexerData lexerData;

    private List<MatchInfo> sizeLimitedMatches;

    /**
     * These lists are used to maintain the lists of lookaheads and expansions 
     * for which code generation in phase 2 and phase 3 is required. 
     */
    private List<Expansion> phase2list, phase3list;
    
    public ParserData(Grammar grammar) {
        this.grammar = grammar;
        this.lexerData = grammar.getLexerData();
    }

    public void buildData()  {
        phase2list = new ArrayList<Expansion>();
        for (BNFProduction production : grammar.getParserProductions()) {
            new Phase2TableBuilder().visit(production.getExpansion());
        }
        phase3list = new ArrayList<>(phase2list);
        for (Expansion expansion : phase3list) {
            expansion.setPhase3LookaheadAmount(expansion.getLookaheadAmount());
        }
        for (int phase3index=0; phase3index < phase3list.size(); phase3index++) {
            Expansion exp = phase3list.get(phase3index);
            new Phase3TableBuilder(exp.getPhase3LookaheadAmount()).visit(exp);
        }
        // Not sure why it's necessary, but we need to get rid of duplicates
        this.phase3list = new ArrayList<>(new LinkedHashSet<>(phase3list));
//        System.out.println("KILROY 1: " + phase2list.size());
//        System.out.println("KILROY 2: " + phase3list.size());
    }

    public List<Expansion> getPhase2Expansions() {
        return phase2list;
    }

    public List<Expansion> getPhase3Expansions() {
        return phase3list;
    }

    public class Phase2TableBuilder extends Node.Visitor {
        public void visit(ExpansionChoice choice) {
            for (Expansion exp : choice.getChoices()) {
                visit(exp);
                if (exp.isAlwaysSuccessful()) break;
                if (exp.getRequiresPhase2Routine()) {
                    phase2list.add(exp.getLookaheadExpansion());
                }
            }
        }

        private void handleOneOrMoreEtc(Expansion exp) {
            visit(exp.getNestedExpansion());
            if (exp.getRequiresPhase2Routine()) {
                phase2list.add(exp.getLookaheadExpansion());
            }
        }

       public void visit(OneOrMore exp) {handleOneOrMoreEtc(exp);}

        public void visit(ZeroOrMore exp) {handleOneOrMoreEtc(exp);}

        public void visit(ZeroOrOne exp) {handleOneOrMoreEtc(exp);}

        public void visit(TryBlock exp) {visit(exp.getNestedExpansion());}

        public void visit(Lookahead la) {}
    };

    /**
     * A visitor that checks whether there is a self-referential loop in a 
     * Regexp reference. It is a much more terse, readable replacement
     * for some ugly legacy code.
     * @author revusky
     *
     */
    public class RegexpVisitor extends Node.Visitor {

        private HashSet<RegularExpression> alreadyVisited = new HashSet<>(), currentlyVisiting = new HashSet<>();

        public void visit(RegexpRef ref) {
            RegularExpression referredTo = ref.getRegexp();
            if (!alreadyVisited.contains(referredTo)) {
                if (!currentlyVisiting.contains(referredTo)) {
                    currentlyVisiting.add(referredTo);
                    visit(referredTo);
                    currentlyVisiting.remove(referredTo);
                } else {
                    alreadyVisited.add(referredTo);
                    grammar.addSemanticError(ref, "Self-referential loop detected");
                }
            }
        }
    }


    public class Phase3TableBuilder extends Node.Visitor {
        private int lookaheadAmount;

        Phase3TableBuilder(int lookaheadAmount) {
            this.lookaheadAmount = lookaheadAmount;
        }

        public void visit(NonTerminal nt) {
            generate3R(nt.getProduction().getExpansion());
        }

        public void visit(ExpansionChoice choice) {
            for (Expansion sub: choice.getChoices()) {
                generate3R(sub);
            }
        }

        public void visit(ExpansionSequence sequence) {
            int prevLookaheadAmount = this.lookaheadAmount;
            for (Expansion sub: sequence.getUnits()) {
                visit(sub);
                lookaheadAmount -= sub.getMinimumSize();
                if (lookaheadAmount <=0) break;
            }
            this.lookaheadAmount = prevLookaheadAmount;
        }

        public void visit(RegularExpression re) {}
        public void visit(Lookahead la) {}

        public void visit(OneOrMore exp) {generate3R(exp.getNestedExpansion()); }
        public void visit(ZeroOrMore exp) {generate3R(exp.getNestedExpansion());}
        public void visit(ZeroOrOne exp) {generate3R(exp.getNestedExpansion());}


        private void generate3R(Expansion expansion) {
            // It appears that the only possible Expansion types here are ExpansionChoice and ExpansionSequence
           if (expansion.getPhase3LookaheadAmount()< lookaheadAmount) {
                phase3list.add(expansion);
                expansion.setPhase3LookaheadAmount(lookaheadAmount);
            }
        }
    }

    // This method contains various sanity checks and adjustments
    // that have been in the code forever. There is a general need
    // to clean this up because it presents a significant obstacle
    // to progress, since the original code is written in such an opaque manner that it is
    // hard to understand what it does.
    public void semanticize() {

        /*
         * Check whether we have any LOOKAHEADs at non-choice points 
         * REVISIT: Why is this not handled in the grammar spec?
         * The legacy code had some kind of very complex munging going on 
         * in these cases, but serious analysis seems to show that it was not something
         * of any real value.
         */

        for (ExpansionSequence sequence : grammar.descendantsOfType(ExpansionSequence.class)) {
            Node parent = sequence.getParent();
            if (!(parent instanceof ExpansionChoice 
                    || parent instanceof OneOrMore 
                    || parent instanceof ZeroOrOne 
                    || parent instanceof ZeroOrMore) 
                    && sequence.hasExplicitLookahead()) {
                grammar.addSemanticError(sequence, "Encountered LOOKAHEAD(...) at a non-choice location." );
            }
        }

        // Check that non-terminals have all been defined.
        for (NonTerminal nt : grammar.descendantsOfType(NonTerminal.class)) {
            if (nt.getProduction() == null) {
                grammar.addSemanticError(nt, "Non-terminal " + nt.getName() + " has not been defined.");
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
        for (TokenProduction tp: grammar.descendantsOfType(TokenProduction.class)) { 
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
                } 
            }
        }

        /*
         * The following loop inserts all names of regular expressions into
         * "named_tokens_table" and "ordered_named_tokens". Duplications are
         * flagged as errors.
         */
        for (TokenProduction tp : grammar.descendantsOfType(TokenProduction.class)) { 
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
        //        	 for (TokenProduction tp: grammar.descendantsOfType(TokenProduction.class)) { 
        // Cripes, for some reason this is order dependent!
        for (TokenProduction tp : grammar.getAllTokenProductions()) {
            List<RegexpSpec> respecs = tp.getRegexpSpecs();
            List<Map<String, Map<String, RegularExpression>>> table = new ArrayList<Map<String, Map<String, RegularExpression>>>();
            for (int i = 0; i < tp.getLexStates().length; i++) {
                LexicalStateData lexState = lexerData.getLexicalState(tp.getLexStates()[i]);
                table.add(lexState.getTokenTable());
            }
            for (RegexpSpec res : respecs) {
                if (res.getRegexp() instanceof RegexpStringLiteral) {
                    // TODO: Clean this mess up! (JR)
                    RegexpStringLiteral stringLiteral = (RegexpStringLiteral) res.getRegexp();
                    // This loop performs the checks and actions with respect to
                    // each lexical state.
                    for (int i = 0; i < table.size(); i++) {
                        // Get table of all case variants of "sl.image" into
                        // table2.
                        Map<String, RegularExpression> table2 = table.get(i).get(stringLiteral.getImage().toUpperCase());
                        if (table2 == null) {
                            // There are no case variants of "sl.image" earlier
                            // than the current one.
                            // So go ahead and insert this item.
                            if (stringLiteral.getOrdinal() == 0) {
                                stringLiteral.setOrdinal(lexerData.getTokenCount());
                                lexerData.addRegularExpression(stringLiteral);
                            }
                            table2 = new HashMap<String, RegularExpression>();
                            table2.put(stringLiteral.getImage(), stringLiteral);
                            table.get(i).put(stringLiteral.getImage().toUpperCase(), table2);
                        } else if (hasIgnoreCase(table2, stringLiteral.getImage())) { // hasIgnoreCase
                            // sets
                            // "other"
                            // if it
                            // is
                            // found.
                            // Since IGNORE_CASE version exists, current one is
                            // useless and bad.
                            if (!stringLiteral.tpContext.isExplicit()) {
                                // inline BNF string is used earlier with an
                                // IGNORE_CASE.
                                grammar
                                .addSemanticError(
                                        stringLiteral,
                                        "String \""
                                                + stringLiteral.getImage()
                                                + "\" can never be matched "
                                                + "due to presence of more general (IGNORE_CASE) regular expression "
                                                + "at line "
                                                + other.getBeginLine()
                                                + ", column "
                                                + other.getBeginColumn()
                                                + ".");
                            } else {
                                // give the standard error message.
                                grammar.addSemanticError(stringLiteral,
                                        "(1) Duplicate definition of string token \""
                                                + stringLiteral.getImage() + "\" "
                                                + "can never be matched.");
                            }
                        } else if (stringLiteral.tpContext.getIgnoreCase()) {
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
                                grammar.addWarning(stringLiteral,
                                        "String with IGNORE_CASE is partially superseded by string at"
                                                + pos + ".");
                            } else {
                                grammar.addWarning(stringLiteral,
                                        "String with IGNORE_CASE is partially superseded by strings at"
                                                + pos + ".");
                            }
                            // This entry is legitimate. So insert it.
                            if (stringLiteral.getOrdinal() == 0) {
                                stringLiteral.setOrdinal(lexerData.getTokenCount());
                                lexerData.addRegularExpression(stringLiteral);
                            }
                            table2.put(stringLiteral.getImage(), stringLiteral);
                            // The above "put" may override an existing entry
                            // (that is not IGNORE_CASE) and that's
                            // the desired behavior.
                        } else {
                            // The rest of the cases do not involve IGNORE_CASE.
                            RegularExpression re = (RegularExpression) table2.get(stringLiteral.getImage());
                            if (re == null) {
                                if (stringLiteral.getOrdinal() == 0) {
                                    stringLiteral.setOrdinal(lexerData.getTokenCount());
                                    lexerData.addRegularExpression(stringLiteral);
                                }
                                table2.put(stringLiteral.getImage(), stringLiteral);
                            } else if (tp.isExplicit()) {
                                // This is an error even if the first occurrence
                                // was implicit.
                                if (tp.getLexStates()[i].equals(grammar.getDefaultLexicalState())) {
                                    grammar.addSemanticError(stringLiteral,
                                            "(2) Duplicate definition of string token \""
                                                    + stringLiteral.getImage() + "\".");
                                } else {
                                    grammar.addSemanticError(stringLiteral,
                                            "(3) Duplicate definition of string token \""
                                                    + stringLiteral.getImage()
                                                    + "\" in lexical state \""
                                                    + tp.getLexStates()[i] + "\".");
                                }
                            } else if (!re.tpContext.getKind().equals("TOKEN")) {
                                grammar
                                .addSemanticError(
                                        stringLiteral,
                                        "String token \""
                                                + stringLiteral.getImage()
                                                + "\" has been defined as a \""
                                                + re.tpContext.getKind()
                                                + "\" token.");
                            } else if (re.isPrivate()) {
                                grammar
                                .addSemanticError(
                                        stringLiteral,
                                        "String token \""
                                                + stringLiteral.getImage()
                                                + "\" has been defined as a private regular expression.");
                            } else {
                                // This is now a legitimate reference to an
                                // existing StringLiteralRegexp.
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
                                stringLiteral.setOrdinal(re.getOrdinal());
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
                    RegularExpression regexp = res.getRegexp();
                    int ordinal = regexp.getOrdinal();
                    grammar.addRegularExpression(ordinal, regexp);
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
            for (TokenProduction tp : grammar.descendantsOfType(TokenProduction.class)) {
                for (RegexpRef ref : tp.descendantsOfType(RegexpRef.class)) {
                    RegularExpression rexp = grammar.getNamedToken(ref.getLabel());
                    if (rexp != null) {
                        ref.setOrdinal(rexp.getOrdinal());
                        ref.setRegexp(rexp);
                    }
                }
            }
            
            for (TokenProduction tp : grammar.descendantsOfType(TokenProduction.class)) {
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
         * case, RegexpRef's"without corresponding regular expressions are
         * given ordinal values here. If a "RegexpRef" refers to a named regular
         * expression, its ordinal value is set to reflect this. All but one
         * RegexpRef node is removed from the lists by the end of execution of
         * this code.
         */

        if (grammar.getOptions().getUserDefinedLexer()) {
            for (TokenProduction tp : grammar.getAllTokenProductions()) {
                List<RegexpSpec> respecs = tp.getRegexpSpecs();
                for (RegexpSpec res : respecs) {
                    if (res.getRegexp() instanceof RegexpRef) {

                        RegexpRef jn = (RegexpRef) res.getRegexp();
                        RegularExpression rexp = grammar.getNamedToken(jn.getLabel());
                        if (rexp == null) {
                            jn.setOrdinal(lexerData.getTokenCount());
                            lexerData.addRegularExpression(jn);
                            grammar.addNamedToken(jn.getLabel(), jn);
                            grammar.addTokenName(jn.getOrdinal(), jn.getLabel());
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

        for (Node child : grammar.descendants((n) -> n instanceof OneOrMore || n instanceof ZeroOrMore || n instanceof ZeroOrOne)) {
            Expansion exp = (Expansion) child;
            if (exp.getNestedExpansion().isPossiblyEmpty()) {
                grammar.addSemanticError(exp, "Expansion can be matched by empty string.");
            }
        }

        if (!grammar.getOptions().getUserDefinedLexer()) {
            RegexpVisitor reVisitor = new RegexpVisitor();
            for (TokenProduction tp : grammar.getAllTokenProductions()) {
                reVisitor.visit(tp);
            }
        }

        /*
         * The following code performs the lookahead ambiguity checking.
         */
        for (ExpansionChoice choice : grammar.descendantsOfType(ExpansionChoice.class)) {
            choiceCalc(choice);
        }
        for (Node node : grammar.descendants((n) -> n instanceof OneOrMore || n instanceof ZeroOrMore || n instanceof ZeroOrOne)) {
            Expansion exp = (Expansion) node;
            if (!(exp.getNestedExpansion() instanceof ExpansionSequence)) {
                ebnfCalc(exp);
            }
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
    private boolean considerSemanticLookahead;
    private  int lookaheadLimit;
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
        MatchInfo matchInfo;
        boolean overlapDetected;
        for (int la = 1; la <= grammar.getOptions().getChoiceAmbiguityCheck(); la++) {
            lookaheadLimit = la;
            considerSemanticLookahead = true;
            for (int i = first; i < choices.size() - 1; i++) {
                sizeLimitedMatches = new ArrayList<MatchInfo>();
                matchInfo = new MatchInfo(lookaheadLimit);
                matchInfo.firstFreeLoc = 0;
                List<MatchInfo> partialMatches = new ArrayList<MatchInfo>();
                partialMatches.add(matchInfo);
                generateFirstSet(partialMatches, choices.get(i));
                dbl.set(i, sizeLimitedMatches);
            }
            considerSemanticLookahead = false;
            for (int i = first + 1; i < choices.size(); i++) {
                sizeLimitedMatches = new ArrayList<MatchInfo>();
                matchInfo= new MatchInfo(lookaheadLimit);
                List<MatchInfo> partialMatches = new ArrayList<MatchInfo>();
                partialMatches.add(matchInfo);
                generateFirstSet(partialMatches, choices.get(i));
                dbr.set(i, sizeLimitedMatches);
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
                    if ((matchInfo = overlap(dbl.get(i), dbr.get(j))) != null) {
                        minLA[i] = la + 1;
                        overlapInfo[i] = matchInfo;
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
            if (choices.get(i).hasExplicitLookahead()) {
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

    int firstChoice(ExpansionChoice ch) {
        List<Expansion> choices = ch.getChoices();
        for (int i = 0; i < choices.size(); i++) {
            if (!choices.get(i).hasExplicitLookahead()) {
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

    void ebnfCalc(Expansion exp) {
        // exp is one of OneOrMore, ZeroOrMore or ZeroOrOne
        Expansion nested = exp.getNestedExpansion();
        MatchInfo matchInfo = null;
        List<MatchInfo> partialMatches = new ArrayList<>();
        lookaheadLimit = 1;
        sizeLimitedMatches = new ArrayList<MatchInfo>();
        matchInfo = new MatchInfo(1);
        partialMatches.add(matchInfo);
        considerSemanticLookahead = true;
        generateFirstSet(partialMatches, nested);
        List<MatchInfo> first = sizeLimitedMatches;
        sizeLimitedMatches = new ArrayList<MatchInfo>();
        considerSemanticLookahead = false;
        generateFollowSet(partialMatches, exp, grammar.nextGenerationIndex());
        List<MatchInfo> follow = sizeLimitedMatches;
        matchInfo = overlap(first, follow);
        if (matchInfo != null) {
            grammar.addWarning(exp, "Choice conflict in " + image(exp) + " construct " + "at line "
                    + exp.getBeginLine() + ", column " + exp.getBeginColumn() + ".");
            System.err
            .println("         Expansion nested within construct and expansion following construct");
            System.err.println("         have common prefixes, one of which is: " + image(matchInfo));
            System.err.println("         Consider using an explicit lookahead for nested expansion.");
        }
    }


    //TODO: Clean this up using a visitor pattern. The algorithm will probably
    // be far easier to understand. (I don't currently understand it.)
    List<MatchInfo> generateFirstSet(List<MatchInfo> partialMatches, Expansion exp) {
        if (exp instanceof RegularExpression) {
            List<MatchInfo> retval = new ArrayList<MatchInfo>();
            for (MatchInfo partialMatch : partialMatches) {
                MatchInfo mnew = new MatchInfo(lookaheadLimit);
                for (int j = 0; j < partialMatch.firstFreeLoc; j++) {
                    mnew.match[j] = partialMatch.match[j];
                }
                mnew.firstFreeLoc = partialMatch.firstFreeLoc;
                mnew.match[mnew.firstFreeLoc++] = ((RegularExpression) exp).getOrdinal();
                if (mnew.firstFreeLoc == lookaheadLimit) {
                    sizeLimitedMatches.add(mnew);
                } else {
                    retval.add(mnew);
                }
            }
            return retval;
        } else if (exp instanceof NonTerminal) {
            BNFProduction prod = ((NonTerminal) exp).getProduction();
            return generateFirstSet(partialMatches, prod.getExpansion());
        } else if (exp instanceof ExpansionChoice) {
            List<MatchInfo> retval = new ArrayList<MatchInfo>();
            ExpansionChoice ch = (ExpansionChoice) exp;
            for (Expansion e : ch.getChoices()) {
                List<MatchInfo> v = generateFirstSet(partialMatches, e);
                retval.addAll(v);
            }
            return retval;
        } else if (exp instanceof ExpansionSequence) {
            if (considerSemanticLookahead && exp.getHasSemanticLookahead()) {
                return new ArrayList<>();
            }
            List<MatchInfo> v = partialMatches;
            ExpansionSequence seq = (ExpansionSequence) exp;
            for (Expansion e : seq.getUnits()) {
                v = generateFirstSet(v, e);
                if (v.size() == 0)
                    break;
            }
            return v;
        } else if (exp instanceof OneOrMore || exp instanceof ZeroOrMore) {
            List<MatchInfo> retval = new ArrayList<MatchInfo>();
            if (exp instanceof ZeroOrMore) retval.addAll(partialMatches);
            List<MatchInfo> v = partialMatches;
            do {
                v = generateFirstSet(v, exp.getNestedExpansion());
                retval.addAll(v);
            } while (!v.isEmpty());
            return retval;
        } else if (exp instanceof ZeroOrOne) {
            List<MatchInfo> retval = new ArrayList<MatchInfo>();
            retval.addAll(partialMatches);
            retval.addAll(generateFirstSet(partialMatches,  exp.getNestedExpansion()));
            return retval;
        } else if (exp instanceof TryBlock) {
            return generateFirstSet(partialMatches, exp.getNestedExpansion());
        }   
        return new ArrayList<>(partialMatches);
    }

    private  static <U extends Object> void listSplit(Collection<U> toSplit,
            Collection<U> mask, Collection<U> partInMask, Collection<U> rest) {
        for (U u : toSplit) {
            if (mask.contains(u)) {
                partInMask.add(u);
            } else {
                rest.add(u);
            }
        }
    }
    // TODO: Clean up this crap, factor it out to use a visitor pattern as well
    List<MatchInfo> generateFollowSet(List<MatchInfo> partialMatches, Expansion exp, long generation) {
        if (exp.myGeneration == generation) {
            return new ArrayList<MatchInfo>();
        }
        exp.myGeneration = generation;
        if (exp.getParent() instanceof BNFProduction) {
            BNFProduction production = (BNFProduction) exp.getParent();
            List<MatchInfo> retval = new ArrayList<MatchInfo>();
            for (NonTerminal nt : production.getReferringNonTerminals()) {
                List<MatchInfo> v = generateFollowSet(partialMatches, nt, generation);
                retval.addAll(v);
            }
            return retval;
        } else if (exp.getParent() instanceof ExpansionSequence) {
            ExpansionSequence seq = (ExpansionSequence) exp.getParent();
            List<MatchInfo> v = partialMatches;
            for (int i = exp.getIndex() + 1; i < seq.getChildCount(); i++) {
                v = generateFirstSet(v, (Expansion) seq.getChild(i));
                if (v.isEmpty()) return v;
            }
            List<MatchInfo> v1 = new ArrayList<MatchInfo>();
            List<MatchInfo> v2 = new ArrayList<MatchInfo>();
            listSplit(v, partialMatches, v1, v2);
            if (!v1.isEmpty()) {
                v1 = generateFollowSet(v1, seq, generation);
            }
            if (!v2.isEmpty()) {
                v2 = generateFollowSet(v2, seq, grammar.nextGenerationIndex());
            }
            v2.addAll(v1);
            return v2;
        } else if (exp.getParent() instanceof OneOrMore || exp.getParent() instanceof ZeroOrMore) {
            List<MatchInfo> moreMatches = new ArrayList<>();
            moreMatches.addAll(partialMatches);
            List<MatchInfo> v = partialMatches;
            do {
                v = generateFirstSet(v, exp);
                moreMatches.addAll(v);
            } while (!v.isEmpty());
            List<MatchInfo> v1 = new ArrayList<>();
            List<MatchInfo> v2 = new ArrayList<>();
            listSplit(moreMatches, partialMatches, v1, v2);
            if (v1.size() != 0) {
                v1 = generateFollowSet(v1, (Expansion) exp.getParent(), generation);
            }
            if (v2.size() != 0) {
                v2 = generateFollowSet(v2, (Expansion) exp.getParent(), grammar.nextGenerationIndex());
            }
            v2.addAll(v1);
            return v2;
        } 
        return generateFollowSet(partialMatches, (Expansion) exp.getParent(), generation);
    }

    static final class MatchInfo {
        int[] match;
        int firstFreeLoc;

        MatchInfo(int lookaheadLimit) {
            this.match = new int[lookaheadLimit];
        }
    }
}
