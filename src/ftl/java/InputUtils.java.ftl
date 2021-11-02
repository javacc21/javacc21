[#ftl strict_vars=true]
[#--
/* Copyright (c) 2020, 2021 Jonathan Revusky, revusky@javacc.com
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
 *     * Neither the name Jonathan Revusky
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

 [#--
    This file just contains some static utility methods for reading in files,
    handling character sets and pesky little details like converting newlines,
    tabs to spaces, and Java unicode escaping. Currently it is included from
    the FileLineMap.java.ftl template
 --]
 
// Icky method to handle annoying stuff. Might make this public later if it is
// needed elsewhere
static String mungeContent(CharSequence content, int tabsToSpaces, boolean preserveLines,
        boolean javaUnicodeEscape, boolean ensureFinalEndline) {
    if (tabsToSpaces <= 0 && preserveLines && !javaUnicodeEscape) {
        if (ensureFinalEndline) {
            if (content.length() == 0) {
                content = "\n";
            } else {
                int lastChar = content.charAt(content.length()-1);
                if (lastChar != '\n' && lastChar != '\r') {
                    if (content instanceof StringBuilder) {
                        ((StringBuilder) content).append((char) '\n');
                    } else {
                        StringBuilder buf = new StringBuilder(content);
                        buf.append((char) '\n');
                        content = buf.toString();
                    }
                }
            }
        }
        return content.toString();
    }
    StringBuilder buf = new StringBuilder();
    // This is just to handle tabs to spaces. If you don't have that setting set, it
    // is really unused.
    int col = 0;
    int index = 0, contentLength = content.length();
    while (index < contentLength) {
        char ch = content.charAt(index++);
        if (ch == '\n') {
            buf.append(ch);
            ++col;
        }
        else if (javaUnicodeEscape && ch == '\\' && index<contentLength && content.charAt(index)=='u') {
            int numPrecedingSlashes = 0;
            for (int i = index-1; i>=0; i--) {
                if (content.charAt(i) == '\\') 
                    numPrecedingSlashes++;
                else break;
            }
            if (numPrecedingSlashes % 2 == 0) {
                buf.append((char) '\\');
                index++;
                continue;
            }
            int numConsecutiveUs = 0;
            for (int i = index; i<contentLength; i++) {
                if (content.charAt(i) == 'u') numConsecutiveUs++;
                else break;
            }
            String fourHexDigits = content.subSequence(index+numConsecutiveUs, index+numConsecutiveUs+4).toString();
            buf.append((char) Integer.parseInt(fourHexDigits, 16));
            index+=(numConsecutiveUs +4);
        }
        else if (!preserveLines && ch == '\r') {
            buf.append((char)'\n');
            if (index < contentLength && content.charAt(index) == '\n') {
                ++index;
                col = 0;
            }
        } else if (ch == '\t' && tabsToSpaces > 0) {
            int spacesToAdd = tabsToSpaces - col % tabsToSpaces;
            for (int i = 0; i < spacesToAdd; i++) {
                buf.append((char) ' ');
                col++;
            }
        } else {
            buf.append(ch);
            if (!Character.isLowSurrogate(ch)) col++;
        }
    }
    if (ensureFinalEndline) {
        if (buf.length() ==0) {
            return "\n";
        }
        char lastChar = buf.charAt(buf.length()-1);
        if (lastChar != '\n' && lastChar!='\r') buf.append((char) '\n');
    }
    return buf.toString();
}

static private int BUF_SIZE = 0x10000;

// Annoying kludge really...
static String readToEnd(Reader reader) {
    try {
        return readFully(reader);
    } catch (IOException ioe) {
        throw new RuntimeException(ioe);
    }
}

static String readFully(Reader reader) throws IOException {
    char[] block = new char[BUF_SIZE];
    int charsRead = reader.read(block);
    if (charsRead < 0) {
        throw new IOException("No input");
    } else if (charsRead < BUF_SIZE) {
        char[] result = new char[charsRead];
        System.arraycopy(block, 0, result, 0, charsRead);
        reader.close();
        return new String(block, 0, charsRead);
    }
    StringBuilder buf = new StringBuilder();
    buf.append(block);
    do {
        charsRead = reader.read(block);
        if (charsRead > 0) {
            buf.append(block, 0, charsRead);
        }
    } while (charsRead == BUF_SIZE);
    reader.close();
    return buf.toString();
}

/**
    * Rather bloody-minded way of converting a byte array into a string
    * taking into account the initial byte order mark (used by Microsoft a lot seemingly)
    * See: https://docs.microsoft.com/es-es/globalization/encoding/byte-order-markc
    * @param bytes the raw byte array 
    * @return A String taking into account the encoding in the byte order mark (if it was present). If no
    * byte-order mark was present, it assumes the raw input is in UTF-8.
    */
static public String stringFromBytes(byte[] bytes) {
    int arrayLength = bytes.length;
    int firstByte = arrayLength>0 ? Byte.toUnsignedInt(bytes[0]) : 1;
    int secondByte = arrayLength>1 ? Byte.toUnsignedInt(bytes[1]) : 1;
    int thirdByte = arrayLength >2 ? Byte.toUnsignedInt(bytes[2]) : 1;
    int fourthByte = arrayLength > 3 ? Byte.toUnsignedInt(bytes[3]) : 1;
    if (firstByte == 0xEF && secondByte == 0xBB && thirdByte == 0xBF) {
        return new String(bytes, 3, bytes.length-3, Charset.forName("UTF-8"));
    }
    if (firstByte == 0 && secondByte==0 && thirdByte == 0xFE && fourthByte == 0xFF) {
        return new String(bytes, 4, bytes.length-4, Charset.forName("UTF-32BE"));
    }
    if (firstByte == 0xFF && secondByte == 0xFE && thirdByte == 0 && fourthByte == 0) {
        return new String(bytes, 4, bytes.length-4, Charset.forName("UTF-32LE"));
    }
    if (firstByte == 0xFE && secondByte == 0xFF) {
        return new String(bytes, 2, bytes.length-2, Charset.forName("UTF-16BE"));
    }
    if (firstByte == 0xFF && secondByte == 0xFE) {
        return new String(bytes, 2, bytes.length-2, Charset.forName("UTF-16LE"));
    }
    return new String(bytes, Charset.forName("UTF-8"));
}