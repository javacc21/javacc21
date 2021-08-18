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

import java.io.IOException;
import java.io.Writer;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import com.javacc.Grammar;
import com.javacc.core.RegularExpression;
import com.javacc.parser.*;
import com.javacc.parser.tree.CompilationUnit;

import freemarker.template.*;
import freemarker.cache.*;
import freemarker.ext.beans.BeansWrapper;

public class FilesGenerator {

    private Configuration fmConfig;
    private Grammar grammar;
    private CodeInjector codeInjector;
    private Set<String> tokenSubclassFileNames = new HashSet<>();
    private HashMap<String, String> superClassLookup = new HashMap<>();

    void initializeTemplateEngine() throws IOException {
        fmConfig = new freemarker.template.Configuration();
        Path filename = grammar.getFilename().toAbsolutePath();
        Path dir = filename.getParent();
        TemplateLoader templateLoader = new MultiTemplateLoader(new FileTemplateLoader(dir.toFile()), 
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

    public void generateAll() throws IOException, TemplateException, ParseException {
        if (grammar.getErrorCount() != 0) {
            throw new ParseException();
        }
        initializeTemplateEngine();
        generateToken();
        generateLexer();
        if (!grammar.getUserDefinedLexer()) {
            generateNfaData();
        }
        generateConstantsFile();
        if (!grammar.getProductionTable().isEmpty()) {
            generateParseException();
            generateParser();
        }
    	if (!grammar.getHugeFileSupport()) {
    		generateFileLineMap();
    	}
    	if (grammar.getFaultTolerant()) {
    	    generateInvalidNode();
            generateParsingProblem();
    	}
        if (grammar.getTreeBuildingEnabled()) {
            generateTreeBuildingFiles();
        }
    }

    public void generate(Path outputFile) throws IOException, ParseException, TemplateException {
        generate(null, outputFile);
    }
    
    public void generate(String nodeName, Path outputFile) throws IOException, ParseException, TemplateException  {
        String currentFilename = outputFile.getFileName().toString();
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
        else if (currentFilename.endsWith("NfaData.java") || currentFilename.equals(grammar.getNfaDataClassName() +".java")) {
            templateName = "NfaData.java.ftl";
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
            System.out.println("Outputting: " + outputFile.normalize());
        }
        if (outputFile.getFileName().toString().endsWith(".java")) {
            outputJavaFile(code, outputFile);
        } else try (Writer outfile = Files.newBufferedWriter(outputFile)) {
            outfile.write(code);
        }
    }
    
    void outputJavaFile(String code, Path outputFile) throws IOException, ParseException, TemplateException {
        Path dir = outputFile.getParent();
        if (Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        CompilationUnit jcu = null;
        Writer out = null;
        try {
            out = Files.newBufferedWriter(outputFile);
            jcu = JavaCCParser.parseJavaFile(outputFile.getFileName().toString(), code);
        } catch (Exception e) {
            out.write(code);
            return;
        } finally {
            out.flush();
            out.close();
        }
        try (Writer output = Files.newBufferedWriter(outputFile)) {
            codeInjector.injectCode(jcu);
            JavaCodeUtils.removeWrongJDKElements(jcu, grammar.getJdkTarget());
            JavaCodeUtils.addGetterSetters(jcu);
  //          JavaCodeUtils.removeUnusedPrivateMethods(jcu);
  //            JavaCodeUtils.removeUnusedVariables(jcu);
            JavaFormatter formatter = new JavaFormatter();
            output.write(formatter.format(jcu));
        } 
    }
    
    void generateConstantsFile() throws IOException, ParseException, TemplateException {
        String filename = grammar.getConstantsClassName() + ".java";
        Path outputFile = grammar.getParserOutputDirectory().resolve(filename);
        generate(outputFile);
    }

    void generateParseException() throws IOException, ParseException, TemplateException {
        Path outputFile = grammar.getParserOutputDirectory().resolve("ParseException.java");
        if (regenerate(outputFile)) {
            generate(outputFile);
        }
    }
    
    void generateParsingProblem() throws IOException, ParseException, TemplateException {
        Path outputFile = grammar.getParserOutputDirectory().resolve("ParsingProblem.java");
        if (regenerate(outputFile)) {
            generate(outputFile);
        }
    }

    void generateInvalidNode() throws IOException, ParseException, TemplateException {
        Path outputFile = grammar.getParserOutputDirectory().resolve("InvalidNode.java");
        if (regenerate(outputFile)) {
            generate(outputFile);
        }
    }

    void generateToken() throws IOException, ParseException, TemplateException {
        Path outputFile = grammar.getParserOutputDirectory().resolve("Token.java");
        if (regenerate(outputFile)) {
            generate(outputFile);
        }
        outputFile = grammar.getParserOutputDirectory().resolve("InvalidToken.java");
        if (regenerate(outputFile)) {
        	generate(outputFile);
        }
    }
    
    void generateFileLineMap() throws IOException, ParseException, TemplateException {
        Path outputFile = grammar.getParserOutputDirectory().resolve("FileLineMap.java");
        if (regenerate(outputFile)) {
            generate(outputFile);
        }
    }

    void generateLexer() throws IOException, ParseException, TemplateException {
        String filename = "Lexer.java";
        if (!grammar.getUserDefinedLexer()) {
            filename = grammar.getLexerClassName() + ".java";
        }
        Path outputFile = grammar.getParserOutputDirectory().resolve(filename);
        generate(outputFile);
    }

    void generateNfaData() throws IOException, ParseException, TemplateException {
        String filename = grammar.getNfaDataClassName() + ".java";
        Path outputFile = grammar.getParserOutputDirectory().resolve(filename);
        generate(outputFile);
    }
    
    void generateParser() throws ParseException, IOException, TemplateException {
        if (grammar.getErrorCount() !=0) {
        	throw new ParseException();
        }
        String filename = grammar.getParserClassName() + ".java";
        Path outputFile = grammar.getParserOutputDirectory().resolve(filename);
        generate(outputFile);
    }
    
    void generateNodeFile() throws IOException, ParseException, TemplateException {
        Path outputFile = grammar.getParserOutputDirectory().resolve("Node.java");
        if (regenerate(outputFile)) {
            generate(outputFile);
        }
    }
    
    private boolean regenerate(Path file) throws IOException {
        if (!Files.exists(file)) {
        	return true;
        } 
        String ourName = file.getFileName().toString();
        String canonicalName = file.normalize().getFileName().toString();
       	if (canonicalName.equalsIgnoreCase(ourName) && !canonicalName.equals(ourName)) {
       		String msg = "You cannot have two files that differ only in case, as in " 
       	                          + ourName + " and "+ canonicalName 
       	                          + "\nThis does work on a case-sensitive file system but fails on a case-insensitive one (i.e. Mac/Windows)"
       	                          + " \nYou will need to rename something in your grammar!";
       		throw new IOException(msg);
        }
        String filename = file.getFileName().toString();
        if (filename.endsWith(".java")) {
            String typename = filename.substring(0, filename.length() -5);
            if (codeInjector.hasInjectedCode(typename)) {
                return true;
            }
        }
        return false;
    }
    
    void generateTreeBuildingFiles() throws IOException, ParseException, TemplateException {
    	generateNodeFile();
        Map<String, Path> files = new LinkedHashMap<>();
        files.put(grammar.getBaseNodeClassName(), getOutputFile(grammar.getBaseNodeClassName()));

        for (RegularExpression re : grammar.getOrderedNamedTokens()) {
            if (re.isPrivate()) continue;
            String tokenClassName = re.getGeneratedClassName();
            Path outputFile = getOutputFile(tokenClassName);
            files.put(tokenClassName, outputFile);
            tokenSubclassFileNames.add(outputFile.getFileName().toString());
            String superClassName = re.getGeneratedSuperClassName();
            if (superClassName != null) {
                outputFile = getOutputFile(superClassName);
                files.put(superClassName, outputFile);
                tokenSubclassFileNames.add(outputFile.getFileName().toString());
                superClassLookup.put(tokenClassName, superClassName);
            }
        }
        for (String nodeName : grammar.getNodeNames()) {
            Path outputFile = getOutputFile(nodeName);
            if (tokenSubclassFileNames.contains(outputFile.getFileName().toString())) {
                String name = outputFile.getFileName().toString();
                name = name.substring(0, name.length() -5);
                grammar.addError("The name " + name + " is already used as a Token subclass.");
            }
            files.put(nodeName, outputFile);
        }
        for (Map.Entry<String, Path> entry : files.entrySet()) {
            if (regenerate(entry.getValue())) {
                generate(entry.getKey(), entry.getValue());
            }
        }
    }

    // only used for tree-building files (a bit kludgy)
    private Path getOutputFile(String nodeName) throws IOException {
        if (nodeName.equals(grammar.getBaseNodeClassName())) {
            return grammar.getParserOutputDirectory().resolve(nodeName + ".java");
        }
        String className = grammar.getNodeClassName(nodeName);
        //KLUDGE
        if (nodeName.equals(grammar.getBaseNodeClassName())) {
            className = nodeName;
        }
        String explicitlyDeclaredPackage = codeInjector.getExplicitlyDeclaredPackage(className);
        if (explicitlyDeclaredPackage == null) {
            return grammar.getNodeOutputDirectory().resolve(className + ".java");
        }
        String sourceBase = grammar.getBaseSourceDirectory();
        if (sourceBase.equals("")) {
            return grammar.getNodeOutputDirectory().resolve(className + ".java");
        }
        Path result = Paths.get(sourceBase);
        result = result.resolve(explicitlyDeclaredPackage.replace('.', '/'));
        return result.resolve(className + ".java");
    }
}
