/* Generatedey: ${generated_by}. ${filename} */
[#if grammar.parserPackage?has_content]
package ${grammar.parserPackage};
[/#if]
import java.util.*;
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

    public TokenType getType() {
        return type;
    }
    
    void setType(TokenType type) {
        this.type=type;
    }

    /**
     * Does this Token represent actual input or was it inserted somehow?
     */
    public boolean isVirtual() {
        [#if grammar.faultTolerant]
            return virtual || type == TokenType.EOF;
        [#else]
            return type == TokenType.EOF;
        [/#if]
    }

    /**
     * Did we skip this token in parsing?
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

    /**
     * beginLine and beginColumn describe the position of the first character
     * of this token; endLine and endColumn describe the position of the
     * last character of this token.
     */
[#if grammar.legacyAPI]public[#else]private[/#if]      
    int beginLine, beginColumn, endLine, endColumn;

    
    public void setBeginColumn(int beginColumn) {
        this.beginColumn = beginColumn;
    }	
    
    public void setEndColumn(int endColumn) {
        this.endColumn = endColumn;
    }	
    
    public void setBeginLine(int beginLine) {
        this.beginLine = beginLine;
    }	
    
    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }	
    
    public int getBeginLine() {
        return beginLine;
    }
    
    public int getBeginColumn() {
        return beginColumn;
    }
    
    public int getEndLine() {
        return endLine;
    }
    
    public int getEndColumn() {
        return endColumn;
    }

[#if grammar.legacyAPI]public[#else]private[/#if]      
    String image;
    
    /**
     * The string image of the token.
     */
    public String getImage() {
        return image;
    }
    
   public void setImage(String image) {
       this.image = image;
   } 
    
[#if grammar.legacyAPI]

    void setKind(int kind) {
        this.type = TokenType.values()[kind];
    }
    
    int getKind() {
        return type.ordinal();
    }

    /**
     * A reference to the next regular (non-special) token from the input
     * stream.  If this is the last token from the input stream, or if the
     * token manager has not read tokens beyond this one, this field is
     * set to null.  This is true only if this token is also a regular
     * token.  Otherwise, see below for a description of the contents of
     * this field.
     */
[#else]
    private
[/#if]
    Token next;
    private Token previousToken, nextToken;
    

    /**
     * This is the same as #getNextParsedToken
     */
    public final Token getNext() {
        return getNextParsedToken();
    }
    
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
     * The next token of any sort (parsed or unparsed or invalid)
     */
     public Token getNextToken() {
         return nextToken;
     }

     void setNextToken(Token nextToken) {
         this.nextToken = nextToken;
     }

     public Token getPreviousToken() {
         [#if grammar.hugeFileSupport]
           throw new UnsupportedOperationException("With HUGE_FILE_SUPPORT turned on, the previousToken is not cached");
         [#else]
           return previousToken;
         [/#if]
     }

     void setPreviousToken(Token previousToken) {
         [#if grammar.hugeFileSupport] if (previousToken == null || previousToken.isUnparsed())[/#if]
         this.previousToken = previousToken;
     }

 [#if !grammar.hugeFileSupport && !grammar.userDefinedLexer]
    [#if !grammar.treeBuildingEnabled]
     public FileLineMap getFileLineMap() {
         return FileLineMap.getFileLineMapByName(getInputSource());
     }
    [/#if]
     
    public String getSource() {
         if (type == TokenType.EOF) return "";
         return getFileLineMap().getText(getBeginLine(), getBeginColumn(), getEndLine(), getEndColumn());
    }
 [/#if]

    /**
     * This field is used to access special tokens that occur prior to this
     * token, but after the immediately preceding regular (non-special) token.
     * If there are no such special tokens, this field is set to null.
     * When there are more than one such special token, this field refers
     * to the last of these special tokens, which in turn refers to the next
     * previous special token through its specialToken field, and so on
     * until the first special token (whose specialToken field is null).
     * The next fields of special tokens refer to other special tokens that
     * immediately follow it (without an intervening regular token).  If there
     * is no such token, this field is null.
     */
[#if grammar.legacyAPI]

    public Token specialToken;

    @Deprecated
    public Token getSpecialToken() {
           return specialToken;
    }
    
    @Deprecated 
    public void setSpecialToken(Token specialToken) {
         this.specialToken = specialToken;
    }
[/#if]    
    
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
        this.image = image;;
    }
    
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

[#if !grammar.userDefinedLexer]
    /** 
     * Utility method to merge two tokens into a single token of a given type.
     */
    static Token merge(Token t1, Token t2, TokenType type) {
        Token merged = newToken(type, t1.getImage() + t2.getImage(), t1.getInputSource());
        t1.copyLocationInfo(merged);
        merged.setEndColumn(t2.getEndColumn());
        merged.setEndLine(t2.getEndLine());
        merged.setNext(t2.getNext());
        merged.setNextToken(t2.getNextToken());
        return merged;
    }

    /**
     * Utility method to split a token in 2. For now, it assumes that the token 
     * is all on a single line. (Will maybe fix that later). Returns the first token.
     */ 
    static Token split(Token tok, int length, TokenType type1, TokenType type2) {
        String img1 = tok.getImage().substring(0, length);
        String img2 = tok.getImage().substring(length);
        Token t1 = newToken(type1, img1, tok.getInputSource());
        Token t2 = newToken(type2, img2, tok.getInputSource()); 
        t1.setBeginColumn(tok.getBeginColumn());
        t1.setEndColumn(tok.getBeginColumn() + length -1);
        t1.setBeginLine(tok.getBeginLine());
        t1.setEndLine(tok.getBeginLine());
        t1.setPreviousToken(tok.getPreviousToken());
        t2.setBeginColumn(t1.getEndColumn() +1);
        t2.setEndColumn(tok.getEndColumn());
        t2.setBeginLine(tok.getBeginLine());
        t2.setEndLine(tok.getEndLine());
        t1.setNext(t2);
        t1.setNextToken(t2);
        t2.setPreviousToken(t1);
        t2.setNext(tok.getNext());
        t2.setNextToken(tok.getNextToken());
        return t1;
    }
[/#if]   

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
        [#if grammar.legacyAPI]
            specialToken = otherTok.specialToken;
        [/#if]
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
        setBeginLine(from.getBeginLine());
        setBeginColumn(from.getBeginColumn());
        setEndLine(from.getEndLine());
        setEndColumn(from.getEndColumn());
        next = from.next;
        nextToken = from.nextToken;
        previousToken = from.previousToken;
        [#if grammar.legacyAPI]
            specialToken = from.specialToken;
        [/#if]
    }

    public void copyLocationInfo(Token start, Token end) {
        if (getInputSource()==null && start.getInputSource()!=null) {
            setInputSource(start.getInputSource()); 
        }
        if (getInputSource()==null && start.getInputSource()!=null) {
            setInputSource(start.getInputSource()); 
        }
        setBeginLine(start.getBeginLine());
        setBeginColumn(start.getBeginColumn());
        setEndLine(end.getEndLine());
        setEndColumn(end.getEndColumn());
        previousToken = start.previousToken;
        next = end.next;
        nextToken = end.nextToken;
    }
[/#if]

[#if grammar.legacyAPI]    
    public static Token newToken(int ofKind, String image) {
       [#if grammar.treeBuildingEnabled]
           switch(ofKind) {
           [#list grammar.orderedNamedTokens as re]
            [#if re.generatedClassName != "Token" && !re.private]
              case ${re.label} : return new ${grammar.nodePrefix}${re.generatedClassName}(ofKind, image);
            [/#if]
           [/#list]
              default: return new Token(ofKind, image);
           }
       [#else]
       return new Token(ofKind, image); 
       [/#if]
    }
[/#if]   
[#if !grammar.userDefinedLexer]
    public static Token newToken(TokenType type, String image, String inputSource) {
           [#--if !grammar.hugeFileSupport]image = null;[/#if--]
           [#if grammar.treeBuildingEnabled]
           switch(type) {
           [#list grammar.orderedNamedTokens as re]
            [#if re.generatedClassName != "Token" && !re.private]
              case ${re.label} : return new ${grammar.nodePrefix}${re.generatedClassName}(TokenType.${re.label}, image, inputSource);
            [/#if]
           [/#list]
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
[/#if]    
    
    public String getLocation() {
//         return "line " + getBeginLine() + ", column " + getBeginColumn() + " of " + getInputSource();
         return getInputSource() + ":" + getBeginLine() + ":" + getBeginColumn();
     }
    
[#if grammar.treeBuildingEnabled]
    
    private Node parent;
    private Map<String,Object> attributes; 

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
    
    public List<Node> children() {
        return Collections.emptyList();
    }

    public void open() {}

    public void close() {}
    
    
    public Object getAttribute(String name) {
        return attributes == null ? null : attributes.get(name); 
    }
     
    public void setAttribute(String name, Object value) {
        if (attributes == null) {
            attributes = new HashMap<String, Object>();
        }
        attributes.put(name, value);
    }
     
    public boolean hasAttribute(String name) {
        return attributes == null ? false : attributes.containsKey(name);
    }
     
    public Set<String> getAttributeNames() {
        if (attributes == null) return Collections.emptySet();
        return attributes.keySet();
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
