[#ftl strict_vars=true]
[#--
/* Copyright (c) 2008-2019 Jonathan Revusky, revusky@javacc.com
 * Copyright (C) 2021 Vinay Sajip, vinay_sajip@yahoo.co.uk
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
 --]


    #
    # the root node of the AST. It only makes sense to call
    # this after a successful parse.
    #
    @property
    def root_node(self):
        return self.current_node_scope.root_node

    #
    # push a node onto the top of the node stack
    #
    def push_node(self, n):
        self.current_node_scope.append(n)

    #
    # return the node on the top of the stack, and remove it from the
    # stack
    def pop_node(self):
        return self.current_node_scope.pop()

    #
    # the node currently on the top of the tree-building stack.
    #
    def peek_node(self):
        return self.current_node_scope.peek()

    #
    # Puts the node on the top of the stack. However, unlike pushNode()
    # it replaces the node that is currently on the top of the stack.
    # This is effectively equivalent to popNode() followed by pushNode(n)
    #
    def poke_node(self, n):
        self.current_node_scope.poke(n)

    #
    # Pop and return a number of nodes. This can be perhaps optimized
    # at the expense of encapsulation (e.g. get a slice of the underlying
    # array)
    #
    def pop_nodes(self, n):
        return [self.pop_node() for i in range(n)]

    #
    # return the number of Nodes on the tree-building stack in the current node
    # scope.
    @property
    def node_arity(self):
        return len(self.current_node_scope)

    def clear_node_scope(self):
        self.current_node_scope.clear()

    def open_node_scope(self, n):
        NodeScope(self)  # as a side-effect, attaches into self
        if n is not None:
            next = self.next_token(self.last_consumed_token)
            n.begin_line = next.begin_line
            n.begin_column = next.begin_column
            n.input_source = self.input_source
            n.open()
[#list grammar.openNodeScopeHooks as hook]
            self.${hook}(n)
[/#list]
        if self.trace_enabled:
            if n is not None:
                logger.info('Opened node scope for node of type: %s', type(n).__name__)
            logger.info('Scope nesting level is %s', self.current_node_scope.nesting_level)

    #
    # A definite node is constructed from a specified number of
    # children.  That number of nodes are popped from the stack and
    # made the children of the definite node.  Then the definite node
    # is pushed on to the stack.
    #
    def close_node_scope_numbered(self, n, num):
        lct = self.last_consumed_token
        n.end_line = lct.end_line
        n.end_column = lct.end_column
        if self.trace_enabled:
            logger.info('Closing node scope for node of type: %s, popping %s nodes off the stack.', type(n).__name__, num)
        self.current_node_scope.close()
        nodes = self.pop_nodes(num)
        for child in reversed(nodes):
            n.add_child(child)
        n.close()
        self.push_node(n)
[#list grammar.closeNodeScopeHooks as hook]
        ${hook}(n)
[/#list]

    #
    # A conditional node is constructed if the condition is true.  All
    # the nodes that have been pushed since the node was opened are
    # made children of the conditional node, which is then pushed
    # on to the stack.  If the condition is false the node is not
    # constructed and they are left on the stack.
    #
    def close_node_scope(self, n, condition_or_num):
        # Sometimes, the condition is just a number, so we pass thar
        # to the relevant method. Perhaps the method should be renamed;
        # in Java the methods are named the same and the correct one
        # is selected via method overloading
        if not isinstance(condition_or_num, bool):
            assert isinstance(condition_or_num, int)
            self.close_node_scope_numbered(n, condition_or_num)
            return
        if n and condition_or_num:
            n.end_line = self.last_consumed_token.end_line
            n.end_column = self.last_consumed_token.end_column
            if self.trace_enabled:
                pass  # logger.debug('Closing node scope for node of type: %s, popping %s nodes off the stack', type(n).__name__, self.node_arity)
            a = self.node_arity
            self.current_node_scope.close()
            nodes = self.pop_nodes(a)
            for child in reversed(nodes):
                if self.unparsed_tokens_are_nodes and isinstance(child, Token):
                    tok = child
                    while tok.previous_token and tok.previous_token.is_unparsed:
                        tok = tok.previous_token
                    while tok.is_unparsed:
                        n.add_child(tok)
                        tok = tok.get_next_token()
                n.add_child(child)
            n.close()
            if self.trace_enabled:
                logger.info('Closing node scope for node of type: %s, leaving %s nodes on the stack.', type(n).__name__, self.node_arity)
                logger.info('Nesting level is : %s', self.current_node_scope.nesting_level)
            self.push_node(n)
            if self.trace_enabled:
                logger.info('Closed node scope for node of type: %s, there are now %s nodes on the stack.', type(n).__name__, self.node_arity)
                logger.info('Nesting level is : %s', self.current_node_scope.nesting_level)
[#list grammar.closeNodeScopeHooks as hook]
            self.${hook}(${nodeVarName})
[/#list]
        else:
            self.current_node_scope.close()
            if self.trace_enabled and n is not None:
                logger.info('Closed node scope for node of type: %s, leaving %s nodes on the stack.', type(n).__name__, self.node_arity)
                logger.info('Nesting level is : %s', self.current_node_scope.nesting_level)

