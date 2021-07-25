/* Generatedey: JavaCC 21 Parser Generator. Token.java */
package org.parsers.json;

import java.util.*;
import org.parsers.json.ast.*;
public class Token implements JSONConstants, Node {
    private TokenType type;
    private String inputSource;
    public String getInputSource() {
        return inputSource;
    }

    public void setInputSource(String inputSource) {
        this.inputSource= inputSource;
    }

    public TokenType getType() {
        return type;
    }

    void setType(TokenType type) {
        this.type= type;
    }

    /**
     * @return whether this Token represent actual input or was it inserted somehow?
     */
    public boolean isVirtual() {
        return type== TokenType.EOF;
    }

    /**
     * @return Did we skip this token in parsing?
     */
    public boolean isSkipped() {
        return false;
    }

    /**
     * beginLine and beginColumn describe the position of the first character
     * of this token; endLine and endColumn describe the position of the
     * last character of this token.
     */
    private int beginLine, beginColumn, endLine, endColumn;
    public void setBeginColumn(int beginColumn) {
        this.beginColumn= beginColumn;
    }

    public void setEndColumn(int endColumn) {
        this.endColumn= endColumn;
    }

    public void setBeginLine(int beginLine) {
        this.beginLine= beginLine;
    }

    public void setEndLine(int endLine) {
        this.endLine= endLine;
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

    private String image;
    /**
     * @return the string image of the token.
     */
    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image= image;
    }

    private Token next;
    private Token previousToken, nextToken;
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
        this.next= next;
    }

    /**
     * @return the next token of any sort (parsed or unparsed or invalid)
     */
    public Token getNextToken() {
        return nextToken;
    }

    void setNextToken(Token nextToken) {
        this.nextToken= nextToken;
    }

    public Token getPreviousToken() {
        return previousToken;
    }

    void setPreviousToken(Token previousToken) {
        this.previousToken= previousToken;
    }

    public String getSource() {
        if (type== TokenType.EOF) return"";
        return getFileLineMap().getText(getBeginLine(), getBeginColumn(), getEndLine(), getEndColumn());
    }

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
    private boolean unparsed;
    //Should find a way to get rid of this.
    Token() {
    }

    public Token(int kind) {
        this(kind, null);
        this.type= TokenType.values()[kind];
    }

    /**
     * Constructs a new token for the specified Image and Kind.
     */
    public Token(int kind, String image) {
        this.type= TokenType.values()[kind];
        this.image= image;
    }

    /**
     * @param type the #TokenType of the token being constructed
     * @param image the String content of the token
     * @param inputSource the lookup name of the object that vended this token.
     */
    public Token(TokenType type, String image, String inputSource) {
        this.type= type;
        this.image= image;
        this.inputSource= inputSource;
    }

    public boolean isUnparsed() {
        return unparsed;
    }

    public void setUnparsed(boolean unparsed) {
        this.unparsed= unparsed;
    }

    /** 
     * Utility method to merge two tokens into a single token of a given type.
     * @param t1 the first token to merge
     * @param t2 the second token to merge
     * @param type the merged token's type
     * @return the result of merging the two tokens
     */
    static Token merge(Token t1, Token t2, TokenType type) {
        Token merged= newToken(type, t1.getImage()+t2.getImage(), t1.getInputSource());
        t1.copyLocationInfo(merged);
        merged.setEndColumn(t2.getEndColumn());
        merged.setEndLine(t2.getEndLine());
        merged.setNext(t2.getNext());
        merged.setNextToken(t2.getNextToken());
        return merged;
    }

    /**
     * Utility method to split a token in 2. For now, it assumes that the token 
     * is all on a single line. (Will maybe fix that later). 
     * @param length the point at which to split the token
     * @param type1 the desired type of the first token
     * @param type2 the desired type of the second token
     * @return the first token that resulted from the split
     */
    static Token split(Token tok, int length, TokenType type1, TokenType type2) {
        String img1= tok.getImage().substring(0, length);
        String img2= tok.getImage().substring(length);
        Token t1= newToken(type1, img1, tok.getInputSource());
        Token t2= newToken(type2, img2, tok.getInputSource());
        t1.setBeginColumn(tok.getBeginColumn());
        t1.setEndColumn(tok.getBeginColumn()+length-1);
        t1.setBeginLine(tok.getBeginLine());
        t1.setEndLine(tok.getBeginLine());
        t1.setPreviousToken(tok.getPreviousToken());
        t2.setBeginColumn(t1.getEndColumn()+1);
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

    public void clearChildren() {
    }

    public String getNormalizedText() {
        if (getType()== TokenType.EOF) {
            return"EOF";
        }
        return getImage();
    }

    public String toString() {
        return getNormalizedText();
    }

    /**
     * Copy the location info from a Node
     */
    public void copyLocationInfo(Node from) {
        Node.super.copyLocationInfo(from);
        if (from instanceof Token) {
            Token otherTok= (Token) from;
            next= otherTok.next;
            nextToken= otherTok.nextToken;
            previousToken= otherTok.previousToken;
        }
    }

    public void copyLocationInfo(Node start, Node end) {
        Node.super.copyLocationInfo(start, end);
        if (start instanceof Token) {
            previousToken= ((Token) start).previousToken;
        }
        if (end instanceof Token) {
            Token endToken= (Token) end;
            next= endToken.next;
            nextToken= endToken.nextToken;
        }
    }

    public static Token newToken(TokenType type, String image, String inputSource) {
        switch(type) {
            case WHITESPACE:
            return new WHITESPACE(TokenType.WHITESPACE, image, inputSource);
            case COLON:
            return new Delimiter(TokenType.COLON, image, inputSource);
            case COMMA:
            return new Delimiter(TokenType.COMMA, image, inputSource);
            case OPEN_BRACKET:
            return new Delimiter(TokenType.OPEN_BRACKET, image, inputSource);
            case CLOSE_BRACKET:
            return new Delimiter(TokenType.CLOSE_BRACKET, image, inputSource);
            case OPEN_BRACE:
            return new Delimiter(TokenType.OPEN_BRACE, image, inputSource);
            case CLOSE_BRACE:
            return new Delimiter(TokenType.CLOSE_BRACE, image, inputSource);
            case TRUE:
            return new BooleanLiteral(TokenType.TRUE, image, inputSource);
            case FALSE:
            return new BooleanLiteral(TokenType.FALSE, image, inputSource);
            case NULL:
            return new NullLiteral(TokenType.NULL, image, inputSource);
            case STRING_LITERAL:
            return new StringLiteral(TokenType.STRING_LITERAL, image, inputSource);
            case NUMBER:
            return new NumberLiteral(TokenType.NUMBER, image, inputSource);
            case INVALID:
            return new InvalidToken(image, inputSource);
            default:
            return new Token(type, image, inputSource);
        }
    }

    public static Token newToken(TokenType type, String image, JSONLexer lexer) {
        return newToken(type, image, lexer.getInputSource());
    }

    public static Token newToken(TokenType type, String image, JSONParser parser) {
        return newToken(type, image, parser.getInputSource());
    }

    public static Token newToken(TokenType type, String image, Node node) {
        return newToken(type, image, node.getInputSource());
    }

    public String getLocation() {
        //         return "line " + getBeginLine() + ", column " + getBeginColumn() + " of " + getInputSource();
        return getInputSource()+":"+getBeginLine()+":"+getBeginColumn();
    }

    private Node parent;
    private Map<String, Object> attributes;
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
        return-1;
    }

    public Node getParent() {
        return parent;
    }

    public void setParent(Node parent) {
        this.parent= parent;
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

    public void open() {
    }

    public void close() {
    }

    public Object getAttribute(String name) {
        return attributes== null?null:
        attributes.get(name);
    }

    public void setAttribute(String name, Object value) {
        if (attributes== null) {
            attributes= new HashMap<String, Object> ();
        }
        attributes.put(name, value);
    }

    public boolean hasAttribute(String name) {
        return attributes== null?false:
        attributes.containsKey(name);
    }

    public Set<String> getAttributeNames() {
        if (attributes== null) return Collections.emptySet();
        return attributes.keySet();
    }

}
