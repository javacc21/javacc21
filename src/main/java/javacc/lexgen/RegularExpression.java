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
import javacc.parser.tree.Expression;
import javacc.parsegen.Expansion;
import javacc.parser.ParseException;
import javacc.parser.tree.CharacterList;
import javacc.parser.tree.CharacterRange;
import javacc.parser.tree.CodeBlock;
import javacc.parser.tree.EndOfFile;
import javacc.parser.tree.OneOrMoreRegexp;
import javacc.parser.tree.ZeroOrMoreRegexp;
import javacc.parser.tree.ZeroOrOneRegexp;
import javacc.parser.tree.RegexpRef;
import javacc.parser.tree.RegexpChoice;
import javacc.parser.tree.RegexpSequence;
import javacc.parser.tree.RegexpStringLiteral;
import javacc.parser.tree.TokenProduction;

/**
 * An abstract base class from which all the AST nodes that
 * are regular expressions inherit.
 */

public abstract class RegularExpression extends Expansion {

    private static final int REGULAR_TOKEN = 0;
    private static final int SPECIAL_TOKEN = 1;
    private static final int SKIP = 2;
    private static final int MORE = 3;


    public RegularExpression(Grammar grammar) {
        super(grammar);
    }
    
    public RegularExpression() {
    }
    
    private int type;
    
    /**
     * The label of the regular expression (if any). If no label is present,
     * this is set to "".
     */
    private String label = "";

    /**
     * The ordinal value assigned to the regular expression. It is used for
     * internal processing and passing information between the parser and the
     * lexical analyzer.
     */
    private int id;

    private boolean ignoreCase;

    private LexicalState newLexicalState;

    private CodeBlock action;

    public CodeBlock getAction() {
        return action;
    }

    public void setAction(CodeBlock action) {
        this.action = action;
    }

    void setIgnoreCase(boolean b) {
        this.ignoreCase = b;
    }

    public boolean getIgnoreCase() {
        return ignoreCase;
    }

    /**
     * The LHS to which the token value of the regular expression is assigned.
     * This can be null.
     */
    public Expression lhs;

    /**
     * This flag is set if the regular expression has a label prefixed with the #
     * symbol - this indicates that the purpose of the regular expression is
     * solely for defining other regular expressions.
     */
    private boolean private_rexp = false;

    /**
     * If this is a top-level regular expression (nested directly within a
     * TokenProduction), then this field point to that TokenProduction object.
     */
    public TokenProduction tpContext = null;

    public boolean canMatchAnyChar() {
        return false;
    }

    /**
     * The following variable is used to maintain state information for the loop
     * determination algorithm: It is initialized to 0, and set to -1 if this
     * node has been visited in a pre-order walk, and then it is set to 1 if the
     * pre-order walk of the whole graph from this node has been traversed.
     * i.e., -1 indicates partially processed, and 1 indicates fully processed.
     */
    public int walkStatus = 0;

    public String getLabel() {
        return label.length() == 0 ? String.valueOf(id) : label;
    }

    public boolean hasLabel() {
        return label.length() > 0;
    }
    
    public boolean getIsNamed() {
        return hasLabel() && !Character.isDigit(label.charAt(0));
    }

    public int getOrdinal() {
        return id;
    }

    public void setOrdinal(int id) {
        this.id =  id;
    }

    public Expression getLHS() {
        return lhs;
    }
    
    public void setLHS(Expression lhs) {
        this.lhs = lhs;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public LexicalState getLexicalState() {
        List<LexicalState> states = getGrammar().getLexerData().getLexicalStates();
        LexicalState result = states.get(0);
        for (LexicalState ls : states) {
            if (ls.containsRegularExpression(this)) {
                result = ls;
            }
        }
        return result;
        
    }
    
    void setNewLexicalState(LexicalState newLexicalState) {
        this.newLexicalState = newLexicalState;
    }

    public LexicalState getNewLexicalState() {
        return newLexicalState;
    }

    public boolean isRegularToken() {
        return type == REGULAR_TOKEN;
    }
    
    public boolean isSpecialToken() {
        return type == SPECIAL_TOKEN;
    }
    
    public boolean isSkip() {
        return type == SKIP || type == SPECIAL_TOKEN;
    }
    
    public boolean isMore() {
        return type == MORE;
    }
    
    void setRegularToken() {
        this.type = REGULAR_TOKEN;
    }
    
    void setSpecialToken() {
        this.type = SPECIAL_TOKEN;
    }
    
    void setMore() {
        this.type = MORE;
    }
    
    void setSkip() {
        this.type = SKIP;
    }
    
    public String getEmit() {
        return emitRE(this);
    }
    
    static public String emitRE(RegularExpression re) {
        String returnString = "";
        boolean hasLabel = !re.label.equals("");
        boolean justName = re instanceof RegexpRef;
        boolean eof = re instanceof EndOfFile;
        boolean isString = re instanceof RegexpStringLiteral;
        boolean toplevelRE = (re.tpContext != null);
        boolean needBrackets = justName || eof || hasLabel
                || (!isString && toplevelRE);
        if (needBrackets) {
            returnString += "<";
            if (!justName) {
                if (re.private_rexp) {
                    returnString += "#";
                }
                if (hasLabel) {
                    returnString += re.label;
                    returnString += ": ";
                }
            }
        }
        if (re instanceof CharacterList) {
            CharacterList cl = (CharacterList) re;
            if (cl.isNegated()) {
                returnString += "~";
            }
            returnString += "[";
            boolean first = true;
            for (CharacterRange o : cl.getDescriptors()) {
                if (!first)
                    returnString += ",";
                if (o.isSingleChar()) {
                    returnString += "\"";
                    char s[] = { o.left };
                    returnString += ParseException.addEscapes(new String(s));
                    returnString += "\"";
                } else if (o instanceof CharacterRange) {
                    returnString += "\"";
                    char s[] = { ((CharacterRange) o).left };
                    returnString += ParseException.addEscapes(new String(s));
                    returnString += "\"-\"";
                    s[0] = ((CharacterRange) o).right;
                    returnString += ParseException.addEscapes(new String(s));
                    returnString += "\"";
                } else {
                    throw new RuntimeException("Oops: unknown character list element type.");
                }
                first = false;
            }
            returnString += "]";
        } else if (re instanceof RegexpChoice) {
            RegexpChoice c = (RegexpChoice) re;
            for (RegularExpression sub : c.getChoices()) {
                returnString += emitRE(sub);
                returnString += " | ";
            }
            if (returnString.endsWith(" | "))
                returnString = returnString.substring(0,
                        returnString.length() - 3);
        } else if (re instanceof EndOfFile) {
            returnString += "EOF";
        } else if (re instanceof RegexpRef) {
            RegexpRef jn = (RegexpRef) re;
            returnString += jn.getLabel();
        } else if (re instanceof OneOrMoreRegexp) {
            OneOrMoreRegexp om = (OneOrMoreRegexp) re;
            returnString += "(";
            returnString += emitRE(om.getRegexp());
            returnString += ")+";
        } else if (re instanceof RegexpSequence) {
            RegexpSequence s = (RegexpSequence) re;
            for (RegularExpression sub : s.getUnits()) {
                boolean needParens = false;
                if (sub instanceof RegexpChoice) {
                    needParens = true;
                }
                if (needParens) {
                    returnString += "(";
                }
                returnString += emitRE(sub);
                if (needParens) {
                    returnString += ")";
                }
                returnString += " ";
            }
            if (returnString.endsWith(" "))
                returnString = returnString.substring(0,
                        returnString.length() - 1);
        } else if (re instanceof RegexpStringLiteral) {
            RegexpStringLiteral sl = (RegexpStringLiteral) re;
            returnString += ("\"" + ParseException.addEscapes(sl.getImage()) + "\"");
        } else if (re instanceof ZeroOrMoreRegexp) {
            ZeroOrMoreRegexp zm = (ZeroOrMoreRegexp) re;
            returnString += "(";
            returnString += emitRE(zm.getRegexp());
            returnString += ")*";
        } else if (re instanceof ZeroOrOneRegexp) {
            ZeroOrOneRegexp zo = (ZeroOrOneRegexp) re;
            returnString += "(";
            returnString += emitRE(zo.getRegexp());
            returnString += ")?";
        } else {
            throw new RuntimeException("Oops: Unknown regular expression type.");
        }
        if (needBrackets) {
            returnString += ">";
        }
        return returnString;
    }
    
    public boolean isPrivate() {
        return this.private_rexp;
    }
    
    public boolean getPrivate() {
        return this.private_rexp;
    }
    
    public void setPrivate(boolean privat) {
        this.private_rexp = privat;
    }
    
    public String getGeneratedClassName() {
        return generatedClassName;
    }
    
    public void setGeneratedClassName(String generatedClassName) {
        this.generatedClassName = generatedClassName;
    }

    public String getGeneratedSuperClassName() {
        return generatedSuperClassName;
    }

    public void setGeneratedSuperClassName(String generatedSuperClassName) {
        this.generatedSuperClassName = generatedSuperClassName;
    }
    
    private String generatedClassName = "Token", generatedSuperClassName;


}
