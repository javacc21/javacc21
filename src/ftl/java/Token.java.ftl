[#--
/* Copyright (c) 2008-2021 Jonathan Revusky, revusky@javacc.com
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
 /* Generated by: ${generated_by}. ${filename} */

[#var parserPackageWithDot = ""]
[#if grammar.parserPackage?has_content]
package ${grammar.parserPackage};
[#set parserPackageWithDot = grammar.ParserPackage + "."]
[/#if]
[#if grammar.nodePackage?has_content && grammar.nodePackage != grammar.parserPackage]
import ${grammar.nodePackage}.*;
[/#if]

import java.util.Iterator;
import java.util.List;

[#if grammar.settings.FREEMARKER_NODES?? && grammar.settings.FREEMARKER_NODES]
import freemarker.template.*;
[/#if]

 [#var extendsNode = ""]

 [#if grammar.treeBuildingEnabled]
    [#set extendsNode =", Node"]
 [/#if]

public class Token implements ${grammar.constantsClassName} ${extendsNode} {

    private TokenType type;

    private ${grammar.lexerClassName} tokenSource;

    private int beginOffset, endOffset;
    
    private boolean unparsed;

[#if grammar.treeBuildingEnabled]
    private Node parent;
[/#if]

[#if !grammar.minimalToken || grammar.faultTolerant]
    private String image;
    public void setImage(String image) {
       this.image = image;
    }
[/#if]

[#if !grammar.minimalToken]

    private Token prependedToken, appendedToken;

    private boolean inserted;

    public boolean isInserted() {return inserted;}


    public void preInsert(Token prependedToken) {
        if (prependedToken == this.prependedToken) return;
        prependedToken.appendedToken = this;
        Token existingPreviousToken = this.previousCachedToken();
        if (existingPreviousToken != null) {
            existingPreviousToken.appendedToken = prependedToken;
            prependedToken.prependedToken = existingPreviousToken;
        }
        prependedToken.inserted = true;
        prependedToken.beginOffset = prependedToken.endOffset = this.beginOffset;
        this.prependedToken = prependedToken;
    }
    void unsetAppendedToken() {
        this.appendedToken = null;
    }

    /**
     * @param type the #TokenType of the token being constructed
     * @param image the String content of the token
     * @param tokenSource the object that vended this token.
     */
    public Token(TokenType type, String image, ${grammar.lexerClassName} tokenSource) {
        this.type = type;
        this.image = image;
        this.tokenSource = tokenSource;
    }

    public static Token newToken(TokenType type, String image, ${grammar.lexerClassName} tokenSource) {
        Token result = newToken(type, tokenSource, 0, 0);
        result.setImage(image);
        return result;
    }
[/#if]

    /**
     * It would be extremely rare that an application
     * programmer would use this method. It needs to
     * be public because it is part of the ${parserPackageWithDot}Node interface.
     */
    public void setBeginOffset(int beginOffset) {
        this.beginOffset = beginOffset;
    }

    /**
     * It would be extremely rare that an application
     * programmer would use this method. It needs to
     * be public because it is part of the ${parserPackageWithDot}Node interface.
     */
    public void setEndOffset(int endOffset) {
        this.endOffset = endOffset;
    }

    /**
     * @return the ${grammar.lexerClassName} object that handles 
     * location info for the tokens. 
     */
    public ${grammar.lexerClassName} getTokenSource() {
      [#if grammar.minimalToken] 
        return this.tokenSource; 
      [#else]
        ${grammar.lexerClassName} flm = this.tokenSource;
        // If this is null and we have chained tokens,
        // we try to get it from there! (Why not?)
        if (flm == null) {
            if (prependedToken != null) {
                flm = prependedToken.getTokenSource();
            }
            if (flm == null && appendedToken != null) {
                flm = appendedToken.getTokenSource();
            }
        }
        return flm;
    [/#if]
    }

    /**
     * It should be exceedingly rare that an application
     * programmer needs to use this method.
     */
    public void setTokenSource(${grammar.lexerClassName} tokenSource) {
        this.tokenSource = tokenSource;
    }

    /**
     * Return the TokenType of this Token object
     */
    public TokenType getType() {
        return type;
    }

    protected void setType(TokenType type) {
        this.type=type;
    }

    /**
     * @return whether this Token represent actual input or was it inserted somehow?
     */
    public boolean isVirtual() {
        [#if grammar.faultTolerant]
            return virtual || type == TokenType.EOF;
        [#else]
            return type == TokenType.EOF;
        [/#if]
    }

    /**
     * @return Did we skip this token in parsing?
     */
    public boolean isSkipped() {
        [#if grammar.faultTolerant]
           return skipped;
        [#else]
           return false;
        [/#if]
    }


[#if grammar.faultTolerant]
    private boolean virtual, skipped, dirty;

    void setVirtual(boolean virtual) {
        this.virtual = virtual;
        if (virtual) dirty = true;
    }

    void setSkipped(boolean skipped) {
        this.skipped = skipped;
        if (skipped) dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

[/#if]


[#if !grammar.treeBuildingEnabled]
 [#-- If tree building is enabled, we can simply use the default 
      implementation in the Node interface--]
    /**
     * @return the (1-based) line location where this Token starts
     */      
    public int getBeginLine() {
        ${grammar.lexerClassName} flm = getTokenSource();
        return flm == null ? 0 : flm.getLineFromOffset(getBeginOffset());                
    };

    /**
     * @return the (1-based) line location where this Token ends
     */
    public int getEndLine() {
        ${grammar.lexerClassName} flm = getTokenSource();
        return flm == null ? 0 : flm.getLineFromOffset(getEndOffset()-1);
    };

    /**
     * @return the (1-based) column where this Token starts
     */
    public int getBeginColumn() {
        ${grammar.lexerClassName} flm = getTokenSource();
        return flm == null ? 0 : flm.getCodePointColumnFromOffset(getBeginOffset());        
    };

    /**
     * @return the (1-based) column offset where this Token ends
     */ 
    public int getEndColumn() {
        ${grammar.lexerClassName} flm = getTokenSource();
        return flm == null ? 0 : flm.getCodePointColumnFromOffset(getEndOffset());
    }

    public String getInputSource() {
        ${grammar.lexerClassName} flm = getTokenSource();
        return flm != null ? flm.getInputSource() : "input";
    }
[/#if]    

    public int getBeginOffset() {
        return beginOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    /**
     * @return the string image of the token.
     */
    public String getImage() {
      [#if grammar.minimalToken]
        return getSource();
      [#else]  
        return image != null ? image : getSource();
      [/#if]
    }

    /**
     * @return the next _cached_ regular (i.e. parsed) token
     * or null
     */
    public final Token getNext() {
        return getNextParsedToken();
    }

    /**
     * @return the next regular (i.e. parsed) token
     */
    private Token getNextParsedToken() {
        Token result = nextCachedToken();
        while (result != null && result.isUnparsed()) {
            result = result.nextCachedToken();
        }
        return result;
    }

    /**
     * @return the next token of any sort (parsed or unparsed or invalid)
     */
    public Token nextCachedToken() {
[#if !grammar.minimalToken]        
        if (appendedToken != null) return appendedToken;
[/#if]        
        ${grammar.lexerClassName} tokenSource = getTokenSource();
        return tokenSource != null ? tokenSource.nextCachedToken(getEndOffset()) : null;
    }

    public Token previousCachedToken() {
[#if !grammar.minimalToken]        
        if (prependedToken !=null) return prependedToken;
[/#if]        
        if (getTokenSource()==null) return null;
        return getTokenSource().previousCachedToken(getBeginOffset());
    }

    Token getPreviousToken() {
        return previousCachedToken();
    }

    public Token replaceType(TokenType type) {
        Token result = newToken(getType(), getTokenSource(), getBeginOffset(), getEndOffset());
[#if !grammar.minimalToken]        
        result.prependedToken = this.prependedToken;
        result.appendedToken = this.appendedToken;
        result.inserted = this.inserted;
        if (result.appendedToken != null) {
            result.appendedToken.prependedToken = result;
        }
        if (result.prependedToken != null) {
            result.prependedToken.appendedToken = result;
        }
        if (!result.inserted) {
            getTokenSource().cacheToken(result);
        }
[#else]
        getTokenSource().cacheToken(result);
[/#if]        

        return result;
    }

    public String getSource() {
         if (type == TokenType.EOF) return "";
         ${grammar.lexerClassName} flm = getTokenSource();
         return flm == null ? null : flm.getText(getBeginOffset(), getEndOffset());
    }



    protected Token() {}

    public Token(TokenType type, ${grammar.lexerClassName} tokenSource, int beginOffset, int endOffset) {
        this.type = type;
        this.tokenSource = tokenSource;
        this.beginOffset = beginOffset;
        this.endOffset = endOffset;
    }

    public boolean isUnparsed() {
        return unparsed;
    }

    public void setUnparsed(boolean unparsed) {
        this.unparsed = unparsed;
    }

    public void clearChildren() {}

    public String getNormalizedText() {
        if (getType() == TokenType.EOF) {
            return "EOF";
        }
        return getImage();
    }

    public String toString() {
        return getNormalizedText();
    }

    /**
     * @return An iterator of the tokens preceding this one.
     */
    public Iterator<Token> precedingTokens() {
        return new Iterator<Token>() {
            Token currentPoint = Token.this;
            public boolean hasNext() {
                return currentPoint.previousCachedToken() != null;
            }
            public Token next() {
                Token previous = currentPoint.previousCachedToken();
                if (previous == null) throw new java.util.NoSuchElementException("No previous token!");
                return currentPoint = previous;
            }
        };
    }

    /**
     * @return An iterator of the (cached) tokens that follow this one.
     */
    public Iterator<Token> followingTokens() {
        return new java.util.Iterator<Token>() {
            Token currentPoint = Token.this;
            public boolean hasNext() {
                return currentPoint.nextCachedToken() != null;
            }
            public Token next() {
                Token next = currentPoint.nextCachedToken();
                if (next == null) throw new java.util.NoSuchElementException("No next token!");
                return currentPoint = next;
            }
        };
    }

[#if grammar.treeBuildingEnabled && !grammar.minimalToken]
    /**
     * Copy the location info from a Node
     */
    public void copyLocationInfo(Node from) {
        Node.super.copyLocationInfo(from);
        if (from instanceof Token) {
            Token otherTok = (Token) from;
            appendedToken = otherTok.appendedToken;
            prependedToken = otherTok.prependedToken;
        }
        setTokenSource(from.getTokenSource());
    }
    
    public void copyLocationInfo(Node start, Node end) {
        Node.super.copyLocationInfo(start, end);
        if (start instanceof Token) {
            prependedToken = ((Token) start).prependedToken;
        }
        if (end instanceof Token) {
            Token endToken = (Token) end;
            appendedToken = endToken.appendedToken;
        }
    }
[#else]
    public void copyLocationInfo(Token from) {
        setTokenSource(from.getTokenSource());
        setBeginOffset(from.getBeginOffset());
        setEndOffset(from.getEndOffset());
    [#if !grammar.minimalToken]    
        appendedToken = from.appendedToken;
        prependedToken = from.prependedToken;
    [/#if]
    }

    public void copyLocationInfo(Token start, Token end) {
        setTokenSource(start.getTokenSource());
        if (tokenSource == null) setTokenSource(end.getTokenSource());
        setBeginOffset(start.getBeginOffset());
        setEndOffset(end.getEndOffset());
    [#if !grammar.minimalToken]
        prependedToken = start.prependedToken;
        appendedToken = end.appendedToken;
    [/#if]
    }
[/#if]

    public static Token newToken(TokenType type, ${grammar.lexerClassName} tokenSource, int beginOffset, int endOffset) {
        [#if grammar.treeBuildingEnabled]
           switch(type) {
           [#list grammar.orderedNamedTokens as re]
            [#if re.generatedClassName != "Token" && !re.private]
              case ${re.label} : return new ${grammar.nodePrefix}${re.generatedClassName}(TokenType.${re.label}, tokenSource, beginOffset, endOffset);
            [/#if]
           [/#list]
           [#list grammar.extraTokenNames as tokenName]
              case ${tokenName} : return new ${grammar.nodePrefix}${grammar.extraTokens[tokenName]}(TokenType.${tokenName}, tokenSource, beginOffset, endOffset);
           [/#list]
              case INVALID : return new InvalidToken(tokenSource, beginOffset, endOffset);
              default : return new Token(type, tokenSource, beginOffset, endOffset);
           }
       [#else]
         return new Token(type, tokenSource, beginOffset, endOffset);
       [/#if]
    }

    public String getLocation() {
        return getInputSource() + ":" + getBeginLine() + ":" + getBeginColumn();
     }

[#if grammar.treeBuildingEnabled]

    public void setChild(int i, Node n) {
        throw new UnsupportedOperationException();
    }

    public void addChild(Node n) {
        throw new UnsupportedOperationException();
    }

    public void addChild(int i, Node n) {
        throw new UnsupportedOperationException();
    }

    public Node removeChild(int i) {
        throw new UnsupportedOperationException();
    }

    public final int indexOf(Node n) {
        return -1;
    }

    public Node getParent() {
        return parent;
    }

    public void setParent(Node parent) {
        this.parent = parent;
    }

    public final int getChildCount() {
        return 0;
    }

    public final Node getChild(int i) {
        return null;
    }

    public final List<Node> children() {
        return java.util.Collections.emptyList();
    }

   [#if grammar.settings.FREEMARKER_NODES?? && grammar.settings.FREEMARKER_NODES]
    public TemplateNodeModel getParentNode() {
        return parent;
    }

    public TemplateSequenceModel getChildNodes() {
        return null;
    }

    public String getNodeName() {
        return getType().toString();
    }

    public String getNodeType() {
        return getClass().getSimpleName();
    }

    public String getNodeNamespace() {
        return null;
    }

    public String getAsString() {
        return getNormalizedText();
    }
  [/#if]

 [/#if]
}
