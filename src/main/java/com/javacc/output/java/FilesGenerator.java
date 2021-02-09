/* Copyright (c) 2008-2021 Jonathan Revusky, revusky@javacc.com
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
 *     * Neither the name Jonathan Revusky nor the names of any contributors 
 *       may be used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
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

package com.javacc.output.java;

import java.io.*;
import java.util.*;

import com.javacc.Grammar;
import com.javacc.MetaParseException;
import com.javacc.parsegen.RegularExpression;
import com.javacc.parser.*;
import com.javacc.parser.tree.CompilationUnit;

import freemarker.template.*;
import freemarker.cache.*;
import freemarker.ext.beans.BeansWrapper;

public class FilesGenerator {

    private Configuration fmConfig;
    private Grammar grammar;
    private String currentFilename;
    private CodeInjector codeInjector;
    private Set<String> tokenSubclassFileNames = new HashSet<>();
    private HashMap<String, String> superClassLookup = new HashMap<>();

    void initializeTemplateEngine() throws IOException {
        fmConfig = new freemarker.template.Configuration();
        String filename = grammar.getFilename();
        File dir = new File(filename).getCanonicalFile().getParentFile();
        TemplateLoader templateLoader = new MultiTemplateLoader(new FileTemplateLoader(dir), 
                                                                new ClassTemplateLoader(this.getClass(), "/templates/java"));
        fmConfig.setTemplateLoader(templateLoader);
        fmConfig.setObjectWrapper(new BeansWrapper());
        fmConfig.setNumberFormat("computer");
        fmConfig.setArithmeticEngine(freemarker.core.ast.ArithmeticEngine.CONSERVATIVE_ENGINE);
    }

    public FilesGenerator(Grammar grammar, List<Node> codeInjections) {
        this.grammar = grammar;
        this.codeInjector = new CodeInjector(grammar.getParserClassName(), 
                                             grammar.getLexerClassName(),
                                             grammar.getConstantsClassName(), 
                                             grammar.getBaseNodeClassName(),
                                             grammar.getParserPackage(), 
                                             grammar.getNodePackage(), 
                                             codeInjections);
    }

    public void generateAll() throws IOException, TemplateException, MetaParseException {
        if (grammar.getErrorCount() != 0) {
            throw new MetaParseException();
        }
        initializeTemplateEngine();
        generateToken();
        generateLexer();
        generateConstantsFile();
        if (!grammar.getProductionTable().isEmpty()) {
            generateParseException();
            generateParser();
        }
        if (grammar.getTreeBuildingEnabled()) {
            generateTreeBuildingFiles();
        }
    	if (!grammar.getHugeFileSupport()) {
    		generateFileLineMap();
    	}
    	if (grammar.getFaultTolerant()) {
    	    generateInvalidNode();
    	}
        
    }

    public void generate(File outputFile) throws IOException, TemplateException {
        generate(null, outputFile);
    }
    
    public void generate(String nodeName, File outputFile) throws IOException, TemplateException  {
        this.currentFilename = outputFile.getName();
        String templateName = currentFilename + ".ftl";
        if (tokenSubclassFileNames.contains(currentFilename)) {
                templateName = "ASTToken.java.ftl";
        }
        else if (currentFilename.equals(grammar.getParserClassName() + ".java")) {
            templateName = "Parser.java.ftl";
        }
        else if (currentFilename.equals(grammar.getConstantsClassName() + ".java")) {
            templateName = "Constants.java.ftl";
        }
        else if (currentFilename.endsWith("Lexer.java")
                 || currentFilename.equals(grammar.getLexerClassName() + ".java")) {
            templateName = "Lexer.java.ftl";
        }
        else if (currentFilename.endsWith(".html")) {
            templateName = "doc.html.ftl";
        }
        else if (currentFilename.equals(grammar.getBaseNodeClassName() + ".java")) {
            templateName = "BaseNode.java.ftl";
        }
        else if (currentFilename.startsWith(grammar.getNodePrefix())) {
            if (!(currentFilename.equals("ParseException.java") 
                    || currentFilename.equals("ParsingProblem.java")
                    || currentFilename.equals("Token.java")
                    || currentFilename.equals("Node.java")
            		|| currentFilename.equals("InvalidToken.java")
            		|| currentFilename.equals("FileLineMap.java")
                    || currentFilename.equals("InvalidNode.java")))
            {
                    templateName = "ASTNode.java.ftl";
            }
        }
        HashMap<String, Object> dataModel = new HashMap<String, Object>();
        dataModel.put("grammar", grammar);
        dataModel.put("filename", currentFilename);
        dataModel.put("isAbstract", grammar.nodeIsAbstract(nodeName));
        dataModel.put("isInterface", grammar.nodeIsInterface(nodeName));
        dataModel.put("generated_by", com.javacc.Main.PROG_NAME);
        String classname = currentFilename.substring(0, currentFilename.length() - 5);
        String superClassName = superClassLookup.get(classname);
        if (superClassName == null) superClassName = "Token";
        dataModel.put("superclass", superClassName);
        if (codeInjector.getExplicitlyDeclaredPackage(classname) != null) {
            dataModel.put("explicitPackageName", codeInjector.getExplicitlyDeclaredPackage(classname));
        }
        Writer out = null;
        out = new StringWriter();
        Template template = fmConfig.getTemplate(templateName);
        template.process(dataModel, out);
        String code = out.toString();
        if (!grammar.isQuiet()) {
            System.out.println("Outputting: " + outputFile.getAbsolutePath());
        }
        if (outputFile.getName().endsWith(".java")) {
            outputJavaFile(code, outputFile);
        } else {
            FileWriter outfile = new FileWriter(outputFile);
            try {
                outfile.write(code);
            } 
            finally {
                outfile.close();
            }
        }
    }
    
    void outputJavaFile(String code, File outputFile) throws IOException, TemplateException {
        File dir = outputFile.getParentFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        FileWriter out = new FileWriter(outputFile);
        CompilationUnit jcu = null;
        try {
            jcu = JavaCCParser.parseJavaFile(outputFile.getName(), code);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                out.write(code);
            } finally {
                out.flush();
                out.close();
            }
            return;
        }
        codeInjector.injectCode(jcu);
        try {
            JavaCodeUtils.removeWrongJDKElements(jcu, grammar.getJdkTarget());
            JavaCodeUtils.addGetterSetters(jcu);
            JavaCodeUtils.removeUnusedPrivateMethods(jcu);
            JavaCodeUtils.removeUnusedVariables(jcu);
            JavaFormatter formatter = new JavaFormatter();
            out.write(formatter.format(jcu));
        } finally {
            out.close();
        }
    }
    
    void generateConstantsFile() throws IOException, TemplateException {
        String filename = grammar.getConstantsClassName() + ".java";
        File outputFile = new File(grammar.getParserOutputDirectory(), filename);
        generate(outputFile);
    }

    void generateParseException() throws IOException, TemplateException {
        File outputFile = new File(grammar.getParserOutputDirectory(), "ParseException.java");
        if (regenerate(outputFile)) {
            generate(outputFile);
        }
    }
    
    void generateParsingProblem() throws IOException, TemplateException {
        File outputFile = new File(grammar.getParserOutputDirectory(), "ParsingProblem.java");
        if (regenerate(outputFile)) {
            generate(outputFile);
        }
    }

    void generateInvalidNode() throws IOException, TemplateException {
        File outputFile = new File(grammar.getParserOutputDirectory(), "InvalidNode.java");
        if (regenerate(outputFile)) {
            generate(outputFile);
        }
    }

    void generateToken() throws IOException, TemplateException {
        File outputFile = new File(grammar.getParserOutputDirectory(), "Token.java");
        if (regenerate(outputFile)) {
            generate(outputFile);
        }
        outputFile = new File(grammar.getParserOutputDirectory(), "InvalidToken.java");
        if (regenerate(outputFile)) {
        	generate(outputFile);
        }
    }
    
    void generateFileLineMap() throws IOException, TemplateException {
        File outputFile = new File(grammar.getParserOutputDirectory(), "FileLineMap.java");
        if (regenerate(outputFile)) {
            generate(outputFile);
        }
    }

    void generateLexer() throws IOException, TemplateException {
        String filename = "Lexer.java";
        if (!grammar.getUserDefinedLexer()) {
            filename = grammar.getLexerClassName() + ".java";
        }
        File outputFile = new File(grammar.getParserOutputDirectory(), filename);
        generate(outputFile);
    }
    
    void generateParser() throws MetaParseException, IOException, TemplateException {
        if (grammar.getErrorCount() !=0) {
        	throw new MetaParseException();
        }
        String filename = grammar.getParserClassName() + ".java";
        File outputFile = new File(grammar.getParserOutputDirectory(), filename);
        generate(outputFile);
    }
    
    void generateNodeFile() throws IOException, TemplateException {
        File outputFile = new File(grammar.getParserOutputDirectory(), "Node.java");
        if (regenerate(outputFile)) {
            generate(outputFile);
        }
    }
    
    private boolean regenerate(File file) throws IOException {
        if (!file.exists()) {
        	return true;
        } 
        String ourName = file.getName();
        String canonicalName = file.getCanonicalFile().getName();
       	if (canonicalName.equalsIgnoreCase(ourName) && !canonicalName.equals(ourName)) {
       		String msg = "You cannot have two files that differ only in case, as in " 
       	                          + ourName + " and "+ canonicalName 
       	                          + "\nThis does work on a case-sensitive file system but fails on a case-insensitive one (i.e. Mac/Windows)"
       	                          + " \nYou will need to rename something in your grammar!";
       		throw new IOException(msg);
        }
        String filename = file.getName();
        if (filename.endsWith(".java")) {
            String typename = filename.substring(0, filename.length() -5);
            if (codeInjector.hasInjectedCode(typename)) {
                return true;
            }
        }
        return false;
    }
    
    void generateTreeBuildingFiles() throws IOException, TemplateException {
    	generateNodeFile();
        Map<String, File> files = new LinkedHashMap<>();
        files.put(grammar.getBaseNodeClassName(), getOutputFile(grammar.getBaseNodeClassName()));

        for (RegularExpression re : grammar.getOrderedNamedTokens()) {
            if (re.isPrivate()) continue;
            String tokenClassName = re.getGeneratedClassName();
            File outputFile = getOutputFile(tokenClassName);
            files.put(tokenClassName, outputFile);
            tokenSubclassFileNames.add(outputFile.getName());
            String superClassName = re.getGeneratedSuperClassName();
            if (superClassName != null) {
                outputFile = getOutputFile(superClassName);
                files.put(superClassName, outputFile);
                tokenSubclassFileNames.add(outputFile.getName());
                superClassLookup.put(tokenClassName, superClassName);
            }
        }
        for (String nodeName : grammar.getNodeNames()) {
            File outputFile = getOutputFile(nodeName);
            if (tokenSubclassFileNames.contains(outputFile.getName())) {
                String name = outputFile.getName();
                name = name.substring(0, name.length() -5);
                grammar.addSemanticError(null, "The name " + name + " is already used as a Token subclass.");
            }
            files.put(nodeName, outputFile);
        }
        for (Map.Entry<String, File> entry : files.entrySet()) {
            if (regenerate(entry.getValue())) {
                generate(entry.getKey(), entry.getValue());
            }
        }
    }

    // only used for tree-building files (a bit kludgy)
    private File getOutputFile(String nodeName) throws IOException {
        if (nodeName.equals(grammar.getBaseNodeClassName())) {
            return new File(grammar.getParserOutputDirectory(), nodeName + ".java");
        }
        String className = grammar.getNodeClassName(nodeName);
        //KLUDGE
        if (nodeName.equals(grammar.getBaseNodeClassName())) {
            className = nodeName;
        }
        String explicitlyDeclaredPackage = codeInjector.getExplicitlyDeclaredPackage(className);
        if (explicitlyDeclaredPackage == null) {
            return new File(grammar.getNodeOutputDirectory(), className + ".java");            
        }
        String sourceBase = grammar.getBaseSourceDirectory();
        if (sourceBase.equals("")) {
            return new File(grammar.getNodeOutputDirectory(), className + ".java");
        }
        return new File(new File(sourceBase, explicitlyDeclaredPackage.replace('.', '/')), className + ".java"); 
    }
}
