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

package com.javacc.output;

import java.util.*;

import com.javacc.Grammar;
import com.javacc.output.python.PythonTranslator;
import com.javacc.parser.Node;
import com.javacc.parser.Token;
import com.javacc.parser.tree.*;

public class Translator {
    protected Grammar grammar;
    protected int tempVarCounter;
    protected Set<String> tokenNames;

    protected class ASTHelperNode {
        ASTHelperNode parent;

        public ASTHelperNode() {
            this(null);
        }

        public ASTHelperNode(ASTHelperNode parent) {
            this.parent = parent;
        }

        public ASTHelperNode getParent() {
            return parent;
        }
    }

    protected class ASTExpression extends ASTHelperNode {
    }

    protected class ASTPrimaryExpression extends ASTExpression {
        protected String name;
        protected String literal;


        public String getName() {
            return name;
        }

        public String getLiteral() {
            return literal;
        }
    }

    protected class ASTTypeExpression extends ASTPrimaryExpression {
        public boolean isNumeric() {
            boolean result = ((literal != null) || name.equals("Integer") ||
                              name.equals("Long") || name.equals("Float") ||
                              name.equals("Double") || name.equals("BigInteger"));
            return result;
        }
    }

    protected class ASTUnaryExpression extends ASTExpression {
        protected String op;
        protected ASTExpression operand;

        public String getOp() {
            return op;
        }

        public ASTExpression getOperand() {
            return operand;
        }
    }

    protected class ASTInstanceofExpression extends ASTExpression {
        private ASTExpression instance;
        private ASTTypeExpression type;

        public ASTExpression getInstance() {
            return instance;
        }

        public ASTTypeExpression getType() {
            return type;
        }
    }

    protected class ASTBinaryExpression extends ASTExpression {
        private String op;
        private ASTExpression lhs;
        private ASTExpression rhs;

        public String getOp() {
            return op;
        }
        public ASTExpression getLhs() {
            return lhs;
        }
        public ASTExpression getRhs() {
            return rhs;
        }
    }

    protected class ASTTernaryExpession extends ASTExpression {
        private ASTExpression condition;
        private ASTExpression trueValue;
        private ASTExpression falseValue;

        public ASTExpression getCondition() {
            return condition;
        }

        public ASTExpression getTrueValue() {
            return trueValue;
        }

        public ASTExpression getFalseValue() {
            return falseValue;
        }
    }

    protected class ASTInvocation extends ASTExpression {
        private ASTExpression receiver;
        private List<ASTExpression> arguments;

        void add(ASTExpression arg) {
            if (arguments == null) {
                arguments = new ArrayList<>();
            }
            arguments.add(arg);
        }

        public int getArgCount() {
            int result = 0;

            if (arguments != null) {
                result = arguments.size();
            }
            return result;
        }

        public String getMethodName() {
            String result = null;
            ASTExpression node = receiver;

            while (result == null) {
                if (node instanceof ASTPrimaryExpression) {
                    result = ((ASTPrimaryExpression) node).name;
                }
                else if (node instanceof ASTBinaryExpression) {
                    node = ((ASTBinaryExpression) node).rhs;
                }
                else {
                    throw new UnsupportedOperationException();
                }
            }
            return result;
        }

        public ASTExpression getReceiver() {
            return receiver;
        }

        public List<ASTExpression> getArguments() {
            return arguments;
        }

        public void setReceiver(ASTExpression receiver) {
            this.receiver = receiver;
        }
    }

    protected class ASTAllocation extends ASTInvocation {}

    protected class ASTPreOrPostfixExpression extends ASTUnaryExpression {
        private boolean postfix;

        public boolean isPostfix() {
            return postfix;
        }
    }
    
    protected class ASTStatement extends ASTHelperNode {}
    protected class ASTBreakStatement extends ASTStatement {}
    protected class ASTContinueStatement extends ASTStatement {}

    protected class ASTStatementList extends ASTStatement {
        private  List<ASTStatement> statements;

        public List<ASTStatement> getStatements() {
            return statements;
        }

        void add(ASTStatement stmt) {
            if (statements == null) {
                statements = new ArrayList<>();
            }
            statements.add(stmt);
        }
    }

    protected class ASTIfStatement extends ASTStatement {
        private ASTExpression condition;
        private ASTStatement thenStmts;
        private ASTStatement elseStmts;

        public ASTExpression getCondition() {
            return condition;
        }

        public ASTStatement getThenStmts() {
            return thenStmts;
        }

        public ASTStatement getElseStmts() {
            return elseStmts;
        }
    }

    protected class ASTReturnStatement extends ASTStatement {
        private ASTExpression value;

        public ASTExpression getValue() {
            return value;
        }
        public void setValue(ASTExpression value) { this.value = value; }
    }

    protected class ASTExpressionStatement extends ASTReturnStatement {}

    protected class ASTForStatement extends ASTStatement {
        private ASTVariableOrFieldDeclaration variable;
        private ASTExpression iterable;
        private ASTExpression condition;
        private ASTStatement statements;
        private List<ASTExpression> iteration;

        public ASTVariableOrFieldDeclaration getVariable() {
            return variable;
        }

        public ASTExpression getIterable() {
            return iterable;
        }

        public ASTExpression getCondition() {
            return condition;
        }

        public ASTStatement getStatements() {
            return statements;
        }

        public List<ASTExpression> getIteration() {
            return iteration;
        }

        void add(ASTExpression iter) {
            if (iteration == null) {
                iteration = new ArrayList<>();
            }
            iteration.add(iter);
        }
    }

    protected class ASTWhileStatement extends ASTStatement {
        private ASTExpression condition;
        private ASTStatement statements;

        public ASTExpression getCondition() {
            return condition;
        }

        public ASTStatement getStatements() {
            return statements;
        }
    }

    protected class ASTCaseStatement extends ASTStatement {
        private List<ASTExpression> caseLabels;
        private ASTStatementList statements;
        private boolean defaultCase;
        private boolean hasbreak;

        public List<ASTExpression> getCaseLabels() {
            return caseLabels;
        }

        public ASTStatementList getStatements() {
            return statements;
        }

        void add(ASTStatement stmt) {
            if (statements == null) {
                statements = new ASTStatementList();
            }
            statements.add(stmt);
        }

        public boolean isDefaultCase() {
            return defaultCase;
        }

        public boolean hasBreak() {
            return hasbreak;
        }
    }

    protected class ASTSwitchStatement extends ASTStatement {
        protected ASTExpression variable;
        protected List<ASTCaseStatement> cases;

        public ASTExpression getVariable() {
            return variable;
        }

        public List<ASTCaseStatement> getCases() {
            return cases;
        }

        void add(ASTCaseStatement c) {
            if (cases == null) {
                cases = new ArrayList<>();
            }
            cases.add(c);
        }
    }

    protected class ASTFormalParameter extends ASTHelperNode {
        protected ASTTypeExpression type;
        protected String name;

        public ASTTypeExpression getType() {
            return type;
        }

        public String getName() {
            return name;
        }
    }

    protected class ASTMethodDeclaration extends ASTStatement {
        protected List<String> modifiers;
        protected ASTTypeExpression returnType;
        protected String name;
        protected List<ASTFormalParameter> parameters;
        protected ASTStatementList statements;

        void addModifier(String modifier) {
            if (modifiers == null) {
                modifiers = new ArrayList<>();
            }
            modifiers.add(modifier);
        }

        void addParameter(ASTFormalParameter parameter) {
            if (parameters == null) {
                parameters = new ArrayList<>();
            }
            parameters.add(parameter);
        }

        public String getName() {
            return name;
        }

        public List<ASTFormalParameter> getParameters() {
            return parameters;
        }

        public ASTStatementList getStatements() { return statements; }
    }

    protected class ASTVariableOrFieldDeclaration extends ASTStatement {
        ASTTypeExpression type;
        List<ASTPrimaryExpression> names;
        List<ASTExpression> initializers;
        Set<String> annotations;
        boolean field;
        List<String> modifiers;

        public boolean isField() { return field; }

        public ASTTypeExpression getType() { return type; }

        public List<ASTPrimaryExpression> getNames() { return names; }

        public List<ASTExpression> getInitializers() { return initializers; }

        public Set<String> getAnnotations() { return annotations; }

        public boolean hasAnnotation(String annotation) {
            return (annotations != null) && annotations.contains(annotation);
        }

        private void addModifier(String modifier) {
            if (modifiers == null) {
                modifiers = new ArrayList<>();
            }
            modifiers.add(modifier);
        }

        private void addAnnotation(String annotation) {
            if (annotations == null) {
                annotations = new HashSet<>();
            }
            annotations.add(annotation);
        }

        private void addNameAndInitializer(ASTPrimaryExpression name, ASTExpression initializer) {
            if (names == null) {
                names = new ArrayList<>();
                initializers = new ArrayList<>();
            }
            names.add(name);
            initializers.add(initializer);
        }

        public boolean hasInitializer() {
            if (initializers == null) {
                return false;
            }
            for (ASTExpression e : initializers) {
                if (e != null) {
                    return true;
                }
            }
            return false;
        }
    }

    public static class SymbolTable extends HashMap<String, ASTTypeExpression> {

    }

    protected List<SymbolTable> symbolStack = new ArrayList<>();

    protected Set<String> properties = new HashSet<>();
    protected SymbolTable fields = new SymbolTable();
    protected Map<String, Set<String>> propertyMap = new HashMap<>();
    protected Set<String> parameterNames = new HashSet<>();

    public void clearFields() { fields.clear(); properties.clear(); }

    public void pushSymbols(SymbolTable syms) {
        symbolStack.add(syms);
    }

    public void popSymbols() {
        symbolStack.remove(symbolStack.size() - 1);
    }

    public void clearSymbols() { symbolStack.clear(); }

    public void addSymbol(String name, ASTTypeExpression type) {
        SymbolTable latest = symbolStack.get(symbolStack.size() - 1);
        latest.put(name, type);
    }

    public void addParameterName(String name) {
        parameterNames.add(name);
    }

    public boolean isParameterName(String name) {
        return parameterNames.contains(name);
    }

    public void clearParameterNames() {
        parameterNames.clear();
    }

    public ASTTypeExpression findSymbol(String name) {
        ASTTypeExpression result = null;

        for (int n = symbolStack.size() - 1; (result == null) && (n >= 0); --n) {
            SymbolTable syms = symbolStack.get(n);
            result = syms.get(name);
        }
        return result;
    }

    public Translator(Grammar grammar) {
        this.grammar = grammar;
        tokenNames = grammar.getUtils().getTokenNames();
    }

    public static Translator getTranslatorFor(Grammar grammar) {
        String codeLang = grammar.getCodeLang();

        if (codeLang.equals("python")) {
            return new PythonTranslator(grammar);
        }
        // Add other language translator cases here
        return new Translator(grammar); // handle the Java case
    }

    protected String getTempVarName() {
        return String.format("_tv_%s", ++tempVarCounter);
    }

    public String translateOperator(String operator) { return operator; }

    public static String camelToSnake(String ident) {
        StringBuilder sb = new StringBuilder();

        sb.append(ident.charAt(0));
        ident = ident.substring(1);
        for (int i = 0; i < ident.length(); i++) {
            char c = ident.charAt(i);
            if (!Character.isUpperCase(c)) {
                sb.append(c);
            }
            else {
                sb.append('_');
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    public String translateIdentifier(String ident) { return ident; }

    public String translateGetter(String getterName) { return getterName; }

    public boolean isGetter(String name) {
        int n = name.length();
        if ((n < 3) || !(name.startsWith("get") || name.startsWith("is"))) {
            return false;
        }
        int idx = name.startsWith("is") ? 2: 3;
        return (idx < n) && Character.isUpperCase(name.charAt(idx));
    }

    public boolean isSetter(String name) {
        if ((name.length() <= 3) || !name.startsWith("set")) {
            return false;
        }
        return Character.isUpperCase(name.charAt(3));
    }

    ASTExpression transformName(Node name) {
        ASTExpression result;
        int m = name.getChildCount();
        if (m == 1) {
            // just a name
            ASTPrimaryExpression resultNode = new ASTPrimaryExpression();
            result = resultNode;
            resultNode.name = ((Identifier) name.getFirstChild()).getImage();
        }
        else {
            // dotted name
            ASTBinaryExpression lhs = new ASTBinaryExpression();
            lhs.op = ".";
            ASTPrimaryExpression pe = new ASTPrimaryExpression();
            pe.name = ((Identifier) name.getChild(0)).getImage();
            lhs.lhs = pe;
            pe = new ASTPrimaryExpression();
            pe.name = ((Identifier) name.getChild(2)).getImage();
            lhs.rhs = pe;
            result = lhs;
            for (int j = 4; j < m; j += 2) {
                ASTBinaryExpression newNode = new ASTBinaryExpression();
                newNode.lhs = lhs;
                lhs.parent = newNode;
                pe = new ASTPrimaryExpression();
                pe.name = ((Identifier) name.getChild(j)).getImage();
                newNode.op = ".";
                newNode.rhs = pe;
                pe.parent = newNode;
                lhs = newNode;
                result = newNode;
            }
        }
        return result;
    }

    // Called when a node's last child is a MethodCall instance.
    ASTInvocation transformMethodCall(Node node) {
        if (node.getChildCount() != 2) {
            throw new UnsupportedOperationException();
        }
        ASTInvocation result = new ASTInvocation();
        result.receiver = (ASTExpression) transformTree(node.getFirstChild());
        Node args = node.getLastChild();
        int m = args.getChildCount();
        for (int j = 1; j < (m - 1); j++) {
            ASTExpression arg = (ASTExpression) transformTree(args.getChild(j));
            result.add(arg);
        }
        return result;
    }

    private void transformArgs(Node args, ASTInvocation resultNode) {
        int m = args.getChildCount();
        for (int j = 1; j < (m - 1); j++) {
            Node arg = args.getChild(j);
            if (!(arg instanceof Delimiter)) {
                resultNode.add((ASTExpression) transformTree(arg));
            }
        }
    }

    ASTHelperNode transformTree(Node node, boolean forType) {
        ASTHelperNode result = null;

        if (node instanceof Delimiter || node instanceof Operator) {
            throw new IllegalArgumentException("internal error");
        }
        else if (node instanceof Name) {
            return transformName(node);
        }
        else if (node instanceof Parentheses) {
            return transformTree(node.getChild(1));
        }
        else if (node instanceof ClassLiteral) {
            ASTTypeExpression resultNode = new ASTTypeExpression();
            resultNode.name = node.getFirstChild().toString();
            return resultNode;
        }
        else if (node instanceof Initializer) {
            return transformTree(node.getFirstChild());
        }
        else if (node instanceof MethodCall) {
            ASTInvocation resultNode = new ASTInvocation();
            resultNode.receiver = (ASTExpression) transformTree(node.getFirstChild());
            transformArgs(node.getLastChild(), resultNode);
            return resultNode;
        }
        else if (node instanceof DotName) {
            ASTBinaryExpression resultNode = new ASTBinaryExpression();
            resultNode.op = ".";
            resultNode.lhs = (ASTExpression) transformTree(node.getFirstChild());
            resultNode.rhs = (ASTExpression) transformTree(node.getLastChild());
            return resultNode;
        }
        else if (node instanceof AllocationExpression) {
            ASTAllocation resultNode = new ASTAllocation();
            resultNode.setReceiver((ASTExpression) transformTree(node.getChild(1)));
            transformArgs(node.getLastChild(), resultNode);
            return resultNode;
        }
        else if (node instanceof Identifier) {
            ASTPrimaryExpression resultNode = forType ? new ASTTypeExpression() : new ASTPrimaryExpression();
            resultNode.name = ((Token) node).getImage();
            return resultNode;
        }
        else if (node instanceof Token) {
            ASTPrimaryExpression resultNode = forType ? new ASTTypeExpression() : new ASTPrimaryExpression();
            resultNode.literal = ((Token) node).getImage();
            return resultNode;
        }
        else if (node instanceof LiteralExpression) {
            ASTPrimaryExpression resultNode = forType ? new ASTTypeExpression() : new ASTPrimaryExpression();
            resultNode.literal = ((Token) node.getFirstChild()).getImage();
            return resultNode;
        }
        else if (node instanceof ObjectType) {
            ASTTypeExpression resultNode = new ASTTypeExpression();
            resultNode.name = ((Identifier) node.getFirstChild()).getImage();
            return resultNode;
        }
        else if (node instanceof ReturnType) {
            return transformTree(node.getFirstChild(), true);
        }
        else if (node instanceof UnaryExpressionNotPlusMinus) {
            ASTUnaryExpression resultNode = new ASTUnaryExpression();
            result = resultNode;
            resultNode.op = ((Operator) node.getChild(0)).getImage();
            resultNode.operand = (ASTExpression) transformTree(node.getChild(1));
        }
        else if (node instanceof PostfixExpression) {
            if (node.getLastChild() instanceof MethodCall) {
                return transformMethodCall(node);
            }
            else {
                ASTPreOrPostfixExpression resultNode = new ASTPreOrPostfixExpression();

                resultNode.op = node.getLastChild().toString();
                resultNode.operand = (ASTExpression) transformTree(node.getFirstChild());
                resultNode.postfix = true;
                return resultNode;
            }
        }
        else if (node instanceof TernaryExpression) {
            ASTTernaryExpession resultNode = new ASTTernaryExpession();
            result = resultNode;
            resultNode.condition = (ASTExpression) transformTree(node.getFirstChild());
            resultNode.trueValue = (ASTExpression) transformTree(node.getChild(2));
            resultNode.falseValue = (ASTExpression) transformTree(node.getLastChild());
        }
        else if (node instanceof ConditionalOrExpression ||
                node instanceof ConditionalAndExpression ||
                node instanceof InclusiveOrExpression ||
                node instanceof ExclusiveOrExpression ||
                node instanceof AndExpression ||
                node instanceof EqualityExpression ||
                node instanceof RelationalExpression ||
                node instanceof ShiftExpression ||
                node instanceof AdditiveExpression ||
                node instanceof MultiplicativeExpression) {
            int n = node.getChildCount();
            ASTBinaryExpression lhs = new ASTBinaryExpression();
            result = lhs;
            lhs.op = ((Operator) node.getChild(1)).getImage();
            lhs.lhs = (ASTExpression) transformTree(node.getChild(0));
            lhs.lhs.parent = lhs;
            lhs.rhs = (ASTExpression) transformTree(node.getChild(2));
            lhs.rhs.parent = lhs;
            if (n > 3) {
                for (int i = 3; i < n; i += 2) {
                    ASTBinaryExpression newNode = new ASTBinaryExpression();
                    newNode.op = ((Operator) node.getChild(i)).getImage();
                    newNode.rhs = (ASTExpression) transformTree(node.getChild(i + 1));
                    newNode.rhs.parent = newNode;
                    newNode.lhs = lhs;
                    newNode.lhs.parent = newNode;
                    lhs = newNode;
                    result = newNode;
                }
            }
        }
        else if (node instanceof ObjectCastExpression) {
            return transformTree(node.getChild(3));
        }
        else if (node instanceof InstanceOfExpression) {
            ASTInstanceofExpression resultNode = new ASTInstanceofExpression();
            resultNode.instance = (ASTExpression) transformTree(node.getFirstChild());
            resultNode.type = (ASTTypeExpression) transformTree(node.getLastChild(), true);
            return resultNode;
        }
        else if (node instanceof AssignmentExpression) {
            ASTBinaryExpression resultNode = new ASTBinaryExpression();
            result = resultNode;
            resultNode.op = "=";
            resultNode.lhs = (ASTExpression) transformTree(node.getFirstChild());
            resultNode.rhs = (ASTExpression) transformTree(node.getLastChild());
        }
        else if (node instanceof BreakStatement) {
            return new ASTBreakStatement();
        }
        else if (node instanceof ContinueStatement) {
            return new ASTContinueStatement();
        }
        else if (node instanceof ExpressionStatement) {
            ASTExpressionStatement resultNode = new ASTExpressionStatement();
            resultNode.setValue((ASTExpression) transformTree(node.getChild(0)));
            return resultNode;
        }
        else if (node instanceof BlockStatement) {
            return transformTree(node.getChild(0));
        }
        else if (node instanceof CodeBlock) {
            ASTStatementList resultNode = new ASTStatementList();
            int n = node.getChildCount();
            for (int i = 0; i < n; i++) {
                Node child = node.getChild(i);
                if (!(child instanceof Delimiter)) {
                    resultNode.add((ASTStatement) transformTree(child));
                }
            }
            return resultNode;
        }
        else if (node instanceof LocalVariableDeclaration || node instanceof FieldDeclaration) {
            ASTVariableOrFieldDeclaration resultNode = new ASTVariableOrFieldDeclaration();
            resultNode.field = node instanceof FieldDeclaration;
            int n = node.getChildCount();
            for (int i = 0; i < n; i++) {
                ASTPrimaryExpression name;
                ASTExpression initializer;
                Node child = node.getChild(i);

                if (child instanceof Primitive || child instanceof ObjectType) {
                    resultNode.type = (ASTTypeExpression) transformTree(child, true);
                }
                else if (child instanceof KeyWord) {
                    resultNode.addModifier(child.toString());
                }
                else if (child instanceof Delimiter) {
                    continue;
                }
                else if (child instanceof Identifier) {
                    name = (ASTPrimaryExpression) transformTree(child);
                    resultNode.addNameAndInitializer(name, null);
                }
                else if (child instanceof VariableDeclarator) {
                    name = (ASTPrimaryExpression) transformTree(child.getFirstChild());
                    initializer = (ASTExpression) transformTree(child.getLastChild());
                    resultNode.addNameAndInitializer(name, initializer);
                }
                else if (child instanceof MarkerAnnotation) {
                    resultNode.addAnnotation(child.getLastChild().toString());
                }
                else {
                    throw new UnsupportedOperationException();
                }
            }
            return resultNode;
        }
        else if (node instanceof ReturnStatement) {
            ASTReturnStatement resultNode = new ASTReturnStatement();
            resultNode.value = (ASTExpression) transformTree(node.getChild(1));
            return resultNode;
        }
        else if (node instanceof IfStatement) {
            ASTIfStatement resultNode = new ASTIfStatement();
            resultNode.condition = (ASTExpression) transformTree(node.getChild(2));
            resultNode.thenStmts = (ASTStatement) transformTree(node.getChild(4));
            return resultNode;
        }
        else if (node instanceof ForStatement) {
            int n = node.getChildCount();
            ASTForStatement resultNode = new ASTForStatement();
            if (node.getChild(4).toString().equals(":")) {
                // iterating for loop
                ASTVariableOrFieldDeclaration decl = new ASTVariableOrFieldDeclaration();
                decl.type = (ASTTypeExpression) transformTree(node.getChild(2), true);
                decl.addNameAndInitializer((ASTPrimaryExpression) transformTree(node.getChild(3)), null);
                resultNode.variable = decl;
                resultNode.iterable = (ASTExpression) transformTree(node.getChild(5));
            }
            else {
                // counted for loop
                resultNode.variable = (ASTVariableOrFieldDeclaration) transformTree(node.getChild(2));
                resultNode.condition = (ASTExpression) transformTree(node.getChild(4));
                for (int i = 5; i < (n - 1); i++) {
                    Node child = node.getChild(i);
                    if (child instanceof Expression) {
                        resultNode.add((ASTExpression) transformTree(child));
                    }
                }
            }
            resultNode.statements = (ASTStatement) transformTree(node.getLastChild());
            return resultNode;
        }
        else if (node instanceof ClassicSwitchStatement) {
            ASTSwitchStatement resultNode = new ASTSwitchStatement();
            int n = node.getChildCount();
            List<ASTExpression> pendingLabels = new ArrayList<>();
            resultNode.variable = (ASTExpression) transformTree(node.getChild(2));
            ASTCaseStatement currentCase = null;
            for (int i = 5; i < n; i++) {
                Node child = node.getChild(i);
                if (!(child instanceof Delimiter)) {
                    if (child instanceof ClassicSwitchLabel) {
                        if (child.getFirstChild().toString().equals("case")) {
                            pendingLabels.add((ASTExpression) transformTree(child.getChild(1)));
                        }
                        else {
                            // must be a default: label
                            if (currentCase != null) {
                                currentCase.defaultCase = true;
                            }
                        }
                    }
                    else if (child instanceof ClassicCaseStatement) {
                        currentCase = new ASTCaseStatement();
                        Node label = child.getChild(0);
                        if (label.getChildCount() < 3) {
                            // default case - don't add to labels
                            currentCase.defaultCase = true;
                        }
                        else {
                            pendingLabels.add((ASTExpression) transformTree(label.getChild(1)));
                        }
                        currentCase.caseLabels = new ArrayList<>(pendingLabels);
                        pendingLabels.clear();
                        int m = child.getChildCount();
                        for (int j = 1; j < m; j++) {
                            ASTStatement s = (ASTStatement) transformTree(child.getChild(j));
                            if (s instanceof ASTBreakStatement) {
                                currentCase.hasbreak = true;
                            }
                            else {
                                currentCase.add(s);
                            }
                        }
                        resultNode.add(currentCase);
                        currentCase = null;
                    }
                    else {
                        throw new UnsupportedOperationException();
                    }
                }
            }
            return resultNode;
        }
        else if (node instanceof MethodDeclaration) {
            ASTMethodDeclaration resultNode = new ASTMethodDeclaration();
            int n = node.getChildCount();
            for (int i = 0; i < (n - 1); i++) {
                Node child = node.getChild(i);
                if (child instanceof KeyWord) {
                    resultNode.addModifier(child.toString());
                }
                else if (child instanceof ReturnType) {
                    resultNode.returnType = (ASTTypeExpression) transformTree(child, true);
                }
                else if (child instanceof Identifier) {
                    resultNode.name = ((Identifier) child).getNormalizedText();
                }
                else if (child instanceof FormalParameters) {
                    int m;

                    if ((m = child.getChildCount()) > 2) {
                        for (int j = 1; j < (m - 1); j++) {
                            Node arg = child.getChild(j);
                            if (!(arg instanceof Delimiter)) {
                                ASTFormalParameter formal = new ASTFormalParameter();
                                formal.type = (ASTTypeExpression) transformTree(arg.getFirstChild(), true);
                                formal.name = arg.getLastChild().toString();
                                resultNode.addParameter(formal);
                            }
                        }
                    }
                }
            }
            resultNode.statements = (ASTStatementList) transformTree(node.getLastChild());
            return resultNode;
        }
        else if (node instanceof StatementExpression) {
            Node child = node.getLastChild();
            if (child instanceof MethodCall) {
                return transformMethodCall(node);
            }
            else {
                throw new UnsupportedOperationException();
            }
        }
        if (result == null) {
            throw new UnsupportedOperationException();
        }
        return result;
    }

    ASTHelperNode transformTree(Node node) { return transformTree(node, false); }

    public void fail() throws UnsupportedOperationException {
        String message = String.format("not supported by translator for the '%s' language", grammar.getCodeLang());
        throw new UnsupportedOperationException(message);
    }

    public boolean isNull(ASTExpression expr) {
        String literal;

        return (expr instanceof ASTPrimaryExpression) && ((literal = ((ASTPrimaryExpression) expr).getLiteral()) != null) && literal.equals("null");
    }

    public boolean isAssignment(ASTExpression expr) {
        return (expr instanceof ASTBinaryExpression) && ((ASTBinaryExpression) expr).getOp().equals("=");
    }

    protected void translatePrimaryExpression(ASTPrimaryExpression expr, StringBuilder result) {
        fail();
    }

    protected void translateUnaryExpression(ASTUnaryExpression expr, StringBuilder result) {
        fail();
    }

    protected void translateBinaryExpression(ASTBinaryExpression expr, StringBuilder result) {
        fail();
    }

    protected void translateTernaryExpression(ASTTernaryExpession expr, StringBuilder result) {
        fail();
    }

    protected void translateInstanceofExpression(ASTInstanceofExpression expr, StringBuilder result) {
        fail();
    }

    protected void translateInvocation(ASTInvocation expr, StringBuilder result) {
        fail();
    }

    protected void internalTranslateExpression(ASTExpression expr, StringBuilder result) {
        if (expr instanceof ASTPrimaryExpression) {
            translatePrimaryExpression((ASTPrimaryExpression) expr, result);
        }
        else if (expr instanceof ASTUnaryExpression) {
            translateUnaryExpression((ASTUnaryExpression) expr, result);
        }
        else if (expr instanceof ASTBinaryExpression) {
            translateBinaryExpression((ASTBinaryExpression) expr, result);
        }
        else if (expr instanceof ASTTernaryExpession) {
            translateTernaryExpression((ASTTernaryExpession) expr, result);
        }
        else if (expr instanceof ASTInvocation) {
            translateInvocation((ASTInvocation) expr, result);
        }
        else if (expr instanceof ASTInstanceofExpression) {
            translateInstanceofExpression((ASTInstanceofExpression) expr, result);
        }
        else {
            throw new UnsupportedOperationException();
        }
    }

    public void translateExpression(Node expr, StringBuilder result) {
        ASTExpression node = (ASTExpression) transformTree(expr);
        internalTranslateExpression(node, result);
    }

    protected void addIndent(int amount, StringBuilder result) {
        for (int i = 0; i < amount; i++) {
            result.append(' ');
        }
    }

    protected void internalTranslateStatement(ASTStatement stmt, int indent, StringBuilder result) {
        fail();
    }

    public void translateStatement(Node stmt, int indent, StringBuilder result) {
        ASTStatement node = (ASTStatement) transformTree(stmt);
        internalTranslateStatement(node, indent, result);
    }

    public void translateProperties(String name, int indent, StringBuilder result) {
        if (!properties.isEmpty()) {
            propertyMap.put(name, new HashSet<>(properties));
        }
    }
}
