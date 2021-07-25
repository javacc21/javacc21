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
# Parser tokens package. Generated by ${generated_by}. Do not edit.

from enum import Enum, auto, unique

from .utils import get_file_line_map_by_name

__all__ = [
    'BaseNode',
    'TokenType',
    'Token',
[#var tokenSubClassInfo = grammar.utils.tokenSubClassInfo()]
[#list tokenSubClassInfo.sortedNames as name]
    '${name}',
[/#list]
    'InvalidToken',
    'LexicalState',
    'new_token'
]

@unique
class TokenType(Enum):
 [#list grammar.lexerData.regularExpressions as regexp]
    ${regexp.label} = auto()
 [/#list]
 [#list grammar.extraTokens as t]
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

[#if include_unwanted!false]
# Utility method to merge two tokens into a single token of a given type.
def merge(t1, t2, tt):
    result = new_token(tt, t1.image + t2.image, t1.input_source)
    result.copy_location_info(t1)
    result.end_line = t2.end_line
    result.end_column = t2.end_column
    result.next = t2.next
    result.set_next_token(t2.next_token)
    return result

# Utility method to split a token in 2. For now, it assumes that the token
# is all on a single line. (Will maybe fix that later).
def split(tok, split_at, tt1, tt2):
    img1 = tok.image[:split_at]
    img2 = tok.image[split_at:]
    t1 = new_token(tt1, img1, tok.input_source)
    t2 = new_token(tt2, img2, tok.input_source)
    t1.begin_line = tok.begin_line
    t1.begin_column = tok.begin_column
    t1.end_line = tok.begin_line
    t1.end_column = tok.begin_column + split_at - 1
    t1.set_previous_token(tok.get_previous_token())
    t2.begin_line = tok.begin_line
    t2.begin_column = t1.end_column + 1
    t2.end_line = tok.end_line
    t2.end_column = tok.end_column
    t1.next = t2
    t1.set_next_token(t2)
    t2.set_previous_token(t1)
    t2.next = tok.next
    t2.set_next_token(tok.next_token)
    return t1

[/#if]

class BaseNode:

    __slots__ = (
[#if grammar.nodeUsesParser]
        'parser',
[/#if]
        'input_source',
        'parent',
        'children',
        'begin_line',
        'begin_column',
        'end_line',
        'end_column'
    )

    def __init__(self, [#if grammar.nodeUsesParser]parser_or_input_source[#else]input_source=None[/#if]):
[#if grammar.nodeUsesParser]
        if isinstance(parser_or_input_source, str):
            self.parser = None
            self.input_source = parser_or_input_source
        else:
            self.parser = parser_or_input_source
            self.input_source = parser_or_input_source.input_source
[#else]
        self.input_source = input_source
[/#if]
        self.parent = None
        self.children = []
        self.begin_line = self.begin_column = 0
        self.end_line = self.end_column = 0
        # self.attributes = {}

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
        if start.input_source and not self.input_source:
            self.input_source = start.input_source
        self.begin_line = start.begin_line
        self.begin_column = start.begin_column
        if end is None:
            self.end_line = start.end_line
            self.end_column = start.end_column
        else:
            if not self.input_source and end.input_source:
                self.input_source = end.input_source
            self.end_line = end.end_line
            self.end_column = end.end_column

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

[/#if]

    def __repr__(self):
        return '<%s (%d, %d)-(%d, %d)>' % (type(self).__name__,
                                           self.begin_line,
                                           self.begin_column,
                                           self.end_line,
                                           self.end_column)

class Token[#if grammar.treeBuildingEnabled](BaseNode)[/#if]:

    __slots__ = (
        'type',
        'image',
        'input_source',
[#if !grammar.treeBuildingEnabled]
        'begin_line',
        'begin_column',
        'end_line',
        'end_column',
[/#if]
        'next',
        'previous_token',
        'next_token',
        'is_unparsed',
        'skipped',
        'dirty'
    )

    def __init__(self, type, image, input_source):
[#if grammar.treeBuildingEnabled]
        super().__init__(input_source)
[#else]
        self.begin_line = self.begin_column = 0
        self.end_line = self.end_column = 0
[/#if]
        self.type = type
        self.image = image
        # A reference to the next regular (non-special) token from the input
        # stream.  If this is the last token from the input stream, or if the
        # token manager has not read tokens beyond this one, this field is
        # set to None.  This is true only if this token is also a regular
        # token.  Otherwise, see below for a description of the contents of
        # this field.
        self.next = None
        self.previous_token = None
        self.next_token = None
        self.is_unparsed = False

    def get_next(self):
        return self.get_next_parsed_token()

    def set_next(self, next):  # This is typically only used internally
        self.set_next_parsed_token(next)

    # return the next regular (i.e. parsed) token
    def get_next_parsed_token(self):
        return self.next

    def set_next_parsed_token(self, next):
        self.next = next

    set_next_token = set_next_parsed_token

    def get_previous_token(self):
        return self.previous_token

    def set_previous_token(self, previous):
        self.previous_token = previous

[#if !grammar.treeBuildingEnabled]
    def get_file_line_map(self):
        return get_file_line_map_by_name(self.input_source)
[/#if]

    def get_source(self):
        if self.type == TokenType.EOF:
            return ''
        return self.get_file_line_map().get_text(self.begin_line, self.begin_column,
                                                 self.end_line, self.end_column)

    def get_normalized_text(self):
        if self.type == TokenType.EOF:
            return 'EOF'
        return self.image

    __str__ = lambda self: self.image

    def __repr__(self):
        tn = self.type.name if self.type else None
        return '<%s %s %r (%d, %d)-(%d, %d)>' % (type(self).__name__,
                                                 tn,
                                                 self.image,
                                                 self.begin_line,
                                                 self.begin_column,
                                                 self.end_line,
                                                 self.end_column)

[#if grammar.treeBuildingEnabled]
    # Copy the location info from another node or start/end nodes
    def copy_location_info(self, start, end=None):
        super().copy_location_info(start, end)
        if isinstance(start, Token):
            self.previous_token = start.previous_token
        if end is None:
            if isinstance(start, Token):
                self.next = start.next
                self.next_token = start.next_token
        else:
            if isinstance(end, Token):
                self.next = end.next
                self.next_token = end.next_token
[#else]
    # Copy the location info from another token or start/end tokens
    def copy_location_info(self, start, end=None):
        if not self.input_source and start.input_source:
            self.input_source = start.input_source
        self.begin_line = start.begin_line
        self.begin_column = start.begin_column
        self.previous_token = start.previous_token
        if end is None:
            self.end_line = start.end_line
            self.end_column = start.end_column
            self.next = start.next
            self.next_token = start.next_token
        else:
            if not self.input_source and end.input_source:
                self.input_source = end.input_source
            self.end_line = end.end_line
            self.end_column = end.end_column
            self.next = end.next
            self.next_token = end.next_token

[/#if]
    @property
    def location(self):
        return '%s:%s:%s' % (self.input_source, self.begin_line,
                             self.begin_column)

class InvalidToken(Token):
    def __init__(self, image, input_source):
        return super().__init__(TokenType.INVALID, image, input_source)

#
# Token subclasses
#
[#list tokenSubClassInfo.sortedNames as name]
class ${name}(${tokenSubClassInfo.tokenClassMap[name]}): pass

[/#list]


def new_token(type, image, input_source):
[#list grammar.orderedNamedTokens as re]
  [#if re.generatedClassName != "Token" && !re.private]
    if type == TokenType.${re.label}:
        return ${grammar.nodePrefix}${re.generatedClassName}(type, image, input_source)
  [/#if]
[/#list]
    if type == TokenType.INVALID:
        return InvalidToken(image, input_source)
    return Token(type, image, input_source)
