/* Copyright (C) 2021 Vinay Sajip, vinay_sajip@yahoo.co.uk
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
 *     * Neither the name Vinay Sajip nor the names of any contributors 
 *       may be used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
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

package com.javacc.output;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class Sequencer {
    private HashSet<String> nodes = new HashSet<>();
    private HashMap<String, HashSet<String>> preds = new HashMap<>();
    private HashMap<String, HashSet<String>> succs = new HashMap<>();

    private static HashSet<String> EMPTY_SET = new HashSet<>();

    public void addNode(String node) {
        nodes.add(node);
    }

    public void removeNode(String node) {
        removeNode(node, false);
    }

    public void removeNode(String node, boolean edges) {
        nodes.remove(node);
        if (edges) {
            for (String p : preds.getOrDefault(node, EMPTY_SET)) {
                remove(p, node);
            }
            for (String s : succs.getOrDefault(node, EMPTY_SET)) {
                remove(node, s);
            }
            // remove empties
            for (Map.Entry<String, HashSet<String>> entry : preds.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    preds.remove(entry.getKey());
                }
            }
            for (Map.Entry<String, HashSet<String>> entry : succs.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    succs.remove(entry.getKey());
                }
            }
        }
    }

    public void add(String pred, String succ) {
        HashSet<String> set;

        if (pred.equals(succ)) {
            throw new IllegalArgumentException(String.format("predecessor & successor can't be the same: %s", pred));
        }
        if ((set = preds.get(succ)) == null) {
            set = new HashSet<>();
            preds.put(succ, set);
        }
        set.add(pred);
        if ((set = succs.get(pred)) == null) {
            set = new HashSet<>();
            succs.put(pred, set);
        }
        set.add(succ);
    }

    public void remove(String pred, String succ) {
        if (pred.equals(succ)) {
            throw new IllegalArgumentException(String.format("predecessor & successor can't be the same: %s", pred));
        }
        HashSet<String> p = preds.get(succ);
        HashSet<String> s = succs.get(pred);
        if ((p == null) || (s == null)) {
            throw new IllegalArgumentException(String.format("Not a successor of anything: %s", succ));
        }
        p.remove(pred);
        s.remove(succ);
    }

    public boolean isStep(String step) {
        return preds.containsKey(step) || succs.containsKey(step) || nodes.contains(step);
    }

    public List<String> steps(String upto) {
        List<String> result = new ArrayList<>();
        List<String> todo = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();

        todo.add(upto);
        while (!todo.isEmpty()) {
            String step = todo.remove(0);
            if (seen.contains(step)) {
                if (!step.equals(upto)) {
                    result.remove(step);
                    result.add(step);
                }
            }
            else {
                seen.add(step);
                result.add(step);
                HashSet<String> p = preds.getOrDefault(step, EMPTY_SET);
                todo.addAll(p);
            }
        }
        return result;
    }
}
