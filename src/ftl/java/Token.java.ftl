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

    public String getInputSource() {return inputSource;}

    public void setInputSource(String inputSource) {this.inputSource = inputSource;}

    private int beginOffset, endOffset;

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

    private Token next, previousToken, nextToken;


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
        setNextParsedToken(next);
    }

    /**
     * @return the next regular (i.e. parsed) token
     */
    public Token getNextParsedToken() {
        return next;
    }

    void setNextParsedToken(Token next) {
        this.next = next;
    }


    /**
     * @return the next token of any sort (parsed or unparsed or invalid)
     */
     public Token getNextToken() {
         return nextToken;
     }

     void setNextToken(Token nextToken) {
         this.nextToken = nextToken;
     }

     public Token getPreviousToken() {
        return previousToken;
     }

     void setPreviousToken(Token previousToken) {
         this.previousToken = previousToken;
     }

 [#if !grammar.treeBuildingEnabled]
     public FileLineMap getFileLineMap() {
         return FileLineMap.getFileLineMapByName(getInputSource());
     }
 [/#if]

    public String getSource() {
         if (type == TokenType.EOF) return "";
//         return getFileLineMap().getText(getBeginLine(), getBeginColumn(), getEndLine(), getEndColumn());
         return getFileLineMap().getText(getBeginOffset(), getEndOffset());
    }

    private boolean unparsed;

    //Should find a way to get rid of this.
    Token() {}

    public Token(int kind) {
       this(kind, null);
       this.type = TokenType.values()[kind];
    }

    /**
     * Constructs a new token for the specified Image and Kind.
     */
    public Token(int kind, String image) {
        this.type = TokenType.values()[kind];
        this.image = image;
    }

    /**
     * @param type the #TokenType of the token being constructed
     * @param image the String content of the token
     * @param inputSource the lookup name of the object that vended this token.
     */
    public Token(TokenType type, String image, String inputSource) {
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
            next = otherTok.next;
            nextToken = otherTok.nextToken;
            previousToken = otherTok.previousToken;
        }
    }

    public void copyLocationInfo(Node start, Node end) {
        Node.super.copyLocationInfo(start, end);
        if (start instanceof Token) {
            previousToken = ((Token) start).previousToken;
        }
        if (end instanceof Token) {
            Token endToken = (Token) end;
            next = endToken.next;
            nextToken = endToken.nextToken;
        }
    }
[#else]
    public void copyLocationInfo(Token from) {
        if (getInputSource()==null && from.getInputSource()!=null) {
            setInputSource(from.getInputSource()); //REVISIT
        }
        setBeginOffset(from.getBeginOffset());
        setEndOffset(from.getEndOffset());
        next = from.next;
        nextToken = from.nextToken;
        previousToken = from.previousToken;
    }

    public void copyLocationInfo(Token start, Token end) {
        if (getInputSource()==null && start.getInputSource()!=null) {
            setInputSource(start.getInputSource());
        }
        if (getInputSource()==null && start.getInputSource()!=null) {
            setInputSource(start.getInputSource());
        }
        setBeginOffset(start.getBeginOffset());
        setEndOffset(end.getEndOffset());
        previousToken = start.previousToken;
        next = end.next;
        nextToken = end.nextToken;
    }
[/#if]

    public static Token newToken(TokenType type, String image, String inputSource) {
        [#if grammar.treeBuildingEnabled]
           switch(type) {
           [#list grammar.orderedNamedTokens as re]
            [#if re.generatedClassName != "Token" && !re.private]
              case ${re.label} : return new ${grammar.nodePrefix}${re.generatedClassName}(TokenType.${re.label}, image, inputSource);
            [/#if]
           [/#list]
              case INVALID : return new InvalidToken(image, inputSource);
           default : return new Token(type, image, inputSource);
           }
       [#else]
         return new Token(type, image, inputSource);
       [/#if]
    }

    public static Token newToken(TokenType type, String image, ${grammar.lexerClassName} lexer) {
        return newToken(type, image, lexer.getInputSource());
    }

    [#if grammar.productionTable?size != 0]
        public static Token newToken(TokenType type, String image, ${grammar.parserClassName} parser) {
            return newToken(type, image, parser.getInputSource());
        }
    [/#if]

    [#if grammar.treeBuildingEnabled]
        public static Token newToken(TokenType type, String image, Node node) {
            return newToken(type, image, node.getInputSource());
        }
    [/#if]

    public String getLocation() {
//         return "line " + getBeginLine() + ", column " + getBeginColumn() + " of " + getInputSource();
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
