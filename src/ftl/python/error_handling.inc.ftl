[#ftl strict_vars=true]
[#--
  Copyright (C) 2008-2020 Jonathan Revusky, revusky@javacc.com
  Copyright (C) 2021 Vinay Sajip, vinay_sajip@yahoo.co.uk
  All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are met:

      * Redistributions of source code must retain the above copyright
        notices, this list of conditions and the following disclaimer.
      * Redistributions in binary form must reproduce the above copyright
        notice, this list of conditions and the following disclaimer in
        the documentation and/or other materials provided with the
        distribution.
      * None of the names Jonathan Revusky, Vinay Sajip, Sun
        Microsystems, Inc. nor the names of any contributors may be
        used to endorse or promote products derived from this software
        without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
  ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
  INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
  CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
  ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
  THE POSSIBILITY OF SUCH DAMAGE.
--]

[#var MULTIPLE_LEXICAL_STATE_HANDLING = (grammar.lexerData.numLexicalStates >1)]

    def stack_iterator_forward(self):

        class ForwardIterator:
            def __init__(self, iter1, iter2):
                self.iter1 = ListIterator(iter1)
                self.iter2 = ListIterator(iter2)

            @property
            def has_next(self):
                return self.iter1.has_next or self.iter2.has_next

            @property
            def next(self):
                return self.iter1.next if self.iter1.has_next else self.iter2.next

            @property
            def has_previous(self):
                return self.iter2.has_previous or self.iter1.has_previous

            @property
            def previous(self):
                return self.iter2.previous if self.iter2.has_previous else self.iter1.previous

        return ForwardIterator(self.parsing_stack, self.lookahead_stack)

    def stack_iterator_backward(self):

        class BackwardIterator:
            def __init__(self, iter1, iter2):
                self.iter1 = ListIterator(iter1, len(iter1))
                self.iter2 = ListIterator(iter2, len(iter2))

            @property
            def has_next(self):
                return self.iter1.has_previous or self.iter2.has_previous

            @property
            def next(self):
                return self.iter2.previous if self.iter2.has_previous else self.iter1.previous

            @property
            def has_previous(self):
                return self.iter2.has_next or self.iter1.has_next

            @property
            def previous(self):
                return self.iter2.next if self.iter2.has_next else self.iter1.next

        return BackwardIterator(self.parsing_stack, self.lookahead_stack)

    def push_onto_lookahead_stack(self, method_name, filename, line, column):
        self.lookahead_stack.append(NonTerminalCall(self, filename, method_name, line, column))

    def pop_lookahead_stack(self):
        ntc = self.lookahead_stack.pop()
        self.current_lookahead_production = ntc.production_name
        self.scan_to_end = ntc.scan_to_end

    def consume_token(self, expected_type[#if grammar.faultTolerant], tolerant, follow_set[/#if]):
        old_token = self.last_consumed_token
        next_token = self.next_token(self.last_consumed_token)
        if next_token.type != expected_type:
            next_token = self.handle_unexpected_token_type(expected_type, next_token[#if grammar.faultTolerant], tolerant, follow_set[/#if])
        self.last_consumed_token = next_token
        self._next_token_type = None
[#if grammar.treeBuildingEnabled]
        if self.build_tree and self.tokens_are_nodes:
  [#list grammar.openNodeScopeHooks as hook]
            ${hook}(self.last_consumed_token)
  [/#list]
            self.push_node(self.last_consumed_token)
  [#list grammar.closeNodeScopeHooks as hook]
            ${hook}(self.last_consumed_token)
  [/#list]
[/#if]
        if self.trace_enabled:
            logger.info('Consumed token of type %s from %s', self.last_consumed_token.type.name, self.last_consumed_token.location)
[#if grammar.faultTolerant]
        # Check whether the very next token is in the follow set of the last consumed token
        # and if it is not, we check one token ahead to see if skipping the next token remedies
        # the problem.
        if follow_set and self.is_tolerant:
            next_token = self.next_token(self.last_consumed_token)
            if next_token.type not in follow_set:
                next_next = self.next_token(next_token)
                if next_next.type in follow_set:
                    next_token.skipped = True
                    if self.debug_fault_tolerant:
                        logger.info('Skipping token %s at: %s', next_token.type, next_token.location)
                    self.last_consumed_token.next = next_next
[/#if]
        return self.last_consumed_token

    def handle_unexpected_token_type(self, expected_type, next_token[#if grammar.faultTolerant], tolerant, follow_set[/#if]):
      [#if !grammar.faultTolerant]
        raise ParseException(self, token=next_token, expected=set([expected_type]))
      [#else]
        if not self.tolerant_parsing:
            raise ParseException(self, token=next_token, expected=set([expected_type]))

        next_next = self.next_token(next_token)
        if next_next.type == expected_type:
            [#-- REVISIT. Here we skip one token (as well as any InvalidToken) but maybe (probably!) this behavior
            should be configurable. But we need to experiment, because this is really a heuristic question, no?--]
            next_token.skipped = True
            if self.debug_fault_tolerant:
                logger.info('Skipping token of type: %s at: %s', next_token.type, next_token.location)
[#if grammar.treeBuildingEnabled]
            self.push_node(next_token)
[/#if]
            self.last_consumed_token.next = next_next
            return next_next

        [#-- Since skipping the next token did not work, we will insert a virtual token --]
        if self.is_tolerant or self.follow_set is None or next_token.type in self.follow_set:
            virtual_token = new_token(expected_type, 'VIRTUAL %s' % expected_type,
                                      self.last_consumed_token.input_source)
            virtual_token.virtual = True
            virtual_token.copy_location_info(next_token)
            virtual_token.next = next_token
            self.last_consumed_token.next = virtual_token
            if self.debug_fault_tolerant:
                logger.info('Inserting virtual token of type: %s at: %s', expected_type, virtual_token.location)
[#if MULTIPLE_LEXICAL_STATE_HANDLING]
            if self.token_source.do_lexical_state_switch(expected_type):
                self.token_source.reset(virtual_token)
[/#if]
            return virtual_token
        raise ParseException(self, token=next_token, expected=set([expected_type]))
      [/#if]
