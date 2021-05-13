/* Copyright (c) 2021 Jonathan Revusky, revusky@javacc.com
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
 *     * Neither the name Jonathan Revusky nor the names of any contributors 
 *       may be used to endorse or promote products derived from this software 
 *       without specific prior written permission.
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

import com.javacc.parsegen.RegularExpression;
import com.javacc.parser.tree.CharacterRange;

public class CompositeStateSet extends NfaState {

    private Set<NfaState> states = new HashSet<>(); 

    CompositeStateSet(LexicalStateData lsd) {
        super(lsd);
    }

    CompositeStateSet(Set<NfaState> states) {
        this(states.iterator().next().getLexicalState());
        this.states = new HashSet<>(states);
    }

    public boolean isComposite() {
        return true;
    }

    public boolean isMoveCodeNeeded() {
        return states.stream().anyMatch(NfaState::isMoveCodeNeeded);
    }


    public String getMethodName() {
        return "NFA_COMPOSITE_" + lexicalState.getName() + "_" + index;
    }

    public boolean equals(Object other) {
        return (other instanceof CompositeStateSet)
               && ((CompositeStateSet)other).states.equals(this.states);
    }

    public Set<NfaState> getStates() {
        return states;
    }

    public RegularExpression getType() {
        int ordinal = Integer.MAX_VALUE;
        for (NfaState state : states) {
            if (state.getType() != null) {
               if (state.getType().getOrdinal() < ordinal) {
                   ordinal = state.getType().getOrdinal();
               }
            }
        }
        return getLexicalState().getGrammar().getLexerData().getRegularExpression(ordinal);
    }

    static List<Integer> combineMoves(List<Integer> first, List<Integer> second) {
        List<CharacterRange> combinedRangeList = moveRangesToCharacterRanges(first);
        combinedRangeList.addAll(moveRangesToCharacterRanges(second));
        combinedRangeList = NfaBuilder.sortDescriptors(combinedRangeList);
        List<Integer> result = new ArrayList<>();
        for (CharacterRange cr : combinedRangeList) {
            result.add(cr.left);
            result.add(cr.right);
        }
        return result;
    }

    static private List<CharacterRange> moveRangesToCharacterRanges(List<Integer> moveRanges) {
        List<CharacterRange> result = new ArrayList<>();
        for (int i =0; i< moveRanges.size()/2; i++) {
            int left = moveRanges.get(i*2);
            int right = moveRanges.get(i*2+1);
            CharacterRange cr = new CharacterRange(left, right);
            result.add(cr);
        }
        assert result.size() == moveRanges.size()/2;
        return result;
    }

    static BitSet moveRangesToBS(List<Integer> ranges) {
        BitSet result = new BitSet();
        for (int i=0; i< ranges.size(); i+=2) {
            int left = ranges.get(i);
            int right = ranges.get(i+1);
            result.set(left, right+1);
        }
        return result;
    }

    static boolean intersect(List<Integer> moves1, List<Integer> moves2) {
        BitSet bs1 = moveRangesToBS(moves1);
        BitSet bs2 = moveRangesToBS(moves2);
        return bs1.intersects(bs2);
    }

    static Map<NfaState, BitSet> createStateToBitSetMap(Set<NfaState> states) {
        final Map<NfaState, BitSet> result = new HashMap<NfaState, BitSet>();
        states.stream().forEach(state -> result.put(state, moveRangesToBS(state.moveRanges)));
        return result;
    }

    static boolean isNonOverlapping(NfaState state, Set<NfaState> states) {
        for (NfaState other : states) {
            if (state != other && intersect(state.moveRanges, other.moveRanges)) return false;
        }
        return true;
    }

    public List<List<NfaState>> getNonOverlappingFollowedByOverlapping() {
        List<List<NfaState>> result = new ArrayList<>();
        Set<NfaState> statesCopy = new HashSet<>(states);
        List<NfaState> nonOverlapping = new ArrayList<>();
        boolean foundNonOverlapping = true;
        while (foundNonOverlapping) {
            foundNonOverlapping = false;
            for (NfaState state : statesCopy) {
                if (isNonOverlapping(state, statesCopy)) {
                    nonOverlapping.add(state);
                    foundNonOverlapping = true;
                }
            }
            if (foundNonOverlapping) {
                result.add(nonOverlapping);
                statesCopy.removeAll(nonOverlapping);
                nonOverlapping = new ArrayList<>();
            }
        } 
        result.add(new ArrayList<>(statesCopy));
        return result;
    }

}