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

[#if grammar.treeBuildingDefault]
    private boolean buildTree = true;
[#else]
    private boolean buildTree = false;
[/#if]    
[#if grammar.tokensAreNodes]
    private boolean tokensAreNodes = true;
[#else]
    private boolean tokensAreNodes = false;
[/#if]
[#if grammar.unparsedTokensAreNodes]
    private boolean unparsedTokensAreNodes = true;
[#else]
    private boolean unparsedTokensAreNodes = false;
[/#if]

    public boolean isTreeBuildingEnabled() {
        return buildTree;
    }

    public void setUnparsedTokensAreNodes(boolean unparsedTokensAreNodes) {
        this.unparsedTokensAreNodes = unparsedTokensAreNodes;
    }
    
    public void setTokensAreNodes(boolean tokensAreNodes) {
        this.tokensAreNodes = tokensAreNodes;
    }

    NodeScope currentNodeScope = new NodeScope();
    

	/** 
	 * @return the root node of the AST. It only makes sense to call
	 * this after a successful parse. 
	 */ 
    public Node rootNode() {
        return currentNodeScope.rootNode();
    }
    
    /**
     * push a node onto the top of the node stack
     * @param n the node to push
     */
    public void pushNode(Node n) {
        currentNodeScope.add(n);
    }

    /** 
     * @return the node on the top of the stack, and remove it from the
     * stack.  
     */ 
    public Node popNode() {
       return currentNodeScope.pop();
    }

    /** 
     * @return the node currently on the top of the tree-building stack. 
     */ 
    public Node peekNode() {
        return currentNodeScope.peek();
    }

    /**
     * Puts the node on the top of the stack. However, unlike pushNode()
     * it replaces the node that is currently on the top of the stack.
     * This is effectively equivalent to popNode() followed by pushNode(n)
     * @param n the node to poke
     */
    public void pokeNode(Node n) {
      	currentNodeScope.poke(n);
    }


	/** 
     * @return the number of Nodes on the tree-building stack in the current node
	 * scope. 
	 */
    public int nodeArity() {
        return currentNodeScope.size();
    }


    private void clearNodeScope() {
        currentNodeScope.clear();
    }
    
    private void openNodeScope(Node n) {
        new NodeScope();
        if (n!=null) {
            Token next = nextToken(lastConsumedToken);
            n.setFileLineMap(lastConsumedToken.getFileLineMap());
            n.setBeginOffset(next.getBeginOffset());
//            n.setInputSource(next.getInputSource());
            n.open();
  [#list grammar.openNodeScopeHooks as hook]
            ${hook}(n);
  [/#list]
        }
        if (trace_enabled && n!=null) LOGGER.info("Opened node scope for node of type: " + n.getClass().getName());
        if (trace_enabled) LOGGER.info("Scope nesting level is "  +  currentNodeScope.nestingLevel());
    }

	/* A definite node is constructed from a specified number of
	 * children.  That number of nodes are popped from the stack and
	 * made the children of the definite node.  Then the definite node
	 * is pushed on to the stack.
	 */
    private void closeNodeScope(Node n, int num) {
        n.setEndOffset(lastConsumedToken.getEndOffset());
        if (trace_enabled) LOGGER.info("Closing node scope for node of type: " + n.getClass().getName() + ", popping " + num + " nodes off the stack.");
        currentNodeScope.close();
        ArrayList<Node> nodes = new ArrayList<Node>();
        for (int i=0;i<num;i++) {
           nodes.add(popNode());
        }
        Collections.reverse(nodes);
        for (Node child : nodes) {
            // FIXME deal with the UNPARSED_TOKENS_ARE_NODES case
            n.addChild(child);
        }
        n.close();
        pushNode(n);
 [#list grammar.closeNodeScopeHooks as hook]
       ${hook}(n);
[/#list]
    }

	/**
	 * A conditional node is constructed if the condition is true.  All
	 * the nodes that have been pushed since the node was opened are
	 * made children of the conditional node, which is then pushed
	 * on to the stack.  If the condition is false the node is not
	 * constructed and they are left on the stack. 
	 */
    private void closeNodeScope(Node n, boolean condition) {
        if (n!= null && condition) {
            n.setEndOffset(lastConsumedToken.getEndOffset());
            if (trace_enabled) LOGGER.finer("Closing node scope for node of type: " + n.getClass().getName() + ", popping " + nodeArity() + " nodes off the stack.");
            int a = nodeArity();
            currentNodeScope.close();
            ArrayList<Node> nodes = new ArrayList<Node>();
            while (a-- > 0) {
                nodes.add(popNode());
            }
            Collections.reverse(nodes);
            for (Node child : nodes) {
                if (unparsedTokensAreNodes && child instanceof Token) {
                    Token tok = (Token) child;
                    while (tok.previousCachedToken() != null && tok.previousCachedToken().isUnparsed()) {
                        tok = tok.previousCachedToken();
                    }
                    while (tok.isUnparsed()) {
                        n.addChild(tok);
                        tok = tok.nextCachedToken();
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
[#list grammar.closeNodeScopeHooks as hook]
           ${hook}(${nodeVarName});
[/#list]
        } else {
            currentNodeScope.close();
            if (trace_enabled && n!=null) {
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

        public NodeScope clone() {
            NodeScope clone = (NodeScope) super.clone();
            if (parentScope != null) {
                clone.parentScope = (NodeScope) parentScope.clone();
            }
            return clone;
        } 
    }

