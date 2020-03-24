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
import javacc.lexgen.RegularExpression;
import javacc.parser.Nodes;
import javacc.parser.tree.ExpansionSequence;
import javacc.parser.tree.ExpansionChoice;
import javacc.parser.tree.ParserProduction;
import javacc.parser.tree.BNFProduction;
import javacc.parser.tree.OneOrMore;
import javacc.parser.tree.ZeroOrMore;
import javacc.parser.tree.ZeroOrOne;
import javacc.parser.tree.NonTerminal;
import javacc.parser.tree.TryBlock;

final class LookaheadWalk {

	private Grammar grammar;

	LookaheadWalk(Grammar grammar) {
		this.grammar = grammar;
	}

	List<MatchInfo> genFirstSet(List<MatchInfo> partialMatches,
			Expansion exp) {
		if (exp instanceof RegularExpression) {
			int lookaheadLimit = exp.getGrammar().getLookaheadLimit();
			List<MatchInfo> retval = new ArrayList<MatchInfo>();
			for (int i = 0; i < partialMatches.size(); i++) {
				MatchInfo m = partialMatches.get(i);
				MatchInfo mnew = new MatchInfo(lookaheadLimit);
				for (int j = 0; j < m.firstFreeLoc; j++) {
					mnew.match[j] = m.match[j];
				}
				mnew.firstFreeLoc = m.firstFreeLoc;
				mnew.match[mnew.firstFreeLoc++] = ((RegularExpression) exp).getOrdinal();
				if (mnew.firstFreeLoc == lookaheadLimit) {
					grammar.getParserData().getSizeLimitedMatches().add(mnew);
				} else {
					retval.add(mnew);
				}
			}
			return retval;
		} else if (exp instanceof NonTerminal) {
			ParserProduction prod = ((NonTerminal) exp).prod;
			if (prod instanceof BNFProduction) {
				return genFirstSet(partialMatches, prod.getExpansion());
			} else {
				return new ArrayList<MatchInfo>();
			}
		} else if (exp instanceof ExpansionChoice) {
			List<MatchInfo> retval = new ArrayList<MatchInfo>();
			ExpansionChoice ch = (ExpansionChoice) exp;
			for (Expansion e : Nodes.childrenOfType(ch, Expansion.class)) {
				List<MatchInfo> v = genFirstSet(partialMatches, e);
				retval.addAll(v);
			}
			return retval;
		} else if (exp instanceof ExpansionSequence) {
			List<MatchInfo> v = partialMatches;
			ExpansionSequence seq = (ExpansionSequence) exp;
			for (Expansion e : Nodes.childrenOfType(seq,  Expansion.class)) {
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

	List<MatchInfo> generateFollowSet(List<MatchInfo> partialMatches, Expansion exp, long generation, Grammar grammar) {
		if (exp.myGeneration == generation) {
			return new ArrayList<MatchInfo>();
		}
		// System.out.println("*** Parent: " + exp.parent);
		exp.myGeneration = generation;
		if (exp.getParent() == null) {
			List<MatchInfo> retval = new ArrayList<MatchInfo>();
			retval.addAll(partialMatches);
			return retval;
		} else if (exp.getParent() instanceof ParserProduction) {
			List<Expansion> parents = ((ParserProduction) exp.getParent()).parents;
			List<MatchInfo> retval = new ArrayList<MatchInfo>();
			// System.out.println("1; gen: " + generation + "; exp: " + exp);
			for (int i = 0; i < parents.size(); i++) {
				List<MatchInfo> v = generateFollowSet(partialMatches,
						parents.get(i), generation, grammar);
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
				v1 = generateFollowSet(v1, seq, generation, grammar);
			}
			if (v2.size() != 0) {
				// System.out.println("3; gen: " + generation + "; exp: " +
				// exp);
				v2 = generateFollowSet(v2, seq, grammar.nextGenerationIndex(),
						grammar);
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
				v1 = generateFollowSet(v1, (Expansion) exp.getParent(), generation,
						grammar);
			}
			if (v2.size() != 0) {
				// System.out.println("5; gen: " + generation + "; exp: " +
				// exp);
				v2 = generateFollowSet(v2, (Expansion) exp.getParent(), grammar
						.nextGenerationIndex(), grammar);
			}
			v2.addAll(v1);
			return v2;
		} else {
			// System.out.println("6; gen: " + generation + "; exp: " + exp);
			return generateFollowSet(partialMatches, (Expansion) exp.getParent(),
					generation, grammar);
		}
	}
}
