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

package com.javacc.lexgen;

import java.util.*;

import com.javacc.Grammar;
import com.javacc.lexgen.TokenSet;
import com.javacc.parsegen.Expansion;
import com.javacc.parser.tree.CodeBlock;
import com.javacc.parser.tree.Expression;
import com.javacc.parser.tree.TokenProduction;

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
     * The ordinal value assigned to the regular expression. It is used for
     * internal processing and passing information between the parser and the
     * lexical analyzer.
     */
    private int id;

    private boolean ignoreCase;

    private LexicalStateData newLexicalState;

    private CodeBlock codeSnippet;

    public CodeBlock getCodeSnippet() {
        return codeSnippet;
    }

    public void setCodeSnippet(CodeBlock codeSnippet) {
        this.codeSnippet = codeSnippet;
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

    boolean canMatchAnyChar() {
        return false;
    }

    public final String getLabel() {
    	String label = super.getLabel();
    	if (label != null && label.length() != 0) {
    	    return label;
    	}
  	    if (id == 0) {
 	        return "EOF";
 	    }
  	    return String.valueOf(id);
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

    public LexicalStateData getLexicalState() {
        List<LexicalStateData> states = getGrammar().getLexerData().getLexicalStates();
        LexicalStateData result = states.get(0);
        for (LexicalStateData ls : states) {
            if (ls.containsRegularExpression(this)) {
                result = ls;
            }
        }
        return result;
        
    }
    
    void setNewLexicalState(LexicalStateData newLexicalState) {
        this.newLexicalState = newLexicalState;
    }

    public LexicalStateData getNewLexicalState() {
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
        if (generatedClassName.equals("Token")) {
            generatedClassName = getLabel();
        }
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
    
    
    public TokenSet getFirstSet() {
    	if (firstSet== null) {
    		firstSet = new TokenSet(getGrammar());
    		firstSet.set(getOrdinal());
    	}
        return firstSet;
    }
    
    public TokenSet getFinalSet() {
        return getFirstSet();	
    }
    
    
    public boolean isPossiblyEmpty() {
    	return false;
    }
    
    public boolean requiresPhase2Routine() {
    	return false;
    }
    
    public int minimumSize(int oldMin) {
    	return 1;
    }
    
}


