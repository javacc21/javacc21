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
import javacc.parser.ParseException;
import javacc.parser.tree.ExpansionChoice;
import javacc.parser.tree.ExpansionSequence;
import javacc.parser.tree.ExplicitLookahead;
import javacc.parser.tree.OneOrMore;
import javacc.parser.tree.ZeroOrMore;
import javacc.parser.tree.RegexpStringLiteral;

class LookaheadCalc {

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

	private String image(MatchInfo m, Grammar grammar) {
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

	void choiceCalc(ExpansionChoice ch, Grammar grammar) {
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
		LookaheadWalk lookaheadWalk = new LookaheadWalk(grammar);
		for (int la = 1; la <= grammar.getOptions().getChoiceAmbiguityCheck(); la++) {
			grammar.setLookaheadLimit(la);
			grammar.setConsiderSemanticLA(!grammar.getOptions().getForceLaCheck());
			for (int i = first; i < choices.size() - 1; i++) {
				grammar.getParserData().setSizeLimitedMatches(new ArrayList<MatchInfo>());
				m = new MatchInfo(grammar.getLookaheadLimit());
				m.firstFreeLoc = 0;
				List<MatchInfo> partialMatches = new ArrayList<MatchInfo>();
				partialMatches.add(m);
				lookaheadWalk.genFirstSet(partialMatches, choices.get(i));
				dbl.set(i, grammar.getParserData().getSizeLimitedMatches());
			}
			grammar.setConsiderSemanticLA(false);
			for (int i = first + 1; i < choices.size(); i++) {
				grammar.getParserData().setSizeLimitedMatches(new ArrayList<MatchInfo>());
				m = new MatchInfo(grammar.getLookaheadLimit());
				m.firstFreeLoc = 0;
				List<MatchInfo> partialMatches = new ArrayList<MatchInfo>();
				partialMatches.add(m);
				lookaheadWalk.genFirstSet(partialMatches, choices.get(i));
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
				.println("         A common prefix is: " + image(overlapInfo[i], grammar));
				System.err.println("         Consider using a lookahead of " + minLA[i] + " or more for earlier expansion.");
			} else if (minLA[i] > 1) {
				grammar.addWarning(null, "Choice conflict involving two expansions at");
				System.err.print("         line " + choices.get(i).getBeginLine());
				System.err.print(", column " + (choices.get(i)).getBeginColumn());
				System.err.print(" and line " + (choices.get(other[i])).getBeginLine());
				System.err.print(", column " + (choices.get(other[i])).getBeginColumn());
				System.err.println(" respectively.");
				System.err.println("         A common prefix is: " + image(overlapInfo[i], grammar));
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

	private static String image(Expansion exp) {
		if (exp instanceof OneOrMore) {
			return "(...)+";
		} else if (exp instanceof ZeroOrMore) {
			return "(...)*";
		} else /* if (exp instanceof ZeroOrOne) */{
			return "[...]";
		}
	}

	void ebnfCalc(Expansion exp, Expansion nested, Grammar grammar) {
		// exp is one of OneOrMore, ZeroOrMore, ZeroOrOne
		MatchInfo m, m1 = null;
		List<MatchInfo> partialMatches = new ArrayList<>();
		int la;
		LookaheadWalk lookaheadWalk = new LookaheadWalk(grammar);
		for (la = 1; la <= grammar.getOptions().getOtherAmbiguityCheck(); la++) {
			grammar.setLookaheadLimit(la);
			grammar.getParserData().setSizeLimitedMatches(new ArrayList<MatchInfo>());
			m = new MatchInfo(la);
			m.firstFreeLoc = 0;
			partialMatches.add(m);
			grammar.setConsiderSemanticLA(!grammar.getOptions().getForceLaCheck());
			lookaheadWalk.genFirstSet(partialMatches, nested);
			List<MatchInfo> first = grammar.getParserData().getSizeLimitedMatches();
			grammar.getParserData().setSizeLimitedMatches(new ArrayList<MatchInfo>());
			grammar.setConsiderSemanticLA(false);
			lookaheadWalk.generateFollowSet(partialMatches, exp, grammar.nextGenerationIndex());
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
					+ image(m1, grammar));
			System.err.println("         Consider using a lookahead of " + la
					+ " or more for nested expansion.");
		} else if (la > 1) {
			grammar.addWarning(exp, "Choice conflict in " + image(exp) + " construct " + "at line "
					+ exp.getBeginLine() + ", column " + exp.getBeginColumn() + ".");
			System.err
			.println("         Expansion nested within construct and expansion following construct");
			System.err.println("         have common prefixes, one of which is: "
					+ image(m1, grammar));
			System.err.println("         Consider using a lookahead of " + la
					+ " for nested expansion.");
		}
	}

}
