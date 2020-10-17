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
 [#if grammar.options.debugParser]
  private boolean trace_enabled = true;
 [#else]
  private boolean trace_enabled = false;
 [/#if]
 
  public void setTracingEnabled(boolean tracingEnabled) {trace_enabled = tracingEnabled;}
  
 /**
 * @deprecated Use #setTracingEnabled
 */
   @Deprecated
  public void enable_tracing() {
    setTracingEnabled(true);
  }

/**
 * @deprecated Use #setTracingEnabled
 */
@Deprecated
 public void disable_tracing() {
    setTracingEnabled(false);
  }
 
ArrayList<NonTerminalCall> parsingStack = new ArrayList<>();
private ArrayList<NonTerminalCall> lookaheadStack = new ArrayList<>();


private EnumSet<TokenType> currentFollowSet;

/**
 * Inner class that represents entering a grammar production
 */
class NonTerminalCall {
    final String sourceFile;
    final String productionName;
    final int line, column;

    // We actually only use this when we're working with the LookaheadStack
    final boolean stopAtScanLimit;

    NonTerminalCall(String sourceFile, String productionName, int line, int column) {
        this.sourceFile = sourceFile;
        this.productionName = productionName;
        this.line = line;
        this.column = column;
        this.stopAtScanLimit = ${grammar.parserClassName}.this.stopAtScanLimit;
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
    parsingStack.remove(parsingStack.size() -1);
}

private final void restoreCallStack(int prevSize) {
    while (parsingStack.size() > prevSize) {
       popCallStack();
    }
}

private Iterator<NonTerminalCall> stackIteratorForward() {
    final Iterator<NonTerminalCall> parseStackIterator = parsingStack.iterator();
    final Iterator<NonTerminalCall> lookaheadStackIterator = lookaheadStack.iterator();
    return new Iterator<NonTerminalCall>() {
        public boolean hasNext() {
            return parseStackIterator.hasNext() || lookaheadStackIterator.hasNext();
        }
        public NonTerminalCall next() {
            return parseStackIterator.hasNext() ? parseStackIterator.next() : lookaheadStackIterator.next();
        }
    };
}

private Iterator<NonTerminalCall> stackIteratorBackward() {
    final ListIterator<NonTerminalCall> parseStackIterator = parsingStack.listIterator(parsingStack.size());
    final ListIterator<NonTerminalCall> lookaheadStackIterator = lookaheadStack.listIterator(lookaheadStack.size());
    return new Iterator<NonTerminalCall>() {
        public boolean hasNext() {
            return parseStackIterator.hasPrevious() || lookaheadStackIterator.hasPrevious();
        }
        public NonTerminalCall next() {
            return lookaheadStackIterator.hasPrevious() ? lookaheadStackIterator.previous() : parseStackIterator.previous();
        }
    };
}


private final void pushOntoLookaheadStack(String methodName, String fileName, int line, int column) {
    lookaheadStack.add(new NonTerminalCall(fileName, methodName, line, column));
}

private final void popLookaheadStack() {
    NonTerminalCall ntc = lookaheadStack.remove(lookaheadStack.size() -1);
    this.stopAtScanLimit = ntc.stopAtScanLimit;
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

[#if grammar.options.faultTolerant]
    private boolean tolerantParsing= true;
    private boolean currentNTForced = false;
    private List<ParsingProblem> parsingProblems;
    // This is the last "legit" token consumed by the parsing machinery, not
    // a virtual or invalid Token inserted to continue parsing.
    
    public void addParsingProblem(ParsingProblem problem) {
        if (parsingProblems == null) {
            parsingProblems = new ArrayList<>();
        }
        parsingProblems.add(problem);
    }
    
    public List<ParsingProblem> getParsingProblems() {
        return parsingProblems;
    }
    
    public boolean hasParsingProblems() {
        return parsingProblems != null && !parsingProblems.isEmpty();
    }    

    private void resetNextToken() {
       currentToken.setNext(null);
//       token_source.reset(currentToken);
       token_source.reset(lastParsedToken);
  }
  
[#else]
    private final boolean tolerantParsing = false;
[/#if]
    public boolean isParserTolerant() {return tolerantParsing;}
    
    public void setParserTolerant(boolean tolerantParsing) {
      [#if grammar.options.faultTolerant]
        this.tolerantParsing = tolerantParsing;
      [#else]
        if (tolerantParsing) {
            throw new UnsupportedOperationException("This parser was not built with that feature!");
        } 
      [/#if]
    }

[#if grammar.options.faultTolerant]
    private Token insertVirtualToken(TokenType tokenType) {
        Token virtualToken = Token.newToken(tokenType, "VIRTUAL " + tokenType, this);
        virtualToken.setLexicalState(token_source.lexicalState);
        virtualToken.setUnparsed(true);
        virtualToken.setVirtual(true);
        int line = lastParsedToken.getEndLine();
        int column = lastParsedToken.getEndColumn();
        virtualToken.setBeginLine(line);
        virtualToken.setEndLine(line);
        virtualToken.setBeginColumn(column);
        virtualToken.setEndColumn(column);
     [#if grammar.lexerData.numLexicalStates >1]
         token_source.doLexicalStateSwitch(tokenType);
     [/#if]
        return virtualToken;
    }
  

     private Token consumeToken(TokenType expectedType) throws ParseException {
        return consumeToken(expectedType, false);
     }
 
     private Token consumeToken(TokenType expectedType, boolean forced) throws ParseException {
 [#else]
      private Token consumeToken(TokenType expectedType) throws ParseException {
        boolean forced = false;
 [/#if]
        InvalidToken invalidToken = null;
        Token oldToken = currentToken;
        currentToken = nextToken(currentToken);
[#if grammar.options.faultTolerant]        
        if (tolerantParsing && currentToken instanceof InvalidToken) {
             addParsingProblem(new ParsingProblem("Lexically invalid input", currentToken));
             invalidToken = (InvalidToken) currentToken;
             currentToken = token_source.getNextToken(); [#-- REVISIT --]
        }
[/#if]
        if (currentToken.getType() != expectedType) {
            handleUnexpectedTokenType(expectedType, forced, oldToken) ;
        }
        else {
            this.lastParsedToken = currentToken;
        }
[#if grammar.options.treeBuildingEnabled]
      if (buildTree && tokensAreNodes) {
  [#if grammar.options.userDefinedLexer]
          currentToken.setInputSource(inputSource);
  [/#if]
  [#if grammar.usesjjtreeOpenNodeScope]
          jjtreeOpenNodeScope(currentToken);
  [/#if]
  [#if grammar.usesOpenNodeScopeHook]
          openNodeScopeHook(currentToken);
  [/#if]
          if (invalidToken != null) {
             pushNode(invalidToken);
          }          
          pushNode(currentToken);
  [#if grammar.usesjjtreeCloseNodeScope]
          jjtreeCloseNodeScope(currentToken);
  [/#if]
  [#if grammar.usesCloseNodeScopeHook]
          closeNodeScopeHook(currentToken);
  [/#if]
      }
[/#if]
      if (trace_enabled) LOGGER.info("Consumed token of type " + currentToken.getType() + " from " + currentToken.getLocation());
      return currentToken;
  }
 
  private void handleUnexpectedTokenType(TokenType expectedType,  boolean forced, Token oldToken) throws ParseException {
[#if grammar.options.faultTolerant]    
       if (forced && tolerantParsing) {
           Token nextToken = currentToken;
           currentToken = oldToken;
           Token virtualToken = insertVirtualToken(expectedType);
           virtualToken.setNext(nextToken);
           currentToken = virtualToken;
           String message = "Expecting token type "+ expectedType + " but encountered " + nextToken.getType();
           message += "\nInserting virtual token to continue parsing";
           addParsingProblem(new ParsingProblem(message, virtualToken));
       } else 
[/#if]      
       throw new ParseException(currentToken, EnumSet.of(expectedType), parsingStack);
  }
  
 [#if !grammar.options.hugeFileSupport && !grammar.options.userDefinedLexer]
 
  private class ParseState {
       Token lastParsed;
  [#if grammar.options.treeBuildingEnabled]
       NodeScope nodeScope;
 [/#if]       
       ParseState() {
           this.lastParsed  = ${grammar.parserClassName}.this.lastParsedToken;
[#if grammar.options.treeBuildingEnabled]            
           this.nodeScope = (NodeScope) currentNodeScope.clone();
[/#if]           
       } 
  }

 private ArrayList<ParseState> parseStateStack = new ArrayList<>();
 
  void stashParseState() {
      parseStateStack.add(new ParseState());
  }
  
  ParseState popParseState() {
      return parseStateStack.remove(parseStateStack.size() -1);
  }
  
  void restoreStashedParseState() {
     ParseState state = popParseState();
[#if grammar.options.treeBuildingEnabled]
     currentNodeScope = state.nodeScope;
[/#if]
    if (state.lastParsed != null) {
        //REVISIT
        currentToken = lastParsedToken = state.lastParsed;
    }
[#if grammar.lexerData.numLexicalStates > 1]     
     token_source.switchTo(lastParsedToken.getLexicalState());
     if (token_source.doLexicalStateSwitch(lastParsedToken.getType())) {
         token_source.reset(lastParsedToken);
         lastParsedToken.setNext(null);
     }
[/#if]          
  } 
  
  [/#if] 