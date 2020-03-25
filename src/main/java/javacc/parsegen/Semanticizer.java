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

package javacc.parsegen;

import java.util.*;

import javacc.Grammar;
import javacc.MetaParseException;
import javacc.lexgen.*;
import javacc.parser.JavaCCConstants;
import javacc.parser.Nodes;
import javacc.parser.Token;
import javacc.parser.tree.*;

public class Semanticizer {
    private Grammar grammar;
    private LexerData lexerData;

    public Semanticizer(Grammar grammar) {
        this.grammar = grammar;
        this.lexerData = grammar.getLexerData();
    }

    public void start() throws MetaParseException {

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
         */
        for (ParserProduction np : grammar.getParserProductions()) {
            ExpansionTreeWalker.postOrderWalk(np.getExpansion(),
                    new LookaheadFixer());
        }

        /*
         * The following loop populates "production_table"
         */
        for (ParserProduction p : grammar.getParserProductions()) {
            grammar.getProductionTable().put(p.getName(), p);
        }

        /*
         * The following walks the entire parse tree to make sure that all
         * non-terminals on RHS's are defined on the LHS.
         */
        for (ParserProduction np : grammar.getParserProductions()) {
            ExpansionTreeWalker.preOrderWalk(np.getExpansion(),
                    new ProductionDefinedChecker());
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
            if (tp.getLexStates() == null) {
                for (LexicalState lexState : lexerData.getLexicalStates()) {
                    tp.addChild(Token.newToken(JavaCCConstants.IDENTIFIER, lexState.getName()));
                }
            }
            List<Map<String, Map<String, RegularExpression>>> table = new ArrayList<Map<String, Map<String, RegularExpression>>>();
            for (int i = 0; i < tp.getLexStates().length; i++) {
                LexicalState lexState = lexerData.getLexicalState(tp.getLexStates()[i]);
                table.add(lexState.getTokenTable());
            }
            for (RegexpSpec res : respecs) {
                if (res.getRegexp() instanceof RegexpStringLiteral) {
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
         * attaching links to "RJustName"s. Error messages are given if
         * undeclared names are used, or if "RJustNames" refer to private
         * regular expressions or to regular expressions of any kind other than
         * TOKEN. In addition, this loop also removes top level "RJustName"s
         * from "rexprlist". This code is not executed if
         * grammar.getOptions().getUserDefinedLexer() is set to true. Instead
         * the following block of code is executed.
         */

        if (!grammar.getOptions().getUserDefinedLexer()) {
            FixRJustNames frjn = new FixRJustNames();
            for (TokenProduction tp : grammar.getAllTokenProductions()) {
                List<RegexpSpec> respecs = tp.getRegexpSpecs();
                for (RegexpSpec res : respecs) {
                    frjn.root = res.getRegexp();
                    ExpansionTreeWalker.preOrderWalk(res.getRegexp(), frjn);
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

        // The following code sets the value of the "emptyPossible" field of
        // JavaCodeProduction
        // nodes. This field is initialized to false, and then the entire list
        // of
        // productions is processed. This is repeated as long as at least one
        // item
        // got updated from false to true in the pass.
        boolean emptyUpdate = true;
        while (emptyUpdate) {
            emptyUpdate = false;
            for (ParserProduction prod : grammar.getParserProductions()) {
                if (prod.getExpansion().isPossiblyEmpty()) {
                    if (!prod.emptyPossible) {
                    	emptyUpdate = true;
                        prod.emptyPossible = true;
                    }
                }
            }
        }

        if (grammar.getErrorCount() == 0) {

            // The following code checks that all ZeroOrMore, ZeroOrOne, and
            // OneOrMore nodes
            // do not contain expansions that can expand to the empty token
            // list.
            for (ParserProduction np : grammar.getParserProductions()) {
                ExpansionTreeWalker.preOrderWalk(np.getExpansion(),
                        new EmptyChecker());
            }

            // The following code goes through the productions and adds pointers
            // to other
            // productions that it can expand to without consuming any tokens.
            // Once this is
            // done, a left-recursion check can be performed.
            for (ParserProduction prod : grammar.getParserProductions()) {
                addLeftMost(prod, prod.getExpansion());
            }

            // Now the following loop calls a recursive walk routine that
            // searches for
            // actual left recursions. The way the algorithm is coded, once a
            // node has
            // been determined to participate in a left recursive loop, it is
            // not tried
            // in any other loop.
            for (ParserProduction prod : grammar.getParserProductions()) {
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
                for (ParserProduction prod : grammar.getParserProductions()) {
                    ExpansionTreeWalker.preOrderWalk(prod.getExpansion(),
                            new LookaheadChecker());
                }
            }

        } 
        if (grammar.getErrorCount() != 0) {
            throw new MetaParseException();
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
    static private void addLeftMost(ParserProduction prod, Expansion exp) {
        if (exp instanceof NonTerminal) {
            for (int i = 0; i < prod.leIndex; i++) {
                if (prod.leftExpansions[i] == ((NonTerminal) exp).getProduction()) {
                    return;
                }
            }
            if (prod.leIndex == prod.leftExpansions.length) {
                ParserProduction[] newle = new ParserProduction[prod.leIndex * 2];
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
            for (Expansion e : Nodes.childrenOfType(exp, Expansion.class)) {
                addLeftMost(prod, e);
            }
        } else if (exp instanceof ExpansionSequence) {
            for (Expansion e : Nodes.childrenOfType(exp, Expansion.class)) {
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
    private boolean prodWalk(ParserProduction prod) {
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
   
    /**
     * Objects of this class are created from class Semanticizer to work on
     * references to regular expressions from RJustName's.
     */
    class FixRJustNames extends TreeWalkerOp {

        private RegularExpression root;

        boolean goDeeper(Expansion e) {
            return true;
        }

        void action(Expansion e) {
            if (e instanceof RegexpRef) {
                RegexpRef jn = (RegexpRef) e;
                RegularExpression rexp = grammar.getNamedToken(jn.getLabel());
                if (rexp == null && !jn.getLabel().equals("EOF")) {
                    grammar.addWarning(e, "Undefined lexical token name \""
                            + jn.getLabel() + "\".");
                } else if (jn == root && !jn.tpContext.isExplicit()
                        && rexp.isPrivate()) {
                    grammar.addSemanticError(e, "Token name \"" + jn.getLabel()
                            + "\" refers to a private "
                            + "(with a #) regular expression.");
                } else if (jn == root && !jn.tpContext.isExplicit()
                        && !rexp.tpContext.getKind().equals("TOKEN")) {
                    grammar
                            .addSemanticError(
                                    e,
                                    "Token name \""
                                            + jn.getLabel()
                                            + "\" refers to a non-token "
                                            + "(SKIP, MORE, IGNORE_IN_BNF) regular expression.");
                } else {
                    jn.setOrdinal(rexp.getOrdinal());
                    jn.setRegexp(rexp);
                }
            }
        }

    }

    class LookaheadFixer extends TreeWalkerOp {

        void action(Expansion e) {
            if (e instanceof ExpansionSequence) {
                if (e.getParent() instanceof ExpansionChoice
                        || e.getParent() instanceof ZeroOrMore
                        || e.getParent() instanceof OneOrMore
                        || e.getParent() instanceof ZeroOrOne) {
                    return;
                }
                ExpansionSequence seq = (ExpansionSequence) e;
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
                newUnits.addAll(Nodes.childrenOfType(seq, Expansion.class));
                seq.clearChildren();
                for (Expansion exp : newUnits) {
                    seq.addChild(exp);
                }
            }
        }

    }

    class ProductionDefinedChecker extends TreeWalkerOp {

        void action(Expansion e) {
            if (e instanceof NonTerminal) {
                NonTerminal nt = (NonTerminal) e;
                ParserProduction prod = grammar.getProductionByName(nt.getName());
                if (prod==null) {
                    grammar.addSemanticError(e, "Non-terminal " + nt.getName() + " has not been defined.");
                } else {
                    prod.parents.add(nt);
                }
            }
        }
    }

    class EmptyChecker extends TreeWalkerOp {

         void action(Expansion e) {
            if (e instanceof OneOrMore) {
                if (e.getNestedExpansion().isPossiblyEmpty()) {
                    grammar
                            .addSemanticError(e,
                                    "Expansion within \"(...)+\" can be matched by empty string.");
                }
            } else if (e instanceof ZeroOrMore) {
                if (e.getNestedExpansion().isPossiblyEmpty()) {
                    grammar
                            .addSemanticError(e,
                                    "Expansion within \"(...)*\" can be matched by empty string.");
                }
            } else if (e instanceof ZeroOrOne) {
                if (e.getNestedExpansion().isPossiblyEmpty()) {
                    grammar
                            .addSemanticError(e,
                                    "Expansion within \"(...)?\" can be matched by empty string.");
                }
            }
        }

    }

    class LookaheadChecker extends TreeWalkerOp {

        private LookaheadCalc lookaheadCalculator = new LookaheadCalc();

        boolean goDeeper(Expansion e) {
        	return !(e instanceof RegularExpression) && !(e instanceof Lookahead);
//        	return !(e instanceof RegularExpression);
        }

        void action(Expansion e) {
            if (e instanceof ExpansionChoice) {
                if (grammar.getOptions().getLookahead() == 1
                        || grammar.getOptions().getForceLaCheck()) {
                    lookaheadCalculator.choiceCalc((ExpansionChoice) e, grammar);
                }
            } 
            else if ((e instanceof OneOrMore) || (e instanceof ZeroOrMore) || (e instanceof ZeroOrOne)) {
                if (grammar.getOptions().getForceLaCheck()
                        || (implicitLA(e.getNestedExpansion()) && grammar.getOptions()
                                .getLookahead() == 1)) {
                    lookaheadCalculator.ebnfCalc(e, e.getNestedExpansion(), grammar);
                }
            } 
        }

        boolean implicitLA(Expansion exp) {
            if (!(exp instanceof ExpansionSequence)) {
                return true;
            }
            ExpansionSequence seq = (ExpansionSequence) exp;
            return (!(seq.getLookahead()  instanceof ExplicitLookahead));
        }
    }
}
