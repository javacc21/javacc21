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

    private List<Expansion> scanAheadExpansions, expansionsNeedingPredicate;
    
    public ParserData(Grammar grammar) {
        this.grammar = grammar;
        this.lexerData = grammar.getLexerData();
    }

    /**
     * The list of Expansions for which we need to generate scanahead routines
     * @return
     */
    public List<Expansion> getScanAheadExpansions() {
        if (scanAheadExpansions == null) {
            scanAheadExpansions = new ArrayList<>();
            for (BNFProduction production : grammar.getParserProductions()) {
                new LookaheadTableBuilder().visit(production.getExpansion());
            }
            for (Expansion expansion : scanAheadExpansions) {
                expansion.setMaxScanAhead(expansion.getLookaheadAmount());
            }
            for (int scanAheadIndex=0; scanAheadIndex < scanAheadExpansions.size(); scanAheadIndex++) {
                Expansion exp = scanAheadExpansions.get(scanAheadIndex);
                new ScanAheadTableBuilder(exp.getMaxScanAhead()).visit(exp);
            }
            // Need to get rid of duplicates
            this.scanAheadExpansions = new ArrayList<>(new LinkedHashSet<>(scanAheadExpansions));
        }
        return scanAheadExpansions;
    }

    public List<Expansion> getExpansionsNeedingPredicate() {
        if (expansionsNeedingPredicate == null) {
            expansionsNeedingPredicate = grammar.descendants(Expansion.class, exp->exp.getRequiresPredicateMethod());
        }
        return expansionsNeedingPredicate;
    }

    public class LookaheadTableBuilder extends Node.Visitor {
        public void visit(ExpansionChoice choice) {
            for (Expansion exp : choice.getChoices()) {
                visit(exp);
                if (exp.isAlwaysSuccessful()) break;
                if (exp.getRequiresScanAhead()) {
                    if (!exp.getLookaheadExpansion().isSingleToken()) {
                        scanAheadExpansions.add(exp.getLookaheadExpansion());
                    }
                }
            }
        }

        private void handleOneOrMoreEtc(Expansion exp) {
            visit(exp.getNestedExpansion());
            if (exp.getRequiresScanAhead()) {
               if (!exp.getLookaheadExpansion().isSingleToken()) {
                    scanAheadExpansions.add(exp.getLookaheadExpansion());
               }
            }
        }

       public void visit(OneOrMore exp) {handleOneOrMoreEtc(exp);}

        public void visit(ZeroOrMore exp) {handleOneOrMoreEtc(exp);}

        public void visit(ZeroOrOne exp) {handleOneOrMoreEtc(exp);}

        public void visit(TryBlock exp) {visit(exp.getNestedExpansion());}

        public void visit(Lookahead la) {
            // We ignore expansions that are part of a syntactic lookahead
        }

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


    public class ScanAheadTableBuilder extends Node.Visitor {
        private int lookaheadAmount;

        ScanAheadTableBuilder(int lookaheadAmount) {
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
           if (expansion.getMaxScanAhead()< lookaheadAmount) {
//                if (!expansion.isSingleToken()) {
                    scanAheadExpansions.add(expansion);
//                }
                expansion.setMaxScanAhead(lookaheadAmount);
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

        for (ExpansionSequence sequence : grammar.descendants(ExpansionSequence.class)) {
            Node parent = sequence.getParent();
            if (sequence.getHasExplicitLookahead() 
               && !(//parent instanceof BNFProduction || //comment out for now
                   parent instanceof Expansion.ChoicePoint
                   || parent instanceof Lookahead)) 
            {
                grammar.addSemanticError(sequence, "Encountered LOOKAHEAD(...) at a non-choice location." );
            }
        }

        for (Node exp : grammar.descendants(Expansion.class, exp -> exp.isScanLimit())) {
            Node grandparent = exp.getParent().getParent();
            if (!(grandparent instanceof Expansion.ChoicePoint
               || grandparent instanceof BNFProduction)) 
            {
                   grammar.addSemanticError(exp, "The up-to-here delimiter can only be at a choice point or at the top level of a grammar production.");
            }

        }

        for (ExpansionChoice choice : grammar.descendants(ExpansionChoice.class)) {
            List<Expansion> choices = choice.childrenOfType(Expansion.class);
            for (int i=0; i < choices.size() -1; i++) {
                Expansion unit = choices.get(i);
                if (unit.isAlwaysSuccessful()) {
                    int numFollowing = choices.size() - i -1;
                    String msg = (numFollowing ==1) ? " The expansion that follows " : "The following " + numFollowing + " expansions ";
                    grammar.addSemanticError(unit, "This expansion can match the empty string." + msg + "can never be matched.");
                }
            }
        }

        for (Node child : grammar.descendants(Expansion.class, n -> n instanceof OneOrMore || n instanceof ZeroOrMore)) {
            Expansion exp = (Expansion) child;
            String starOrPlus = exp instanceof ZeroOrMore ? "(...)*" : "(...)+";
            if (exp.getNestedExpansion().isAlwaysSuccessful()) {
                Failure failure = exp.getNestedExpansion().firstChildOfType(Failure.class);
                if (failure != null) {
                    grammar.addSemanticError(exp, "Expansion inside " + starOrPlus + " always fails! This cannot be right!");
                } else {
                  grammar.addSemanticError(exp, "Expansion inside " + starOrPlus + " can be matched by the empty string, so it would produce an infinite loop!");
                }
            }
        }

        for (ZeroOrOne zoo : grammar.descendants(ZeroOrOne.class, zoo->zoo.getNestedExpansion().isAlwaysSuccessful())) {
            if (zoo.getNestedExpansion() instanceof Failure) {
                grammar.addWarning(zoo, "The FAIL inside this construct is always triggered. This may not be your intention.");
            } else {
                grammar.addWarning(zoo, "The expansion inside this (...)? construct can be matched by the empty string so it is always matched. This may not be your intention.");
            }
        }
   

        // Check that non-terminals have all been defined.
        for (NonTerminal nt : grammar.descendants(NonTerminal.class, nt->nt.getProduction()==null)) {
            grammar.addSemanticError(nt, "Non-terminal " + nt.getName() + " has not been defined.");
        }

        // Check that no LookBehind predicates refer to an undefined Production
        for (LookBehind lb : grammar.getAllLookBehinds()) {
            for (String name: lb.getPath()) {
                if (Character.isJavaIdentifierStart(name.charAt(0))) {
                    if (grammar.getProductionByName(name) == null) {
                        grammar.addSemanticError(lb, "Predicate refers to undefined Non-terminal: " + name);
                    }
                }
            }
        }

        for (Lookahead la : grammar.getAllLookaheads()) {
            Expansion exp = la.getUpToExpansion();
            if (exp != null && !exp.isSingleToken()) {
                grammar.addSemanticError(exp, "The expansion after UPTO here must be matched by exactly one token.");
            }
         }


// Below this point is legacy code that I'm still schlepping around.

        /*
         * The following loop ensures that all target lexical states are
         * defined. Also piggybacking on this loop is the detection of <EOF> and
         * <name> in token productions. After reporting an error, these entries
         * are removed. Also checked are definitions on inline private regular
         * expressions. This loop works slightly differently when
         * USER_DEFINED_LEXER is set to true. In this case, <name> occurrences
         * are OK, while regular expression specs generate a warning.
         */
        for (TokenProduction tp: grammar.descendants(TokenProduction.class)) { 
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
        for (TokenProduction tp : grammar.descendants(TokenProduction.class)) { 
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
        //Let's jump out here, I guess.
        if (grammar.getErrorCount() >0) return;

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
            List<RegexpRef> refs = grammar.descendants(RegexpRef.class);
            for (RegexpRef ref : refs) {
                String label = ref.getLabel();
                RegularExpression referenced = grammar.getNamedToken(label);
                if (referenced == null && !ref.getLabel().equals("EOF")) {
                    grammar.addSemanticError(ref,  "Undefined lexical token name \"" + label + "\".");
                } else if (referenced != null && ref.tpContext != null && !ref.tpContext.isExplicit()) {
                    if (referenced.isPrivate()) {
                        grammar.addSemanticError(ref, "Token name \"" + label + "\" refers to a private (with a #) regular expression.");
                    }   else if (!referenced.tpContext.getKind().equals("TOKEN")) {
                        grammar.addSemanticError(ref, "Token name \"" + label + "\" refers to a non-token (SKIP, MORE, UNPARSED) regular expression.");
                    } 
                } 
            }
            for (TokenProduction tp : grammar.descendants(TokenProduction.class)) {
                for (RegexpRef ref : tp.descendants(RegexpRef.class)) {
                    RegularExpression rexp = grammar.getNamedToken(ref.getLabel());
                    if (rexp != null) {
                        ref.setOrdinal(rexp.getOrdinal());
                        ref.setRegexp(rexp);
                    }
                }
            }
            
            for (TokenProduction tp : grammar.descendants(TokenProduction.class)) {
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

        if (!grammar.getOptions().getUserDefinedLexer()) {
            RegexpVisitor reVisitor = new RegexpVisitor();
            for (TokenProduction tp : grammar.getAllTokenProductions()) {
                reVisitor.visit(tp);
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
}
