/* Generatedey: ${generated_by}. ${filename} */
[#if grammar.parserPackage?has_content]
package ${grammar.parserPackage};
[/#if]
[#if grammar.nodePackage?has_content && grammar.nodePackage != grammar.parserPackage]
import ${grammar.nodePackage}.*;
[/#if]

[#if grammar.settings.FREEMARKER_NODES?? && grammar.settings.FREEMARKER_NODES]
import freemarker.template.*;
[/#if]

 [#var extendsNode = ""]

 [#if grammar.treeBuildingEnabled]
    [#set extendsNode =", Node"]
 [/#if]

public class Token implements ${grammar.constantsClassName} ${extendsNode} {

    private TokenType type;

    private String inputSource;

    private FileLineMap fileLineMap;

    private int beginOffset, endOffset;

    public FileLineMap getFileLineMap() {
        return this.fileLineMap;
    }

    public void setFileLineMap(FileLineMap fileLineMap) {
        this.fileLineMap = fileLineMap;
    }


    public TokenType getType() {
        return type;
    }

    void setType(TokenType type) {
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


    public void setBeginOffset(int beginOffset) {
        this.beginOffset = beginOffset;
    }

    public void setEndOffset(int endOffset) {
        this.endOffset = endOffset;
    }

[#if !grammar.treeBuildingEnabled]
    /**
     * @return the (1-based) line location where this Token starts
     */      
    public int getBeginLine() {
        return getFileLineMap().getLineFromOffset(getBeginOffset());                
    };

    /**
     * @return the (1-based) line location where this Token ends
     */
    public int getEndLine() {
        return getFileLineMap().getLineFromOffset(getEndOffset()-1);
    };

    /**
     * @return the (1-based) column where this Token starts
     */
    public int getBeginColumn() {
        return getFileLineMap().getCodePointColumnFromOffset(getBeginOffset());        
    };

    /**
     * @return the (1-based) column offset where this Token ends
     */ 
    public int getEndColumn() {
        return getFileLineMap().getCodePointColumnFromOffset(getEndOffset());
    }

    public String getInputSource() {
        return getFileLineMap().getInputSource();
    }
[/#if]    

    public int getBeginOffset() {
        return beginOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    private String image;

    /**
     * @return the string image of the token.
     */
    public String getImage() {
        return image;
    }

   public void setImage(String image) {
       this.image = image;
   }

    private Token nextChainedToken, previousChainedToken;


    /**
     * This is the same as #getNextParsedToken
     * @return the next parsed token
     */
    public final Token getNext() {
        return getNextParsedToken();
    }

    /**
     * This is typically only used internally
     * @param next the token parsed after this one
     */
    final void setNext(Token next) {
        this.nextChainedToken = next;
    }

    /**
     * @return the next regular (i.e. parsed) token
     */
    public Token getNextParsedToken() {
        Token result = getNextToken();
        while (result != null && result.isUnparsed()) {
            result = result.getNextToken();
        }
        return result;
    }

    void setNextChainedToken(Token nextChainedToken) {
        this.nextChainedToken = nextChainedToken;
        if (nextChainedToken != null) nextChainedToken.previousChainedToken = this;
    }

    /**
     * @return the next token of any sort (parsed or unparsed or invalid)
     */
     public Token getNextToken() {
        if (nextChainedToken != null) return nextChainedToken;
        if (getFileLineMap()==null) return null;
        return getFileLineMap().getCachedToken(getEndOffset());
     }

     public Token getPreviousToken() {
        if (previousChainedToken !=null) return previousChainedToken;
        if (getFileLineMap()==null) return null;
        return getFileLineMap().getPreviousCachedToken(getBeginOffset());
     }

     void setPreviousToken(Token previousToken) {
         this.previousChainedToken = previousToken;
         if (previousChainedToken != null) previousChainedToken.nextChainedToken = this;
     }

    public String getSource() {
         if (type == TokenType.EOF) return "";
         return getFileLineMap().getText(getBeginOffset(), getEndOffset());
    }

    private boolean unparsed;

    //Should find a way to get rid of this.
    Token() {}

    /**
     * @param type the #TokenType of the token being constructed
     * @param image the String content of the token
     * @param fileLineMap the object that vended this token.
     */
    public Token(TokenType type, String image, FileLineMap fileLineMap) {
        this.type = type;
        this.image = image;
        this.inputSource = inputSource;
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

[#if grammar.treeBuildingEnabled]
    /**
     * Copy the location info from a Node
     */
    public void copyLocationInfo(Node from) {
        Node.super.copyLocationInfo(from);
        if (from instanceof Token) {
            Token otherTok = (Token) from;
            nextChainedToken = otherTok.nextChainedToken;
            previousChainedToken = otherTok.previousChainedToken;
        }
        setFileLineMap(from.getFileLineMap());
    }

    public void copyLocationInfo(Node start, Node end) {
        Node.super.copyLocationInfo(start, end);
        if (start instanceof Token) {
            previousChainedToken = ((Token) start).previousChainedToken;
        }
        if (end instanceof Token) {
            Token endToken = (Token) end;
            nextChainedToken = endToken.nextChainedToken;
        }
    }
[#else]
    public void copyLocationInfo(Token from) {
        setFileLineMap(from.getFileLineMap());
        setBeginOffset(from.getBeginOffset());
        setEndOffset(from.getEndOffset());
        nextChainedToken = from.nextChainedToken;
        previousChainedToken = from.previousChainedToken;
    }

    public void copyLocationInfo(Token start, Token end) {
        setFileLineMap(start.getFileLineMap());
        if (fileLineMap == null) setFileLineMap(end.getFileLineMap());
        setBeginOffset(start.getBeginOffset());
        setEndOffset(end.getEndOffset());
        previousChainedToken = start.previousChainedToken;
        nextChainedToken = end.nextChainedToken;
    }
[/#if]

    public static Token newToken(TokenType type, String image, FileLineMap fileLineMap) {
        [#if grammar.treeBuildingEnabled]
           switch(type) {
           [#list grammar.orderedNamedTokens as re]
            [#if re.generatedClassName != "Token" && !re.private]
              case ${re.label} : return new ${grammar.nodePrefix}${re.generatedClassName}(TokenType.${re.label}, image, fileLineMap);
            [/#if]
           [/#list]
              case INVALID : return new InvalidToken(image, fileLineMap);
           default : return new Token(type, image, fileLineMap);
           }
       [#else]
         return new Token(type, image, fileLineMap);
       [/#if]
    }

    public static Token newToken(TokenType type, String image, ${grammar.lexerClassName} lexer) {
        return newToken(type, image, lexer.input_stream);
    }

    [#if grammar.productionTable?size != 0]
        public static Token newToken(TokenType type, String image, ${grammar.parserClassName} parser) {
            return newToken(type, image, parser.token_source);
        }
    [/#if]

    [#if grammar.treeBuildingEnabled]
        public static Token newToken(TokenType type, String image, Node node) {
            return newToken(type, image, node.getFileLineMap());
        }
    [/#if]

    public String getLocation() {
        return getInputSource() + ":" + getBeginLine() + ":" + getBeginColumn();
     }

[#if grammar.treeBuildingEnabled]

    private Node parent;

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

    public int indexOf(Node n) {
        return -1;
    }

    public Node getParent() {
        return parent;
    }

    public void setParent(Node parent) {
        this.parent = parent;
    }

    public int getChildCount() {
        return 0;
    }

    public Node getChild(int i) {
        return null;
    }

    public java.util.List<Node> children() {
        return java.util.Collections.emptyList();
    }

    public void open() {}

    public void close() {}


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
