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

import javacc.parser.ParseException;

/**
 * Entry point.
 */
public final class Main {
    
    public static final String PROG_NAME = "JavaCC 21 Parser Generator";
    public static final String VERSION = "20.02.21";
    public static final String URL = "Go to https://javacc.com for more information.";
  
    
    private Main() {
    }

    static void usage() {
        System.out.println("Usage:");
        System.out.println("    java -jar javacc.jar option-settings inputfile");
        System.out.println("");
        System.out.println("\"option-settings\" is a sequence of settings separated by spaces.");
        System.out.println("Each option setting must be of one of the following forms:");
        System.out.println("");
        System.out.println("    -optionname=value (e.g., -IGNORE_CASE=true)");
        System.out.println("    -optionname:value (e.g., -IGNORE_CASE:true)");
        System.out.println("    -optionname       (equivalent to -optionname=true.  e.g., -TREE_BUILDING_ENABLED)");
        System.out.println("    -NOoptionname     (equivalent to -optionname=false. e.g., -NOTREE_BUILDING_ENABLED)");
        System.out.println("");
        System.out.println("Option settings are not case-sensitive, so one can say \"-nOsTaTiC\" instead");
        System.out.println("of \"-NOSTATIC\".  Option values must be appropriate for the corresponding");
        System.out.println("option, and must be either an integer, a boolean, or a string value.");
        System.out.println("");
        System.out.println("The integer valued options are:");
        System.out.println("");
        System.out.println("    LOOKAHEAD              (default 1)");
        System.out.println("    CHOICE_AMBIGUITY_CHECK (default 2)");
        System.out.println("    OTHER_AMBIGUITY_CHECK  (default 1)");
        System.out.println("");
        System.out.println("The boolean valued options are:");
        System.out.println("");
        System.out.println("    BUILD_PARSER           (default true)");
        System.out.println("    BUILD_LEXER            (default true)");
        System.out.println("    DEBUG_PARSER           (default false)");
        System.out.println("    DEBUG_LOOKAHEAD        (default false)");
        System.out.println("    DEBUG_LEXER            (default false)");
        System.out.println("    ERROR_REPORTING        (default true)");
        System.out.println("    FREEMARKER_NODES       (default false)");
        System.out.println("    IGNORE_CASE            (default false)");
        System.out.println("    JAVA_UNICODE_ESCAPE    (default false)");
        System.out.println("    LEXER_USES_PARSER      (default false)");
        System.out.println("    NODES_USE_PARSER       (default false)");
        System.out.println("    SPECIAL_TOKENS_ARE_NODES (default false)");
        System.out.println("    SMART_NODE_CREATION    (default true)");
        System.out.println("    TOKENS_ARE_NODES       (default true)");
        System.out.println("    TREE_BUILDING_DEFAULT  (default true)");
        System.out.println("    TREE_BUILDING_ENABLED  (default true)");
        System.out.println("    USER_CHAR_STREAM       (default false)");
        System.out.println("    USER_DEFINED_LEXER     (default false)");
        System.out.println();
        System.out.println("The string valued options are:");
        System.out.println("");
        System.out.println("    BASE_SRC_DIR           (default same directory as input file)");
        System.out.println("    DEFAULT_LEXICAL_STATE  (default DEFAULT)");
        System.out.println("    LEXER_CLASS            (default XXXLexer based on grammar filename)");
        System.out.println("    NODE_PACKAGE           (default not defined)");
        System.out.println("    NODE_CLASS             (default BaseNode)");
        System.out.println("    NODE_PREFIX            (default empty string)");
        System.out.println("    OUTPUT_DIRECTORY       (default Current Directory)");
        System.out.println("    PARSER_CLASS           (default XXXParser based on grammar filename)");
        System.out.println("    PARSER_PACKAGE         (default not defined)");
        System.out.println("    TOKEN_FACTORY          (default not defined)");
        System.out.println("");
        System.out.println("EXAMPLE:");
        System.out.println("    javacc -IGNORE_CASE=true -LOOKAHEAD:2 -debug_parser MyGrammar.javacc");
        System.out.println("");
    }

    /**
     * A main program that exercises the parser.
     */
    public static void main(String args[]) throws Exception {
    	try {
    		Class fmClass = Class.forName("freemarker.core.Scope");
    	}
    	catch (ClassNotFoundException e) {
    		System.err.println("You must have an appropriate (V3 or later) freemarker.jar on your classpath to run JavaCC 21");
    		System.exit(-1);
    	}
  		int errorcode = mainProgram(args);
        System.exit(errorcode);
    }

    /**
     * The method to call to exercise the parser from other Java programs. It
     * returns an error code. See how the main program above uses this method.
     */
    public static int mainProgram(String args[]) throws Exception {
        if (args.length == 0) {
            JavaCCUtils.bannerLine();
            usage();
            return 1;
        } 
        JavaCCOptions options = new JavaCCOptions(args);
        boolean quiet = options.getQuiet();
        if (!quiet) {
            System.out.println("(type \"java -jar javacc.jar\" with no arguments for help)");
        	JavaCCUtils.bannerLine();
        }
        String filename = args[args.length -1];
        Grammar grammar = new Grammar(options);
        grammar.parse(filename);
        try {
            grammar.createOutputDir();
            grammar.semanticize();

            if (grammar.getOptions().getBuildLexer() && !grammar.getOptions().getUserDefinedLexer()
                    && grammar.getErrorCount() == 0) {
                grammar.generateLexer();
            }

            grammar.generateFiles();

            if ((grammar.getErrorCount() == 0)
                    && (grammar.getOptions().getBuildParser() || grammar.getOptions().getBuildLexer())) {
                if (grammar.getWarningCount() == 0) {
                    System.out.println("Parser generated successfully.");
                } else {
                    System.out
                            .println("Parser generated with 0 errors and " + grammar.getWarningCount() + " warnings.");
                }
                return 0;
            } else {
                System.out.println("Detected " + grammar.getErrorCount() + " errors and " + grammar.getWarningCount()
                        + " warnings.");
                return (grammar.getErrorCount() == 0) ? 0 : 1;
            }
        } catch (MetaParseException e) {
            System.out.println("Detected " + grammar.getErrorCount() + " errors and " + grammar.getWarningCount()
                    + " warnings.");
            return 1;
        } catch (ParseException e) {
            System.out.println(e.toString());
            System.out.println("Detected " + (grammar.getErrorCount() + 1) + " errors and " + grammar.getWarningCount()
                    + " warnings.");
            return 1;
        }
    }

}
