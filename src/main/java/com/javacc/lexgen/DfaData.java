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
package com.javacc.lexgen;

import java.util.*;
import com.javacc.Grammar;
import com.javacc.parsegen.RegularExpression;
import com.javacc.parser.tree.RegexpStringLiteral;

/**
 * Class to hold the data for generating the DFA's for 
 * string literals
 */
public class DfaData {

    final private LexicalStateData lexicalState;
    final private Grammar grammar;
    private BitSet singlesToSkipSet = new BitSet();
    private BitSet subStringSet = new BitSet();
    private BitSet subStringAtPosSet = new BitSet();
    private List<Map<String, KindInfo>> stringLiteralTables = new ArrayList<>();
    private int[] maxStringLengthForActive = new int[100]; 
    private int maxStringIndex, maxStringLength;

    DfaData(LexicalStateData lexicalState) {
        this.lexicalState = lexicalState;
        this.grammar = lexicalState.getGrammar();
    }

    public long getSinglesToSkip(int byteNum) {
        long[] ll = singlesToSkipSet.toLongArray();
        return ll.length > byteNum ? ll[byteNum] : 0L;
    }

    public boolean getHasSinglesToSkip() {
        return singlesToSkipSet.cardinality()>0;
    }

    public int getMaxStringIndex() {
        return this.maxStringIndex;
    }

    void setMaxStringIndex(int maxStringIndex) {
        this.maxStringIndex = maxStringIndex;
    }

    public List<Map<String, KindInfo>> getStringLiteralTables() {
        return stringLiteralTables;
    }

    public int getMaxStringLength() {
        return maxStringLength;
    }

    public int getMaxStringLengthForActive(int i) {
        return maxStringLengthForActive[i];
    }

    public boolean getSubString(int i) {
        return subStringSet.get(i);
    }

    public boolean getSubStringAtPos(int i) {
        return subStringAtPosSet.get(i);
    }

    void generate(final RegexpStringLiteral rsLiteral) {
        final int ordinal = rsLiteral.getOrdinal();
        final String stringLiteral = rsLiteral.getImage();
        final int stringLength = stringLiteral.length();
        this.maxStringLength = Math.max(stringLength, maxStringLength);
        this.maxStringIndex = Math.max(maxStringIndex, ordinal+1);
        while (stringLiteralTables.size() < stringLength) {
            stringLiteralTables.add(new HashMap<>());
        }
        for (int i = 0; i < stringLength; i++) {
            final char c = stringLiteral.charAt(i);
            String s = Character.toString(c);
            if (grammar.getOptions().getIgnoreCase()) {
                s = s.toLowerCase(Locale.ROOT);
            }
            Map<String, KindInfo> table = stringLiteralTables.get(i);
            if (!table.containsKey(s)) {
                table.put(s, new KindInfo(grammar));
            }
            KindInfo info = table.get(s);
            if (!grammar.getOptions().getIgnoreCase() && rsLiteral.getIgnoreCase()) {
                table.put(s.toLowerCase(Locale.ROOT), info);
                table.put(s.toUpperCase(Locale.ROOT), info);
            }
            if (i + 1 == stringLength) {
                info.insertFinalKind(ordinal);
            }
            else {
                info.insertValidKind(ordinal);
            }
        }
        maxStringLengthForActive[ordinal/64] = Math.max(maxStringLengthForActive[ordinal/64], stringLength - 1);
    }


    void generateData() {
        fillSubString();
        for (int i = 0; i < getMaxStringLength(); i++) {
            Map<String, KindInfo> tab = getStringLiteralTables().get(i);
            for (String key : tab.keySet()) {
                generateDfaCase(key.charAt(0), tab.get(key), i);
            }
        }
    }

    public boolean generateDfaCase(char ch, KindInfo info, int index) {
        for (int kind = 0; kind<getMaxStringIndex(); kind++) {
        	if (index == 0 && ch < 128 && info.getFinalKindCnt() !=0
        			&& (!lexicalState.hasNfa() || !lexicalState.getNfaData().canStartNfaUsingAscii(ch))) {
        			if (info.isFinalKind(kind) && !subStringSet.get(kind)) {
                        if (lexicalState.getNfaData().cannotBeMatchedAsStringLiteral(kind, index)) {
                            break; 
                        }
                        else if (grammar.getLexerData().getSkipSet().get(kind)
        				        && !grammar.getLexerData().getSpecialSet().get(kind)
        						&& grammar.getLexerData().getRegularExpression(kind).getCodeSnippet() == null
        						&& grammar.getLexerData().getRegularExpression(kind).getNewLexicalState() == null) {
                            singlesToSkipSet.set(ch);
        					if (grammar.getOptions().getIgnoreCase()) {
                                singlesToSkipSet.set(Character.toUpperCase(ch));
                                singlesToSkipSet.set(Character.toLowerCase(ch));
        					}
        					return false;
        				}
        			}
        	}
        }
        return true;
    }

    final int getStrKind(String str) {
        for (int i = 0; i < getMaxStringIndex(); i++) {
            RegularExpression re = grammar.getLexerData().getRegularExpression(i);
            if (lexicalState.containsRegularExpression(re)) {
                if (re.getImage() != null && re.getImage().equals(str))
                    return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    void fillSubString() {
        for (int i = 0; i < getMaxStringIndex(); i++) {
            RegularExpression re = grammar.getLexerData().getRegularExpression(i);
            subStringSet.clear(i);
            if (re.getImage() == null || !lexicalState.containsRegularExpression(re)) {
                continue;
            }
            if (lexicalState.isMixedCase()) {
                // We will not optimize ior mixed case
                subStringSet.set(i);
                subStringAtPosSet.set(re.getImage().length() - 1);
                continue;
            }
            for (int j = 0; j < maxStringIndex; j++) {
                RegularExpression re2 = grammar.getLexerData().getRegularExpression(j);
                if (j != i && lexicalState.containsRegularExpression(re2) && re2.getImage() != null) {
                    if (re2.getImage().indexOf(re.getImage()) == 0) {
                        subStringSet.set(i);
                        subStringAtPosSet.set(re.getImage().length() - 1);
                        break;
                    } else if (grammar.getOptions().getIgnoreCase()
                            && re2.getImage().toLowerCase().startsWith(re.getImage().toLowerCase())) {
                        subStringSet.set(i);
                        subStringAtPosSet.set(re.getImage().length() - 1);
                        break;
                    }
                }
            }
        }
    }

    static public List<String> rearrange(Map<String, KindInfo> table) {
    	List<String> result = new ArrayList<>(table.keySet());
    	Collections.sort(result);
    	return result;
    }
}