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

package com.javacc.core;

import java.util.*;

import com.javacc.Grammar;
import com.javacc.parser.tree.CodeBlock;
import com.javacc.parser.tree.Name;
import com.javacc.parser.tree.TokenProduction;

/**
 * An abstract base class from which all the AST nodes that
 * are regular expressions inherit.
 */

public abstract class RegularExpression extends Expansion {

    public RegularExpression(Grammar grammar) {
        super(grammar);
    }
    
    public RegularExpression() {
    }
    
   /**
     * The ordinal value assigned to the regular expression. It is used for
     * internal processing and passing information between the parser and the
     * lexical analyzer.
     */
    private int id;

    private LexicalStateData newLexicalState;

    private CodeBlock codeSnippet;

    public CodeBlock getCodeSnippet() {
        return codeSnippet;
    }

    void setCodeSnippet(CodeBlock codeSnippet) {
        this.codeSnippet = codeSnippet;
    }

    protected boolean getIgnoreCase() {
        TokenProduction tp = firstAncestorOfType(TokenProduction.class);
        if (tp !=null) return tp.isIgnoreCase();
        return getGrammar().isIgnoreCase();//REVISIT
    }

    /**
     * The LHS to which the token value of the regular expression is assigned.
     * This can be null.
     */
    private Name lhs;

    /**
     * This flag is set if the regular expression has a label prefixed with the #
     * symbol - this indicates that the purpose of the regular expression is
     * solely for defining other regular expressions.
     */
    private boolean _private = false;

    protected TokenProduction getTokenProduction() {
        return firstAncestorOfType(TokenProduction.class);
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

    protected final void setOrdinal(int id) {
        this.id =  id;
    }

    public Name getLHS() {
        return lhs;
    }
    
    public void setLHS(Name lhs) {
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
    
    public void setNewLexicalState(LexicalStateData newLexicalState) {
        this.newLexicalState = newLexicalState;
    }

    public LexicalStateData getNewLexicalState() {
        return newLexicalState;
    }
 
    public boolean isPrivate() {
        return this._private;
    }

    public String getImage() {
        return null;
    }
    
    public void setPrivate(boolean _private) {
        this._private = _private;
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
    
    
    final public boolean isPossiblyEmpty() {
    	return false;
    }
    
    final public int getMinimumSize() {
        return 1;
    }

    final public int getMaximumSize() {
        return 1;
    }

    public boolean isSingleToken() {return true;}
    
    abstract public boolean matchesEmptyString();
}


