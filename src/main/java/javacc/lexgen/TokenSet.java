/* Copyright (c) 2020 Jonathan Revusky, revusky@javacc.com
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notices,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary formnt must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name Jonathan Revusky, Sun Microsystems, Inc.
 *       nor the names of any contributors may be used to endorse 
 *       or promote products derived from this software without specific prior written 
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

/**
 * A class to represent a set of Token types.
 * Will probably eventually move this into the Token.java.ftl as 
 * something available to all generated parsers.
 */

public class TokenSet extends BitSet {
	
	private static final long serialVersionUID = 1L;
	
	private Grammar grammar;

	public TokenSet(Grammar grammar) {
		this.grammar = grammar;
	}
	
	public List<String> getTokenNames() {
		List<String> names = new ArrayList<>();
		int tokCount = grammar.getLexerData().getTokenCount();
		for (int i = 0; i<tokCount; i++) {
			if (get(i)) {
				names.add(grammar.getTokenName(i));
			}
		}
		return names;
	}
	
	private String getFirstTokenName() {
		int tokCount = grammar.getLexerData().getTokenCount();
		for (int i=0; i<tokCount; i++) {
			if (get(i)) {
				return grammar.getTokenName(i);
			}
		}
		return null;
	}
	
	public String getCommaDelimitedTokens() {
		if (cardinality() <=1) {
			return getFirstTokenName();
		}
		StringBuilder result = new StringBuilder();
		for (String name : getTokenNames()) {
			result.append(name);
			result.append(", ");
		}
		result.setLength(result.length() -2);
		return result.toString();
	}
}
