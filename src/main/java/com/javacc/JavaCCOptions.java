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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.javacc.JavaCCError.ErrorCode;
import com.javacc.parser.Node;


public class JavaCCOptions {

    private Grammar grammar;

    public JavaCCOptions(String[] args) {
        init();
        if (args[0].equalsIgnoreCase("convert")) {
            return;
        }
        String filename = args[args.length -1];
        if (isOption(filename)) {
            throw new IllegalArgumentException("Last argument \"" + filename + " is not a filename.");
        }
        for (int i=0; i<args.length -1; i++) {
            String arg = args[i];
            if (!isOption(arg)) {
                throw new IllegalArgumentException("Argument \"" + arg + "\" must be an option setting.");
            }
            setCmdLineOption(arg);
        }
    }

    public JavaCCOptions(Grammar grammar) {
        this.grammar = grammar;
        init();
    }

    void setGrammar(Grammar grammar) {
        this.grammar = grammar;
    }

    /**
     * A mapping of option names (Strings) to values (Integer, Boolean, String).
     * This table is initialized by the main program. Its contents defines the
     * set of legal options. Its initial values define the default option
     * values, and the option types can be determined from these values too.
     */
    private Map<String, Object> optionValues = new HashMap<String, Object>();

    private Set<String> noLongerSetOnCL = new HashSet<>();
    
    private Map<String, String> aliases = new HashMap<String, String>();

    /**
     * Convenience method to retrieve integer options.
     */
    protected int intValue(final String option) {
        Object value = optionValues.get(option);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof String) {
            return Integer.parseInt(value.toString());
        }
        return ((Integer) optionValues.get(option)).intValue();
    }
    /**
     * Convenience method to retrieve boolean options.
     */
    protected boolean booleanValue(final String option) {
        Object value = optionValues.get(option);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value.equals("true") || value.equals("yes") || value.equals("on")) {
            return true;
        }
        if (value.equals("false") || value.equals("no") || value.equals("off")) {
            return false;
        }
        throw new IllegalArgumentException("The option " + option + " is supposed to be a boolean (true/false) value.");
    }

    /**
     * Convenience method to retrieve string options.
     */
    protected String stringValue(final String option) {
        return (String) optionValues.get(option);
    }

    /**
     * Keep track of what options were set as a command line argument. We use
     * this to see if the options set from the command line and the ones set in
     * the input files clash in any way.
     */
    private Set<String> cmdLineSetting = new HashSet<String>();

    /**
     * Keep track of what options were set from the grammar file. We use this to
     * see if the options set from the command line and the ones set in the
     * input files clash in any way.
     */
    private Set<String> inputFileSetting = new HashSet<String>();

    /**
     * Initialize
     */
    public void init() {
        optionValues = new HashMap<String, Object>();
        optionValues.put("QUIET",  false);
        optionValues.put("TABS_TO_SPACES",  0);
        noLongerSetOnCL.add("TABS_TO_SPACES");
        optionValues.put("DEBUG_PARSER", false);
        optionValues.put("DEBUG_LEXER", false);
        optionValues.put("FAULT_TOLERANT", false);
        optionValues.put("PRESERVE_LINE_ENDINGS",  true); // Will change this to false pretty soon.
        noLongerSetOnCL.add("PRESERVE_LINE_ENDINGS");
        optionValues.put("JAVA_UNICODE_ESCAPE", false);
        noLongerSetOnCL.add("JAVA_UNICODE_ESCAPE");
        optionValues.put("IGNORE_CASE", false);
        noLongerSetOnCL.add("IGNORE_CASE");
        optionValues.put("USER_DEFINED_LEXER", false);
        noLongerSetOnCL.add("USER_DEFINED_LEXER");
        optionValues.put("LEXER_USES_PARSER", false);
        noLongerSetOnCL.add("LEXER_USES_PARSER");

        optionValues.put("PARSER_PACKAGE", "");
        noLongerSetOnCL.add("PARSER_PACKAGE");
        optionValues.put("PARSER_CLASS", "");
        noLongerSetOnCL.add("PARSER_CLASS");
        optionValues.put("LEXER_CLASS", "");
        noLongerSetOnCL.add("LEXER_CLASS");
        optionValues.put("CONSTANTS_CLASS", "");
        noLongerSetOnCL.add("CONSTANTS_CLASS");
	    optionValues.put("BASE_SRC_DIR", ".");
        optionValues.put("BASE_NODE_CLASS", "BaseNode");
        optionValues.put("TOKEN_FACTORY", "");
        noLongerSetOnCL.add("TOKEN_FACTORY");

        optionValues.put("NODE_DEFAULT_VOID", false);
        noLongerSetOnCL.add("NODE_DEFAULT_VOID");
        optionValues.put("SMART_NODE_CREATION", true);
        noLongerSetOnCL.add("NODE_DEFAULT_VOID");
        optionValues.put("NODE_USES_PARSER", false);
        noLongerSetOnCL.add("NODE_USES_PARSER");

        optionValues.put("NODE_PREFIX", "");
        noLongerSetOnCL.add("NODE_PREFIX");
        optionValues.put("NODE_CLASS", "");
        noLongerSetOnCL.add("NODE_CLASS");
        optionValues.put("NODE_FACTORY", false);
        noLongerSetOnCL.add("NODE_FACTORY");
        optionValues.put("NODE_PACKAGE", "");
        optionValues.put("TREE_BUILDING_DEFAULT", true);
        noLongerSetOnCL.add("TREE_BUILDING_DEFAULT");
        optionValues.put("TREE_BUILDING_ENABLED", true);
        noLongerSetOnCL.add("TREE_BUILDING_ENABLED");
        optionValues.put("TOKENS_ARE_NODES", true);
        noLongerSetOnCL.add("TOKENS_ARE_NODES");
        optionValues.put("UNPARSED_TOKENS_ARE_NODES", false);
        noLongerSetOnCL.add("UNPARSED_TOKENS_ARE_NODES");
        optionValues.put("FREEMARKER_NODES", false);
        noLongerSetOnCL.add("FREEMARKER_NODES");
        optionValues.put("DEFAULT_LEXICAL_STATE", "DEFAULT");
        optionValues.put("HUGE_FILE_SUPPORT", false);
        noLongerSetOnCL.add("HUGE_FILE_SUPPORT");
        optionValues.put("LEGACY_API", false);
        noLongerSetOnCL.add("LEGACY_API");
        aliases.put("DEBUG_TOKEN_MANAGER", "DEBUG_LEXER");
        aliases.put("USER_TOKEN_MANAGER", "USER_DEFINED_LEXER");
        aliases.put("TOKEN_MANAGER_USES_PARSER", "LEXER_USES_PARSER");
        aliases.put("NODE_CLASS", "BASE_NODE_CLASS");
        aliases.put("SPECIAL_TOKENS_ARE_NODES","UNPARSED_TOKENS_ARE_NODES");
        aliases.put("OUTPUT_DIRECTORY", "BASE_SRC_DIR");
        aliases.put("D", "BASE_SRC_DIR");
        aliases.put("Q",  "QUIET");
    }
    
    public void setOption(String name, Object value) {
        if (aliases.containsKey(name.toUpperCase())) {
            name = aliases.get(name.toUpperCase());
        }
        optionValues.put(name, value);
    }

    /**
     * Determine if a given command line argument might be an option flag.
     * Command line options start with a dash&nbsp;(-).
     *
     * @param opt
     *            The command line argument to examine.
     * @return True when the argument looks like an option flag.
     */
    static public boolean isOption(final String opt) {
        return opt != null && opt.length() > 1 && opt.charAt(0) == '-';
    }

    /**
     * Help function to handle cases where the meaning of an option has changed
     * over time. If the user has supplied an option in the old format, it will
     * be converted to the new format.
     *
     * @param name
     *            The name of the option being checked.
     * @param value
     *            The option's value.
     * @return The upgraded value.
     */
    public Object upgradeValue(final String name, Object value) {
        if (name.equalsIgnoreCase("NODE_FACTORY") && value instanceof Boolean) if ((Boolean) value) {
            value = "*";
        } else {
            value = "";
        }
        return value;
    }

    public void setInputFileOption(Object nameloc, Object valueloc, String name, Object value, boolean inInclude) {
        String s = name.toUpperCase();
        
        if (inInclude) {
        	if (s.equalsIgnoreCase("DEFAULT_LEXICAL_STATE")) {
        		grammar.setDefaultLexicalState((String) value);
            }
            // For now, we just ignore any other options if we are within a INCLUDE processing.
            // This will be revisited later.
            return;
        }
        Node node = (nameloc instanceof Node) ? (Node) nameloc : null;

        if (s.equalsIgnoreCase("STATIC")) {
            grammar.addWarning(node, "In JavaCC 21, the STATIC option is superfluous. All parsers are non-static. Option setting will be ignored.");
            return;
        }
        else if (s.equalsIgnoreCase("LOOKAHEAD")) {
            grammar.addWarning(node, "In JavaCC 21, the LOOKAHEAD option has been removed. It is always 1 but you can override that at key points in your grammar.");
            return;
        }
	    else if (s.equalsIgnoreCase("NODE_FACTORY")) {
            grammar.addWarning(node, "In JavaCC 21, the NODE_FACTORY option from JJTree has been removed. Option setting will be ignored.");
            return;
        }
	    else if (s.equalsIgnoreCase("MULTI")) {
            grammar.addWarning(node, "In JavaCC 21, the MULTI option from JJTree has been removed. Effectively, it is always true. Option setting will be ignored.");
            return;
        }
        else if (s.equalsIgnoreCase("KEEP_LINE_COLUMN")) {
            grammar.addWarning(node, "In JavaCC 21, the KEEP_LINE_COLUMN option is superfluous. Location info is always retained. Option setting will be ignored.");
            return;
        }
        else if (s.equalsIgnoreCase("COMMON_TOKEN_ACTION")) {
            grammar.addWarning(node, "In JavaCC 21, the COMMON_TOKEN_ACTION option is superfluous. If your Lexer class contains a CommonTokenAction(Token t) method it is used. Option setting will be ignored.");
            return;
        }
        else if (s.equalsIgnoreCase("NODE_SCOPE_HOOK")) {
            grammar.addWarning(node, "In JavaCC 21, the NODE_SCOPE_HOOK option is superfluous. If your Parser class contains either or both of openNodeScopeHook(Node n) and closeNodeScopeHook(Node n)," +
            		"then calls to those methods will be inserted at the appropriate locations. This option setting will be ignored.");
            return;
        }
        else if (s.equalsIgnoreCase("UNICODE_INPUT")) {
            grammar.addWarning(node, "In JavaCC 21, all input to a parser is assumed to be unicode. The UNICODE_INPUT option is now superfluous.");
            return;
        }
        else if (s.equalsIgnoreCase("TRACK_TOKENS")) {
            grammar.addWarning(node, "The JJTree option TRACK_TOKENS is ignored because it is basically obsolete in JavaCC 21.");
            return;
        }
        else if (s.equalsIgnoreCase("NODE_EXTENDS")) {
            grammar.addWarning(node, "The NODE_EXTENDS option is obsolete and unsupported. You can simply use code injection instead.");
            return;
        }
        else if (!optionValues.containsKey(s) && !aliases.containsKey(s)) {
        	// Unrecognized Option warning
            grammar.addWarning(node, ErrorCode.UnrecognizedOption, name);
            return;
        }
        final Object existingValue = optionValues.get(s);

        if (existingValue != null) {
            if ((existingValue.getClass() != value.getClass())
                    || (value instanceof Integer && ((Integer) value).intValue() <= 0)) {
                node = (valueloc instanceof Node) ? (Node) valueloc : null;
				// Option value type mismatch warning
				grammar.addWarning(node, ErrorCode.OptionValueTypeMismatch, value, name,
						value.getClass().getSimpleName());
                return;
            }

            if (inputFileSetting.contains(s)) {
            	// Duplicate option warning
                grammar.addWarning(node, ErrorCode.DuplicateOption, name);
                return;
            }

            if (cmdLineSetting.contains(s)) {
                if (!existingValue.equals(value)) {
                    node = (valueloc instanceof Node) ? (Node) valueloc
                            : null;
                    grammar.addWarning(node, "Command line setting of \"" + name
                            + "\" modifies option value in file.");
                }
                return;
            }
        }

        setOption(s, value);
        inputFileSetting.add(s);
    }


   /**
     * Process a single command-line option. The option is parsed and stored in
     * the optionValues map.
     *
     * @param arg
     */
    public void setCmdLineOption(String arg) {
        String s = arg;
        if (arg.charAt(0) == '-') {
            s = arg.substring(1);
        } 
        
        if (s.equalsIgnoreCase("quiet")||s.equalsIgnoreCase("q")) {
        	this.setOption("QUIET", true);
        	return;
        }

        s = s.replaceFirst("=", ":");
        String name;
        Object Val;

        // Look for the first ":" or "=", which will separate the option name
        // from its value (if any).
        int index = s.indexOf(':');

        if (index < 0) {
            name = s.toUpperCase();
            if (optionValues.containsKey(name)) {
                Val = Boolean.TRUE;
            } else if (name.length() > 2 && name.charAt(0) == 'N' && name.charAt(1) == 'O') {
                Val = Boolean.FALSE;
                name = name.substring(2);
            } else {
                System.out.println("Warning: Bad option \"" + arg + "\" will be ignored.");
                return;
            }
        } else {
            name = s.substring(0, index).toUpperCase();
            if (s.substring(index + 1).equalsIgnoreCase("TRUE")) {
                Val = Boolean.TRUE;
            } else if (s.substring(index + 1).equalsIgnoreCase("FALSE")) {
                Val = Boolean.FALSE;
            } else {
                Val = s.substring(index + 1);
                if (s.length() > index + 2) {
                    // i.e., there is space for two '"'s in value
                    if (s.charAt(index + 1) == '"' && s.charAt(s.length() - 1) == '"') {
                        // remove the two '"'s.
                        Val = s.substring(index + 2, s.length() - 1);
                    }
                }
            }
        }
        String alias = aliases.get(name.toUpperCase());
        if (alias !=null) name = alias;
        if (!optionValues.containsKey(name)) {
            System.out.println("Warning: Bad option \"" + arg + "\" will be ignored.");
            return;
        }
        if (noLongerSetOnCL.contains(name)) {
            System.out.println("The option " + name + " can now only be set in the grammar file.");
            return;
        }
        Object valOrig = optionValues.get(name);
        if (Val.getClass() != valOrig.getClass()) {
            System.out.println("Warning: Bad option value in \"" + arg + "\" will be ignored.");
            return;
        }
        if (cmdLineSetting.contains(name)) {
            System.out
                    .println("Warning: Duplicate option setting \"" + arg + "\" will be ignored.");
            return;
        }

        Val = upgradeValue(name, Val);

        setOption(name, Val);
        cmdLineSetting.add(name);
    }

    public void normalize() {
        grammar.setParserPackage(stringValue("PARSER_PACKAGE"));
        grammar.setParserClassName(stringValue("PARSER_CLASS"));
        grammar.setLexerClassName(stringValue("LEXER_CLASS"));
        grammar.setConstantsClassName(stringValue("CONSTANTS_CLASS"));
        grammar.setBaseNodeClassName(stringValue("BASE_NODE_CLASS"));
        grammar.setDefaultLexicalState(stringValue("DEFAULT_LEXICAL_STATE"));
    }

    /**
     * Find the debug parser value.
     *
     * @return The requested debug parser value.
     */
    public boolean getDebugParser() {
        return booleanValue("DEBUG_PARSER");
    }

    /**
     * Find the debug tokenmanager value.
     *
     * @return The requested debug tokenmanager value.
     */
    public boolean getDebugLexer() {
        return booleanValue("DEBUG_LEXER");
    }

    /**
     * Find the Java unicode escape value.
     *
     * @return The requested Java unicode escape value.
     */
    public boolean getJavaUnicodeEscape() {
        return booleanValue("JAVA_UNICODE_ESCAPE");
    }

    /**
     * Find the ignore case value.
     *
     * @return The requested ignore case value.
     */
    public boolean getIgnoreCase() {
        return booleanValue("IGNORE_CASE");
    }

    /**
     * Find the user tokenmanager value.
     *
     * @return The requested user tokenmanager value.
     */
    public boolean getUserDefinedLexer() {
        return booleanValue("USER_DEFINED_LEXER");
    }

    public boolean getLegacyAPI() {
        return booleanValue("LEGACY_API");
    }

    /**
     * the LEXER_USES_PARSER setting
     */
    public boolean getLexerUsesParser() {
        return booleanValue("LEXER_USES_PARSER");
    }

    /**
     * @return a CSS file to use for outputting HTML docs
     */
    public String getCSS() {
        return stringValue("CSS");
    }

    /**
     * Return the Token's factory class.
     *
     * @return The required factory class for Token.
     */
    public String getTokenFactory() {
        return stringValue("TOKEN_FACTORY");
    }

    /**
     * Find the output directory.
     *
     * @return The requested output directory.
     *//*
    public String getOutputDirectory() {
        return stringValue("OUTPUT_DIRECTORY");
    }*/
    
    public boolean getQuiet() {
    	return booleanValue("QUIET");
    }
    
    public boolean getPreserveLineEndings() {
    	return booleanValue("PRESERVE_LINE_ENDINGS");
    }
    
    public int getTabsToSpaces() {
    	return intValue("TABS_TO_SPACES");
    }

    public boolean getFaultTolerant() {
        return booleanValue("FAULT_TOLERANT");
    }
    
    public boolean getHugeFileSupport() {
    	return booleanValue("HUGE_FILE_SUPPORT") && !getTreeBuildingEnabled() && !getFaultTolerant();
    }

    /**
     * Find the node default void value.
     *
     * @return The requested node default void value.
     */
    public boolean getNodeDefaultVoid() {
        return booleanValue("NODE_DEFAULT_VOID");
    }

    public boolean getSmartNodeCreation() {
        return booleanValue("SMART_NODE_CREATION");
    }

    /**
     * Find the node uses parser value.
     *
     * @return The requested node uses parser value.
     */
    public boolean getNodeUsesParser() {
        return booleanValue("NODE_USES_PARSER");
    }

    /**
     * Find the node prefix value.
     *
     * @return The requested node prefix value.
     */
    public String getNodePrefix() {
        return stringValue("NODE_PREFIX");
    }

    public String getNodePackage() {
        return stringValue("NODE_PACKAGE");
    }

    public String getParserPackage() {
        return stringValue("PARSER_PACKAGE");
    }

    public boolean getTreeBuildingEnabled() {
        return booleanValue("TREE_BUILDING_ENABLED");
    }

    public boolean getTreeBuildingDefault() {
        return booleanValue("TREE_BUILDING_DEFAULT") && getTreeBuildingEnabled();
    }

    public boolean getTokensAreNodes() {
        return booleanValue("TOKENS_ARE_NODES");
    }

    public boolean getUnparsedTokensAreNodes() {
        return booleanValue("UNPARSED_TOKENS_ARE_NODES");
    }

    public boolean getFreemarkerNodes() {
        return booleanValue("FREEMARKER_NODES");
    }

    public String getDefaultLexicalState() {
        return this.stringValue("DEFAULT_LEXICAL_STATE");
    }

    public String getBaseSourceDirectory() {
        return stringValue("BASE_SRC_DIR");
    }


    /**
     * Some warnings if incompatible options are set.
     */
    public void sanityCheck() {
        boolean nodePackageDefined = getNodePackage().length() >0;

        if (!getTreeBuildingEnabled()) {
            String msg = "You have specified the OPTION_NAME option but it is "
                + "meaningless unless the TREE_BUILDING_ENABLED is set to true."
                + " This option will be ignored.\n";
            if (nodePackageDefined) {
                grammar.addWarning(null, msg.replace("OPTION_NAME", "NODE_PACKAGE"));
            }
            if (getTokensAreNodes()) {
                grammar.addWarning(null, msg.replace("OPTION_NAME", "TOKENS_ARE_NODES"));
            }
            if (getUnparsedTokensAreNodes()) {
                grammar.addWarning(null, msg.replace("OPTION_NAME", "UNPARSED_TOKENS_ARE_NODES"));
            }
            if (getSmartNodeCreation()) {
                grammar.addWarning(null, msg.replace("OPTION_NAME", "SMART_NODE_CREATION"));
            }
            if (getNodeDefaultVoid()) {
                grammar.addWarning(null, msg.replace("OPTION_NAME", "NODE_DEFAULT_VOID"));
            }
            if (getNodeUsesParser()) {
                grammar.addWarning(null, msg.replace("OPTION_NAME", "NODE_USES_PARSER"));
            }
        }
        if (booleanValue("HUGE_FILE_SUPPORT")) {
            if (booleanValue("TREE_BUILDING_ENABLED")) {
                grammar.addWarning(null, "HUGE_FILE_SUPPORT setting is ignored because TREE_BUILDING_ENABLED is set.");
            }
            if (booleanValue("FAULT_TOLERANT")) {
                grammar.addWarning(null, "HUGE_FILE_SUPPORT setting is ignored because FAULT_TOLERANT is set.");
            }
        }
    }
}
