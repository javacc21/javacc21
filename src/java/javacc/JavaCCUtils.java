/* Copyright (c) 2008-2019 Jonathan Revusky, revusky@javacc.com
 * Copyright (c) 2006, Sun Microsystems Inc.
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
 *       nor the names of any contributors may be used to endorse or promote
 *       products derived from this software without specific prior written
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

package javacc;

import javacc.parser.Token;

/**
 * This package contains data created as a result of parsing and semanticizing a
 * JavaCC input file. This data is what is used by the back-ends of JavaCC as
 * well as any other back-end of JavaCC related tools such as JJTree.
 */
public class JavaCCUtils {

    /**
     * This prints the banner line when the various tools are invoked. This
     * takes as argument the tool's full name and its version.
     */
    static public void bannerLine() {
        System.out.println(Main.PROG_NAME);
        System.out.println(Main.URL);
        System.out.println();
    }

    static public int ElemOccurs(int elem, int[] arr) {
        for (int i = arr.length; i-- > 0;)
            if (arr[i] == elem)
                return i;

        return -1;
    }
    
    static int hexval(char ch) {
        if (ch >= '0' && ch <= '9')
            return ((int) ch) - ((int) '0');
        if (ch >= 'A' && ch <= 'F')
            return ((int) ch) - ((int) 'A') + 10;
        return ((int) ch) - ((int) 'a') + 10;
    }

    static public String removeEscapesAndQuotes(Grammar grammar, Token t, String str) {
        String retval = "";
        int index = 1;
        char ch, ch1;
        int ordinal;
        while (index < str.length() - 1) {
            if (str.charAt(index) != '\\') {
                retval += str.charAt(index);
                index++;
                continue;
            }
            index++;
            ch = str.charAt(index);
            if (ch == 'b') {
                retval += '\b';
                index++;
                continue;
            }
            if (ch == 't') {
                retval += '\t';
                index++;
                continue;
            }
            if (ch == 'n') {
                retval += '\n';
                index++;
                continue;
            }
            if (ch == 'f') {
                retval += '\f';
                index++;
                continue;
            }
            if (ch == 'r') {
                retval += '\r';
                index++;
                continue;
            }
            if (ch == '"') {
                retval += '\"';
                index++;
                continue;
            }
            if (ch == '\'') {
                retval += '\'';
                index++;
                continue;
            }
            if (ch == '\\') {
                retval += '\\';
                index++;
                continue;
            }
            if (ch >= '0' && ch <= '7') {
                ordinal = ((int) ch) - ((int) '0');
                index++;
                ch1 = str.charAt(index);
                if (ch1 >= '0' && ch1 <= '7') {
                    ordinal = ordinal * 8 + ((int) ch1) - ((int) '0');
                    index++;
                    ch1 = str.charAt(index);
                    if (ch <= '3' && ch1 >= '0' && ch1 <= '7') {
                        ordinal = ordinal * 8 + ((int) ch1) - ((int) '0');
                        index++;
                    }
                }
                retval += (char) ordinal;
                continue;
            }
            if (ch == 'u') {
                index++;
                ch = str.charAt(index);
                if (hexchar(ch)) {
                    ordinal = hexval(ch);
                    index++;
                    ch = str.charAt(index);
                    if (hexchar(ch)) {
                        ordinal = ordinal * 16 + hexval(ch);
                        index++;
                        ch = str.charAt(index);
                        if (hexchar(ch)) {
                            ordinal = ordinal * 16 + hexval(ch);
                            index++;
                            ch = str.charAt(index);
                            if (hexchar(ch)) {
                                ordinal = ordinal * 16 + hexval(ch);
                                index++;
                                continue;
                            }
                        }
                    }
                }
                grammar.addParseError(t, "Encountered non-hex character '" + ch
                        + "' at position " + index + " of string "
                        + "- Unicode escape must have 4 hex digits after it.");
                return retval;
            }
            grammar.addParseError(t, "Illegal escape sequence '\\" + ch
                    + "' at position " + index + " of string.");
            return retval;
        }
        return retval;
    }
    
    static boolean hexchar(char ch) {
        if (ch >= '0' && ch <= '9')
            return true;
        if (ch >= 'A' && ch <= 'F')
            return true;
        if (ch >= 'a' && ch <= 'f')
            return true;
        return false;
    }
}
