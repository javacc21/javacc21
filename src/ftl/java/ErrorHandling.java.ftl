[#ftl strict_vars=true]
[#--
/* Copyright (c) 2020 Jonathan Revusky, revusky@javacc.com
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

[#var MULTIPLE_LEXICAL_STATE_HANDLING = (grammar.lexerData.numLexicalStates >1)]
 
ArrayList<NonTerminalCall> parsingStack = new ArrayList<>();
private ArrayList<NonTerminalCall> lookaheadStack = new ArrayList<>();

[#if grammar.faultTolerant]
  private EnumSet<TokenType> currentFollowSet;
[/#if]

/**
 * Inner class that represents entering a grammar production
 */
class NonTerminalCall {
    final String sourceFile;
    final String productionName;
    final int line, column;

   [#if grammar.faultTolerant]
    final EnumSet<TokenType> followSet;
   [/#if]


    NonTerminalCall(String sourceFile, String productionName, int line, int column) {
        this.sourceFile = sourceFile;
        this.productionName = productionName;
        this.line = line;
        this.column = column;
      [#if grammar.faultTolerant]
        this.followSet = ${grammar.parserClassName}.this.outerFollowSet;
      [/#if]
    }

    final ${grammar.lexerClassName} getTokenSource() {
        return ${grammar.parserClassName}.this.token_source;
    }

    StackTraceElement createStackTraceElement() {
        return new StackTraceElement("${grammar.parserClassName}", productionName, sourceFile, line);
    }

    void dump(PrintStream ps) {
         ps.println(productionName + ":" + line + ":" + column);
    }
}

private final void pushOntoCallStack(String methodName, String fileName, int line, int column) {
   parsingStack.add(new NonTerminalCall(fileName, methodName, line, column));
}

private final void popCallStack() {
    NonTerminalCall ntc = parsingStack.remove(parsingStack.size() -1);
    this.currentlyParsedProduction = ntc.productionName;
   [#if grammar.faultTolerant]
    this.outerFollowSet = ntc.followSet;
   [/#if]
}

private final void restoreCallStack(int prevSize) {
    while (parsingStack.size() > prevSize) {
       popCallStack();
    }
}

private ListIterator<NonTerminalCall> stackIteratorForward() {
    final ListIterator<NonTerminalCall> parseStackIterator = parsingStack.listIterator();
    final ListIterator<NonTerminalCall> lookaheadStackIterator = lookaheadStack.listIterator();
    return new ListIterator<NonTerminalCall>() {
        public boolean hasNext() {
            return parseStackIterator.hasNext() || lookaheadStackIterator.hasNext();
        }
        public NonTerminalCall next() {
            return parseStackIterator.hasNext() ? parseStackIterator.next() : lookaheadStackIterator.next();
        }
        public NonTerminalCall previous() {
           return lookaheadStackIterator.hasPrevious() ? lookaheadStackIterator.previous() : parseStackIterator.previous(); 
        }
        public boolean hasPrevious() {
            return lookaheadStackIterator.hasPrevious() || parseStackIterator.hasPrevious();
        }
        public void add(NonTerminalCall ntc) {throw new UnsupportedOperationException();}
        public void set(NonTerminalCall ntc) {throw new UnsupportedOperationException();}
        public void remove() {throw new UnsupportedOperationException();}
        public int previousIndex() {throw new UnsupportedOperationException();}
        public int nextIndex() {throw new UnsupportedOperationException();}
    };
}

private ListIterator<NonTerminalCall> stackIteratorBackward() {
    final ListIterator<NonTerminalCall> parseStackIterator = parsingStack.listIterator(parsingStack.size());
    final ListIterator<NonTerminalCall> lookaheadStackIterator = lookaheadStack.listIterator(lookaheadStack.size());
    return new ListIterator<NonTerminalCall>() {
        public boolean hasNext() {
            return lookaheadStackIterator.hasPrevious() || parseStackIterator.hasPrevious();
        }
        public NonTerminalCall next() {
            return lookaheadStackIterator.hasPrevious() ? lookaheadStackIterator.previous() : parseStackIterator.previous();
        }
        public NonTerminalCall previous() {
           return parseStackIterator.hasNext() ? parseStackIterator.next() : lookaheadStackIterator.next();
        }
        public boolean hasPrevious() {
            return parseStackIterator.hasNext() || lookaheadStackIterator.hasNext();
        }
        public void add(NonTerminalCall ntc) {throw new UnsupportedOperationException();}
        public void set(NonTerminalCall ntc) {throw new UnsupportedOperationException();}
        public void remove() {throw new UnsupportedOperationException();}
        public int previousIndex() {throw new UnsupportedOperationException();}
        public int nextIndex() {throw new UnsupportedOperationException();}
    };
}


private final void pushOntoLookaheadStack(String methodName, String fileName, int line, int column) {
    lookaheadStack.add(new NonTerminalCall(fileName, methodName, line, column));
}

private final void popLookaheadStack() {
    NonTerminalCall ntc = lookaheadStack.remove(lookaheadStack.size() -1);
    this.currentLookaheadProduction = ntc.productionName;
}

void dumpLookaheadStack(PrintStream ps) {
    ListIterator<NonTerminalCall> it = lookaheadStack.listIterator(lookaheadStack.size());
    while (it.hasPrevious()) {
        it.previous().dump(ps);
    }
}

void dumpCallStack(PrintStream ps) {
    ListIterator<NonTerminalCall> it = parsingStack.listIterator(parsingStack.size());
    while (it.hasPrevious()) {
        it.previous().dump(ps);
    }
}

void dumpLookaheadCallStack(PrintStream ps) {
    ps.println("Current Parser Production is: " + currentlyParsedProduction);
    ps.println("Current Lookahead Production is: " + currentLookaheadProduction);
    ps.println("---Lookahead Stack---");
    dumpLookaheadStack(ps);
    ps.println("---Call Stack---");
    dumpCallStack(ps);
}

[#if grammar.faultTolerant] 
    private boolean tolerantParsing = true;
    // Are we pending a recovery routine to
    // get back on the rails?
    private boolean pendingRecovery;
//    private boolean debugFaultTolerant = false;

    private java.util.List<ParsingProblem> parsingProblems = new java.util.ArrayList<>();

    public java.util.List<ParsingProblem> getParsingProblems() {
        return parsingProblems;
    }

    public boolean hasProblems() {
        return !parsingProblems.isEmpty();
    }

    void addParsingProblem(ParsingProblem problem) {
        parsingProblems.add(problem);
    }
[/#if]    

    public boolean isParserTolerant() {
       [#if grammar.faultTolerant] 
        return tolerantParsing;
       [#else]
        return false;
       [/#if]
    }
    
    public void setParserTolerant(boolean tolerantParsing) {
        [#if grammar.faultTolerant]
          this.tolerantParsing = tolerantParsing;
        [#else]
          if (tolerantParsing) {
            throw new UnsupportedOperationException("This parser was not built with that feature!");
          }
        [/#if]
    }

      private Token consumeToken(TokenType expectedType 
        [#if grammar.faultTolerant], boolean tolerant, EnumSet<TokenType> followSet [/#if]
      ) 
      [#if grammar.useCheckedException] throws ParseException [/#if]
      {
        Token oldToken = lastConsumedToken;
        Token nextToken = nextToken(lastConsumedToken);
        if (nextToken.getType() != expectedType) {
            nextToken = handleUnexpectedTokenType(expectedType, nextToken
            [#if grammar.faultTolerant], tolerant, followSet[/#if]
            ) ;
        }
        this.lastConsumedToken = nextToken;
        this.nextTokenType = null;
[#if grammar.treeBuildingEnabled]
      if (buildTree && tokensAreNodes) {
  [#list grammar.openNodeScopeHooks as hook]
     ${hook}(lastConsumedToken);
  [/#list]
          pushNode(lastConsumedToken);
  [#list grammar.closeNodeScopeHooks as hook]
     ${hook}(lastConsumedToken);
  [/#list]
      }
[/#if]
[#if grammar.faultTolerant]
// Check whether the very next token is in the follow set of the last consumed token
// and if it is not, we check one token ahead to see if skipping the next token remedies
// the problem.
      if (followSet != null && isParserTolerant()) {
         nextToken = nextToken(lastConsumedToken);
         if (!followSet.contains(nextToken.getType())) {
            Token nextNext = nextToken(nextToken);
            if (followSet.contains(nextNext.getType())) {
               nextToken.setSkipped(true);
//               if (debugFaultTolerant) LOGGER.info("Skipping token " + nextToken.getType() + " at: " + nextToken.getLocation());
               //lastConsumedToken.setNext(nextNext);
            }
         }
      }
[/#if]      
      return lastConsumedToken;
  }
 
  private Token handleUnexpectedTokenType(TokenType expectedType, Token nextToken
      [#if grammar.faultTolerant], boolean tolerant, EnumSet<TokenType> followSet[/#if]
      ) 
      [#if grammar.useCheckedException] throws ParseException [/#if]
      {
      [#if !grammar.faultTolerant]
       throw new ParseException(this, nextToken, EnumSet.of(expectedType), parsingStack);
      [#else]
       if (!this.tolerantParsing) {
          throw new ParseException(this, nextToken, EnumSet.of(expectedType), parsingStack);
       }
       Token nextNext = nextToken(nextToken);
       if (nextNext.getType() == expectedType) {
             [#-- REVISIT. Here we skip one token (as well as any InvalidToken) but maybe (probably!) this behavior
             should be configurable. But we need to experiment, because this is really a heuristic question, no?--]
             nextToken.setSkipped(true);
//             if (debugFaultTolerant) LOGGER.info("Skipping token of type: " + nextToken.getType() + " at: " + nextToken.getLocation());
[#if grammar.treeBuildingEnabled]             
             pushNode(nextToken);
[/#if]             
             //lastConsumedToken.setNext(nextNext);
             return nextNext;
       }
         [#-- Since skipping the next token did not work, we will insert a virtual token --]
       if (tolerant || followSet==null || followSet.contains(nextToken.getType())) {
           Token virtualToken = Token.newToken(expectedType, token_source, 0,0);
           virtualToken.setImage("VIRTUAL " + expectedType);
           virtualToken.setVirtual(true);
           virtualToken.copyLocationInfo(nextToken);
           //virtualToken.setNext(nextToken);
           //lastConsumedToken.setNext(virtualToken);
//           if (debugFaultTolerant) LOGGER.info("Inserting virtual token of type: " + expectedType + " at " + virtualToken.getLocation());
[#if MULTIPLE_LEXICAL_STATE_HANDLING]
           if (token_source.doLexicalStateSwitch(expectedType)) {
              token_source.reset(virtualToken);
           }
[/#if]
           return virtualToken;
       }
       throw new ParseException(this, nextToken, EnumSet.of(expectedType), parsingStack);
      [/#if]
  }
  
  /**
   * pushes the last token back.
   */
  private void pushLastTokenBack() {
     [#if grammar.treeBuildingEnabled]
        if (peekNode() == lastConsumedToken) {
            popNode();
        }
     [/#if]
     lastConsumedToken = lastConsumedToken.previousCachedToken();
  }
 
  private class ParseState {
       Token lastConsumed;
       ArrayList<NonTerminalCall> parsingStack;
   [#if MULTIPLE_LEXICAL_STATE_HANDLING]
       LexicalState lexicalState;
   [/#if]
   [#if grammar.treeBuildingEnabled]
       NodeScope nodeScope;
 [/#if]       
       ParseState() {
           this.lastConsumed = ${grammar.parserClassName}.this.lastConsumedToken;
          @SuppressWarnings("unchecked")
           ArrayList<NonTerminalCall> parsingStack = (ArrayList<NonTerminalCall>) ${grammar.parserClassName}.this.parsingStack.clone();
           this.parsingStack = parsingStack;
[#if grammar.lexerData.numLexicalStates > 1]
           this.lexicalState = token_source.lexicalState;
[/#if]
[#if grammar.treeBuildingEnabled]            
           this.nodeScope = currentNodeScope.clone();
[/#if]           
       } 
  }

  private ArrayList<ParseState> parseStateStack = new ArrayList<>();
 
  private void stashParseState() {
      parseStateStack.add(new ParseState());
  }
  
  private ParseState popParseState() {
      return parseStateStack.remove(parseStateStack.size() -1);
  }
  
  private void restoreStashedParseState() {
     ParseState state = popParseState();
[#if grammar.treeBuildingEnabled]
     currentNodeScope = state.nodeScope;
[/#if]
     ${grammar.parserClassName}.this.parsingStack = state.parsingStack;
    if (state.lastConsumed != null) {
        //REVISIT
         lastConsumedToken = state.lastConsumed;
    }
[#if MULTIPLE_LEXICAL_STATE_HANDLING]
     token_source.reset(lastConsumedToken, state.lexicalState);
[#else]     
     token_source.reset(lastConsumedToken);
[/#if]          
  } 
  
