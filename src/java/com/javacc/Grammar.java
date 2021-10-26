/* Copyright (c) 2008-2021 Jonathan Revusky, revusky@javacc.com
 * Copyright (c) 2006, Sun Microsystems Inc.
 * Copyright (c) 2021 Vinay Sajip, vinay_sajip@yahoo.co.uk - changes for Python support.
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

import java.util.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.javacc.core.Expansion;
import com.javacc.core.LexerData;
import com.javacc.core.RegularExpression;
import com.javacc.core.SanityChecker;
import com.javacc.output.Sequencer;
import com.javacc.output.java.FilesGenerator;
import com.javacc.output.java.CodeInjector;
import com.javacc.output.Translator;
import com.javacc.parser.*;
import com.javacc.parser.tree.*;

import freemarker.template.TemplateException;

/**
 * This object is the root Node of the data structure that contains all the
 * information regarding a JavaCC processing job.
 */
public class Grammar extends BaseNode {
    private String parserClassName,
                   lexerClassName,
                   parserPackage,
                   constantsClassName,
                   baseNodeClassName,
                   defaultLexicalState;
    private Path filename;
    private Map<String, Object> settings = new HashMap<>();
    private CompilationUnit parserCode;
    private LexerData lexerData = new LexerData(this);
    private int includeNesting;

    private List<TokenProduction> tokenProductions = new ArrayList<>();

    private Map<String, BNFProduction> productionTable;
    private Map<String, RegularExpression> namedTokensTable = new LinkedHashMap<>();
    private Map<String, String> tokenNamesToConstName = new HashMap<>();
    private Set<String> lexicalStates = new LinkedHashSet<>();
    private Map<String, String> preprocessorSymbols = new HashMap<>();
    private Map<Integer, String> tokenNames = new HashMap<>();
    private Set<String> nodeNames = new LinkedHashSet<>();
    private Map<String,String> nodeClassNames = new HashMap<>();
    // TODO use these later for Nodes that correspond to abstract
    // classes or interfaces
    private Set<String> abstractNodeNames = new HashSet<>();
    private Set<String> interfaceNodeNames = new HashSet<>();
    private Map<String, String> nodePackageNames = new HashMap<>();
    private Set<String> usedIdentifiers = new HashSet<>();
    private List<Node> codeInjections = new ArrayList<>();
    private List<String> lexerTokenHooks = new ArrayList<>(),
                         parserTokenHooks = new ArrayList<>(),
                         openNodeScopeHooks = new ArrayList<>(),
                         closeNodeScopeHooks = new ArrayList<>();
    private Map<String, List<String>> closeNodeHooksByClass = new HashMap<>();

    private Set<Path> alreadyIncluded = new HashSet<>();

    private Path includedFileDirectory;

    private List<String> tokensOffByDefault = new ArrayList<>();

    private List<String> extraTokens = new ArrayList<>();

    private Set<RegexpStringLiteral> stringLiteralsToResolve = new HashSet<>();

    List<String> errorMessages = new ArrayList<>(), warningMessages = new ArrayList<>();

	private int parseErrorCount;
	private int semanticErrorCount;
    private int warningCount;

    private Path outputDir;
    private boolean quiet;
    private String codeLang;
    private Translator translator;

    public Grammar(Path outputDir, String codeLang, int jdkTarget, boolean quiet, Map<String, String> preprocessorSymbols) {
        this.outputDir = outputDir;
        this.codeLang = codeLang;
        this.jdkTarget = jdkTarget;
        this.quiet = quiet;
        this.preprocessorSymbols = preprocessorSymbols;
    }

    public Grammar() {}

    public boolean isQuiet() {return quiet;}

    public String getCodeLang() { return codeLang; }

    public String[] getLexicalStates() {
        return lexicalStates.toArray(new String[]{});
    }

    public void addInplaceRegexp(RegularExpression regexp) {
        if (regexp instanceof RegexpStringLiteral) {
            stringLiteralsToResolve.add((RegexpStringLiteral) regexp);
        }
        TokenProduction tp = new TokenProduction();
        tp.setGrammar(this);
        tp.setExplicit(false);
        tp.setImplicitLexicalState(getDefaultLexicalState());
        addChild(tp);
        addTokenProduction(tp);
        RegexpSpec res = new RegexpSpec();
        res.addChild(regexp);
        tp.addChild(res);
    }

    private void resolveStringLiterals() {
        for (RegexpStringLiteral stringLiteral : stringLiteralsToResolve) {
            String label = lexerData.getStringLiteralLabel(stringLiteral.getImage());
            stringLiteral.setLabel(label);
        }
    }

    private String separatorString() {
        // Temporary solution. Use capital sigma for Python, for now
        return codeLang.equals("java") ? "$": "\u03A3";
    }

    public String generateIdentifierPrefix(String basePrefix) {
        return basePrefix + separatorString();
    }

    public String generateUniqueIdentifier(String prefix, Node exp) {
        String inputSource = exp.getInputSource();
        String sep = separatorString();

        if (inputSource != null) {
            int lastSlash = Math.max(inputSource.lastIndexOf('\\'), inputSource.lastIndexOf('/'));
            if (lastSlash+1<inputSource.length()) inputSource = inputSource.substring(lastSlash+1);
        } else {
            inputSource = "";
        }
        String id = prefix + inputSource + sep + exp.getBeginLine() + sep + exp.getBeginColumn();
        id = removeNonJavaIdentifierPart(id);
        while (usedIdentifiers.contains(id)) {
            id += sep;
        }
        usedIdentifiers.add(id);
        return id;
    }

    public Node parse(Path file, boolean enterIncludes) throws IOException, ParseException {
        Path canonicalPath = file.normalize();
        if (alreadyIncluded.contains(canonicalPath)) return null;
        else alreadyIncluded.add(canonicalPath);
        JavaCCParser parser = new JavaCCParser(this, canonicalPath, preprocessorSymbols);
        parser.setEnterIncludes(enterIncludes);
        Path prevIncludedFileDirectory = includedFileDirectory;
        if (!isInInclude()) {
            setFilename(file);
        } else {
            includedFileDirectory = canonicalPath.getParent();
        }
        GrammarFile rootNode = parser.Root();
        includedFileDirectory = prevIncludedFileDirectory;
        if (!isInInclude()) {
            addChild(rootNode);
        }
        return rootNode;
    }

    private Path resolveLocation(List<String> locations) {
        for (String location : locations) {
            Path path = resolveLocation(location);
            if (path != null) return path;
        }
        return null;
    }

    private Path resolveLocation(String location) {
        Path path = Paths.get(location);
        if (Files.exists(path)) return path;
        if (!path.isAbsolute()) {
            path = filename.getParent();
            if (path == null) {
                path = Paths.get(".");
            }
            path = path.resolve(location);
            if (Files.exists(path)) return path;
        }
        if (includedFileDirectory != null) {
            path = includedFileDirectory.resolve(location);
            if (Files.exists(path)) return path;
        }
        if (LexerData.isJavaIdentifier(location)) {
            location = resolveAlias(location);
        }
        URI uri = null;
        try {
            uri = getClass().getResource(location).toURI();
        } catch (Exception e) {
            return null;
        }
        try {
            return Paths.get(uri);
        } catch (FileSystemNotFoundException fsne) {
           try {
               FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
               return fs.getPath(location);
           }
           catch (Exception e) {
               e.printStackTrace();
           }
        }
        return null;
    }

    private static Map<String, String> locationAliases = new HashMap<String, String>() {
        {
            put("JAVA_IDENTIFIER_DEF", "/include/java/JavaIdentifierDef.javacc");
            put("JAVA_LEXER", "/include/java/JavaLexer.javacc");
            put("JAVA", "/include/java/Java.javacc");
            put("PYTHON_IDENTIFIER_DEF", "/include/python/PythonIdentifierDef.javacc");
            put("PYTHON_LEXER", "/include/python/PythonLexer.javacc");
            put("PYTHON", "/include/python/Python.javacc");
        }
    };

    private String resolveAlias(String location) {
        return locationAliases.getOrDefault(location, location);
    }

    public Node include(List<String> locations, Node includeLocation) throws IOException, ParseException {
        Path path = resolveLocation(locations);
        if (path == null) {
            addError(includeLocation, "Could not resolve location of include file");
            throw new FileNotFoundException(includeLocation.getLocation());
        }
        String location = path.toString();
        if (location.toLowerCase().endsWith(".java") || location.toLowerCase().endsWith(".jav")) {
            Path includeFile = Paths.get(location);
            String content = new String(Files.readAllBytes(path),Charset.forName("UTF-8"));
            CompilationUnit cu = JavaCCParser.parseJavaFile(includeFile.normalize().toString(), content);
            codeInjections.add(cu);
            return cu;
        } else {
            Path prevLocation = this.filename;
            String prevDefaultLexicalState = this.defaultLexicalState;
            boolean prevIgnoreCase = this.ignoreCase;
            includeNesting++;
            Node root = parse(path, true);
            if (root==null) return null;
            includeNesting--;
            setFilename(prevLocation);
            this.defaultLexicalState = prevDefaultLexicalState;
            this.ignoreCase = prevIgnoreCase;
            return root;
        }
    }

    public void createOutputDir() {
        Path outputDir = Paths.get(".");
        if (!Files.isWritable(outputDir)) {
            addError(null, "Cannot write to the output directory : \"" + outputDir + "\"");
        }
    }

    /**
     * The grammar file being processed.
     */
    public Path getFilename() {
        return filename;
    }

    public void setFilename(Path filename) {
        this.filename = filename;
    }

    public void generateLexer() {
        lexerData.buildData();
    }


    public void doSanityChecks() {
        if (defaultLexicalState == null) {
            setDefaultLexicalState("DEFAULT");
        }
        for (String lexicalState : lexicalStates) {
            lexerData.addLexicalState(lexicalState);
        }
        new SanityChecker(this).doChecks();
        if (getErrorCount() > 0) {
            return;
        }
        lexerData.ensureStringLabels();
        resolveStringLiterals();
    }

    public void generateFiles() throws ParseException, IOException, TemplateException {
        translator = Translator.getTranslatorFor(this);
        new FilesGenerator(this, codeLang, codeInjections).generateAll();
    }

    public LexerData getLexerData() {
        return lexerData;
    }

    public String getConstantsClassName() {
        if (constantsClassName == null) {
            constantsClassName = (String) settings.get("CONSTANTS_CLASS");
        }
        if (constantsClassName == null) {
            constantsClassName = getParserClassName();
            if (constantsClassName.toLowerCase().endsWith("parser")) {
                constantsClassName = constantsClassName.substring(0, constantsClassName.length() -6);
            }
            constantsClassName += "Constants";
        }
        return constantsClassName;
    }

    public String getParserClassName() {
        if (parserClassName ==null) {
            parserClassName = (String) settings.get("PARSER_CLASS");
        }
        if (parserClassName == null) {
            String name = filename.getFileName().toString();
            int lastDot = name.lastIndexOf('.');
            if (lastDot >0) {
                name = name.substring(0, lastDot);
            }
            if (!name.toLowerCase().endsWith("parser")) {
                name += "Parser";
            }
            if (Character.isLowerCase(name.charAt(0))) {
                name = name.substring(0, 1).toUpperCase() + name.substring(1);
            }
            parserClassName = name;
        }
        return parserClassName;
    }

    public void setParserClassName(String parserClassName) {
        this.parserClassName = parserClassName;
    }

    public String getBaseNodeClassName() {
        if (baseNodeClassName == null) {
            baseNodeClassName = (String) settings.get("BASE_NODE_CLASS");
        }
        if (baseNodeClassName == null) {
            baseNodeClassName = "BaseNode";
        }
        return baseNodeClassName;
    }

    public String getLexerClassName() {
        if (lexerClassName == null) {
            lexerClassName = (String) settings.get("LEXER_CLASS");
        }
        if (lexerClassName == null) {
            lexerClassName = getParserClassName();
            if (lexerClassName.toLowerCase().endsWith("parser")) {
                lexerClassName = lexerClassName.substring(0, lexerClassName.length() - 6);
            }
            if (!lexerClassName.toLowerCase().endsWith("lexer")) {
                lexerClassName += "Lexer";
            }
        }
        return lexerClassName;
    }

    public String getNfaDataClassName() {
        String lexerClassName = getLexerClassName();
        if (lexerClassName.contains("Lexer")) {
            return lexerClassName.replace("Lexer", "NfaData");
        }
        return lexerClassName + "NfaData";
    }

    public String getDefaultLexicalState() {
        return this.defaultLexicalState;
    }

    public void setDefaultLexicalState(String defaultLexicalState) {
        this.defaultLexicalState = defaultLexicalState;
        addLexicalState(defaultLexicalState);
    }

    public CodeInjector getInjector() {
        return new CodeInjector(parserClassName, lexerClassName,
                                constantsClassName, baseNodeClassName,
                                parserPackage, getNodePackage(),
                                codeInjections);
    }

    public List<Node> getOtherParserCodeDeclarations() {
        List<Node> result = new ArrayList<Node>();
        if (parserCode != null) {
            for (Node child : parserCode.children()) {
                if (child instanceof Token) {
                    continue;
                }
                if (child instanceof PackageDeclaration || child instanceof ImportDeclaration) {
                    continue;
                }
                if (child instanceof TypeDeclaration) {
                    if (((TypeDeclaration) child).getName().equals(parserClassName)) {
                        continue;
                    }
                }
                result.add(child);
            }
        }
        return result;
    }

    public List<ImportDeclaration> getParserCodeImports() {
        return parserCode == null ? Collections.emptyList(): parserCode.childrenOfType(ImportDeclaration.class);
    }

    public void setParserCode(CompilationUnit parserCode) {
        this.parserCode = parserCode;
        parserPackage = (String) settings.get("PARSER_PACKAGE");
        String specifiedPackageName = parserCode.getPackageName();
        if (specifiedPackageName != null && specifiedPackageName.length() >0) {
            if (parserPackage != null) {
                if (!parserPackage.equals(specifiedPackageName)) {
                    String msg = "PARSER_PACKAGE was specified in the options directory as " + parserPackage + " but is specified in the PARSER_BEGIN/PARSER_END section as " + specifiedPackageName +".";
                    addError(null, msg);
                }
            }
            parserPackage = specifiedPackageName;
        }
        addCodeInjection(parserCode);
    }

    public void setParserPackage(String parserPackage) {
        this.parserPackage = parserPackage;
    }

    public Collection<BNFProduction> getParserProductions() {
        List<BNFProduction> productions = descendantsOfType(BNFProduction.class);
        LinkedHashMap<String, BNFProduction> map = new LinkedHashMap<>();
        for (BNFProduction production: productions) {
            map.put(production.getName(), production);
        }
        return map.values();
    }

    /**
     * @return a List containing all the expansions that are at a choice point
     */
    public List<Expansion> getChoicePointExpansions() {
        return descendants(Expansion.class, Expansion::isAtChoicePoint);
    }

    public List<Expansion> getAssertionExpansions() {
        return descendants(Expansion.class, exp->exp.getParent() instanceof Assertion);
    }

    public List<Expansion> getExpansionsNeedingPredicate() {
        return descendants(Expansion.class, Expansion::getRequiresPredicateMethod);
    }

    public List<Expansion> getExpansionsNeedingRecoverMethod() {
        Set<String> alreadyAdded = new HashSet<>();
        List<Expansion> result = new ArrayList<>();
        for (Expansion exp : descendants(Expansion.class, Expansion::getRequiresRecoverMethod)) {
            String methodName = exp.getRecoverMethodName();
            if (!alreadyAdded.contains(methodName)) {
                result.add(exp);
                alreadyAdded.add(methodName);
            }
        }
        return result;
    }

    public List<String> getLexerTokenHooks() {
        return lexerTokenHooks;
    }

    public List<String> getParserTokenHooks() {
        return parserTokenHooks;
    }

    public List<String> getOpenNodeScopeHooks() {
        return openNodeScopeHooks;
    }

    public List<String> getCloseNodeScopeHooks() {
        return closeNodeScopeHooks;
    }

    public Map<String, List<String>> getCloseNodeHooksByClass() {
        return closeNodeHooksByClass;
    }

    private List<String> getCloseNodeScopeHooks(String className) {
        List<String> result = closeNodeHooksByClass.get(className);
        if (result == null) {
            result = new ArrayList<>();
            closeNodeHooksByClass.put(className, result);
        }
        return result;
    }


    /**
     * A symbol table of all grammar productions.
     */
    public Map<String, BNFProduction> getProductionTable() {
        if (productionTable == null) {
            productionTable = new LinkedHashMap<>();
            for (BNFProduction production : descendants(BNFProduction.class )) {
                productionTable.put(production.getName(), production);
            }
        }
        return productionTable;
    }

    public BNFProduction getProductionByName(String name) {
        return getProductionTable().get(name);
    }

    /**
     * Add a new lexical state
     */
    public void addLexicalState(String name) {
        if (!lexicalStates.contains(name)) lexicalStates.add(name);
    }

    public List<Expansion> getExpansionsForFirstSet() {
        return getExpansionsForSet(0);
    }

    public List<Expansion> getExpansionsForFinalSet() {
        return getExpansionsForSet(1);
    }

    public List<Expansion> getExpansionsForFollowSet() {
        return getExpansionsForSet(2);
    }

    private List<Expansion> getExpansionsForSet(int type) {
        HashSet<String> usedNames = new HashSet<>();
        List<Expansion> result = new ArrayList<>();
        for (Expansion expansion : descendants(Expansion.class)) {
            if (expansion.getParent() instanceof BNFProduction) continue; // Handle these separately
            String varName = null;
            if (type==0) {
                varName = expansion.getFirstSetVarName();
            } else if (type ==1) {
                varName = expansion.getFinalSetVarName();
            } else {
                varName = expansion.getFollowSetVarName();
            }
            if (!usedNames.contains(varName)) {
                result.add(expansion);
                usedNames.add(varName);
            }
        }
        return result;
    }

    /**
     * The list of all TokenProductions from the input file. This list includes
     * implicit TokenProductions that are created for uses of regular
     * expressions within BNF productions.
     */
    public List<TokenProduction> getAllTokenProductions() {
        return tokenProductions;
    }

    public List<Lookahead> getAllLookaheads() {
        return this.descendants(Lookahead.class);
    }

    public List<LookBehind> getAllLookBehinds() {
        return this.descendants(LookBehind.class);
    }

    public void addTokenProduction(TokenProduction tp) {
        tokenProductions.add(tp);
    }

    /**
     * This is a symbol table that contains all named tokens (those that are
     * defined with a label). The index to the table is the image of the label
     * and the contents of the table are of type "RegularExpression".
     */
    public RegularExpression getNamedToken(String name) {
        return namedTokensTable.get(name);
    }

    public RegularExpression addNamedToken(String name, RegularExpression regexp) {
        RegularExpression alreadyThere = namedTokensTable.get(name);
        if (alreadyThere != null) {
            return alreadyThere;
        }
        namedTokensTable.put(name, regexp);
        return null;
    }

    public boolean hasTokenOfName(String name) {
        return namedTokensTable.containsKey(name);
    }

    /**
     * Contains the same entries as "named_tokens_table", but this is an ordered
     * list which is ordered by the order of appearance in the input file.
     * (Actually, the only place where this is used is in generating the
     * XXXConstants.java file)
     */
    public List<RegularExpression> getOrderedNamedTokens() {
        return new ArrayList<RegularExpression>(namedTokensTable.values());
    }

    /**
     * A mapping of ordinal values (represented as objects of type "Integer") to
     * the corresponding labels (of type "String"). An entry exists for an
     * ordinal value only if there is a labeled token corresponding to this
     * entry. If there are multiple labels representing the same ordinal value,
     * then only one label is stored.
     */

    public String getTokenName(int index) {
        String tokenName = tokenNames.get(index);
        return tokenName == null ? String.valueOf(index) : tokenName;
    }

    public String classNameFromTokenName(String tokenName) {
        if (Character.isDigit(tokenName.charAt(0))) {
            return null;
        }
        if (namedTokensTable.get(tokenName).isPrivate()) {
            return null;
        }
        tokenNamesToConstName.put(tokenName, tokenName);
        return tokenName;
    }

    public String constNameFromClassName(String className) {
        return this.tokenNamesToConstName.get(className);
    }

    public void addTokenName(int index, String name) {
        tokenNames.put(index, name);
    }

	/**
	 * Returns the warning count during grammar parsing.
	 *
	 * @return the warning count during grammar parsing.
	 */
	public int getWarningCount() {
		return warningCount;
	}

	/**
	 * Returns the parse error count during grammar parsing.
	 *
	 * @return the parse error count during grammar parsing.
	 */
	public int getParseErrorCount() {
		return parseErrorCount;
	}

	/**
	 * Returns the semantic error count during grammar parsing.
	 *
	 * @return the semantic error count during grammar parsing.
	 */
	public int getSemanticErrorCount() {
		return semanticErrorCount;
	}

    public void addError(String errorMessage) {
        errorMessages.add(errorMessage);
    }

    public void addError(Node location, String errorMessage) {
        errorMessages.add("Error: " + location.getLocation() + ":" + errorMessage);
    }

    public void addWarning(String warningMessage) {
        warningMessages.add(warningMessage);
    }

    public void addWarning(Node location, String warningMessage) {
        String message = "Warning: " + location.getLocation() + ":" + warningMessage;
        warningMessages.add(message);
    }

	/**
	 * @return the total error count during grammar parsing.
	 */
	public int getErrorCount() {
        return errorMessages.size();
	}

    public Set<String> getNodeNames() {
        return nodeNames;
    }

    public String getNodePrefix() {
        String nodePrefix = (String) settings.get("NODE_PREFIX");
        if (nodePrefix == null) nodePrefix = "";
        return nodePrefix;
    }

    public void addNodeType(String productionName, String nodeName) {
        if (nodeName.equals("void")) {
            return;
        }
        if (nodeName.equals("abstract")) {
            abstractNodeNames.add(productionName);
            nodeName = productionName;
        }
        else if (nodeName.equals("interface")) {
            interfaceNodeNames.add(productionName);
            nodeName = productionName;
        }
        nodeNames.add(nodeName);
        nodeClassNames.put(nodeName, getNodePrefix() + nodeName);
        nodePackageNames.put(nodeName, getNodePackage());
    }

    public boolean nodeIsInterface(String nodeName) {
        return interfaceNodeNames.contains(nodeName);
    }

    public boolean nodeIsAbstract(String nodeName) {
        return abstractNodeNames.contains(nodeName);
    }

    public String getNodeClassName(String nodeName) {
        String className = nodeClassNames.get(nodeName);
        if (className ==null) {
            return getNodePrefix() + nodeName;
        }
        return className;
    }

    public String getNodePackageName(String nodeName) {
        return nodePackageNames.get(nodeName);
    }

    // A bit kludgy
    // Also, this code doesn't really belong in this class, I don't think.
    private void checkForHooks(Node node, String className) {
        if (node == null || null instanceof Token) {
            return;
        }
        else if (node instanceof TokenManagerDecls) {
            ClassOrInterfaceBody body = node.childrenOfType(ClassOrInterfaceBody.class).get(0);
            checkForHooks(body, getLexerClassName());
        }
        else if (node instanceof ParserCodeDecls) {
            List<CompilationUnit> cus = node.childrenOfType(CompilationUnit.class);
            if (!cus.isEmpty()) {
                checkForHooks(cus.get(0), getParserClassName());
            }
        }
        else if (node instanceof CodeInjection) {
            CodeInjection ci = (CodeInjection) node;
            if (ci.name.equals(getLexerClassName())) {
                checkForHooks(ci.body, lexerClassName);
            }
            else if (ci.name.equals(getParserClassName())) {
                checkForHooks(ci.body, parserClassName);
            }
        }
        else if (node instanceof TypeDeclaration) {
            TypeDeclaration typeDecl = (TypeDeclaration) node;
            String typeName = typeDecl.getName();
            if (typeName.equals(getLexerClassName()) || typeName.endsWith("." +lexerClassName)) {
                for (Iterator<Node> it = typeDecl.iterator(); it.hasNext();) {
                    checkForHooks(it.next(), lexerClassName);
                }
            }
            else if (typeName.equals(getParserClassName()) || typeName.endsWith("." + parserClassName)) {
                for (Iterator<Node> it = typeDecl.iterator(); it.hasNext();) {
                    checkForHooks(it.next(), parserClassName);
                }
            }
        }
        else if (node instanceof MethodDeclaration) {
            MethodDeclaration decl = (MethodDeclaration) node;
            String sig = decl.getFullSignature();
            if (sig != null) {
                String methodName = new StringTokenizer(sig, "(\n ").nextToken();
                if (className.equals(lexerClassName)) {
                    String prefix = generateIdentifierPrefix("tokenHook");
                    if (methodName.startsWith(prefix) || methodName.equals("tokenHook") || methodName.equals("CommonTokenAction")) {
                        lexerTokenHooks.add(methodName);
                    }
                }
                else if (className.equals(parserClassName)) {
                    if (methodName.startsWith("tokenHook$")) {
                        parserTokenHooks.add(methodName);
                    }
                    else if (methodName.equals("jjtreeOpenNodeScope") || methodName.startsWith("openNodeScopeHook")) {
                        openNodeScopeHooks.add(methodName);
                    }
                    else if (methodName.equals("jjtreeCloseNodeScope") || methodName.startsWith("closeNodeScopeHook")) {
                        closeNodeScopeHooks.add(methodName);
                    }
                }
                else if (methodName.startsWith("closeNodeHook$")) {
                        getCloseNodeScopeHooks(className).add(methodName);
                }
            }
        } else {
            for (Iterator<Node> it= node.iterator();  it.hasNext();) {
                checkForHooks(it.next(), className);
            }
        }
    }

    public void addCodeInjection(Node n) {
        checkForHooks(n, null);
        codeInjections.add(n);
    }

    public boolean isInInclude() {
        return includeNesting >0;
    }

    public String getParserPackage() {
        if (parserPackage == null) {
            parserPackage = (String) settings.get("PARSER_PACKAGE");
        }
        return parserPackage;
    }

    public Path getParserOutputDirectory() throws IOException {
        String baseSrcDir = (String) settings.get("BASE_SRC_DIR");
        if (baseSrcDir == null) {
            baseSrcDir = outputDir == null ? "." : outputDir.toString();
        }
        Path dir = Paths.get(baseSrcDir);
        if (!dir.isAbsolute()){
            Path inputFileDir = filename.toAbsolutePath().getParent();
            dir = inputFileDir.resolve(baseSrcDir);
        }
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        String packageName = getParserPackage();
        if (packageName != null  && packageName.length() >0) {
            if (codeLang.equals("java")) {
                packageName = packageName.replace('.', '/');
                dir = dir.resolve(packageName);
                if (!Files.exists(dir)) {
                    Files.createDirectories(dir);
                }
            }
            else if (codeLang.equals("python")) { // Use last part of package, append "parser"
                int dotPosition = packageName.lastIndexOf('.');

                if (dotPosition >= 0) {
                    packageName = packageName.substring(dotPosition + 1);
                }
                packageName = packageName.concat("parser");
                // Use a user-specified value if available
                packageName = preprocessorSymbols.getOrDefault("py.package", packageName);
                dir = dir.resolve(packageName);
                if (!Files.exists(dir)) {
                    Files.createDirectories(dir);
                }
            }
            else {
                throw new UnsupportedOperationException(String.format("Code generation in '%s' is not currently supported.", codeLang));
            }
        }
        return dir;
    }

    //FIXME.
    public String getBaseSourceDirectory() {
        return outputDir == null ? "." : outputDir.toString();
    }

    public Path getNodeOutputDirectory() throws IOException {
        String nodePackage = getNodePackage();
        String baseSrcDir = getBaseSourceDirectory();
        if (nodePackage == null || nodePackage.equals("") || baseSrcDir.equals("")) {
            return getParserOutputDirectory();
        }
        Path baseSource = Paths.get(baseSrcDir);
        if (!baseSource.isAbsolute()) {
            Path grammarFileDir = filename.normalize().getParent();
            if (grammarFileDir == null) grammarFileDir = Paths.get(".");
            baseSource = grammarFileDir.resolve(baseSrcDir).normalize();
        }
        if (!Files.isDirectory(baseSource)) {
            if (!Files.exists(baseSource)) {
                throw new FileNotFoundException("Directory " + baseSrcDir + " does not exist.");
            }
            throw new FileNotFoundException(baseSrcDir + " is not a directory.");
        }
        Path result = baseSource. resolve(nodePackage.replace('.', '/')).normalize();
        if (!Files.exists(result)) {
            Files.createDirectories(result);
        } else if (!Files.isDirectory(result)) {
            throw new IOException(result.toString() + " is not a directory.");
        }
        return result;
    }

    public String getNodePackage() {
        String nodePackage = (String) settings.get("NODE_PACKAGE");
        if (nodePackage == null) {
            nodePackage = this.getParserPackage();
        }
        return nodePackage;
    }

    public String getCurrentNodeVariableName() {
        if (nodeVariableNameStack.isEmpty())
            return "null";
        return nodeVariableNameStack.get(nodeVariableNameStack.size() - 1);
    }


    static public String removeNonJavaIdentifierPart(String s) {
        StringBuilder buf = new StringBuilder(s.length());
        for (int ch : s.codePoints().toArray()) {
            boolean addChar = buf.length() == 0 ? (Character.isJavaIdentifierStart(ch)) : Character.isJavaIdentifierPart(ch);
            if (addChar) {
                buf.appendCodePoint(ch);
            }
            if (ch == '.') buf.appendCodePoint('_');
        }
        return buf.toString();
    }

    public boolean getTreeBuildingEnabled() {
        Boolean b = (Boolean) settings.get("TREE_BUILDING_ENABLED");
        return b == null ? true : b;
    }

    public boolean getTreeBuildingDefault() {
        Boolean b = (Boolean) settings.get("TREE_BUILDING_DEFAULT");
        return b == null ? true : b;
    }

    public boolean getNodeDefaultVoid() {
        Boolean b = (Boolean) settings.get("NODE_DEFAULT_VOID");
        return b == null ? false : b;
    }

    public boolean getSmartNodeCreation() {
        Boolean b = (Boolean) settings.get("SMART_NODE_CREATION");
        return b == null ? true : b;
    }

    public boolean getTokensAreNodes() {
        Boolean b = (Boolean) settings.get("TOKENS_ARE_NODES");
        return b == null ? true : b;
    }

    public boolean getUnparsedTokensAreNodes() {
        Boolean b = (Boolean) settings.get("TOKENS_ARE_NODES");
        if (b == null) b = (Boolean) settings.get("SPECIAL_TOKENS_ARE_NODES");
        return b== null ? false : true;
    }

    public boolean getNodeUsesParser() {
        Boolean b = (Boolean) settings.get("NODE_USES_PARSER");
        return b == null ? false : b;
    }

    public boolean getLexerUsesParser() {
        Boolean b = (Boolean) settings.get("LEXER_USES_PARSER");
        return b == null ? false : b;
    }

    public boolean getFaultTolerant() {
        Boolean b = (Boolean) settings.get("FAULT_TOLERANT");
        return b== null ? false : b;
    }

    public boolean getDebugParser() {
        Boolean b = (Boolean) settings.get("DEBUG_PARSER");
        return b == null ? false : b;
    }

    public boolean getDebugLexer() {
        Boolean b = (Boolean) settings.get("DEBUG_LEXER");
        return b==null ? false : b;
    }

    public boolean getLegacyAPI() {
        Boolean b = (Boolean) settings.get("LEGACY_API");
        return b == null ? false : b;
    }

    public boolean getEnsureFinalEOL() {
        Boolean b = (Boolean) settings.get("ENSURE_FINAL_EOL");
        return b== null ? false : b;
    }

    public boolean getDebugFaultTolerant() {
        Boolean b = (Boolean) settings.get("DEBUG_FAULT_TOLERANT");
        return b== null ? false : b;
    }

    public int getJdkTarget() {
        if (jdkTarget == 0) return 8;
        return jdkTarget;
    }

    public List<String> getDeactivatedTokens() {
        return tokensOffByDefault;
    }

    public List<String> getExtraTokens() {
        return extraTokens;
    }

    private boolean ignoreCase;
    public boolean isIgnoreCase() {return ignoreCase;}
    public void setIgnoreCase(boolean ignoreCase) {this.ignoreCase = ignoreCase;}

    public void setSettings(Map<String, Object> settings) {
        typeCheckSettings(settings);
        sanityCheckSettings();
        if (!isInInclude()) this.settings = settings;
        for (String key : settings.keySet()) {
            Object value = settings.get(key);
            if (key.equals("IGNORE_CASE")) {
                setIgnoreCase((Boolean) value);
            }
            else if (key.equals("DEFAULT_LEXICAL_STATE")) {
                setDefaultLexicalState((String) value);
            }
            else if (key.equals("DEACTIVATE_TOKENS") || key.equals("TURN_OFF_TOKENS")) {
                String tokens = (String) settings.get(key);
                for (StringTokenizer st = new StringTokenizer(tokens, ", \t\n\r"); st.hasMoreTokens();) {
                    String tokenName = st.nextToken();
                    if (!tokensOffByDefault.contains(tokenName)) {
                        tokensOffByDefault.add(tokenName);
                    }
                }
            }
            else if (key.equals("EXTRA_TOKENS")) {
                String tokens = (String) settings.get(key);
                for (StringTokenizer st = new StringTokenizer(tokens, ", \t\n\r"); st.hasMoreTokens();) {
                    String tokenName = st.nextToken();
                    if (!extraTokens.contains(tokenName)) {
                        extraTokens.add(tokenName);
                    }
                }
            }
            else if (key.equals("BASE_SRC_DIR") || key.equals("OUTPUT_DIRECTORY")) {
                if (!isInInclude() && outputDir == null)
                    outputDir = Paths.get((String)value);
            }
            if (!isInInclude() && key.equals("JDK_TARGET") && jdkTarget ==0){
                int jdkTarget = (Integer) value;
                if (jdkTarget >=8 && jdkTarget <= 17) {
                    this.jdkTarget = (Integer) value; 
                }
                else {
                    addWarning(null, "Invalid JDK Target " + jdkTarget);
                }
            }
        }
    }
    private int jdkTarget = 8;
    private String booleanSettings = "FAULT_TOLERANT,DEBUG_FAULT_TOLERANT,DEBUG_LEXER,DEBUG_PARSER,PRESERVE_LINE_ENDINGS,JAVA_UNICODE_ESCAPE,IGNORE_CASE,LEXER_USES_PARSER,NODE_DEFAULT_VOID,SMART_NODE_CREATION,NODE_USES_PARSER,TREE_BUILDING_DEFAULT,TREE_BUILDING_ENABLED,TOKENS_ARE_NODES,SPECIAL_TOKENS_ARE_NODES,UNPARSED_TOKENS_ARE_NODES,FREEMARKER_NODES,LEGACY_API,NODE_FACTORY,DEBUG_TOKEN_MANAGER,TOKEN_MANAGER_USES_PARSER,ENSURE_FINAL_EOL";
    private String stringSettings = "PARSER_PACKAGE,PARSER_CLASS,LEXER_CLASS,CONSTANTS_CLASS,BASE_SRC_DIR,BASE_NODE_CLASS,TOKEN_FACTORY,NODE_PREFIX,NODE_CLASS,NODE_PACKAGE,DEFAULT_LEXICAL_STATE,NODE_CLASS,OUTPUT_DIRECTORY,DEACTIVATE_TOKENS,TURN_OFF_TOKENS,EXTRA_TOKENS";
    private String integerSettings = "TABS_TO_SPACES,JDK_TARGET";

    private void typeCheckSettings(Map<String, Object> settings) {
        for (String key : settings.keySet()) {
            Object value = settings.get(key);
            if (booleanSettings.indexOf(key)>=0) {
                if (!(value instanceof Boolean)) {
                    errorMessages.add("The option " + key + " is supposed to be a boolean (true/false) type");
                }
            }
            else if (stringSettings.indexOf(key)>=0) {
                if (!(value instanceof String)) {
                    errorMessages.add("The option " + key + " is supposed to be a string");
                }
            }
            else if (integerSettings.indexOf(key)>=0) {
                if (!(value instanceof Integer)) {
                    errorMessages.add("The option " + key + " is supposed to be an integer");
                }
            }
            else {
                warningMessages.add("The option " + key + " is not recognized and will be ignored.");
            }
        }
    }

    public Map<String, Object> getSettings() {return settings;}

    /**
     * Some warnings if incompatible options are set.
     */
    private void sanityCheckSettings() {
        if (!getTreeBuildingEnabled()) {
            String msg = "You have specified the OPTION_NAME option but it is "
                    + "meaningless unless the TREE_BUILDING_ENABLED is set to true."
                    + " This option will be ignored.\n";
            if (getTokensAreNodes()) {
                addWarning(null, msg.replace("OPTION_NAME", "TOKENS_ARE_NODES"));
            }
            if (getUnparsedTokensAreNodes()) {
                addWarning(null, msg.replace("OPTION_NAME", "UNPARSED_TOKENS_ARE_NODES"));
            }
            if (getSmartNodeCreation()) {
                addWarning(null, msg.replace("OPTION_NAME", "SMART_NODE_CREATION"));
            }
            if (getNodeDefaultVoid()) {
                addWarning(null, msg.replace("OPTION_NAME", "NODE_DEFAULT_VOID"));
            }
            if (getNodeUsesParser()) {
                addWarning(null, msg.replace("OPTION_NAME", "NODE_USES_PARSER"));
            }
        }
    }

    private final Utils utils = new Utils();
    private List<String> nodeVariableNameStack = new ArrayList<>();

    public Utils getUtils() {return utils;}

    public class Utils {

        public void pushNodeVariableName(String jjtThis) {
            nodeVariableNameStack.add(jjtThis);
        }

        public void popNodeVariableName() {
            nodeVariableNameStack.remove(nodeVariableNameStack.size() - 1);
        }


        private Map<String, String> id_map = new HashMap<String, String>();
        private int id = 1;

        public String toHexString(int i) {
            return "0x" + Integer.toHexString(i);
        }

        public String toHexStringL(long l) {
            return "0x" + Long.toHexString(l) + "L";
        }

        public String toOctalString(int i) {
            return "\\" + Integer.toOctalString(i);
        }

        public String addEscapes(String input) {
            return JavaCCConstants.addEscapes(input);
        }

        public long[] bitSetToLongArray(BitSet bs, int numLongs) {
            long[] longs = bs.toLongArray();
            return Arrays.copyOf(longs, numLongs);
        }

        public String codePointAsString(int ch) {
            return new String(new int[]{ch}, 0, 1);
        }


        public boolean isBitSet(long num, int bit) {
            return (num & (1L<<bit)) != 0;
        }

        public int firstCharAsInt(String s) {
            return s.codePointAt(0);
        }

        public String powerOfTwoInHex(int i) {
            return toHexStringL(1L << i);
        }

        public String getID(String name) {
            String value = id_map.get(name);
            if (value == null) {
              value = "prod" + id++;
              id_map.put(name, value);
            }
            return value;
        }

        // For use from templates.
        public String getPreprocessorSymbol(String key, String defaultValue) {
            return preprocessorSymbols.getOrDefault(key, defaultValue);
        }

        /**
         * @param ch the code point. If it is not ASCII, we just display the integer in hex.
         * @return a String to use in generated Java code. Rather than display the integer 97, we display 'a',
         * for example.
         */

        public String displayChar(int ch) {
            String s;

            if (ch == '\'') return "\'\\'\'";
            if (ch == '\\') return "\'\\\\\'";
            if (ch == '\t') return "\'\\t\'";
            if (ch == '\r') return "\'\\r\'";
            if (ch == '\n') return "\'\\n\'";
            if (ch == '\f') return "\'\\f\'";
            if (ch == ' ') return "\' \'";
            if (ch < 128 && !Character.isWhitespace(ch) && !Character.isISOControl(ch)) return "\'" + (char) ch + "\'";
            s = "0x" + Integer.toHexString(ch);
            if (Grammar.this.codeLang.equals("python")) {
                s = String.format("as_chr(%s)", s);
            }
            return s;
        }

        /**
         * This method is only here to help with debugging NFA state-related logic in templates.
         * Sometimes, you want to see ASCII rather than code points.
         *
         * @param char_array a list of code points.
         * @return a String to use in generated template code.
         */
        public String displayChars(int[] char_array) {
            StringBuilder sb = new StringBuilder();
            int n = char_array.length;

            sb.append('[');
            for (int i = 0; i < n; i++) {
                sb.append(displayChar(char_array[i]));
                if (i < (n - 1)) {
                    sb.append(", ");
                }
            }
            sb.append(']');
            return sb.toString();
        }

        // The following methods added for supporting generation in languages other than Java.

        public Map<String, Object> tokenSubClassInfo() {
            Map<String, String> tokenClassMap = new HashMap<>();
            Map<String, String> superClassMap = new HashMap<>();
            //List<String> classes = new ArrayList<>();

            for (RegularExpression re : Grammar.this.getOrderedNamedTokens()) {
                if (re.isPrivate()) continue;
                String tokenClassName = re.getGeneratedClassName();
                String superClassName = re.getGeneratedSuperClassName();

                if (superClassName == null) {
                    superClassName = "Token";
                }
                else {
                    if (!superClassMap.containsKey(superClassName)) {
                        //classes.add(superClassName);
                        superClassMap.put(superClassName, null); // TODO not always!
                    }
                }
                if (!tokenClassMap.containsKey(tokenClassName)) {
                    //classes.add(tokenClassName);
                    tokenClassMap.put(tokenClassName, superClassName);
                }
            }
            // Sort out superclasses' superclasses
            CodeInjector injector = Grammar.this.getInjector();
            String pkg = injector.getNodePackage();
            Map<String, List<ObjectType>> extendsLists = injector.getExtendsLists();
            for (String key: superClassMap.keySet()) {
                String qualifiedName = String.format("%s.%s", pkg, key);
                List<ObjectType> extendsList = extendsLists.get(qualifiedName);

                if ((extendsList == null) || (extendsList.size() == 0)) {
                    superClassMap.put(key, "Token");
                }
                else {
                    superClassMap.put(key, extendsList.get(0).toString());
                }
            }
            tokenClassMap.putAll(superClassMap);

            // Topologically sort classes
            Sequencer seq = new Sequencer();
            for (Map.Entry<String, String> entry : tokenClassMap.entrySet()) {
                seq.addNode(entry.getKey());
                seq.addNode(entry.getValue());
                seq.add(entry.getKey(), entry.getValue());
            }
            List<String> sorted = seq.steps("Token");
            sorted.remove(0);
            HashMap<String, Object> result = new HashMap<>();
            result.put("sortedNames", sorted);
            result.put("tokenClassMap", tokenClassMap);
            return result;
        }

        public String translateParameters(String parameterList) throws ParseException {
            StringBuilder sb = new StringBuilder();
            // First construct the parameter list with parentheses, so
            // that we can parse it and get the AST
            Translator translator = Grammar.this.translator;
            sb.append('(');
            sb.append(parameterList);
            sb.append(')');
            JavaCCParser parser = new JavaCCParser(sb.toString());
            parser.FormalParameters();
            List<FormalParameter> parameters = ((FormalParameters) parser.rootNode()).getParams();
            int n = parameters.size();
            // Now build the result
            sb.setLength(0);
            for (int i = 0; i < n; i++) {
                FormalParameter param = parameters.get(i);
                String name = param.getName();
                translator.addParameterName(name);
                sb.append(translator.translateIdentifier(name));
                if (i < (n - 1)) {
                    sb.append(", ");
                }
            }
            return sb.toString();
        }

        public String clearParameters() {
            Grammar.this.translator.clearParameterNames();
            return "";      // so it can be easily called from a template
        }

        public String translateExpression(Node expr) {
            StringBuilder result = new StringBuilder();
            Grammar.this.translator.translateExpression(expr, result);
            return result.toString();
        }

        public String translateString(String expr) throws ParseException {
            // For debugging. Just parse the passed string as an expression
            // and output the translation.
            JavaCCParser parser = new JavaCCParser(expr);
            parser.Expression();
            StringBuilder result = new StringBuilder();
            Grammar.this.translator.translateExpression(parser.rootNode(), result);
            return result.toString();
        }

        private void translateStatements(Node node, int indent, StringBuilder result) {
            if (node instanceof Statement) {
                Grammar.this.translator.translateStatement(node, indent, result);
            }
            else {
                for (int i = 0; i < node.getChildCount(); i++) {
                    Node child = node.getChild(i);
                    if (child instanceof Delimiter) {
                        continue;   // could put in more checks here
                    }
                    Grammar.this.translator.translateStatement(child, indent, result);
                }
            }
        }

        public Set<String> getTokenNames() {
            HashSet<String> result = new HashSet<>();
            for (RegularExpression re : lexerData.getRegularExpressions()) {
                result.add(re.getLabel());
            }
            return result;
        }

        private void addIndent(int amount, StringBuilder result) {
            for (int i = 0; i < amount; i++) {
                result.append(' ');
            }
        }

        public String translateCodeBlock(String cb, int indent) throws ParseException {
            StringBuilder result = new StringBuilder();
            if (cb != null) {
                cb = cb.trim();
                if (cb.length() == 0) { // must add a "pass"
                    addIndent(indent, result);
                    result.append("pass  # empty code block\n");
                }
                else {
                    String block = "{" + cb + "}";
                    JavaCCParser parser = new JavaCCParser(block);
                    parser.Block();
                    Node node = parser.rootNode();
                    Translator.SymbolTable syms = new Translator.SymbolTable();
                    translator.pushSymbols(syms);
                    translateStatements(node, indent, result);
                    translator.popSymbols();
                }
            }
            return result.toString();
        }

        public String translateNonterminalArgs(String args) {
            StringBuilder result = new StringBuilder();

            if (args != null) {
                String[] parts = args.replace("EnumSet.of(", "").replace(")", "").split(",");
                result.append("_Set({");
                if (!args.equals("null")) {
                    if ((parts.length > 0) && (parts[0].trim().length() > 0)) {
                        for (int i = 0; i < parts.length; i++) {
                            result.append("TokenType.");
                            result.append(parts[i].trim());
                            if (i < (parts.length - 1)) {
                                result.append(", ");
                            }
                        }
                    }
                }
                result.append("})");
            }
            return result.toString();
        }

        public String translateInjectedClass(CodeInjector injector, String name) {
            StringBuilder result = new StringBuilder();
            Map<String, List<ObjectType>> extendsLists = injector.getExtendsLists();
            Map<String, List<ObjectType>> implementsLists = injector.getImplementsLists();
            Map<String, List<ClassOrInterfaceBodyDeclaration>> bodyDeclarations = injector.getBodyDeclarations();
            String qualifiedName = String.format("%s.%s", injector.getNodePackage(), name);
            List<ObjectType> extendsList = extendsLists.get(qualifiedName);
            List<ObjectType> implementsList = implementsLists.get(qualifiedName);
            List<String> nameList = new ArrayList<>();

            if (extendsList.isEmpty() && implementsList.isEmpty()) {
                 nameList.add(injector.getBaseNodeClassName());
            }
            else {
                for (ObjectType ot : extendsList) {
                    nameList.add(ot.toString());
                }
                for (ObjectType ot : implementsList) {
                    nameList.add(ot.toString());
                }
            }
            result.append("class ");
            result.append(name);
            result.append('(');
            for (int i = 0; i < nameList.size(); i++) {
                result.append(nameList.get(i));
                if (i < (nameList.size() - 1)) {
                    result.append(", ");
                }
            }
            result.append("):");
            List<ClassOrInterfaceBodyDeclaration> decls = bodyDeclarations.get(qualifiedName);
            if (decls.size() == 0) {
                result.append(" pass\n");
            }
            else {
                int n = decls.size();
                result.append('\n');
                // Collect all the field declarations
                List<FieldDeclaration> fieldDecls = new ArrayList<>();
                for (ClassOrInterfaceBodyDeclaration decl : decls) {
                    if (decl instanceof FieldDeclaration) {
                        fieldDecls.add((FieldDeclaration) decl);
                    }
                }
                translator.clearFields();
                if (!fieldDecls.isEmpty()) {
                    result.append("    def __init__(self, input_source=None):\n");
                    result.append("        super().__init__(input_source)\n");
                    for (FieldDeclaration fd : fieldDecls) {
                        translator.translateStatement(fd, 8, result);
                    }
                    result.append('\n');
                }
                translator.translateProperties(name, 4, result);
                for (ClassOrInterfaceBodyDeclaration decl : decls) {
                    if (decl instanceof MethodDeclaration) {
                        Grammar.this.translator.translateStatement((MethodDeclaration) decl, 4, result);
                    }
                    else if (decl instanceof FieldDeclaration) {
                        continue;
                    }
                    else {
                        throw new UnsupportedOperationException();
                    }
//                    if (idx < (n - 1)) {
//                        result.append('\n');
//                    }
                }
            }
            return result.toString();
        }

        public String translateInjections(String className, CodeInjector injector, boolean fields) {
            StringBuilder result = new StringBuilder();
            Translator t = Grammar.this.translator;
            if (fields) {
                translator.clearFields();
            }
            Map<String, List<ClassOrInterfaceBodyDeclaration>> bodyDeclarations = injector.getBodyDeclarations();
            List<ClassOrInterfaceBodyDeclaration> declsToProcess = bodyDeclarations.get(className);
            int indent = 8;
            if (declsToProcess != null) {
                for (ClassOrInterfaceBodyDeclaration decl : declsToProcess) {
                    boolean process = (fields != (decl instanceof MethodDeclaration));
                    if (process) {
                        if (decl instanceof FieldDeclaration || decl instanceof CodeBlock || decl instanceof Initializer) {
                            Grammar.this.translator.translateStatement(decl, indent, result);
                        }
                        else if (decl instanceof MethodDeclaration) {
                            Grammar.this.translator.translateStatement((MethodDeclaration) decl, 4, result);
                        }
                        else {
                            throw new UnsupportedOperationException();
                        }
                    }
                }
            }
            return result.toString();
        }

        public List<String> injectedParserFieldNames(CodeInjector injector) {
            ArrayList<String> result = new ArrayList<>();
            String className = String.format("%s.%s", Grammar.this.getParserPackage(),
                    Grammar.this.getParserClassName());
            Map<String, List<ClassOrInterfaceBodyDeclaration>> bodyDeclarations = injector.getBodyDeclarations();
            List<ClassOrInterfaceBodyDeclaration> declsToProcess = bodyDeclarations.get(className);
            if (declsToProcess != null) {
                for (ClassOrInterfaceBodyDeclaration decl : declsToProcess) {
                    if (decl instanceof MethodDeclaration) {
                        continue;
                    }
                    else if (decl instanceof FieldDeclaration) {
                        String name = null;
                        for (Node child : decl.children()) {
                            if (child instanceof Identifier) {
                                name = ((Identifier) child).getImage();
                                break;
                            }
                            else if (child instanceof VariableDeclarator) {
                                Identifier ident = child.firstChildOfType(Identifier.class);
                                if (ident == null) {
                                    throw new UnsupportedOperationException();
                                }
                                name = ident.getImage();
                                break;
                            }
                        }
                        if (name == null) {
                            throw new UnsupportedOperationException();
                        }
                        result.add(translator.translateIdentifier(name));
                    }
                    else {
                        throw new UnsupportedOperationException();
                    }
                }
            }
            return result;
        }

        public String translateLexerInjections(CodeInjector injector, boolean fields) {
            String className = String.format("%s.%s", Grammar.this.getParserPackage(),
                    Grammar.this.getLexerClassName());
            return translateInjections(className, injector, fields);
        }

        public String translateParserInjections(CodeInjector injector, boolean fields) {
            String className = String.format("%s.%s", Grammar.this.getParserPackage(),
                    Grammar.this.getParserClassName());
            return translateInjections(className, injector, fields);
        }

        public List<String> getSortedNodeClassNames() {
            Sequencer seq = new Sequencer();
            CodeInjector injector = getInjector();
            Map<String, List<ObjectType>> extendsMap = injector.getExtendsLists();
            Map<String, List<ObjectType>> implementsMap = injector.getImplementsLists();
            String pkg = injector.getNodePackage();
            String bnn = injector.getBaseNodeClassName();

            seq.addNode(bnn);
            for (String cn : getNodeNames()) {
                String qn = String.format("%s.%s", pkg, cn);
                List<ObjectType> elist = extendsMap.get(qn);
                List<ObjectType> ilist = implementsMap.get(qn);
                List<String> preds = new ArrayList<>();
                if (elist != null) {
                    for (ObjectType ot : elist) {
                        preds.add(ot.toString());
                    }
                }
                if (ilist != null) {
                    for (ObjectType ot : ilist) {
                        preds.add(ot.toString());
                    }
                }
                if (preds.isEmpty()) {
                    preds.add(bnn);
                }
                for (String pn : preds) {
                    seq.addNode(pn);
                    seq.addNode(cn);
                    seq.add(cn, pn);  // Add in reverse order
                }
            }
            List<String> result = seq.steps(bnn);
            result.remove(0); // The bnn value
            return result;
        }
    }
}
