/* Copyright (C) 2021-2022 Vinay Sajip, vinay_sajip@yahoo.co.uk
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
 *     * Neither the name Vinay Sajip nor the names of any contributors
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

package com.javacc.output.csharp;

import java.util.*;

import com.javacc.Grammar;
import com.javacc.output.Translator;
import com.javacc.output.java.CodeInjector;
import com.javacc.parser.tree.*;

public class CSharpTranslator extends Translator {
    public CSharpTranslator(Grammar grammar) {
        super(grammar);
        methodIndent = 8;
        fieldIndent = 8;
        isTyped = true;
    }

    @Override public String translateOperator(String operator) {
        return operator;
    }

    private static final Set<String> specialPrefixes = new HashSet<>();

    private static boolean isSpecialPrefix(String ident) {
        boolean result = false;

        for (String p : specialPrefixes) {
            if (ident.startsWith(p)) {
                result = true;
                break;
            }
        }
        return result;
    }

    private static Set<String> propertyIdentifiers = makeSet("image", "lastConsumedToken");

    @Override public String translateIdentifier(String ident, TranslationContext kind) {
        // TODO proper method name translation
        if (kind == TranslationContext.TYPE) {
            return translateTypeName(ident);
        }
        String result = ident;

        if (specialPrefixes.isEmpty()) {
            specialPrefixes.add(grammar.generateIdentifierPrefix("tokenHook"));
        }
        if (ident.equals("toString")) {
            result = "ToString";
        }
        else if (ident.equals("addAll")) {
            result = "AddRange";
        }
        else if (ident.equals("preInsert")) {
            result = "PreInsert";
        }
        else if (ident.equals("size")) {
            result = "Count";
        }
        else if (ident.equals("String")) {
            result = "string";
        }
        else if (ident.equals("isUnparsed")) {
            result = "IsUnparsed";
        }
        else if ((kind != TranslationContext.VARIABLE || propertyIdentifiers.contains(ident)) && kind != TranslationContext.PARAMETER && Character.isLowerCase(ident.charAt(0)) && !isSpecialPrefix(ident)) {
            result = Character.toUpperCase(ident.charAt(0)) + ident.substring(1);
        }
//        else if (kind == TranslationContext.VARIABLE && ident.equals("image")) {
//            result = "Image";
//        }
        return result;
    }

    @Override public String translateGetter(String getterName) {
        if (getterName.startsWith("is")) {
            return translateIdentifier(getterName, TranslationContext.METHOD);
        }
        String result = Character.toLowerCase(getterName.charAt(3)) +
                getterName.substring(4);
        return translateIdentifier(result, TranslationContext.METHOD);
    }

    @Override protected void translatePrimaryExpression(ASTPrimaryExpression expr, TranslationContext ctx, StringBuilder result) {
        String s = expr.getLiteral();
        String n = expr.getName();
        boolean isName = false;

        if (s == null) {
            s = translateIdentifier(n, TranslationContext.VARIABLE);
            isName = true;
        }
        if (isName && fields.containsKey(n)) {  // must be a field, then
            if (properties.containsKey(n)) {
                result.append('_');
            }
        }
        if ((ctx == TranslationContext.PARAMETER) && (expr instanceof ASTTypeExpression)) {
            result.append("typeof(");
            result.append(s);
            result.append(')');
        }
        else {
            result.append(s);
        }
    }

    @Override protected void translateUnaryExpression(ASTUnaryExpression expr, TranslationContext ctx, StringBuilder result) {
        String xop = translateOperator(expr.getOp());
        boolean parens = needsParentheses(expr);

        if (xop.equals("++") || xop.equals("--")) {
            internalTranslateExpression(expr.getOperand(), ctx, result);
            result.append(' ');
            result.append(xop.charAt(0));
            result.append("= 1");
        }
        else {
            if (parens) {
                result.append('(');
            }
            result.append(xop);
            internalTranslateExpression(expr.getOperand(), ctx, result);
            if (parens) {
                result.append(')');
            }
        }
    }

    @Override protected void translateBinaryExpression(ASTBinaryExpression expr, StringBuilder result) {
        String xop = translateOperator(expr.getOp());
        boolean parens = needsParentheses(expr);
        ASTExpression lhs = expr.getLhs();
        ASTExpression rhs = expr.getRhs();

        processBinaryExpression(parens, lhs, xop, rhs, result);
    }

    @Override protected void translateInstanceofExpression(ASTInstanceofExpression expr, StringBuilder result) {
        internalTranslateExpression(expr.getInstance(), TranslationContext.UNKNOWN, result);
        result.append(" is ");
        internalTranslateExpression(expr.getType(), TranslationContext.UNKNOWN, result);
    }

    @SuppressWarnings("DuplicatedCode")
    @Override protected void translateTernaryExpression(ASTTernaryExpression expr, StringBuilder result) {
        boolean parens = needsParentheses(expr);
        ASTExpression condition = expr.getCondition();
        ASTExpression trueValue = expr.getTrueValue();
        ASTExpression falseValue = expr.getFalseValue();

        if (parens) {
            result.append('(');
        }
        internalTranslateExpression(condition, TranslationContext.UNKNOWN, result);
        result.append(" ? ");
        internalTranslateExpression(trueValue, TranslationContext.UNKNOWN, result);
        result.append(" : ");
        internalTranslateExpression(falseValue, TranslationContext.UNKNOWN, result);
        if (parens) {
            result.append(')');
        }
    }

    void renderReceiver(ASTExpression expr, StringBuilder result) {
        if (expr instanceof ASTBinaryExpression) {
            internalTranslateExpression(((ASTBinaryExpression) expr).getLhs(), TranslationContext.UNKNOWN, result);
        }
        else if (expr instanceof ASTPrimaryExpression) {
            // Do nothing
        }
        else {
            throw new UnsupportedOperationException();
        }
    }

    protected void translateArguments(List<ASTExpression> arguments, StringBuilder result) {
        int nargs;

        if ((arguments == null) || ((nargs = arguments.size()) == 0)) {
            result.append("()");
        }
        else {
            result.append('(');
            for (int i = 0; i < nargs; i++) {
                internalTranslateExpression(arguments.get(i), TranslationContext.PARAMETER, result);
                if (i < (nargs - 1))
                    result.append(", ");
            }
            result.append(')');
        }
    }

    private static Set<String> propertyNames = makeSet("getImage", "getType", "getBeginLine", "getBeginColumn",
                                                       "getEndLine", "getEndColumn");

    @Override protected void translateInvocation(ASTInvocation expr, StringBuilder result) {
        String methodName = expr.getMethodName();
        int nargs = expr.getArgCount();
        ASTExpression receiver = expr.getReceiver();
        boolean treatAsProperty = propertyNames.contains(methodName);
        ASTExpression firstArg = (nargs != 1) ? null : expr.getArguments().get(0);
        boolean needsGeneric = methodName.equals("firstChildOfType") || methodName.equals("childrenOfType") ||
                               methodName.equals("descendantsOfType") || methodName.equals("descendants");

        needsGeneric = needsGeneric && (firstArg instanceof ASTPrimaryExpression);

        if (methodName.equals("size") && (nargs == 0)) {
            renderReceiver(receiver, result);
            result.append(".Count");
        }
        else if (methodName.equals("isParserTolerant") && (nargs == 0)) {
            int n = result.length();
            renderReceiver(receiver, result);
            if (n < result.length()) {
                result.append('.');
            }
            result.append("IsTolerant");
        }
        else if (methodName.equals("previousCachedToken") && (nargs == 0)) {
            int n = result.length();
            renderReceiver(receiver, result);
            if (n < result.length()) {
                result.append('.');
            }
            result.append("PreviousCachedToken");
        }
        else if (methodName.equals("get") && (nargs == 1)) {
            renderReceiver(receiver, result);
            result.append('[');
            internalTranslateExpression(firstArg, TranslationContext.UNKNOWN, result);
            result.append(']');
        }
        else if (treatAsProperty && isGetter(methodName) && (nargs == 0)) {
            // treat as a property
            int n = result.length();
            renderReceiver(receiver, result);
            if (n < result.length()) {
                result.append('.');
            }
            result.append(translateGetter(methodName));
        }
        else if (methodName.equals("nodeArity") && (nargs == 0)) {
            renderReceiver(receiver, result);
            result.append(".NodeArity");
        }
        else if (methodName.equals("isUnparsed") && (nargs == 0)) {
            renderReceiver(receiver, result);
            result.append(".IsUnparsed");
        }
        else if (methodName.equals("setUnparsed") && (nargs == 1)) {
            renderReceiver(receiver, result);
            result.append(".IsUnparsed = ");
            internalTranslateExpression(firstArg, TranslationContext.UNKNOWN, result);
        }
        else if (methodName.equals("of") && isEnumSet(receiver)) {
            result.append("Utils.EnumSet(");
            if (nargs > 0) {
                translateArguments(expr.getArguments(), false, result);
            }
            result.append(")");
        }
        else if (isSetter(methodName) && (nargs == 1)) {
            String s = translateIdentifier(methodName, TranslationContext.METHOD);
            renderReceiver(receiver, result);
            result.append('.');
            result.append(s.substring(3));
            result.append(" = ");
            internalTranslateExpression(firstArg, TranslationContext.UNKNOWN, result);
        }
        else if (expr instanceof ASTAllocation) {
            if (isList(receiver)) {
                result.append("new ListAdapter<");
                List<ASTTypeExpression> tps = ((ASTTypeExpression) receiver).getTypeParameters();
                if (tps != null) {
                    translateType(tps.get(0), result);
                }
                result.append(">");
            }
            else {
                result.append("new ");
                internalTranslateExpression(receiver, TranslationContext.UNKNOWN, result);
            }
            translateArguments(expr.getArguments(), result);
        }
        else {
            if (!methodName.equals("newToken")) {
                int n = result.length();
                renderReceiver(receiver, result);
                if (n < result.length()) {
                    result.append('.');
                }
            }
            String ident = translateIdentifier(methodName, TranslationContext.METHOD);
            result.append(ident);
            if (needsGeneric) {
                result.append('<');
                result.append(((ASTPrimaryExpression) firstArg).getName());
                result.append('>');
            }
            translateArguments(expr.getArguments(), result);
        }
    }

    protected String translateTypeName(String name) {
        String result = name;

        switch (name) {
            case "List":
            case "java.util.List":
                result = "ListAdapter";
                break;
            case "EnumSet":
                result = "SetAdapter";
                break;
            case "Iterator":
            case "java.util.Iterator":
                result = "Iterator";
                break;
            case "boolean":
                result = "bool";
                break;
            case "Integer":
                result = "int";
                break;
            case "LEXER_CLASS":
                result = "Lexer";
                break;
            case "PARSER_CLASS":
                result = "Parser";
                break;
        }
        return result;
    }

    @Override protected void translateType(ASTTypeExpression expr, StringBuilder result) {
        String s = expr.getName();

        if (s == null) {
            s = expr.getLiteral();
        }
        result.append(translateTypeName(s));  // TODO translate
        List<ASTTypeExpression> tp = expr.getTypeParameters();
        if (tp != null) {
            result.append('<');
            int n = tp.size();
            for (int i = 0; i < n; i++) {
                translateType(tp.get(i), result);
                if (i < (n - 1)) {
                    result.append(", ");
                }
            }
            result.append('>');
        }
    }

    protected static final HashSet<String> accessModifiers = new HashSet<>(Arrays.asList("public", "protected", "private"));

    protected void translateModifiers(List<String> modifiers, StringBuilder result) {
        HashSet<String> mods = new HashSet<>(modifiers);
        List<String> translated_mods = new ArrayList<>();
        boolean accessModifierAdded = false;

        mods.remove("default");
        mods.remove("final");
        for (String s : accessModifiers) {
            if (mods.contains(s)) {
                mods.remove(s);
                translated_mods.add(s);
                accessModifierAdded = true;
            }
        }
        if (!accessModifierAdded && !inInterface) {
            translated_mods.add("internal");
        }
        if (mods.contains("static")) {
            translated_mods.add("static");
            mods.remove("static");
        }
        if (mods.size() > 0) {
            throw new UnsupportedOperationException();
        }
        for (String mod: translated_mods) {
            result.append(mod);
            result.append(' ');
        }
    }

    @Override protected void internalTranslateStatement(ASTStatement stmt, int indent, StringBuilder result) {
        boolean addNewline = false;

        if (!(stmt instanceof ASTStatementList)) {  // it adds its own indents
            addIndent(indent, result);
        }
        if (stmt instanceof ASTExpressionStatement) {
            internalTranslateExpression(((ASTExpressionStatement) stmt).getValue(), TranslationContext.UNKNOWN, result);
            result.append(';');
            addNewline = true;
        }
        else if (stmt instanceof ASTStatementList) {
            List<ASTStatement> statements = ((ASTStatementList) stmt).getStatements();

            for (ASTStatement s : statements) {
                internalTranslateStatement(s, indent, result);
            }
        }
        else if (stmt instanceof ASTVariableOrFieldDeclaration) {
            ASTVariableOrFieldDeclaration vd = (ASTVariableOrFieldDeclaration) stmt;
            List<ASTPrimaryExpression> names = vd.getNames();
            List<ASTExpression> initializers = vd.getInitializers();
            ASTTypeExpression type = vd.getType();
            int n = names.size();
            boolean isProperty = vd.hasAnnotation("Property");
            boolean isField = vd.isField();
            List<String> modifiers = vd.getModifiers();

            if (modifiers == null) {
                if (isField) {
                    result.append("internal ");  // default access modifier
                }
            }
            else {
                translateModifiers(modifiers, result);
            }
            translateType(vd.getType(), result);
            result.append(' ');
            for (int i = 0; i < n; i++) {
                ASTPrimaryExpression name = names.get(i);
                ASTExpression initializer = initializers.get(i);

                processVariableDeclaration(type, name, isField, isProperty);
                TranslationContext ctx = isField ? TranslationContext.FIELD : TranslationContext.VARIABLE;
                internalTranslateExpression(name, ctx, result);
                if (initializer != null) {
                    result.append(" = ");
                    internalTranslateExpression(initializer, TranslationContext.UNKNOWN, result);
                }
                if (i < (n - 1)) {
                    result.append(", ");
                }
                addNewline = true;
            }
            result.append(';');
        }
        else if (stmt instanceof ASTReturnStatement) {
            result.append("return");
            ASTExpression value = ((ASTReturnStatement) stmt).getValue();
            if (value != null) {
                result.append(' ');
                internalTranslateExpression(value, TranslationContext.UNKNOWN, result);
            }
            result.append(';');
            addNewline = true;
        }
        else if (stmt instanceof ASTIfStatement) {
            ASTIfStatement s = (ASTIfStatement) stmt;

            result.append("if (");
            internalTranslateExpression(s.getCondition(), TranslationContext.UNKNOWN, result);
            result.append(") {\n");
            internalTranslateStatement(s.getThenStmts(), indent + 4, result);
            if (s.getElseStmts() != null) {
                addIndent(indent, result);
                result.append("else {\n");
                internalTranslateStatement(s.getElseStmts(), indent + 4, result);
            }
            addIndent(indent, result);
            result.append("}\n");
        }
        else if (stmt instanceof ASTForStatement) {
            ASTForStatement s = (ASTForStatement) stmt;
            ASTExpression iterable;
            ASTVariableOrFieldDeclaration decl = s.getVariable();

            if ((iterable = s.getIterable()) != null) {
                // iterating for
                ASTVariableOrFieldDeclaration vd = s.getVariable();
                result.append("foreach (var ");
                internalTranslateExpression(vd.getNames().get(0), TranslationContext.UNKNOWN, result);
                result.append(" in ");
                internalTranslateExpression(iterable, TranslationContext.UNKNOWN, result);
                result.append(") {\n");
                internalTranslateStatement(s.getStatements(), indent + 4, result);
            }
            else {
                // counting for
                List<ASTPrimaryExpression> names = decl.getNames();
                List<ASTExpression> initializers = decl.getInitializers();
                int n = names.size();
                for (int i = 0; i < n; i++) {
                    ASTExpression name = names.get(i);
                    ASTExpression initializer = initializers.get(i);
                    if (initializer != null) {
                        translateType(decl.getType(), result);
                        result.append(' ');
                        internalTranslateExpression(name, TranslationContext.UNKNOWN, result);
                        result.append(" = ");
                        internalTranslateExpression(initializer, TranslationContext.UNKNOWN, result);
                        if (i < (n - 1)) {
                            result.append("; ");
                        }
                    }
                    else {
                        throw new UnsupportedOperationException();
                    }
                }
                result.append(";\n");
                addIndent(indent, result);
                result.append("while (");
                internalTranslateExpression(s.getCondition(), TranslationContext.UNKNOWN, result);
                result.append(") {\n");
                internalTranslateStatement(s.getStatements(), indent + 4, result);
                List<ASTExpression> iteration = s.getIteration();
                if (iteration != null) {
                    processForIteration(iteration, indent + 4, result);
                    result.append(";\n");
                }
            }
            addIndent(indent, result);
            result.append("}\n");
        }
        else if (stmt instanceof ASTSwitchStatement) {
            ASTSwitchStatement s = (ASTSwitchStatement) stmt;
            String tv = getTempVarName();
            ASTExpression expr = s.getVariable();
            boolean isTT = isTokenType(expr);
            result.append("var ");
            result.append(tv);
            result.append(" = ");
            internalTranslateExpression(expr, TranslationContext.UNKNOWN, result);
            result.append(";\n");

            addIndent(indent, result);
            result.append("switch (");
            result.append(tv);
            result.append(") {\n");
            for (ASTCaseStatement c : s.getCases()) {
                List<ASTExpression> labels = c.getCaseLabels();
                int lc = labels.size();
                if (lc == 0) {
                    addIndent(indent, result);
                    result.append("default:\n");
                }
                else {
                    for (ASTExpression label : labels) {
                        addIndent(indent, result);
                        result.append("case ");
                        if (isTT) {
                            result.append("TokenType.");
                        }
                        internalTranslateExpression(label, TranslationContext.UNKNOWN, result);
                        result.append(":\n");
                    }
                }
                internalTranslateStatement(c.getStatements(), indent + 4, result);
                if (!hasUnconditionalExit(c.getStatements())) {
                    addIndent(indent + 4, result);
                    result.append("break;\n");
                }
            }
            addIndent(indent, result);
            result.append("}\n");
        }
        else if (stmt instanceof ASTMethodDeclaration) {
            ASTMethodDeclaration decl = (ASTMethodDeclaration) stmt;
            String methodName = translateIdentifier(decl.getName(), TranslationContext.METHOD);
            List<ASTFormalParameter> formals = decl.getParameters();
            SymbolTable symbols = new SymbolTable();
            List<String> modifiers = decl.getModifiers();
            boolean isOverride = methodName.equals("Equals") || methodName.equals("ToString"); // TODO generalise

            pushSymbols(symbols);
            if (modifiers == null) {
                result.append("internal ");  // default access modifier
            }
            else {
                if (methodName.equals("GetIndents") || methodName.equals("IsVirtual")) { // TODO generalise
                    if ("Token".equals(currentClass)) {
                        result.append("virtual ");
                    }
                    else {
                        result.append("override ");
                    }
                }
                translateModifiers(modifiers, result);
            }
            if (isOverride) {
                result.append("override ");
            }
            translateType(((ASTMethodDeclaration) stmt).getReturnType(), result);
            result.append(' ');
            result.append(methodName);
            result.append('(');
            if (formals != null) {
                translateFormals(formals, symbols, true, true, result);
            }
            result.append(") {\n");
            internalTranslateStatement(decl.getStatements(), indent + 4, result);
            addIndent(indent, result);
            result.append("}\n\n");
            popSymbols();
        }
        else {
            throw new UnsupportedOperationException();
        }
        if (addNewline) {
            result.append('\n');
        }
    }

    @Override public void translateProperties(String name, int indent, StringBuilder result) {
        super.translateProperties(name, indent, result);
        if (!properties.isEmpty()) {
            for (Map.Entry<String, ASTTypeExpression> prop : properties.entrySet()) {
                String k = prop.getKey();
                String s = translateIdentifier(k, TranslationContext.FIELD);
                addIndent(indent, result);
                result.append("public ");
                translateType(prop.getValue(), result);
                result.append(' ');
                result.append(s);
                result.append(" { get { return _");
                result.append(k);
                result.append("; } set { _");
                result.append(k);
                result.append(" = value; } }\n\n");
            }
        }
    }

    @Override  public String translateInjectedClass(CodeInjector injector, String name) {
        String qualifiedName = String.format("%s.%s", injector.getNodePackage(), name);
        List<String> nameList = injector.getParentClasses(qualifiedName);
        List<ClassOrInterfaceBodyDeclaration> decls = injector.getBodyDeclarations(qualifiedName);
        int n = decls.size();
        int indent = 4;
        StringBuilder result = new StringBuilder();

        inInterface = grammar.nodeIsInterface(name);
        try {
            addIndent(indent, result);
            result.append("public ").append(inInterface ? "interface" : "class").append(' ').append(name).append(" : ");

            result.append(String.join(", ", nameList));
            result.append(" {\n");
            if (n > 0) {
                result.append('\n');
                // Collect all the field declarations
                List<FieldDeclaration> fieldDecls = new ArrayList<>();
                for (ClassOrInterfaceBodyDeclaration decl : decls) {
                    if (decl instanceof FieldDeclaration) {
                        fieldDecls.add((FieldDeclaration) decl);
                    }
                }
                clearFields();
                if (!fieldDecls.isEmpty()) {
                    for (FieldDeclaration fd : fieldDecls) {
                        translateStatement(fd, 8, result);
                    }
                }
                translateProperties(name, indent + 4, result);
                for (ClassOrInterfaceBodyDeclaration decl : decls) {
                    if (decl instanceof FieldDeclaration) {
                        continue;
                    }
                    if (decl instanceof MethodDeclaration) {
                        translateStatement(decl, indent + 4, result);
                    }
                    else {
                        throw new UnsupportedOperationException();
                    }
                }
            }
            if (!inInterface) {
                addIndent(indent + 4, result);
                result.append(String.format("public %s(Lexer tokenSource) : base(tokenSource) {}\n", name));
            }
            addIndent(indent, result);
            result.append("}\n");
            return result.toString();
        }
        finally {
            inInterface = false;
        }
    }

    @Override protected void translateCast(ASTTypeExpression cast, StringBuilder result) {
        result.append('(');
        translateType(cast, result);;
        result.append(") ");
    }

    @Override  public void translateFormals(List<FormalParameter> formals, SymbolTable symbols, StringBuilder result) {
        translateFormals(transformFormals(formals), symbols, true, true, result);
    }
}
