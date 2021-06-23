/* Copyright (C) 2021 Vinay Sajip, vinay_sajip@yahoo.co.uk
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

package com.javacc.output.python;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.javacc.Grammar;
import com.javacc.output.Translator;
import com.javacc.parser.tree.IfStatement;
import com.javacc.parser.tree.ReturnStatement;

public class PythonTranslator extends Translator {
    public PythonTranslator(Grammar grammar) {
        super(grammar);
    }

    @Override public String translateOperator(String operator) {
        String result = operator;

        if (result.equals("||")) {
            result = "or";
        }
        else if (result.equals("&&")) {
            result = "and";
        }
        else if (result.equals("!")) {
            result = "not";
        }
        return result;
    }

    private static Set<String> specialPrefixes = new HashSet<>();

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

    @Override public String translateIdentifier(String ident) {
        String result = ident;

        if (specialPrefixes.isEmpty()) {
            specialPrefixes.add(grammar.generateIdentifierPrefix("tokenHook"));
        }
        if (ident.equals("null")) {
            result = "None";
        }
        else if (ident.equals("true")) {
            result = "True";
        }
        else if (ident.equals("false")) {
            result = "False";
        }
        else if (ident.equals("this")) {
            result = "self";
        }
        else if (ident.equals("currentLookaheadToken") ||
                ident.equals("lastConsumedToken")) {
            result = String.format("self.%s", camelToSnake(ident));
        }
        else if (ident.equals("toString")) {
            ident = "__str__";
        }
        else if (Character.isLowerCase(ident.charAt(0)) && !isSpecialPrefix(ident)) {
            result = camelToSnake(result);
        }
        return result;
    }

    @Override public String translateGetter(String getterName) {
        StringBuilder result = new StringBuilder();

        result.append(Character.toLowerCase(getterName.charAt(3)));
        result.append(getterName.substring(4));
        return translateIdentifier(result.toString());
    }

    public boolean needsParentheses(ASTExpression expr) {
        boolean result = true;

        if (expr instanceof ASTPrimaryExpression ||
            expr instanceof ASTInstanceofExpression ||
            expr instanceof ASTUnaryExpression) {
            result = false;
        }
        else if (expr instanceof ASTBinaryExpression) {
            String op = ((ASTBinaryExpression) expr).getOp();
            if (op.equals(".") || op.equals("=")) {
                result = false;
            }
            else {
                result = (expr.getParent() != null);
            }
        }
        return result;
    }

    @Override protected void translatePrimaryExpression(ASTPrimaryExpression expr, StringBuilder result) {
        String s = expr.getLiteral();
        String n = expr.getName();
        boolean isName = false;

        if (s == null) {
            s = translateIdentifier(n);
            isName = true;
        }
        else {
            if (s.equals("null")) {
                s = "None";
            }
            else if (s.equals("true")) {
                s = "True";
            }
            else if (s.equals("false")) {
                s = "False";
            }
        }
        if (isName && fields.containsKey(n)) {  // must be a field, then
            result.append("self.");
        }
        result.append(s);
    }

    @Override protected void translateUnaryExpression(ASTUnaryExpression expr, StringBuilder result) {
        String xop = translateOperator(expr.getOp());
        boolean parens = needsParentheses(expr);

        if (xop.equals("++") || xop.equals("--")) {
            internalTranslateExpression(expr.getOperand(), result);
            result.append(' ');
            result.append(xop.charAt(0));
            result.append("= 1");
        }
        else {
            if (parens) {
                result.append('(');
            }
            result.append(xop);
            if (xop.equals("not")) {
                result.append(' ');
            }
            internalTranslateExpression(expr.getOperand(), result);
            if (parens) {
                result.append(')');
            }
        }
    }

    @Override protected void translateBinaryExpression(ASTBinaryExpression expr, StringBuilder result) {
        String xop = translateOperator(expr.getOp());
        boolean parens = needsParentheses(expr);
        boolean isDot = xop.equals(".");
        ASTExpression lhs = expr.getLhs();
        ASTExpression rhs = expr.getRhs();

        if (isNull(rhs)) {
            if (xop.equals("==")) {
                xop = "is";
            }
            else if (xop.equals("!=")) {
                xop = "is not";
            }
        }
        if (parens) {
            result.append('(');
        }
        if (isDot && isThis(lhs)) {
            // zap the op to empty string, don't render lhs
            xop = "";
        }
        else {
            internalTranslateExpression(lhs, result);
        }
        if (!isDot) {
            result.append(' ');
        }
        result.append(xop);
        if (!isDot) {
            result.append(' ');
        }
        internalTranslateExpression(rhs, result);
        if (parens) {
            result.append(')');
        }
    }

    private boolean isThis(ASTExpression expr) {
        if (!(expr instanceof ASTPrimaryExpression)) {
            return false;
        }
        else {
            return "this".equals(((ASTPrimaryExpression) expr).getLiteral());
        }
    }

    @Override protected void translateInstanceofExpression(ASTInstanceofExpression expr, StringBuilder result) {
        result.append("isinstance(");
        internalTranslateExpression(expr.getInstance(), result);
        result.append(", ");
        internalTranslateExpression(expr.getType(), result);
        result.append(')');
    }

    @Override protected void translateTernaryExpression(ASTTernaryExpession expr, StringBuilder result) {
        boolean parens = needsParentheses(expr);
        ASTExpression condition = expr.getCondition();
        ASTExpression trueValue = expr.getTrueValue();
        ASTExpression falseValue = expr.getFalseValue();
        if (parens) {
            result.append('(');
        }
        internalTranslateExpression(trueValue, result);
        result.append(" if ");
        internalTranslateExpression(condition, result);
        result.append(" else ");
        internalTranslateExpression(falseValue, result);
        if (parens) {
            result.append(')');
        }
    }

    void renderReceiver(ASTExpression expr, StringBuilder result) {
        if (expr instanceof ASTBinaryExpression) {
            internalTranslateExpression(((ASTBinaryExpression) expr).getLhs(), result);
        }
        else if (expr instanceof ASTPrimaryExpression) {
            result.append("self");
        }
        else {
            throw new UnsupportedOperationException();
        }
    }

    protected boolean isList(ASTExpression node) {
        if (!(node instanceof ASTPrimaryExpression)) {
            return false;
        }
        ASTPrimaryExpression pe = (ASTPrimaryExpression) node;
        String name = pe.getName();
        if (name == null) {
            return false;
        }
        return name.equals("ArrayList");
    }

    protected void translateArguments(List<ASTExpression> arguments, StringBuilder result) {
        int nargs;

        if ((arguments == null) || ((nargs = arguments.size()) == 0)) {
            result.append("()");
        }
        else {
            result.append('(');
            for (int i = 0; i < nargs; i++) {
                internalTranslateExpression(arguments.get(i), result);
                if (i < (nargs - 1))
                    result.append(", ");
            }
            result.append(')');
        }
    }

    @Override protected void translateInvocation(ASTInvocation expr, StringBuilder result) {
        String methodName = expr.getMethodName();
        int nargs = expr.getArgCount();
        ASTExpression receiver = expr.getReceiver();
        ASTExpression firstArg = (nargs != 1) ? null : expr.getArguments().get(0);

        if (methodName.equals("equals") && (nargs == 1)) {
            renderReceiver(receiver, result);
            result.append(" == ");
            internalTranslateExpression(firstArg, result);
        }
        else if ((methodName.equals("contains") || methodName.equals("containsKey")) && (nargs == 1)) {
            internalTranslateExpression(firstArg, result);
            result.append(" in ");
            renderReceiver(receiver, result);
        }
        else if (methodName.equals("toString") && (nargs == 0)) {
            result.append("str(");
            renderReceiver(receiver, result);
            result.append(')');
        }
        else if (methodName.startsWith("get") && (nargs == 0)) {
            // treat as a property
            renderReceiver(receiver, result);
            result.append('.');
            result.append(translateGetter(methodName));
        }
        else if (methodName.equals("isParserTolerant") && (nargs == 0)) {
            renderReceiver(receiver, result);
            result.append(".is_tolerant");
        }
        else if (methodName.equals("nodeArity") && (nargs == 0)) {
            renderReceiver(receiver, result);
            result.append(".node_arity");
        }
        else if (methodName.equals("isUnparsed") && (nargs == 0)) {
            renderReceiver(receiver, result);
            result.append(".unparsed");
        }
        else if (methodName.equals("setUnparsed") && (nargs == 1)) {
            renderReceiver(receiver, result);
            result.append(".unparsed = ");
            internalTranslateExpression(firstArg, result);
        }
        else if (methodName.equals("setBeginLine") && (nargs == 1)) {
            renderReceiver(receiver, result);
            result.append(".begin_line = ");
            internalTranslateExpression(firstArg, result);
        }
        else if (methodName.equals("setBeginColumn") && (nargs == 1)) {
            renderReceiver(receiver, result);
            result.append(".begin_column = ");
            internalTranslateExpression(firstArg, result);
        }
        else if (methodName.equals("setEndLine") && (nargs == 1)) {
            renderReceiver(receiver, result);
            result.append(".end_line = ");
            internalTranslateExpression(firstArg, result);
        }
        else if (methodName.equals("setEndColumn") && (nargs == 1)) {
            renderReceiver(receiver, result);
            result.append(".end_column = ");
            internalTranslateExpression(firstArg, result);
        }
        else if (methodName.equals("setInputSource") && (nargs == 1)) {
            renderReceiver(receiver, result);
            result.append(".input_source = ");
            internalTranslateExpression(firstArg, result);
        }
        else if (expr instanceof ASTAllocation) {
            if (isList(receiver)) {
                result.append("List()");
            }
            else {
                internalTranslateExpression(receiver, result);
                translateArguments(expr.getArguments(), result);
            }
        }
        else {
            if (!methodName.equals("newToken")) {
                renderReceiver(receiver, result);
                result.append('.');
            }
            String ident = translateIdentifier(methodName);
            result.append(ident);
            translateArguments(expr.getArguments(), result);
        }
    }

    boolean shouldIndent(ASTStatement stmt) {
        boolean result = true;

        if (stmt instanceof ASTStatementList) {
            result = false;
        }
        else if (stmt instanceof ASTVariableOrFieldDeclaration) {
            ASTVariableOrFieldDeclaration d = (ASTVariableOrFieldDeclaration) stmt;
            result = d.isField() || d.hasInitializer();
        }
        return result;
    }

    @Override protected void internalTranslateStatement(ASTStatement stmt, int indent, StringBuilder result) {
        boolean addNewline = false;
        boolean doIndent = shouldIndent(stmt);

        if (doIndent) {
            addIndent(indent, result);
        }
        if (stmt instanceof ASTExpressionStatement) {
            internalTranslateExpression(((ASTExpressionStatement) stmt).getValue(), result);
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
            String defaultInitializer = "None";

            if (type.isNumeric()) {
                defaultInitializer = "0";
            }
            for (int i = 0; i < n; i++) {
                ASTPrimaryExpression name = names.get(i);
                ASTExpression initializer = initializers.get(i);

                if (vd.isField()) {
                    fields.put(name.getName(), type);
                }
                else {
                    addSymbol(name.getName(), type);
                }
                if (vd.isField() || (initializer != null)) {
                    internalTranslateExpression(name, result);
                    result.append(" = ");
                    if (initializer == null) {
                        result.append(defaultInitializer);
                    }
                    else {
                        internalTranslateExpression(initializer, result);
                    }
                    if (i < (n - 1)) {
                        result.append("; ");
                    }
                    addNewline = true;
                }
            }
        }
        else if (stmt instanceof ASTVariableOrFieldDeclaration) { // TODO refactor/combine previous case
            ASTVariableOrFieldDeclaration vd = (ASTVariableOrFieldDeclaration) stmt;
            List<ASTPrimaryExpression> names = vd.getNames();
            List<ASTExpression> initializers = vd.getInitializers();
            int n = names.size();

            for (int i = 0; i < n; i++) {
                ASTExpression name = names.get(i);
                ASTExpression initializer = initializers.get(i);

                if (initializer != null) {
                    internalTranslateExpression(name, result);
                    result.append(" = ");
                    internalTranslateExpression(initializer, result);
                }
                if (i < (n - 1)) {
                    result.append("; ");
                }
                addNewline = true;
            }
        }
        else if (stmt instanceof ASTReturnStatement) {
            result.append("return ");
            internalTranslateExpression(((ASTReturnStatement) stmt).getValue(), result);
            addNewline = true;
        }
        else if (stmt instanceof ASTIfStatement) {
            ASTIfStatement s = (ASTIfStatement) stmt;

            result.append("if ");
            internalTranslateExpression(s.getCondition(), result);
            result.append(":\n");
            internalTranslateStatement(s.getThenStmts(), indent + 4, result);
            if (s.getElseStmts() != null) {
                addIndent(indent, result);
                internalTranslateStatement(s.getElseStmts(), indent + 4, result);
            }
        }
        else if (stmt instanceof ASTForStatement) {
            ASTForStatement s = (ASTForStatement) stmt;
            ASTExpression iterable;
            ASTVariableOrFieldDeclaration decl = s.getVariable();

            if ((iterable = s.getIterable()) != null) {
                // iterating for
                ASTVariableOrFieldDeclaration vd = s.getVariable();
                result.append("for ");
                internalTranslateExpression(vd.getNames().get(0), result);
                result.append(" in ");
                internalTranslateExpression(iterable, result);
                result.append(":\n");
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
                        internalTranslateExpression(name, result);
                        result.append(" = ");
                        internalTranslateExpression(initializer, result);
                        if (i < (n - 1)) {
                            result.append("; ");
                        }
                    }
                }
                result.append('\n');
                addIndent(indent, result);
                result.append("while ");
                internalTranslateExpression(s.getCondition(), result);
                result.append(":\n");
                internalTranslateStatement(s.getStatements(), indent + 4, result);
                addIndent(indent + 4, result);
                List<ASTExpression> iteration = s.getIteration();
                n = iteration.size();
                for (int i = 0; i < n; i++) {
                    ASTExpression e = iteration.get(i);
                    internalTranslateExpression(e, result);
                    if (i < (n - 1)) {
                        result.append("; ");
                    }
                }
                result.append('\n');
            }
        }
        else if (stmt instanceof ASTSwitchStatement) {
            ASTSwitchStatement s = (ASTSwitchStatement) stmt;
            String tv = getTempVarName();
            result.append(tv);
            result.append(" = ");
            internalTranslateExpression(s.getVariable(), result);
            result.append('\n');
            boolean useIf = true;
            for (ASTCaseStatement c : s.getCases()) {
                List<ASTExpression> labels = c.getCaseLabels();
                int lc = labels.size();
                addIndent(indent, result);
                if (lc == 0) {
                   result.append("else:\n");
                }
                else {
                    result.append(useIf ? "if " : "elif ");
                    useIf = false;
                    result.append(tv);
                    if (lc == 1) {
                        result.append(" == ");
                        internalTranslateExpression(labels.get(0), result);
                    }
                    else {
                        result.append(" in (");
                        for (int i = 0; i < lc; i++) {
                            internalTranslateExpression(labels.get(i), result);
                            if (i < (lc - 1)) {
                                result.append(", ");
                            }
                        }
                        result.append(')');
                    }
                    result.append(":\n");
                }
                internalTranslateStatement(c.getStatements(), indent + 4, result);
                useIf = !c.hasBreak();
            }
        }
        else if (stmt instanceof ASTMethodDeclaration) {
            ASTMethodDeclaration decl = (ASTMethodDeclaration) stmt;
            String methodName = translateIdentifier(decl.getName());
            List<ASTFormalParameter> formals = decl.getParameters();
            SymbolTable syms = new SymbolTable();

            pushSymbols(syms);
            result.append("def ");
            result.append(methodName);
            result.append("(self");
            if (formals != null) {
                result.append(", ");
                int n = formals.size();
                for (int i = 0; i < n; i++) {
                    ASTFormalParameter formal = formals.get(i);
                    String name = formal.getName();
                    ASTTypeExpression type = formal.getType();
                    result.append(translateIdentifier(name));
                    if (i < (n - 1)) {
                        result.append(", ");
                    }
                    syms.put(name, type);
                }
            }
            result.append("):\n");
            internalTranslateStatement(decl.getStatements(), indent + 4, result);
            result.append('\n');
            popSymbols();
        }
        else {
            throw new UnsupportedOperationException();
        }
        if (addNewline) {
            result.append('\n');
        }
    }
}
