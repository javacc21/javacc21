[#ftl strict_vars=true]
[#--
/* Copyright (c) 2008-2019 Jonathan Revusky, revusky@javacc.com
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
 *       nor the names of any contributors may be used to endorse 
 *       or promote products derived from this software without specific prior written
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
 --]
[#if grammar.options.treeBuildingDefault]
    private boolean buildTree = true;
[#else]
    private boolean buildTree = false;
[/#if]    
[#if grammar.options.tokensAreNodes]
    private boolean tokensAreNodes = true;
[#else]
    private boolean tokensAreNodes = false;
[/#if]
[#if grammar.options.specialTokensAreNodes]
    private boolean specialTokensAreNodes = true;
[#else]
    private boolean specialTokensAreNodes = false;
[/#if]

    public void setSpecialTokensAreNodes(boolean specialTokensAreNodes) {
        this.specialTokensAreNodes = specialTokensAreNodes;
    }
    
    public void setTokensAreNodes(boolean tokensAreNodes) {
        this.tokensAreNodes = tokensAreNodes;
    }

    NodeScope currentNodeScope = new NodeScope();
    

	/** 
	 * Returns the root node of the AST.  It only makes sense to call
	 * this after a successful parse. 
	 */ 
    public Node rootNode() {
        Node root = currentNodeScope.rootNode();
[#if !grammar.options.hugeFileSupport && !grammar.options.userDefinedLexer]
        recursivelySetInputSource(root, this.token_source.input_stream);
[/#if]        
        return root;
    }
    
[#if !grammar.options.hugeFileSupport && !grammar.options.userDefinedLexer]    
    static private void recursivelySetInputSource(Node n, FileLineMap fileLineMap) {
        n.setInputSource(fileLineMap);
        for (Node child : n.children()) {
//            if (child instanceof Token) {
//                 ((Token) child).setImage(null);
//            } 
            recursivelySetInputSource(child, fileLineMap); 
        } 
    }
[/#if]    
    

    /**
     * push a node onto the top of the node stack
     */
    public void pushNode(Node n) {
        currentNodeScope.add(n);
    }

    /** 
     * Returns the node on the top of the stack, and remove it from the
     * stack.  
     */ 
    public Node popNode() {
       return currentNodeScope.pop();
    }

    /** 
     * Returns the node currently on the top of the stack. 
     */ 
    public Node peekNode() {
        return currentNodeScope.peek();
    }

    /**
     * Puts the node on the top of the stack. However, unlike pushNode()
     * it replaces the node that is currently on the top of the stack.
     * This is effectively equivalent to popNode() followed by pushNode(n)
     */
    public void pokeNode(Node n) {
      	currentNodeScope.poke(n);
    }


	/** Returns the number of children on the stack in the current node
	 * scope. 
	 */
    public int nodeArity() {
        return currentNodeScope.size();
    }


    public void clearNodeScope() {
        currentNodeScope.clear();
    }
    
    public void openNodeScope(Node n) {
        new NodeScope();
        n.open();
        if (trace_enabled) LOGGER.info("Opened node scope for node of type: " + n.getClass().getName());
        if (trace_enabled) LOGGER.info("Scope nesting level is "  +  currentNodeScope.nestingLevel());
    }


	/* A definite node is constructed from a specified number of
	 * children.  That number of nodes are popped from the stack and
	 * made the children of the definite node.  Then the definite node
	 * is pushed on to the stack.
	 */
    public void closeNodeScope(Node n, int num) {
        if (trace_enabled) LOGGER.info("Closing node scope for node of type: " + n.getClass().getName() + ", popping " + num + " nodes off the stack.");
        currentNodeScope.close();
        ArrayList<Node> nodes = new ArrayList<Node>();
        for (int i=0;i<num;i++) {
           nodes.add(popNode());
        }
        Collections.reverse(nodes);
        for (Node child : nodes) {
        	if (specialTokensAreNodes && (child instanceof Token)) {
        	    Token token = (Token) child;
        	    Token specialToken = token;
        	    while (specialToken != null) {
        	        specialToken = specialToken.getSpecialToken();
        	    }
        	    while (specialToken !=null && specialToken != token) {
        	        n.addChild(specialToken);
        	        specialToken = specialToken.getNext();
        	    }
        	}
            n.addChild(child);
        }
        n.close();
        pushNode(n);
 [#if grammar.usesjjtreeCloseNodeScope]
        jjtreeCloseNodeScope(${nodeVarName});
 [/#if]
 [#if grammar.usesCloseNodeScopeHook]
        closeNodeScopeHook(${nodeVarName});
 [/#if]
     
    }

	/**
	 * A conditional node is constructed if the condition is true.  All
	 * the nodes that have been pushed since the node was opened are
	 * made children of the conditional node, which is then pushed
	 * on to the stack.  If the condition is false the node is not
	 * constructed and they are left on the stack. 
	 */
	 
    public void closeNodeScope(Node n, boolean condition) {
        if (condition) {
            if (trace_enabled) LOGGER.finer("Closing node scope for node of type: " + n.getClass().getName() + ", popping " + nodeArity() + " nodes off the stack.");
            int a = nodeArity();
            currentNodeScope.close();
            ArrayList<Node> nodes = new ArrayList<Node>();
            while (a-- > 0) {
                nodes.add(popNode());
            }
            Collections.reverse(nodes);
            for (Node child : nodes) {
	        	if (specialTokensAreNodes && (child instanceof Token)) {
	        	    Token token = (Token) child;
	        	    Token specialToken = token;
	        	    while (specialToken.getSpecialToken() !=null) {
	        	        specialToken = specialToken.getSpecialToken();
	        	    }
	        	    while (specialToken !=null && specialToken != token) {
	        	        n.addChild(specialToken);
	        	        specialToken = specialToken.getNext();
	        	    }
	        	}
                n.addChild(child);
            }
            n.close();
            if (trace_enabled) {
                LOGGER.info("Closing node scope for node of type: " + n.getClass().getName() + ", leaving " + nodeArity() + " nodes on the stack.");
                LOGGER.info("Nesting level is : " + currentNodeScope.nestingLevel());
            }
            pushNode(n);
            if (trace_enabled) {
                LOGGER.info("Closed node scope for node of type: " + n.getClass().getName() + ", there are now " + nodeArity() + " nodes on the stack.");
                LOGGER.info("Nesting level is : " + currentNodeScope.nestingLevel());
            }
 [#if grammar.usesjjtreeCloseNodeScope]
	        jjtreeCloseNodeScope(${nodeVarName});
 [/#if]
 [#if grammar.usesCloseNodeScopeHook]
    	    closeNodeScopeHook(${nodeVarName});
 [/#if]
        } else {
            currentNodeScope.close();
            if (trace_enabled) {
                LOGGER.info("Closed node scope for node of type: " + n.getClass().getName() + ", leaving " + nodeArity() + " nodes on the stack.");
                LOGGER.info("Nesting level is : " + currentNodeScope.nestingLevel());
            }
        }
    }
    
    
    public boolean getBuildTree() {
    	return buildTree;
    }
    
    public void setBuildTree(boolean buildTree) {
        this.buildTree = buildTree;
    }

    /**
     * Just a kludge so that existing jjtree-based code that uses
     * parser.jjtree.foo can work without change.
     */
    
    ${grammar.parserClassName} jjtree = this; 
    
    
    
    @SuppressWarnings("serial")
    class NodeScope extends ArrayList<Node> {
        NodeScope parentScope;
        NodeScope() {
            this.parentScope = ${grammar.parserClassName}.this.currentNodeScope;
            ${grammar.parserClassName}.this.currentNodeScope = this;
        }

        boolean isRootScope() {
            return parentScope == null;
        }


        Node rootNode() {
            NodeScope ns = this;
            while (ns.parentScope != null) {
                ns = ns. parentScope;
            }
            return ns.isEmpty() ? null : ns.get(0);
        }

        Node peek() {
            return isEmpty() ? parentScope.peek() : get(size()-1);
        }

        Node pop() {
            return isEmpty() ? parentScope.pop() : remove(size()-1);
        }

        void poke(Node n) {
            if (isEmpty()) {
                parentScope.poke(n);
            } else {
                set(size()-1, n);
            }
        }

        void close() {
            parentScope.addAll(this);
            ${grammar.parserClassName}.this.currentNodeScope = parentScope;
        }
        
        int nestingLevel() {
            int result = 0;
            NodeScope parent = this;
            while (parent.parentScope != null) {
               result++;
               parent = parent.parentScope;
            }
            return result;            
        } 
    }

