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

[#var MULTIPLE_LEXICAL_STATE_HANDLING = (grammar.lexerData.numLexicalStates >1)]

        private void PushOntoLookaheadStack(string methodName, string fileName, uint offset) {
            _lookaheadStack.Add(new NonTerminalCall(this, fileName, methodName, offset));
        }

        private void PopLookaheadStack() {
            var ntc = _lookaheadStack.Pop();
            _currentLookaheadProduction = ntc.ProductionName;
            ScanToEnd = ntc.ScanToEnd;
        }

        private Token ConsumeToken(TokenType expectedType[#if grammar.faultTolerant], bool tolerant, HashSet<TokenType> followSet[/#if]) {
            var oldToken = LastConsumedToken;
            var nextToken = NextToken(LastConsumedToken);
            if (nextToken.Type != expectedType) {
                nextToken = HandleUnexpectedTokenType(expectedType, nextToken[#if grammar.faultTolerant], tolerant, followSet[/#if]);
            }
            LastConsumedToken = nextToken;
            _nextTokenType = null;
[#if grammar.treeBuildingEnabled]
            if (BuildTree && TokensAreNodes) {
            }
  [#list grammar.openNodeScopeHooks as hook]
            ${hook}(LastConsumedToken);
  [/#list]
            PushNode(LastConsumedToken);
  [#list grammar.closeNodeScopeHooks as hook]
            ${hook}(LastConsumedToken);
  [/#list]
[/#if]
[#if grammar.faultTolerant]
            // Check whether the very next token is in the follow set of the last consumed token
            // and if it is not, we check one token ahead to see if skipping the next token remedies
            // the problem.
            if (followSet && IsTolerant) {
                nextToken = NextToken(LastConsumedToken);
                if nextToken.Type not in followSet:
                    nextNext = NextToken(nextToken);
                    if nextNext.Type in followSet:
                        nextToken.skipped = true;
                        if (DebugFaultTolerant) {
                            Log(LogLevel.INFO, "Skipping token {0} at: {1}", nextToken.Type, nextToken.Location);
                        }
                        LastConsumedToken.Next = nextNext;
            }
[/#if]
            return LastConsumedToken;
        }

        private Token HandleUnexpectedTokenType(TokenType expectedType, Token nextToken[#if grammar.faultTolerant], bool tolerant, HashSet<TokenType> followSet[/#if]) {
[#if !grammar.faultTolerant]
            throw new ParseException(this, null, nextToken, Utils.EnumSet(expectedType));
[#else]
            if (!TolerantParsing) {
                throw new ParseException(this, null, nextToken, Utils.EnumSet(expectedType));
            }
            var nextNext = NextToken(nextToken);
            if (nextNext.Type == expectedType) {
                [#-- REVISIT. Here we skip one token (as well as any InvalidToken) but maybe (probably!) this behavior
                should be configurable. But we need to experiment, because this is really a heuristic question, no?--]
                nextToken.skipped = true;
  [#if grammar.treeBuildingEnabled]
                PushNode(nextToken);
  [/#if]
                return nextNext;
            }
            [#-- Since skipping the next token did not work, we will insert a virtual token --]
            if (tolerant || (followSet == null) || followSet.Contains(nextToken.Type)) {
                virtualToken = Token.NewToken(expectedType, tokenSource, 0, 0);
                virtualToken.virtual = true;
                virtualToken.CopyLocationInfo(nextToken);
                if (debugFaultTolerant) {
                    // logger.info('Inserting virtual token of type: %s at: %s', expectedType, virtualToken.location)
                }
  [#if MULTIPLE_LEXICAL_STATE_HANDLING]
                if (tokenSource.DoLexicalStateSwitch(expectedType)) {
                    tokenSource.Reset(virtualToken);
                }
  [/#if]
                return virtualToken;
            }
            throw new ParseException(this, null, nextToken, Utils.EnumSet(expectedType));
[/#if]
        }