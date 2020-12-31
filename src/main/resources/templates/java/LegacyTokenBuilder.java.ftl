[#ftl strict_vars = true]

[#--  This file used to be used to build a separate class called SimpleCharStream
      that should never have been exposed as public.
      Now the contents of this file are an include from LexGen.java.ftl.
  --]

[#var TABS_TO_SPACES = 0, PRESERVE_LINE_ENDINGS=true, JAVA_UNICODE_ESCAPE=false]
[#if grammar.settings.TABS_TO_SPACES??]
    [#set TABS_TO_SPACES = grammar.settings.TABS_TO_SPACES]
[/#if]
[#if grammar.settings.PRESERVE_LINE_ENDINGS?? && !grammar.settings.PRESERVE_LINE_ENDINGS]
    [#set PRESERVE_LINE_ENDINGS = false]
[/#if]
[#if grammar.settings.JAVA_UNICODE_ESCAPE?? && grammar.settings.JAVA_UNICODE_ESCAPE]
    [#set JAVA_UNICODE_ESCAPE = true]
[/#if]


private class TokenBuilder {

    private int tokenBegin;
    private int bufpos = -1;
    private int backupAmount;
    private Reader reader;
    private StringBuilder pushBackBuffer = new StringBuilder();
    private int column, line;
    private boolean prevCharIsCR, prevCharIsLF, prevCharIsTAB;
    private char lookaheadBuffer[] = new char[8192]; // Maybe this should be adjustable but 8K should be fine. Maybe revisit...
    private int lookaheadIndex, charsReadLast;
        

    TokenBuilder(Reader reader, int startline, int startcolumn) {
        this.reader = reader;
        line = startline;
        column = startcolumn - 1;
    }
    
    TokenBuilder(String inputStream, Reader reader, int startline, int startcolumn) {
        this(reader, startline, startcolumn);
    }

    TokenBuilder(Reader reader) {
        this(reader, 1, 1);
    }

   
     public void backup(int amount) {
        backupAmount += amount;
        bufpos -= amount;
        if (bufpos  < 0) {
                throw new RuntimeException("Should never get here, I don't think!");
        } 
    }


    public String getImage() {
          StringBuilder buf = new StringBuilder();
          for (int i =tokenBegin; i<= bufpos; i++) {
              buf.append(getCharAt(i));
          }
          return buf.toString();
    }
    
    String getSuffix(final int len) {
         StringBuilder buf = new StringBuilder();
         int startPos = bufpos - len +1;
         for (int i=0; i<len; i++) {
             buf.append(getCharAt(startPos +i));
        }
        return buf.toString();
    } 

     int readChar() {
        ++bufpos;
        if (backupAmount > 0) {
           --backupAmount;
           return getCharAt(bufpos);
        }
         int ch = read();
         if (ch < 0) {
           if (bufpos >0) --bufpos;
         }
        return ch;
    }

  
    int beginToken() {
         if (backupAmount > 0) {
              --backupAmount;
            ++bufpos;
            tokenBegin = bufpos;
            return getCharAt(bufpos);
        }
        tokenBegin = 0;
        bufpos = -1;
        return readChar();
    }
    
    
   
    int getBeginColumn() {
        return getColumn(tokenBegin);
    }
    
    int getBeginLine() {
        return getLine(tokenBegin);
    }
   
    int getEndColumn() {
        return getColumn(bufpos);
    }
    
    int getEndLine() {
        return getLine(bufpos);
    }
       
   
    private int nextChar()  {

        if (lookaheadIndex<charsReadLast) {
            return lookaheadBuffer[lookaheadIndex++];
        }
        if (charsReadLast >0 && charsReadLast < 8192) {
            return -1;
        }
        try {
            charsReadLast = reader.read(lookaheadBuffer, 0, 8192);
            if (charsReadLast <= 0) {
                 return -1;
            }
        } catch (IOException ioe) {
             return -1; // Maybe handle this. REVISIT
        }
        lookaheadIndex = 0;
        return lookaheadBuffer[lookaheadIndex++];
    }

    private int read()  {
         int ch;
         int pushBack = pushBackBuffer.length();
         if (pushBack >0) {
             ch = pushBackBuffer.charAt(0);
             pushBackBuffer.deleteCharAt(0);
             updateLineColumn(ch);
             return ch;
         }
         ch = nextChar();
         if (ch <0) {
             return ch;
         }

[#if JAVA_UNICODE_ESCAPE]             
         if (ch == '\\') {
             ch = handleBackSlash();
         } else {
             lastCharWasUnicodeEscape = false;
         }
[/#if]

[#if TABS_TO_SPACES > 0]
        int tabsToSpaces = ${grammar.options.tabsToSpaces};
        if (ch == '\t') {
              ch = ' ';
              int spacesToAdd = tabsToSpaces - (column % tabsToSpaces) - 1; 
              for (int i = 0; i < spacesToAdd; i++) {
                  pushBackBuffer.append((char) ' ');
              }
        }
[/#if]

[#if !PRESERVE_LINE_ENDINGS]
     if (ch == '\r') {
        int nextChar = nextChar();
        if (nextChar >=0 && nextChar != '\n') {
            pushBackBuffer.append((char) nextChar);
        }
     }
[/#if]
         updateLineColumn(ch);
         return ch;
    }
        
    private void updateLineColumn(int c) {
        column++;
        if (prevCharIsLF || (prevCharIsCR && c!='\n')) {
            ++line;
            column = 1;
        }
        else if (prevCharIsTAB) {
           column--;
           column += (tabSize - (column % tabSize));        
        }
        
[#if JAVA_UNICODE_ESCAPE]
        if (lastCharWasUnicodeEscape) {
            column += (hexEscapeBuffer.length() -1);
        }
[/#if]        
        prevCharIsCR = (c=='\r');
        prevCharIsLF = (c=='\n');
        prevCharIsTAB = (c=='\t');
        setLocationInfo(bufpos, c, line, column);
    }

        
[#if JAVA_UNICODE_ESCAPE]
    private StringBuilder hexEscapeBuffer = new StringBuilder();
    private boolean lastCharWasUnicodeEscape;
    
    private int handleBackSlash() {
           int nextChar = nextChar();
           if (nextChar == -1) {
                return '\\';
           }
           if (nextChar != 'u') {
               pushBackBuffer.append((char) nextChar);
               lastCharWasUnicodeEscape = false;
               return '\\';
           }
           hexEscapeBuffer = new StringBuilder("\\u");
           boolean invalid = false;
           while (nextChar == 'u') {
              nextChar = nextChar();
              if (nextChar == 'u' || isHexChar(nextChar))  {
                  hexEscapeBuffer.append((char) nextChar);
              } else {
                  invalid = true;
              }
           }
          // NB: There must be 4 chars after the u and 
          // they must be valid hex chars!
           if (!invalid) for (int i =0;i<3;i++) {
               nextChar = nextChar();
               if (isHexChar(nextChar)) {
	               hexEscapeBuffer.append((char) nextChar);
               } else {
                   invalid = true;
                   break;
               }
           }
           if (!invalid) {
               lastCharWasUnicodeEscape = true;
               String hexString = hexEscapeBuffer.substring(hexEscapeBuffer.length()-4);
               return hexVal(hexString);
           }
           return -2; // REVISIT
    }
    
    private boolean isHexChar(int ch) {
        return (ch>='0' && ch<='9') || (ch>='a' && ch<='f') || (ch>='A' && ch<='F');
    }
    
    private int hexVal(String fourHexChars) {
         int result =0;
         for (int i=0; i<4; i++) {
              result <<= 4;
              int ch = fourHexChars.charAt(i);
              if (ch >= 'a') {
                  result += (10+ch-'a');
              }
              else if (ch >= 'A') {
                  result += (10+ch - 'A');
              }
              else result += (ch - '0'); 
         }
         return result;
    }
[/#if]

     private int[] locationInfoBuffer = new int[3072];
   
     private int getLine(int pos) {
         return locationInfoBuffer[pos*3+1];
     }
     
     private int getColumn(int pos) {
         return locationInfoBuffer[pos*3+2];
     }
     
     private char getCharAt(int pos) {
         return (char) locationInfoBuffer[pos*3];
     }
     
     private void setLocationInfo(int pos, int ch, int line, int column) {
          pos *=3;
          if (pos >= locationInfoBuffer.length) {
              expandBuff();
          }
          locationInfoBuffer[pos++] = ch;
          locationInfoBuffer[pos++] = line;
          locationInfoBuffer[pos++] = column;
    }
    
     private void expandBuff() {
           int[] newBuf = new int[locationInfoBuffer.length*2];
           System.arraycopy(locationInfoBuffer, 0, newBuf, 0,  locationInfoBuffer.length);
           locationInfoBuffer = newBuf;
     }
}

