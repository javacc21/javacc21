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

package com.javacc;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;
import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.Scanner;

import com.javacc.parser.ParseException;

/**
 * Entry point.
 */
public final class Main {
    
    public static final String PROG_NAME = "JavaCC 21 Parser Generator";
    public static final String URL = "Go to https://javacc.com for more information.";
    private static String manifestContent = "", jarFileName = "javacc.jar";
    private static File jarFile;
    
    static {
    	try {
    	   	Enumeration<URL> urls = Main.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
        	while(urls.hasMoreElements()) {
        		URL url = urls.nextElement();
        		InputStream is = url.openStream();
        		int available = is.available();
        		byte[] bytes = new byte[available];
        		is.read(bytes);
        		is.close();
        		String content = new String(bytes);
        		if (content.indexOf("javacc.Main") >=0) {
            		String path = url.getFile();
            		if (path.startsWith("file:")) {
            			path = path.substring(5);
            		}
            		int exclamIndex = path.lastIndexOf('!');
            		if (exclamIndex >0) {
            			path = path.substring(0, exclamIndex);
            		}
            		jarFile = new File(path);
            		jarFileName = jarFile.getName();
        			manifestContent = content;
         		    break;
        		}
        	}    		
    	}
    	catch (Exception e) {
    		//Oh well, never mind!
        }
    }
    
    static void checkForNewer() {
        if (jarFile !=null && jarFile.exists()) try {
            long jarLastModified = jarFile.lastModified();
            URL url = new URL("https://javacc.com/download/" + jarFile.getName());
            long lastUpdate = url.openConnection().getLastModified();
            if (lastUpdate > jarLastModified) {
                System.out.println("Found newer version of JavaCC 21 at " + url);
                System.out.println("Download it? (y/N)");
                Scanner scanner = new Scanner(System.in);
                String response = scanner.nextLine().trim().toLowerCase();
                if (response.equals("y") || response.equals("yes")) {
                    System.out.println("Updating jarfile...");
                    InputStream inputStream = url.openStream();
                    FileOutputStream fileOS = new FileOutputStream(jarFile);
                    byte data[] = new byte[1024];
                    int byteContent;
                    while ((byteContent = inputStream.read(data, 0, 1024)) != -1) {
                        fileOS.write(data, 0, byteContent);
                    }            
                    System.out.println("Fetched newer jarfile from server. Exiting...");
                    System.exit(-1);
                }
            }
        }
        catch (Exception e) {
            //Never mind.
        }
    }
    
    static void usage() {
        System.out.println("Usage:");
        System.out.println("    java -jar " + jarFileName + " option-settings inputfile");
        System.out.println();
        System.out.println("\"option-settings\" is a sequence of settings separated by spaces.");
        System.out.println("Each option setting must be of one of the following forms:");
        System.out.println();
        System.out.println("    -optionname=value (e.g., -IGNORE_CASE=true)");
        System.out.println("    -optionname:value (e.g., -IGNORE_CASE:true)");
        System.out.println("    -optionname       (equivalent to -optionname=true.  e.g., -TREE_BUILDING_ENABLED)");
        System.out.println("    -NOoptionname     (equivalent to -optionname=false. e.g., -NOTREE_BUILDING_ENABLED)");
        System.out.println();
        System.out.println("Option settings are not case-sensitive, so one can say \"-nOsTaTiC\" instead");
        System.out.println("of \"-NOSTATIC\".  Option values must be appropriate for the corresponding");
        System.out.println("option, and must be either an integer, a boolean, or a string value.");
        System.out.println();
        System.out.println("The integer valued options are:");
        System.out.println();
        System.out.println("    LOOKAHEAD              (default 1)");
        System.out.println("    CHOICE_AMBIGUITY_CHECK (default 2)");
        System.out.println("    OTHER_AMBIGUITY_CHECK  (default 1)");
        System.out.println("    TABS_TO_SPACES (default not set)");
        System.out.println();
        System.out.println("The boolean valued options are:");
        System.out.println();
        System.out.println("    DEBUG_PARSER           (default false)");
        System.out.println("    DEBUG_LOOKAHEAD        (default false)");
        System.out.println("    DEBUG_LEXER            (default false)");
        System.out.println("    FAULT_TOLERANT         (default false)");
        System.out.println("    FREEMARKER_NODES       (default false)");
        System.out.println("    IGNORE_CASE            (default false)");
        System.out.println("    JAVA_UNICODE_ESCAPE    (default false)");
        System.out.println("    LEGACY_API                    (default false)");
        System.out.println("    LEXER_USES_PARSER      (default false)");
        System.out.println("    NODES_USE_PARSER       (default false)");
        System.out.println("    PRESERVE_LINE_ENDINGS       (default true)");
        System.out.println("    SMART_NODE_CREATION    (default true)");
        System.out.println("    TOKENS_ARE_NODES       (default true)");
        System.out.println("    TREE_BUILDING_DEFAULT  (default true)");
        System.out.println("    TREE_BUILDING_ENABLED  (default true)");
        System.out.println("    UNPARSED_TOKENS_ARE_NODES (default false)");
        System.out.println("    USER_DEFINED_LEXER     (default false)");
        System.out.println();
        System.out.println("The string valued options are:");
        System.out.println();
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
        System.out.println();
        System.out.println("EXAMPLE:");
        System.out.println("    javacc -IGNORE_CASE=true -LOOKAHEAD:2 -debug_parser MyGrammar.javacc");
        System.out.println("");
    }

    /**
     * A main program that exercises the parser.
     */
    @SuppressWarnings("unused")
	public static void main(String[] args) throws Exception {
    	try {
    		Class<?> fmClass = Class.forName("freemarker.core.Scope");
    	}
    	catch (ClassNotFoundException e) {
    		System.err.println("You must have an appropriate (V3 or later) freemarker.jar on your classpath to run JavaCC 21");
    		System.exit(-1);
    	}
    	checkForNewer();   
        if (args.length == 0) {
            bannerLine();
            usage();
            System.exit(1);
        } 
  		int errorcode = mainProgram(args);
        System.exit(errorcode);
    }

    /**
     * The method to call to exercise the parser from other Java programs. It
     * returns an error code. See how the main program above uses this method.
     */
    public static int mainProgram(String[] args) throws Exception {
        JavaCCOptions options = new JavaCCOptions(args);
        boolean quiet = options.getQuiet();
        if (!quiet) {
        	bannerLine();
            System.out.println("(type \"java -jar javacc.jar\" with no arguments for help)\n");
        }
        String filename = args[args.length -1];
        Grammar grammar = new Grammar(options);
        grammar.parse(filename);
        try {
            grammar.createOutputDir();
            grammar.semanticize();

            if (!grammar.getOptions().getUserDefinedLexer() && grammar.getErrorCount() == 0) {
                grammar.generateLexer();
            }

            grammar.generateFiles();

            if ((grammar.getErrorCount() == 0)) {
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
    
    /**
     * This prints the banner line when the various tools are invoked. This
     * takes as argument the tool's full name and its version.
     */
    static public void bannerLine() {
    	System.out.println();
        System.out.println(Main.PROG_NAME + getBuiltOnString());
        System.out.println(Main.URL);
        System.out.println();
    }
    
    
    static private String getBuiltOnString() {
    	if (manifestContent == "") {
    		return "";
    	}
    	String buildDate = "unknown date";
    	String builtBy = "somebody";
    	StringTokenizer st = new StringTokenizer(manifestContent, ": \t\n\r", false);
    	while (st.hasMoreTokens()) {
    		String s = st.nextToken();
    		if (s.equals("Build-Date")) {
    			buildDate = st.nextToken();
    		}
    		if (s.equals("Built-By")) {
    			builtBy = st.nextToken();
    		}
    	}
    	return " (" + jarFileName + " built by " + builtBy + " on " + buildDate + ")";
    }
}
    

