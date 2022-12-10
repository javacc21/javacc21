[#ftl strict_vars=true]
[#--
/* Copyright (c) 2008-2019 Jonathan Revusky, revusky@javacc.com
 * Copyright (C) 2021-2022 Vinay Sajip, vinay_sajip@yahoo.co.uk
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
 --]

        //
        // the root node of the AST. It only makes sense to call
        // this after a successful parse.
        //
        public Node RootNode { get { return CurrentNodeScope.RootNode; } }

        //
        // push a node onto the top of the node stack
        //
        internal void PushNode(Node n) {
            CurrentNodeScope.Add(n);
        }

        //
        // return the node on the top of the stack, and remove it from the
        // stack
        internal Node PopNode() {
            return CurrentNodeScope.Pop();
        }

        //
        // the node currently on the top of the tree-building stack.
        //
        internal Node PeekNode() {
            return CurrentNodeScope.Peek();
        }

        //
        // Puts the node on the top of the stack. However, unlike pushNode()
        // it replaces the node that is currently on the top of the stack.
        // This is effectively equivalent to PopNode() followed by PushNode(n)
        //
        internal void PokeNode(Node n) {
            CurrentNodeScope.Poke(n);
        }

        //
        // Pop and return a number of nodes. This can be perhaps optimized
        // at the expense of encapsulation (e.g. get a slice of the underlying
        // array)
        //
        internal IList<Node> PopNodes(uint n) {
            var result = new List<Node>();
            for (uint i = 0; i < n; i++) {
                result.Add(PopNode());
            }
            return result;
        }

        //
        // return the number of Nodes on the tree-building stack in the current node
        // scope.
        internal int NodeArity { get { return CurrentNodeScope.Count; } }

        internal void ClearNodeScope() {
            CurrentNodeScope.Clear();
        }

        internal void OpenNodeScope(Node n) {
            new NodeScope(this);    // as a side-effect, attaches to parser instance
            if (n != null) {
                var next = NextToken(LastConsumedToken);
                n.TokenSource = LastConsumedToken.TokenSource;
                n.BeginOffset = next.BeginOffset;
                n.Open();
    [#list grammar.openNodeScopeHooks as hook]
                ${hook}(n);
    [/#list]
            }
        }

        /*
        * A definite node is constructed from a specified number of
        * children.  That number of nodes are popped from the stack and
        * made the children of the definite node.  Then the definite node
        * is pushed on to the stack.
        */
        private void CloseNodeScope(Node n, int num) {
            n.EndOffset = LastConsumedToken.EndOffset;
            CurrentNodeScope.Close();
            var nodes = new List<Node>();
            for (int i = 0; i < num; i++) {
                nodes.Add(PopNode());
            }
            nodes.Reverse();
            foreach (var child in nodes) {
                // FIXME deal with the UNPARSED_TOKENS_ARE_NODES case
                n.AddChild(child);
            }
            n.Close();
            PushNode(n);
    [#list grammar.closeNodeScopeHooks as hook]
            ${hook}(n);
    [/#list]
        }

        /*
        * A conditional node is constructed if the condition is true.  All
        * the nodes that have been pushed since the node was opened are
        * made children of the conditional node, which is then pushed
        * on to the stack.  If the condition is false the node is not
        * constructed and they are left on the stack.
        */
        private void CloseNodeScope(Node n, bool condition) {
            if (n!= null && condition) {
                n.EndOffset = LastConsumedToken.EndOffset;
                var a = NodeArity;
                CurrentNodeScope.Close();
                var nodes = new List<Node>();
                while (a-- > 0) {
                    nodes.Add(PopNode());
                }
                nodes.Reverse();
                foreach (var child in nodes) {
                    if (UnparsedTokensAreNodes && child is Token tok) {
                        while (tok.PreviousCachedToken != null && tok.PreviousCachedToken.IsUnparsed) {
                            tok = tok.PreviousCachedToken;
                        }
                        while (tok.IsUnparsed) {
                            n.AddChild(tok);
                            tok = tok.NextCachedToken;
                        }
                    }
                    n.AddChild(child);
                }
                n.Close();
                PushNode(n);
    [#list grammar.closeNodeScopeHooks as hook]
                ${hook}(${nodeVarName});
    [/#list]
            }
            else {
                CurrentNodeScope.Close();
            }
        }
