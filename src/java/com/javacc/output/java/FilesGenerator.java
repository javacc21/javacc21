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
    private final Grammar grammar;
    private final CodeInjector codeInjector;
    private final Set<String> tokenSubclassFileNames = new HashSet<>();
    private final HashMap<String, String> superClassLookup = new HashMap<>();
    private final String codeLang;

    void initializeTemplateEngine() throws IOException {
        fmConfig = new freemarker.template.Configuration();
        Path filename = grammar.getFilename().toAbsolutePath();
        Path dir = filename.getParent();
        //
        // The first two loaders are really for developers - templates
        // are looked for in the grammar's directory, and then in a
        // 'templates' subdirectory below that, which could, of course, be
        // a symlink to somewhere else.
        // We check for the 'templates' subdirectory existing, because otherwise
        // FreeMarker will raise an exception.
        //
        TemplateLoader templateLoader;
        String templateFolder = "/templates/".concat(codeLang);
        Path altDir = dir.resolve(templateFolder.substring(1));
        ArrayList<TemplateLoader> loaders = new ArrayList<>();
        loaders.add(new FileTemplateLoader(dir.toFile()));
        if (Files.exists(altDir)) {
            loaders.add(new FileTemplateLoader(altDir.toFile()));
        }
        loaders.add(new ClassTemplateLoader(this.getClass(), templateFolder));
        templateLoader = new MultiTemplateLoader(loaders.toArray(new TemplateLoader[0]));

        fmConfig.setTemplateLoader(templateLoader);
        fmConfig.setObjectWrapper(new BeansWrapper());
        fmConfig.setNumberFormat("computer");
        fmConfig.setArithmeticEngine(freemarker.core.ast.ArithmeticEngine.CONSERVATIVE_ENGINE);
    }

    public FilesGenerator(Grammar grammar, String codeLang, List<Node> codeInjections) {
        this.grammar = grammar;
        this.codeLang = codeLang;
        this.codeInjector = new CodeInjector(grammar,
                                             grammar.getParserPackage(), 
                                             grammar.getNodePackage(), 
                                             codeInjections);
    }

    public void generateAll() throws IOException, TemplateException, ParseException {
        if (grammar.getErrorCount() != 0) {
            throw new ParseException();
        }
        initializeTemplateEngine();
        switch (codeLang) {
            case "java":
                generateToken();
                generateLexer();
                generateNfaData();
                generateConstantsFile();
                if (!grammar.getProductionTable().isEmpty()) {
                    generateParseException();
                    generateParser();
                }
                if (grammar.getFaultTolerant()) {
                    generateInvalidNode();
                    generateParsingProblem();
                }
                if (grammar.getTreeBuildingEnabled()) {
                    generateTreeBuildingFiles();
                }
                break;
            case "python": {
                // Hardcoded for now, could make configurable later
                String[] paths = new String[]{
                        "__init__.py",
                        "utils.py",
                        "tokens.py",
                        "lexer.py",
                        "parser.py"
                };
                Path outDir = grammar.getParserOutputDirectory();
                for (String p : paths) {
                    Path outputFile = outDir.resolve(p);
                    // Could check if regeneration is needed, but for now
                    // always (re)generate
                    generate(outputFile);
                }
                break;
            }
            case "csharp": {
                // Hardcoded for now, could make configurable later
                String[] paths = new String[]{
                        "Utils.cs",
                        "Tokens.cs",
                        "Lexer.cs",
                        "Parser.cs",
                        null  // filled in below
                };
                String csPackageName = grammar.getUtils().getPreprocessorSymbol("cs.package", grammar.getParserPackage());
                paths[paths.length - 1] = csPackageName + ".csproj";
                Path outDir = grammar.getParserOutputDirectory();
                for (String p : paths) {
                    Path outputFile = outDir.resolve(p);
                    // Could check if regeneration is needed, but for now
                    // always (re)generate
                    generate(outputFile);
                }
                break;
            }
            default:
                throw new UnsupportedOperationException(String.format("Code generation in '%s' is currently not supported.", codeLang));
        }
    }

    public void generate(Path outputFile) throws IOException, ParseException, TemplateException {
        generate(null, outputFile);
    }

    private final Set<String> nonNodeNames = new HashSet<String>() {
        {
            add("ParseException.java");
            add("ParsingProblem.java");
            add("Token.java");
            add("InvalidToken.java");
            add("Node.java");
            add("InvalidNode.java");
        }
    };

    private String getTemplateName(String outputFilename) {
        String result = outputFilename + ".ftl";

        if (codeLang.equals("java")) {
            if (tokenSubclassFileNames.contains(outputFilename)) {
                result = "ASTToken.java.ftl";
            } else if (outputFilename.equals(grammar.getParserClassName() + ".java")) {
                result = "Parser.java.ftl";
            } else if (outputFilename.equals(grammar.getConstantsClassName() + ".java")) {
                result = "Constants.java.ftl";
            } else if (outputFilename.endsWith("Lexer.java")
                    || outputFilename.equals(grammar.getLexerClassName() + ".java")) {
                result = "Lexer.java.ftl";
            } else if (outputFilename.endsWith("NfaData.java") ||
                    outputFilename.equals(grammar.getNfaDataClassName() + ".java")) {
                result = "NfaData.java.ftl";
            } else if (outputFilename.endsWith(".html")) {
                result = "doc.html.ftl";
            } else if (outputFilename.equals(grammar.getBaseNodeClassName() + ".java")) {
                result = "BaseNode.java.ftl";
            } else if (outputFilename.startsWith(grammar.getNodePrefix())) {
                if (!nonNodeNames.contains(outputFilename)) {
                    result = "ASTNode.java.ftl";
                }
            }
        }
        else if (codeLang.equals("csharp")) {
            if (outputFilename.endsWith(".csproj")) {
                result = "project.csproj.ftl";
            }
        }
        return result;
    }

    public void generate(String nodeName, Path outputFile) throws IOException, ParseException, TemplateException  {
        String currentFilename = outputFile.getFileName().toString();
        String templateName = getTemplateName(currentFilename);
        HashMap<String, Object> dataModel = new HashMap<>();
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
        Writer out = new StringWriter();
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

    void outputJavaFile(String code, Path outputFile) throws IOException {
        Path dir = outputFile.getParent();
        if (Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        CompilationUnit jcu;
        Writer out = Files.newBufferedWriter(outputFile);
        try {
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
            JavaCodeUtils.stripUnused(jcu);
//            JavaFormatter2 formatter = new JavaFormatter2();
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
    
    void generateLexer() throws IOException, ParseException, TemplateException {
        String filename = grammar.getLexerClassName() + ".java";
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
        // Changes here to allow different rules to be used for different
        // languages. At the moment there are no non-Java code injections
        String extension = codeLang.equals("java") ? ".java" : codeLang.equals("python") ? ".py" : ".cs";
        if (filename.endsWith(extension)) {
            String typename = filename.substring(0, filename.length()  - extension.length());
            if (codeInjector.hasInjectedCode(typename)) {
                return true;
            }
        }
        //
        // For now regenerate() isn't called for generating Python or C# files,
        // but I'll leave this here for the moment
        //
        return extension.equals(".py") || extension.equals(".cs");    // for now, always regenerate
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
        for (Map.Entry<String, String> es : grammar.getExtraTokens().entrySet()) {
            String value = es.getValue();
            Path outputFile = getOutputFile(value);
            files.put(value, outputFile);
            tokenSubclassFileNames.add(outputFile.getFileName().toString());
        }
        for (String nodeName : grammar.getNodeNames()) {
            if (nodeName.indexOf('.')>0) continue;
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
