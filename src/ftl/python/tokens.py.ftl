[#ftl strict_vars=true]
[#--
  Copyright (C) 2008-2020 Jonathan Revusky, revusky@javacc.com
  Copyright (C) 2021-2022 Vinay Sajip, vinay_sajip@yahoo.co.uk
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
# Parser tokens package. Generated by ${generated_by}. Do not edit.

from enum import Enum, auto, unique

from .utils import _GenWrapper, _List
[#var injector = grammar.injector]

__all__ = [
    '${grammar.baseNodeClassName}',
    'TokenType',
    'Token',
[#var tokenSubClassInfo = grammar.utils.tokenSubClassInfo()]
[#list tokenSubClassInfo.sortedNames as name]
    '${name}',
[/#list]
[#if !grammar.minimalToken]
    'new_token',
[/#if]
    'InvalidToken',
    'LexicalState'
]

@unique
class TokenType(Enum):
 [#list grammar.lexerData.regularExpressions as regexp]
    ${regexp.label} = auto()
 [/#list]
 [#list grammar.extraTokenNames as t]
    ${t} = auto()
 [/#list]
    INVALID = auto()

@unique
class LexicalState(Enum):
  [#list grammar.lexerData.lexicalStates as lexicalState]
    ${lexicalState.name} = auto()
  [/#list]

[#--
token_strings = [
    '<EOF>',
[#list grammar.allTokenProductions as tokenProduction]
  [#list tokenProduction.regexpSpecs as regexpSpec]
  [@output_regexp regexpSpec.regexp, tokenProduction_has_next || regexpSpec_has_next /]
  [/#list]
[/#list]
]

[#macro output_regexp regexp, comma]
  [#if regexp.class.name?ends_with("StringLiteral")]
    '"${grammar.utils.addEscapes(regexp.image)}"'[#if comma],[/#if]
  [#elseif regexp.label != ""]
    '<${regexp.label}>'[#if comma],[/#if]
  [#else]
    '<token of kind ${regexp.ordinal}>'[#if comma],[/#if]
  [/#if]
[/#macro]

--]

class ${grammar.baseNodeClassName}:

    __slots__ = (
[#if grammar.nodeUsesParser]
        'parser',
[/#if]
        '_token_source',
        'parent',
        'children',
        'is_unparsed',
        'begin_offset',
        'end_offset',
[#if grammar.faultTolerant]
        'dirty',
[/#if]
    )

    def __init__(self, token_source, begin_offset=0, end_offset=0):
        self._token_source = token_source
        self.parent = None
        self.children = []
        self.begin_offset = begin_offset
        self.end_offset = end_offset
        # self.attributes = {}

    @property
    def begin_line(self):
        ts = self.token_source
        return 0 if not ts else ts.get_line_from_offset(self.begin_offset)

    @property
    def begin_column(self):
        ts = self.token_source
        return 0 if not ts else ts.get_codepoint_column_from_offset(self.begin_offset)

    @property
    def end_line(self):
        ts = self.token_source
        return 0 if not ts else ts.get_line_from_offset(self.end_offset - 1)

    @property
    def end_column(self):
        ts = self.token_source
        return 0 if not ts else ts.get_codepoint_column_from_offset(self.end_offset - 1)

    @property
    def token_source(self):
        result = self._token_source
[#if !grammar.minimalToken]
        if not result:
            if self.prepended_token:
                result = self.prepended_token.token_source
            if not result and self.appended_token:
                result = self.appended_token.token_source
        self._token_source = result
[/#if]
        return result

    @token_source.setter
    def token_source(self, value):
        self._token_source = value

    @property
    def input_source(self):
        ts = self.token_source
        return "input" if not ts else ts.input_source

    def add_child(self, node, index=-1):
        if index < 0:
            self.children.append(node)
        else:
            self.children.insert(index, node)
        node.parent = self

    def remove_child(self, index):
        assert index >= 0
        self.children.pop(index)

[#if include_unwanted!false]
    def set_child(self, node, index):
        self.children[index] = node
        node.parent = self

    def clear_children(self):
        self.children.clear()

[/#if]
    @property
    def child_count(self):
        return len(self.children)

    def get_child(self, index):
        assert index >= 0
        return self.children[index]

    @property
    def first_child(self):
        if self.children:
            return self.children[0]

    @property
    def last_child(self):
        if self.children:
            return self.children[-1]

    def chlldren_of_type(self, cls):
        return [child for child in self.children if isinstance(child, cls)]

    # Copy the location info from another node or start/end nodes
    def copy_location_info(self, start, end=None):
        self.token_source = start.token_source
        self.begin_offset = start.begin_offset
        if end is None:
            self.end_offset = start.end_offset
[#if !grammar.minimalToken]
        self.prepended_token = start.prepended_token
        if end is None:
            self.appended_token = start.appended_token
[/#if]
        if end is not None:
            if self.token_source is None:
                self.token_source = end.token_source
            self.end_offset = end.end_offset
[#if !grammar.minimalToken]
            self.appended_token = end.appended_token
[/#if]

    def open(self): pass

    def close(self): pass

    @property
    def token_type(self):
        if isinstance(self, Token):
            return self.type
        # return None

[#if grammar.tokensAreNodes]
    #
    # Return the very first token that is part of this node.
    # It may be an unparsed (i.e. special) token.
    #
    @property
    def first_token(self):
        first = self.first_child
        if first is None:
            return None
        if isinstance(first, Token):
            tok = first
            while tok.previous_token is not None and tok.previous_token.is_unparsed:
                tok = tok.previous_token
            return tok
        return first.first_token

    @property
    def last_token(self):
        last = self.last_child
        if last is None:
            return None
        if isinstance(last, Token):
            return last
        return last.last_token


    def first_child_of_type(self, type):
        for child in self.children:
            if isinstance(child, Token) and child.type == type:
                return child

    def first_descendant_of_type(self, type):
        for child in self.children:
            if isinstance(child, Token):
                if child.type == type:
                    return child
            else:
                child = child.first_descendant_of_type(type)
                if child:
                    return child

[/#if]

    def __repr__(self):
        return '<%s (%d, %d)-(%d, %d)>' % (type(self).__name__,
                                           self.begin_line,
                                           self.begin_column,
                                           self.end_line,
                                           self.end_column)

class Token[#if grammar.treeBuildingEnabled](${grammar.baseNodeClassName})[/#if]:

    __slots__ = (
        'type',
[#if !grammar.minimalToken || grammar.faultTolerant]
        '_image',
[/#if]
[#if !grammar.minimalToken]
        'prepended_token',
        'appended_token',
        'is_inserted',
[/#if]
        'next',
        'previous_token',
        'next_token',
[#var injectedFields = grammar.utils.injectedTokenFieldNames(injector)]
[#if injectedFields?size > 0]
        # injected fields
[#list injectedFields as fieldName]
        '${fieldName}',
[/#list]
[/#if]
[#if grammar.faultTolerant]
        '_is_skipped',
        '_is_virtual',
        'dirty'
[/#if]
    )

    def __init__(self, type, token_source, begin_offset, end_offset):
[#if grammar.treeBuildingEnabled]
        super().__init__(token_source, begin_offset, end_offset)
[#else]
        self.begin_offset = begin_offset
        self.end_offset = end_offset
[/#if]
${grammar.utils.translateTokenInjections(injector, true)}
        self.type = type
        self.previous_token = None
        self.next_token = None
        self.is_unparsed = False
[#if grammar.faultTolerant]
        self.dirty = False
        self._is_virtual = False
        self._is_skipped = False
[/#if]
[#if !grammar.minimalToken || grammar.faultTolerant]
        self._image = None
[/#if]
[#if !grammar.minimalToken]
        self.prepended_token = None
        self.appended_token = None
        self.is_inserted = False

    def pre_insert(self, prepended_token):
        if prepended_token is self.prepended_token:
            return
        prepended_token.appended_token = self
        existing_previous_token = self.previous_cached_token
        if existing_previous_token:
            existing_previous_token.appended_token = prepended_token
            prepended_token.prepended_token = existing_previous_token
        prepended_token.is_inserted = True
        prepended_token.begin_offset = prepended_token.end_offset = self.begin_offset
        self.prepended_token = prepended_token

    def unset_appended_token(self):
        self.appended_token = None

[/#if]

    @property
    def image(self):
[#if grammar.minimalToken]
        return self.source
[#else]
        return self._image if self._image else self.source
[/#if]

    @property
    def source(self):
        if self.type == TokenType.EOF:
            return ''
        ts = self.token_source
        return None if not ts else ts.get_text(self.begin_offset, self.end_offset)

[#if !grammar.minimalToken || grammar.faultTolerant]
    @image.setter
    def image(self, value):
        self._image = value

[/#if]

    @property
    def normalized_text(self):
        if self.type == TokenType.EOF:
            return ''
        return self.image

    def __str__(self):
        return self.normalized_text

    def _preceding_tokens(self):
        current = self
        t = current.previous_cached_token
        while t:
            current = t
            t = current.previous_cached_token
            yield current

    def preceding_tokens(self):
        return _GenWrapper(self._preceding_tokens())

    def _following_tokens(self):
        current = self
        t = current.next_cached_token
        while t:
            current = t
            t = current.next_cached_token
            yield current

    def following_tokens(self):
        return _GenWrapper(self._following_tokens())

    @property
    def is_virtual(self):
[#if grammar.faultTolerant]
        return self._is_virtual || self.type == TokenType.EOF
[#else]
        return self.type == TokenType.EOF
[/#if]

[#if grammar.faultTolerant]
    @is_virtual.setter
    def is_virtual(self, value):
        self._is_virtual = value

[/#if]
    @property
    def is_skipped(self):
[#if grammar.faultTolerant]
        return self._is_skipped
[#else]
        return False
[/#if]

    def get_next(self):
        return self.get_next_parsed_token()

    def set_next(self, next):  # This is typically only used internally
        self.set_next_parsed_token(next)

    # return the next regular (i.e. parsed) token
    def get_next_parsed_token(self):
        result = self.next_cached_token
        while result and result.is_unparsed:
            result = result.next_cached_token
        return result

    @property
    def previous_cached_token(self):
[#if !grammar.minimalToken]
        if self.prepended_token:
            return self.prepended_token
[/#if]
        ts = self.token_source
        if not ts:
            return None
        return ts.previous_cached_token(self.begin_offset)

    @property
    def next_cached_token(self):
[#if !grammar.minimalToken]
        if self.appended_token:
            return self.appended_token
[/#if]
        if not self.token_source:
            return None
        return self.token_source.next_cached_token(self.end_offset)

    def __repr__(self):
        tn = self.type.name if self.type else None
        return '<%s %s %r (%d, %d)-(%d, %d)>' % (type(self).__name__,
                                                 tn,
                                                 self.image,
                                                 self.begin_line,
                                                 self.begin_column,
                                                 self.end_line,
                                                 self.end_column)

[#if grammar.treeBuildingEnabled && !grammar.minimalToken]
    # Copy the location info from another node or start/end nodes
    def copy_location_info(self, start, end=None):
        super().copy_location_info(start, end)
        if isinstance(start, Token):
            self.previous_token = start.previous_token
        if end is None:
            if isinstance(start, Token):
                self.appended_token = start.appended_token
                self.prepended_token = start.prepended_token
        else:
            if isinstance(start, Token):
                self.prepended_token = start.prepended_token
            if isinstance(end, Token):
                self.appended_token = end.appended_token
        self.token_source = start.token_source
[#else]
    # Copy the location info from another token or start/end tokens
    def copy_location_info(self, start, end=None):
        self.token_source = start.token_source
        self.begin_offset = start.begin_offset
        if end is None:
            self.end_offset = start.end_offset
[#if !grammar.minimalToken]
            self.prepended_token = start.prepended_token
            self.appended_token = start.appended_token
[/#if]
        else:
            if self.token_source is None:
                self.token_source = end.token_source
            self.end_offset = end.end_offset
[#if !grammar.minimalToken]
            self.prepended_token = start.prepended_token
            self.appended_token = end.appended_token
[/#if]
[/#if]

    @property
    def input_source(self):
        ts = self.token_source
        return 'input' if ts is None else ts.input_source

    @property
    def location(self):
        return '%s:%s:%s' % (self.input_source, self.begin_line,
                             self.begin_column)

${grammar.utils.translateTokenInjections(injector, false)}

class InvalidToken(Token):
    def __init__(self, token_source, begin_offset, end_offset):
        super().__init__(TokenType.INVALID, token_source, begin_offset, end_offset)
[#if grammar.faultTolerant]
        self.is_unparsed = True
        self.dirty = True
[/#if]


#
# Token subclasses
#
[#list tokenSubClassInfo.sortedNames as name]
class ${name}(${tokenSubClassInfo.tokenClassMap[name]}): pass

[/#list]
[#if grammar.extraTokens?size > 0]
  [#list grammar.extraTokenNames as name]
    [#var cn = grammar.extraTokens[name]]
class ${cn}(Token):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
${grammar.utils.translateTokenSubclassInjections(cn, injector, true)}
${grammar.utils.translateTokenSubclassInjections(cn, injector, false)}
  [/#list]
[/#if]

def new_token(type, *args):
[#-- list grammar.orderedNamedTokens as re]
  [#if re.generatedClassName != "Token" && !re.private]
    if type == TokenType.${re.label}:
        return ${grammar.nodePrefix}${re.generatedClassName}(type, image, input_source)
  [/#if]
[/#list --]
    if isinstance(args[0], str):  # called with an image
        # assert isinstance(args[1], ${grammar.lexerClassName})
        result = Token(type, args[1], 0, 0)
        result.image = args[0]
    else:
        # assert isinstance(args[0], ${grammar.lexerClassName})
        result = Token(type, args[0], args[1], args[2])
    return result
