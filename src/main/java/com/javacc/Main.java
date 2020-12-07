/* Copyright (c) 2008-2020 Jonathan Revusky, revusky@javacc.com
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
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
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(1000);
            long lastUpdate = connection.getLastModified();
            if (lastUpdate > jarLastModified) {
                System.out.println("Found newer version of JavaCC 21 at " + url);
                System.out.println("Download it? (y/N)");
                Scanner scanner = new Scanner(System.in);
                String response = scanner.nextLine().trim().toLowerCase();
                if (response.equals("y") || response.equals("yes")) {
                    boolean renamedFileSuccessfully = false;
                    String oldFileName =jarFile.getName().replace("javacc",  "javacc-" + System.currentTimeMillis());
                    File oldFile = new File(jarFile.getParentFile(), oldFileName);
                    try {
                        renamedFileSuccessfully = jarFile.renameTo(oldFile);
                    } catch (Exception e) {
                        System.out.println("Failed to save older version of jarfile");
                        System.out.println("Possibly directory " + oldFile.getParent() + " is not writeable.");
                        scanner.close();
                        return;
                    }
                    System.out.println("Updating jarfile...");
                    InputStream inputStream = url.openStream();
                    FileOutputStream fileOS = new FileOutputStream(jarFile);
                    byte data[] = new byte[1024];
                    int byteContent;
                    while ((byteContent = inputStream.read(data, 0, 1024)) != -1) {
                        fileOS.write(data, 0, byteContent);
                    }            
                    fileOS.close();
                    scanner.close();
                    System.out.println("Fetched newer jarfile from server.");
                    if (renamedFileSuccessfully) System.out.println("Older jarfile is at: " + oldFile);
                    System.out.println("Exiting...");
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
        System.out.println("NB: Most option-settings now must be set from the grammar file.");
        System.out.println("The ones which can still be set from the command line are:");
        System.out.println();
        System.out.println("    -DEBUG_PARSER");
        System.out.println("    -DEBUG_LEXER");
        System.out.println("    -FAULT_TOLERANT");
        System.out.println();
        System.out.println("By default, source files are generated relative to the location of the input file.");
        System.out.println("This can be changed by setting as follows:");
        System.out.println();
        System.out.println("-d:\"../../src/generated\"");
        System.out.println();
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
        if (args.length == 0) {
            bannerLine();
            usage();
            System.exit(1);
        } 
    	checkForNewer();   
        if (args[0].equalsIgnoreCase("convert")) {
            com.javacc.output.lint.SyntaxConverter.main(args);
            System.exit(0);
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
        grammar.parse(filename, true);
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
    

