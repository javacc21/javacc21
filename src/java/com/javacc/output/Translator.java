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

package com.javacc.output;

import java.util.*;

import com.javacc.Grammar;
import com.javacc.output.csharp.CSharpTranslator;
import com.javacc.output.java.CodeInjector;
import com.javacc.output.python.PythonTranslator;
import com.javacc.parser.Node;
import com.javacc.parser.Token;
import com.javacc.parser.tree.*;

public class Translator {
    protected Grammar grammar;
    protected int tempVarCounter;
    protected Set<String> tokenNames;
    protected int fieldIndent;
    protected int methodIndent;
    protected boolean isTyped;
    protected boolean inInterface;
    protected String currentClass;

    public static <T> Set<T> makeSet(T... objs) {
        Set<T> set = new HashSet<>();
        Collections.addAll(set, objs);
        return set;
    }

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
        ASTTypeExpression cast;

        public ASTTypeExpression getCast() {
            return cast;
        }

        public ASTExpression() {
            this(null);
        }

        public ASTExpression(ASTHelperNode parent) {
            this.parent = parent;
        }
    }

    protected class ASTPrimaryExpression extends ASTExpression {
        protected String name;
        protected String literal;

        public ASTPrimaryExpression() {
            this(null);
        }

        public ASTPrimaryExpression(ASTHelperNode parent) {
            this.parent = parent;
        }

        public String getName() {
            return name;
        }

        public String getLiteral() {
            return literal;
        }
    }

    protected class ASTTypeExpression extends ASTPrimaryExpression {
        protected List<ASTTypeExpression> typeParameters;

        public boolean isNumeric() {
            return ((literal != null) || name.equals("Integer") ||
                              name.equals("Long") || name.equals("Float") ||
                              name.equals("Double") || name.equals("BigInteger"));
        }

        void add(ASTTypeExpression tp) {
            if (typeParameters == null) {
                typeParameters = new ArrayList<>();
            }
            typeParameters.add(tp);
        }

        public List<ASTTypeExpression> getTypeParameters() { return typeParameters; }
    }

    protected class ASTUnaryExpression extends ASTExpression {
        protected String op;
        private ASTExpression operand;

        public String getOp() {
            return op;
        }

        public ASTExpression getOperand() {
            return operand;
        }
        public void setOperand(ASTExpression expr) {
            operand = expr;
            expr.parent = this;
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

        private void setLhs(ASTExpression expr) {
            lhs = expr;
            expr.parent = this;
        }

        private void setRhs(ASTExpression expr) {
            rhs = expr;
            expr.parent = this;
        }
    }

    protected class ASTTernaryExpression extends ASTExpression {
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
        protected ASTExpression receiver;
        protected List<ASTExpression> arguments;

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
                    if (result == null) {
                        result = ((ASTPrimaryExpression) node).literal;
                    }
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

    protected class ASTArrayAccess extends ASTExpression {
        protected ASTExpression array;
        protected ASTExpression index;

        public ASTExpression getArray() {
            return array;
        }

        public ASTExpression getIndex() {
            return index;
        }
    }

    protected class ASTMethodReference extends ASTExpression {
        protected ASTTypeExpression type;
        protected List<ASTTypeExpression> typeArguments;
        protected ASTExpression identifier;

        public ASTTypeExpression getType() {
            return type;
        }

        public List<ASTTypeExpression> getTypeArguments() {
            return typeArguments;
        }

        public ASTExpression getIdentifier() {
            return identifier;
        }
    }

    protected class ASTExplicitConstructorInvocation extends ASTInvocation {
        protected List<ASTTypeExpression> typeArguments;

        public List<ASTTypeExpression> getTypeArguments() {
            return typeArguments;
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

    protected class ASTExpressionStatement extends ASTStatement {
        private ASTExpression value;

        public ASTExpression getValue() {
            return value;
        }
        public void setValue(ASTExpression value) { this.value = value; }
    }

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
        private boolean hasBreak;

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
            return hasBreak;
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

    protected class ASTAssertStatement extends ASTStatement {
        protected ASTExpression condition;
        protected ASTExpression message;

        public ASTExpression getCondition() {
            return condition;
        }

        public ASTExpression getMessage() {
            return message;
        }
    }

    protected class ASTExceptionInfo {
        protected List<ASTTypeExpression> exceptionTypes;
        protected String variable;
        protected ASTStatement block;

        protected void addExceptionType(ASTTypeExpression te) {
            if (exceptionTypes == null) {
                exceptionTypes = new ArrayList<>();
            }
            exceptionTypes.add(te);
        }

        public String getVariable() {
            return variable;
        }

        public ASTStatement getBlock() {
            return block;
        }

        public List<ASTTypeExpression> getExceptionTypes() {
            return exceptionTypes;
        }
    }

    protected class ASTTryStatement extends ASTStatement {
        protected List<ASTHelperNode> resources;
        protected ASTStatement block;
        protected List<ASTExceptionInfo> catchBlocks;
        protected ASTStatement finallyBlock;

        protected void addCatchBlock(ASTExceptionInfo info) {
            if (catchBlocks == null) {
                catchBlocks = new ArrayList<ASTExceptionInfo>();
            }
            catchBlocks.add(info);
        }

        public ASTStatement getBlock() {
            return block;
        }

        public List<ASTExceptionInfo> getCatchBlocks() {
            return catchBlocks;
        }

        public ASTStatement getFinallyBlock() {
            return finallyBlock;
        }
    }

    protected class ASTFormalParameter extends ASTHelperNode {
        protected boolean isFinal;
        protected ASTTypeExpression type;
        protected String name;

        public ASTTypeExpression getType() {
            return type;
        }

        public String getName() {
            return name;
        }
    }

    abstract class ASTStatementWithName extends ASTStatement {
        protected String name;

        public String getName() {
            return name;
        }
    }

    protected class ASTMethodDeclaration extends ASTStatementWithName {
        protected List<String> modifiers;

        protected ASTTypeExpression returnType;
        protected List<ASTFormalParameter> parameters;
        protected ASTStatementList statements;
        protected boolean constructor;

        public List<String> getModifiers() {
            return modifiers;
        }

        void addModifier(String modifier) {
            if (modifiers == null) {
                modifiers = new ArrayList<>();
            }
            modifiers.add(modifier);
        }

        public boolean isConstructor() {
            return constructor;
        }

        void addParameter(ASTFormalParameter parameter) {
            if (parameters == null) {
                parameters = new ArrayList<>();
            }
            parameters.add(parameter);
        }

        public List<ASTFormalParameter> getParameters() {
            return parameters;
        }

        public ASTStatementList getStatements() { return statements; }

        public ASTTypeExpression getReturnType() { return returnType; }
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

        public List<String> getModifiers() {
            return modifiers;
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

    protected class ASTEnumDeclaration extends ASTStatementWithName {
        protected List<String> values;

        public List<String> getValues() { return values; }

        protected void addValue(String v) {
            if (values == null) {
                values = new ArrayList<>();
            }
            values.add(v);
        }
    }

    protected class ASTClassDeclaration extends ASTStatementWithName {
        protected List<ASTStatement> declarations;

        public List<ASTStatement> getDeclarations() { return declarations; }

        protected void addDeclaration(ASTStatement decl) {
            if (declarations == null) {
                declarations = new ArrayList<>();
            }
            declarations.add(decl);
        }
    }

    public static class SymbolTable extends HashMap<String, ASTTypeExpression> {

    }

    protected List<SymbolTable> symbolStack = new ArrayList<>();

    protected Map<String, ASTTypeExpression> properties = new HashMap<>();
    protected SymbolTable fields = new SymbolTable();
    protected Map<String, Set<String>> propertyMap = new HashMap<>();
    protected Set<String> parameterNames = new HashSet<>();

    public void clearFields() { fields.clear(); properties.clear(); }

    public void pushSymbols(SymbolTable symbols) {
        symbolStack.add(symbols);
    }

    public void popSymbols() {
        symbolStack.remove(symbolStack.size() - 1);
    }

    public SymbolTable topSymbols() { return symbolStack.get(symbolStack.size() - 1); }

    // public void clearSymbols() { symbolStack.clear(); }

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
            SymbolTable symbols = symbolStack.get(n);
            result = symbols.get(name);
        }
        return result;
    }

    public Translator(Grammar grammar) {
        this.grammar = grammar;
        tokenNames = grammar.getUtils().getTokenNames();
    }

    public int getFieldIndent() {
        return fieldIndent;
    }

    public int getMethodIndent() {
        return methodIndent;
    }

    public static Translator getTranslatorFor(Grammar grammar) {
        String codeLang = grammar.getCodeLang();

        if (codeLang.equals("python")) {
            return new PythonTranslator(grammar);
        }
        else if (codeLang.equals("csharp")) {
            return new CSharpTranslator(grammar);
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

    public enum TranslationContext {
        VARIABLE,
        PARAMETER,
        METHOD,
        FIELD,
        TYPE,
        UNKNOWN
    }

    public String translateIdentifier(String ident, TranslationContext kind) { return ident; }

    public String translateGetter(String getterName) { return getterName; }

    public boolean isGetter(String name) {
        int n = name.length();
        if ((n < 3) || !(name.startsWith("get") || name.startsWith("is"))) {
            return false;
        }
        int idx = name.startsWith("is") ? 2: 3;
        return (idx < n) && Character.isUpperCase(name.charAt(idx));
    }

    protected Set<String> notSetters = new HashSet<>(Arrays.asList("setLineSkipped"));

    public boolean isSetter(String name) {
        if ((name.length() <= 3) || !name.startsWith("set") || notSetters.contains(name)) {
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
            ASTPrimaryExpression pe = new ASTPrimaryExpression(lhs);
            pe.name = ((Identifier) name.getChild(0)).getImage();
            lhs.setLhs(pe);
            pe = new ASTPrimaryExpression(lhs);
            pe.name = ((Identifier) name.getChild(2)).getImage();
            lhs.setRhs(pe);
            result = lhs;
            for (int j = 4; j < m; j += 2) {
                ASTBinaryExpression newNode = new ASTBinaryExpression();
                newNode.setLhs(lhs);
                pe = new ASTPrimaryExpression();
                pe.name = ((Identifier) name.getChild(j)).getImage();
                newNode.op = ".";
                newNode.setRhs(pe);
                lhs = newNode;
                result = newNode;
            }
        }
        return result;
    }

    protected void processArguments(ASTInvocation invocation, Node args) {
        int m = args.getChildCount();
        for (int j = 1; j < (m - 1); j++) {
            Node child = args.getChild(j);
            if (child instanceof Delimiter) {
                continue;
            }
            ASTExpression arg = (ASTExpression) transformTree(child);
            invocation.add(arg);
        }
    }

    // Called when a node's last child is a MethodCall instance.
    ASTInvocation transformMethodCall(Node node) {
        if (node.getChildCount() != 2) {
            throw new UnsupportedOperationException();
        }
        ASTInvocation result = new ASTInvocation();
        result.receiver = (ASTExpression) transformTree(node.getFirstChild());
        Node args = node.getLastChild();
        processArguments(result, args);
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

    protected ASTFormalParameter transformFormal(FormalParameter fp) {
        ASTFormalParameter result = new ASTFormalParameter();
        // A "final" modifier is allowed
        Node ac = fp.getFirstChild();
        result.isFinal = (ac instanceof Token) && ((Token) ac).getImage().equals("final");
        if (result.isFinal) {
            ac = fp.getChild(1);
        }
        result.type = (ASTTypeExpression) transformTree(ac, true);
        result.name = fp.getLastChild().toString();
        return result;
    }

    protected ASTHelperNode transformTree(Node node, boolean forType) {
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
            resultNode.setLhs((ASTExpression) transformTree(node.getFirstChild()));
            resultNode.setRhs((ASTExpression) transformTree(node.getLastChild()));
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
        else if (node instanceof PrimitiveType) {
            ASTTypeExpression resultNode = new ASTTypeExpression();
            if (node.getChildCount() != 1) {
                throw new UnsupportedOperationException();
            }
            Node child = node.getFirstChild();
            return transformTree(child, forType);
        }
        else if (node instanceof ObjectType) {
            ASTTypeExpression resultNode = new ASTTypeExpression();
            int n = node.getChildCount();
            if (n == 1) {
                Node child = node.getFirstChild();
                if (child instanceof ObjectType) {
                    return transformTree(child, forType);
                }
                else if (child instanceof Identifier) {
                    resultNode.name = ((Identifier) child).getImage();
                }
                else {
                    throw new UnsupportedOperationException();
                }
            }
            else {
                StringBuilder sb = new StringBuilder();
                for (Node child : node.children()) {
                    if (child instanceof Token) {
                        sb.append(((Token) child).getImage());
                    }
                    else if (child instanceof TypeArguments) {
                        for (Node gc : child.children()) {
                            if (gc instanceof Operator) {
                                continue;
                            }
                            if (gc instanceof ObjectType) {
                                ASTTypeExpression tp = (ASTTypeExpression) transformTree(gc, true);
                                resultNode.add(tp);
                            }
                            else {
                                throw new UnsupportedOperationException();
                            }
                        }
                    }
                    else {
                        throw new UnsupportedOperationException();
                    }
                }
                resultNode.name = sb.toString();
            }
            return resultNode;
        }
        else if (node instanceof ReturnType) {
            return transformTree(node.getFirstChild(), true);
        }
        else if (node instanceof UnaryExpressionNotPlusMinus || node instanceof UnaryExpression) {
            ASTUnaryExpression resultNode = new ASTUnaryExpression();
            result = resultNode;
            resultNode.op = ((Operator) node.getChild(0)).getImage();
            resultNode.setOperand((ASTExpression) transformTree(node.getChild(1)));
        }
        else if (node instanceof PostfixExpression) {
            if (node.getLastChild() instanceof MethodCall) {
                return transformMethodCall(node);
            }
            else {
                ASTPreOrPostfixExpression resultNode = new ASTPreOrPostfixExpression();

                resultNode.op = node.getLastChild().toString();
                resultNode.setOperand((ASTExpression) transformTree(node.getFirstChild()));
                resultNode.postfix = true;
                return resultNode;
            }
        }
        else if (node instanceof PreDecrementExpression || node instanceof PreIncrementExpression) {
            ASTPreOrPostfixExpression resultNode = new ASTPreOrPostfixExpression();

            resultNode.op = node.getFirstChild().toString();
            resultNode.setOperand((ASTExpression) transformTree(node.getLastChild()));
            return resultNode;
        }
        else if (node instanceof ArrayAccess) {
            ASTArrayAccess resultNode = new ASTArrayAccess();

            resultNode.array = (ASTExpression) transformTree(node.getFirstChild());
            resultNode.index = (ASTExpression) transformTree(node.getChild(2));
            return resultNode;
        }
        else if (node instanceof MethodReference) {
            ASTMethodReference resultNode = new ASTMethodReference();
            int n = node.getChildCount();

            resultNode.type = (ASTTypeExpression) transformTree(node.getFirstChild(), true);
            resultNode.identifier = (ASTExpression) transformTree(node.getLastChild());
            return resultNode;
        }
        else if (node instanceof TernaryExpression) {
            ASTTernaryExpression resultNode = new ASTTernaryExpression();
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
            lhs.setLhs((ASTExpression) transformTree(node.getChild(0)));
            lhs.setRhs((ASTExpression) transformTree(node.getChild(2)));
            if (n > 3) {
                for (int i = 3; i < n; i += 2) {
                    ASTBinaryExpression newNode = new ASTBinaryExpression();
                    newNode.op = ((Operator) node.getChild(i)).getImage();
                    newNode.setRhs((ASTExpression) transformTree(node.getChild(i + 1)));
                    newNode.setLhs(lhs);
                    lhs = newNode;
                    result = newNode;
                }
            }
        }
        else if (node instanceof ObjectCastExpression) {
            ASTExpression resultNode = (ASTExpression) transformTree(node.getChild(3));
            resultNode.cast = (ASTTypeExpression) transformTree(node.getChild(1), true);
            return resultNode;
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
            resultNode.setLhs((ASTExpression) transformTree(node.getFirstChild()));
            resultNode.setRhs((ASTExpression) transformTree(node.getLastChild()));
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
            int n = node.getChildCount();
            if (n == 1) {
                return transformTree(node.getChild(0));
            }

            ASTVariableOrFieldDeclaration resultNode = new ASTVariableOrFieldDeclaration();

            for (int i = 0; i < n; i++) {
                Node child = node.getChild(i);

                if (child instanceof Delimiter) {
                    continue;
                }

                ASTPrimaryExpression name;
                ASTExpression initializer;

                if (child instanceof Primitive || child instanceof PrimitiveType || child instanceof ObjectType) {
                    resultNode.type = (ASTTypeExpression) transformTree(child, true);
                }
                else if (child instanceof Identifier) {
                    name = (ASTPrimaryExpression) transformTree(child);
                    resultNode.addNameAndInitializer(name, null);
                }
                else if (child instanceof VariableDeclarator) {
                    name = (ASTPrimaryExpression) transformTree(child.getFirstChild());
                    initializer = (child.getChildCount() == 1) ? null : (ASTExpression) transformTree(child.getLastChild());
                    resultNode.addNameAndInitializer(name, initializer);
                }
                else if (child instanceof LocalVariableDeclaration) {
                    return transformTree(child, forType);
                }
                else {
                    throw new UnsupportedOperationException();
                }
            }
            return resultNode;
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

                if (child instanceof Delimiter) {
                    continue;
                }
                if (child instanceof Primitive || child instanceof PrimitiveType || child instanceof ObjectType) {
                    resultNode.type = (ASTTypeExpression) transformTree(child, true);
                }
                else if (child instanceof KeyWord) {
                    resultNode.addModifier(child.toString());
                }
                else if (child instanceof Identifier) {
                    name = (ASTPrimaryExpression) transformTree(child);
                    resultNode.addNameAndInitializer(name, null);
                }
                else if (child instanceof VariableDeclarator) {
                    name = (ASTPrimaryExpression) transformTree(child.getFirstChild());
                    initializer = (child.getChildCount() == 1) ? null : (ASTExpression) transformTree(child.getLastChild());
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
            if (node.getChildCount() > 2) {
                resultNode.value = (ASTExpression) transformTree(node.getChild(1));
            }
            return resultNode;
        }
        else if (node instanceof IfStatement) {
            ASTIfStatement resultNode = new ASTIfStatement();
            Node child = node.getNamedChild("condition");
            resultNode.condition = (ASTExpression) transformTree(child);
            child = node.getNamedChild("thenBlock");
            resultNode.thenStmts = (ASTStatement) transformTree(child);
            child = node.getNamedChild("elseBlock");
            if (child != null) {
                resultNode.elseStmts = (ASTStatement) transformTree(child);
            }
            return resultNode;
        }
        else if (node instanceof WhileStatement) {
            ASTWhileStatement resultNode = new ASTWhileStatement();

            resultNode.condition = (ASTExpression) transformTree(node.getChild(2));
            resultNode.statements = (ASTStatement) transformTree(node.getLastChild());
            return resultNode;
        }
        else if (node instanceof BasicForStatement) {
            // counted for loop
            int n = node.getChildCount();
            ASTForStatement resultNode = new ASTForStatement();
            Node child = node.getChild(2);
            if (child instanceof LocalVariableDeclaration) {
                resultNode.variable = (ASTVariableOrFieldDeclaration) transformTree(child, forType);
                resultNode.condition = (ASTExpression) transformTree(node.getChild(4));
            }
            else {
                ASTVariableOrFieldDeclaration vd = new ASTVariableOrFieldDeclaration();
                vd.type = (ASTTypeExpression) transformTree(child, true);
                VariableDeclarator d = (VariableDeclarator) node.getChild(3);
                ASTPrimaryExpression name = (ASTPrimaryExpression) transformTree(d.getFirstChild());
                ASTExpression initializer = (d.getChildCount() == 1) ? null : (ASTExpression) transformTree(d.getLastChild());
                vd.addNameAndInitializer(name, initializer);
                resultNode.variable = vd;
                resultNode.condition = (ASTExpression) transformTree(node.getChild(5));
            }
            for (int i = 6; i < (n - 1); i++) {
                child = node.getChild(i);
                if (child instanceof Expression) {
                    resultNode.add((ASTExpression) transformTree(child));
                }
            }
            resultNode.statements = (ASTStatement) transformTree(node.getLastChild());
            return resultNode;
        }
        else if (node instanceof EnhancedForStatement) {
            // iterating for loop
            ASTForStatement resultNode = new ASTForStatement();
            ASTVariableOrFieldDeclaration decl = new ASTVariableOrFieldDeclaration();
            Node child = node.getChild(2);
            if (child instanceof LocalVariableDeclaration) {
                resultNode.variable = (ASTVariableOrFieldDeclaration) transformTree(child, forType);
                resultNode.iterable = (ASTExpression) transformTree(node.getChild(4));
            }
            else {
                decl.type = (ASTTypeExpression) transformTree(node.getChild(2), true);
                Node vd = node.getChild(3);
                ASTPrimaryExpression name;
                ASTExpression initializer;
                name = (ASTPrimaryExpression) transformTree(vd.getFirstChild());
                initializer = vd.getChildCount() == 1 ? null : (ASTExpression) transformTree(vd.getLastChild());
                decl.addNameAndInitializer(name, initializer);
                resultNode.variable = decl;
                resultNode.iterable = (ASTExpression) transformTree(node.getChild(5));
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
                                currentCase.hasBreak = true;
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
        else if (node instanceof MethodDeclaration || node instanceof ConstructorDeclaration) {
            ASTMethodDeclaration resultNode = new ASTMethodDeclaration();
            resultNode.constructor = node instanceof ConstructorDeclaration;
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
                                ASTFormalParameter formal = transformFormal((FormalParameter) arg);
                                resultNode.addParameter(formal);
                            }
                        }
                    }
                }
            }
            if (node instanceof MethodDeclaration) {
                resultNode.statements = (ASTStatementList) transformTree(node.getLastChild());
            }
            else {
                List<Node> stmts = node.getNamedChildList("statements");
                if (stmts != null) {
                    resultNode.statements = new ASTStatementList();
                    for (Node child : stmts) {
                        ASTStatement stmt = (ASTStatement) transformTree(child);
                        resultNode.statements.add(stmt);
                    }
                }
/*
                for (int i = 0; i < (n - 1); i++) {
                    Node child = node.getChild(i);
                    ASTStatement stmt = null;

                    if (child instanceof ExplicitConstructorInvocation) {
                        ASTExpressionStatement es = new ASTExpressionStatement();
                        es.value = (ASTExpression) transformTree(child);
                        stmt = es;
                    }
                    else if (child instanceof BlockStatement) {
                        stmt = (ASTStatement) transformTree(child);
                    }
                    else {
                        throw new UnsupportedOperationException();
                    }
                    if (stmt != null) {
                        resultNode.statements.add(stmt);
                    }
                }
*/
            }
            return resultNode;
        }
        else if (node instanceof StatementExpression) {
            Node child = node.getLastChild();
            if (child instanceof MethodCall) {
                return transformMethodCall(child);
            }
            else if (child instanceof AssignmentExpression) {
                ASTBinaryExpression resultNode = new ASTBinaryExpression();
                result = resultNode;
                resultNode.op = "=";
                resultNode.setLhs((ASTExpression) transformTree(child.getFirstChild()));
                resultNode.setRhs((ASTExpression) transformTree(child.getLastChild()));
            }
            else if (child instanceof PostfixExpression || child instanceof PreDecrementExpression || child instanceof PreIncrementExpression) {
                return transformTree(child);
            }
            else {
                throw new UnsupportedOperationException();
            }
        }
        else if (node instanceof AssertStatement) {
            ASTAssertStatement resultNode = new ASTAssertStatement();
            resultNode.condition = (ASTExpression) transformTree(node.getChild(1));
            if (node.getChildCount() >= 4) {
                resultNode.message = (ASTExpression) transformTree(node.getChild(3));
            }
            return resultNode;
        }
        else if (node instanceof ExplicitConstructorInvocation) {
            ASTExplicitConstructorInvocation resultNode = new ASTExplicitConstructorInvocation();

            resultNode.receiver = (ASTExpression) transformTree(node.getFirstChild());
            processArguments(resultNode, node.firstChildOfType(InvocationArguments.class));
            return resultNode;
        }
        else if (node instanceof ClassicTryStatement) {
            ASTTryStatement resultNode = new ASTTryStatement();
            resultNode.block = (ASTStatement) transformTree(node.getNamedChild("block"));
            List<Node> catchBlocks = node.getNamedChildList("catchBlocks");
            for (Node cb: catchBlocks) {
                ASTExceptionInfo info = new ASTExceptionInfo();
                List<Node> excTypes = cb.getNamedChildList("exceptionTypes");

                for (Node et: excTypes) {
                    info.addExceptionType((ASTTypeExpression) transformTree(et, true));
                }
                info.variable = ((Token) cb.getNamedChild("varDecl")).getImage();
                info.block = (ASTStatement) transformTree(cb.getLastChild());
                resultNode.addCatchBlock(info);
            }
            Node fb = node.getNamedChild("finallyBlock");
            if (fb != null) {
                resultNode.finallyBlock = (ASTStatement) transformTree(fb);
            }
            return resultNode;
        }
        else if (node instanceof EnumDeclaration) {
            ASTEnumDeclaration resultNode = new ASTEnumDeclaration();

            resultNode.name = ((Token) node.getNamedChild("name")).getImage();
            addNestedDeclaration(resultNode.name);
            List<Node> values = node.getLastChild().getNamedChildList("values");
            for (Node child: values) {
                resultNode.addValue(((Token) child).getImage());
            }
            return resultNode;
        }
        else if (node instanceof ClassDeclaration) {
            ASTClassDeclaration resultNode = new ASTClassDeclaration();

            resultNode.name = ((Token) node.getNamedChild("name")).getImage();
            addNestedDeclaration(resultNode.name);
            List<Node> decls = node.getLastChild().getNamedChildList("decls");
            for (Node decl: decls) {
                resultNode.addDeclaration((ASTStatement) transformTree(decl));
            }
            return resultNode;
        }
        if (result == null) {
            throw new UnsupportedOperationException();
        }
        return result;
    }

    protected ASTHelperNode transformTree(Node node) { return transformTree(node, false); }

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

    protected void translatePrimaryExpression(ASTPrimaryExpression expr, TranslationContext ctx, StringBuilder result) {
        fail();
    }

    protected void translateUnaryExpression(ASTUnaryExpression expr, TranslationContext ctx, StringBuilder result) {
        fail();
    }

    public void translateImport(String javaName, StringBuilder result) {
        fail();
    }

    protected void translateBinaryExpression(ASTBinaryExpression expr, StringBuilder result) {
        fail();
    }

    protected void translateTernaryExpression(ASTTernaryExpression expr, StringBuilder result) {
        fail();
    }

    protected void translateInstanceofExpression(ASTInstanceofExpression expr, StringBuilder result) {
        fail();
    }

    protected void translateArrayAccess(ASTArrayAccess expr, StringBuilder result) {
        fail();
    }

    protected void translateInvocation(ASTInvocation expr, StringBuilder result) {
        fail();
    }

    protected void internalTranslateExpression(ASTExpression expr, TranslationContext ctx, StringBuilder result) {
        ASTTypeExpression cast = expr.getCast();

        if (isTyped && (cast != null)) {
            result.append('(');
            translateCast(cast, result);
        }
        if (expr instanceof ASTPrimaryExpression) {
            translatePrimaryExpression((ASTPrimaryExpression) expr, ctx, result);
        }
        else if (expr instanceof ASTUnaryExpression) {
            translateUnaryExpression((ASTUnaryExpression) expr, ctx, result);
        }
        else if (expr instanceof ASTBinaryExpression) {
            translateBinaryExpression((ASTBinaryExpression) expr, result);
        }
        else if (expr instanceof ASTTernaryExpression) {
            translateTernaryExpression((ASTTernaryExpression) expr, result);
        }
        else if (expr instanceof ASTInvocation) {
            translateInvocation((ASTInvocation) expr, result);
        }
        else if (expr instanceof ASTInstanceofExpression) {
            translateInstanceofExpression((ASTInstanceofExpression) expr, result);
        }
        else if (expr instanceof ASTArrayAccess) {
            translateArrayAccess((ASTArrayAccess) expr, result);
        }
        else if (expr instanceof ASTMethodReference) {
            internalTranslateExpression(((ASTMethodReference) expr).getType(), TranslationContext.UNKNOWN, result);
            result.append('.');
            internalTranslateExpression(((ASTMethodReference) expr).getIdentifier(), TranslationContext.UNKNOWN, result);
        }
        else {
            throw new UnsupportedOperationException();
        }
        if (isTyped && (cast != null)) {
            result.append(')');
        }
    }

    protected void translateCast(ASTTypeExpression cast, StringBuilder result) {
        fail();
    }

    public void translateExpression(Node expr, StringBuilder result) {
        ASTExpression node = (ASTExpression) transformTree(expr);
        internalTranslateExpression(node, TranslationContext.UNKNOWN, result);
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
            propertyMap.put(name, properties.keySet());
        }
    }

    public String translateNonterminalArgs(String args) {
        return args;
    }

    protected void translateType(ASTTypeExpression expr, StringBuilder result) {
        fail();
    }

    protected void translateFormals(List<ASTFormalParameter> formals, SymbolTable symbols, boolean withType, boolean typeFirst, StringBuilder result) {
        int n = formals.size();
        if (symbols == null) {
            symbols = topSymbols();
        }
        for (int i = 0; i < n; i++) {
            ASTFormalParameter formal = formals.get(i);
            String name = formal.getName();
            String ident = translateIdentifier(name, TranslationContext.PARAMETER);
            ASTTypeExpression type = formal.getType();

            if (!withType) {
                result.append(ident);
            }
            else {
                if (typeFirst) {
                    translateType(type, result);
                    result.append(' ');
                    result.append(ident);
                }
                else {
                    result.append(ident);
                    result.append(' ');
                    translateType(type, result);
                }
            }
            if (i < (n - 1)) {
                result.append(", ");
            }
            symbols.put(name, type);
        }
    }

    protected List<ASTFormalParameter> transformFormals(List<FormalParameter> formals) {
        List<ASTFormalParameter> result = new ArrayList<>();

        for (FormalParameter fp : formals) {
            result.add(transformFormal(fp));
        }
        return result;
    }

    public void translateFormals(List<FormalParameter> formals, SymbolTable symbols, StringBuilder result) {
        fail();
    }

    public String translateInjectedClass(CodeInjector injector, String name) {
        fail();
        return null;
    }

    // common code for Python and CSharp translators
    protected void processVariableDeclaration(ASTTypeExpression type, ASTPrimaryExpression name, boolean isField, boolean isProperty) {
        String s = name.getName();

        if (!isField) {
            addSymbol(s, type);
        }
        else {
            fields.put(s, type);
            if (isProperty) {
                properties.put(s, type);
            }
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

    protected void processForIteration(List<ASTExpression> iteration, int indent, StringBuilder result) {
        addIndent(indent, result);
        int n = iteration.size();
        for (int i = 0; i < n; i++) {
            ASTExpression e = iteration.get(i);
            internalTranslateExpression(e, TranslationContext.UNKNOWN, result);
            if (i < (n - 1)) {
                result.append("; ");
            }
        }
    }

    protected boolean isThis(ASTExpression expr) {
        if (!(expr instanceof ASTPrimaryExpression)) {
            return false;
        }
        else {
            return "this".equals(((ASTPrimaryExpression) expr).getLiteral());
        }
    }

    protected boolean needsParentheses(ASTExpression expr) {
        boolean result = true;

        if (expr instanceof ASTPrimaryExpression ||
                expr instanceof ASTInstanceofExpression) {
            result = false;
        }
        else if (expr instanceof ASTUnaryExpression) {
            result = needsParentheses(((ASTUnaryExpression) expr).getOperand());
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

    protected void processBinaryExpression(boolean parens, ASTExpression lhs, String xop, ASTExpression rhs, StringBuilder result) {
        boolean isDot = xop.equals(".");

        if (parens) {
            result.append('(');
        }
        /*
        if (isDot && isThis(lhs)) {
            // zap the op to empty string, don't render lhs
            xop = "";
        }
        else {
            internalTranslateExpression(lhs, result);
        }
         */
        internalTranslateExpression(lhs, TranslationContext.UNKNOWN, result);
        if (!isDot) {
            result.append(' ');
        }
        result.append(xop);
        if (!isDot) {
            result.append(' ');
        }
        internalTranslateExpression(rhs, TranslationContext.UNKNOWN, result);
        if (parens) {
            result.append(')');
        }
    }

    protected boolean hasUnconditionalExit(ASTStatementList statementList) {
        boolean result = false;

        for (ASTStatement stmt : statementList.statements) {
            if ((stmt instanceof ASTReturnStatement) || (stmt instanceof ASTBreakStatement) ||
                    (stmt instanceof ASTContinueStatement)) {
                result = true;
                break;
            }
        }
        return result;
    }

    protected ASTTypeExpression getExpressionType(ASTExpression expr) {
        ASTTypeExpression result = null;

        if (expr instanceof ASTPrimaryExpression) {
            ASTPrimaryExpression pe = (ASTPrimaryExpression) expr;
            String s = pe.getName();
            result = findSymbol(s);
        }
        else if (expr instanceof ASTInvocation) {
            // TODO find the method and get its return type. Temporary hack for now to get things going
            ASTExpression receiver = ((ASTInvocation) expr).receiver;
            if (receiver instanceof ASTBinaryExpression) {
                ASTExpression lhs = ((ASTBinaryExpression) receiver).lhs;
                ASTExpression rhs = ((ASTBinaryExpression) receiver).rhs;
                ASTTypeExpression te = getExpressionType(lhs);
                if ((te != null) && te.name.equals("Token") && (rhs instanceof ASTPrimaryExpression) && ((ASTPrimaryExpression) rhs).name.equals("getType")) {
                    result = new ASTTypeExpression();
                    result.name = "TokenType";
                }
            }
        }
        return result;
    }

    protected boolean isEnumSet(ASTExpression receiver) {
        boolean result = false;

        if (receiver instanceof ASTBinaryExpression) {
            ASTExpression lhs = ((ASTBinaryExpression) receiver).getLhs();
            if (lhs instanceof ASTPrimaryExpression) {
                result = ((ASTPrimaryExpression) lhs).getName().equals("EnumSet");
            }
        }
        return result;
    }

    protected void translateArguments(List<ASTExpression> arguments, boolean parens, StringBuilder result) {
        int nargs;

        if ((arguments == null) || ((nargs = arguments.size()) == 0)) {
            result.append("()");
        }
        else {
            if (parens) {
                result.append('(');
            }
            for (int i = 0; i < nargs; i++) {
                internalTranslateExpression(arguments.get(i), TranslationContext.UNKNOWN, result);
                if (i < (nargs - 1))
                    result.append(", ");
            }
            if (parens) {
                result.append(')');
            }
        }
    }

    protected boolean isTokenType(ASTExpression expr) {
        boolean result = false;
        ASTTypeExpression te = getExpressionType(expr);

        if (te != null) {
            result = te.name.equals("TokenType");
        }
        return result;
    }

    protected HashMap<String, Set<String>> nestedDeclarations;

    public HashMap<String, Set<String>> getNestedDeclarations() {
        return nestedDeclarations;
    }

    protected void addNestedDeclaration(String name) {
        if (nestedDeclarations == null) {
            nestedDeclarations = new HashMap<>();
        }
        Set<String> existing = nestedDeclarations.get(currentClass);
        if (existing == null) {
            existing = new HashSet<>();
            nestedDeclarations.put(currentClass, existing);
        }
        existing.add(name);
    }

    public void startClass(String name, boolean fields, StringBuilder result) {
        currentClass = name;
    }

    public void endClass(String name, boolean fields, StringBuilder result) {
        if (!currentClass.equals(name)) {
            throw new IllegalStateException("Unexpected end of class");
        }
        currentClass = null;
    }
}
