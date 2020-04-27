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
 


[#if grammar.options.faultTolerant]
    private boolean tolerantParsing= true;
    private boolean currentNTForced = false;
    private List<ParsingProblem> parsingProblems;
    
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
        Token virtualToken = Token.newToken(tokenType, "VIRTUAL " + tokenType);
        virtualToken.setUnparsed(true);
        virtualToken.setVirtual(true);
        int line = current_token.getEndLine();
        int column = current_token.getEndColumn();
        virtualToken.setBeginLine(line);
        virtualToken.setEndLine(line);
        virtualToken.setBeginColumn(column);
        virtualToken.setEndColumn(column);
     [#if grammar.lexerData.numLexicalStates >1]
            token_source.doLexicalStateSwitch(tokenType);
     [/#if]
        if (tokensAreNodes && buildTree) {
             currentNodeScope.add(virtualToken);           
        }
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
        Token oldToken = current_token;
        current_token = current_token.getNext();
        if (current_token == null ) {
            current_token = token_source.getNextToken();
        }
[#if grammar.options.faultTolerant]        
        if (tolerantParsing && current_token instanceof InvalidToken) {
             addParsingProblem(new ParsingProblem("Lexically invalid input", current_token));
             invalidToken = (InvalidToken) current_token;
             current_token = token_source.getNextToken();     
        }
[/#if]        
        if (current_token.getType() != expectedType) {
            handleUnexpectedTokenType(expectedType, forced, oldToken) ;
        }      
[#if grammar.options.treeBuildingEnabled]
      if (buildTree && tokensAreNodes) {
  [#if grammar.options.userDefinedLexer]
          current_token.setInputSource(inputSource);
  [/#if]
  [#if grammar.usesjjtreeOpenNodeScope]
          jjtreeOpenNodeScope(current_token);
  [/#if]
  [#if grammar.usesOpenNodeScopeHook]
          openNodeScopeHook(current_token);
  [/#if]
          if (invalidToken != null) {
             pushNode(invalidToken);
          }          
          pushNode(current_token);
  [#if grammar.usesjjtreeCloseNodeScope]
          jjtreeCloseNodeScope(current_token);
  [/#if]
  [#if grammar.usesCloseNodeScopeHook]
          closeNodeScopeHook(current_token);
  [/#if]
      }
[/#if]
      if (trace_enabled) LOGGER.info("Consumed token of type " + current_token.getType() + " from " + current_token.getLocation());
      return current_token;
  }
 
  private void handleUnexpectedTokenType(TokenType expectedType,  boolean forced, Token oldToken) throws ParseException {
[#if grammar.options.faultTolerant]    
       if (forced && tolerantParsing) {
           Token nextToken = current_token;
           current_token = oldToken;
           Token virtualToken = insertVirtualToken(expectedType);
           virtualToken.setNext(nextToken);
           current_token = virtualToken;
           String message = "Expecting token type "+ expectedType + " but encountered " + nextToken.getType();
           message += "\nInserting virtual token to continue parsing";
           addParsingProblem(new ParsingProblem(message, virtualToken));
       } 
[/#if]      
       throw new ParseException(current_token, EnumSet.of(expectedType));
  }
  
  