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
    private java.util.ArrayList<Node> nodes = new java.util.ArrayList<Node>();
    private java.util.ArrayList<java.lang.Integer> marks = new java.util.ArrayList<java.lang.Integer>();

    private int mark;    // current mark
    private boolean node_created;
    
    
	/** 
     * Determines whether the current node was actually closed and
     * pushed.  This should only be called in the final user action of a
     * node scope.  
     */ 
    public boolean nodeCreated() {
        return node_created;
    }

	/** 
	 * Returns the root node of the AST.  It only makes sense to call
	 * this after a successful parse. 
	 */ 
    public Node rootNode() {
        return nodes.get(0);
    }

    /**
     * push a node onto the top of the node stack
     */
    public void pushNode(Node n) {
        nodes.add(n);
    }

    /** 
     * Returns the node on the top of the stack, and remove it from the
     * stack.  
     */ 
    public Node popNode() {
        return nodes.remove(nodes.size() - 1);
    }

    /** 
     * Returns the node currently on the top of the stack. 
     */ 
    public Node peekNode() {
        return nodes.get(nodes.size() - 1);
    }

    /**
     * Puts the node on the top of the stack. However, unlike pushNode()
     * it replaces the node that is currently on the top of the stack.
     * This is effectively equivalent to popNode() followed by pushNode(n)
     */
    public void pokeNode(Node n) {
        pokeNode(n, false);
    }

    /**
     * Puts the node on the top of the stack. If clearNodeScope is true,
     * it removes all the nodes in the current node scope and pushes
     * n onto the top. Otherwise, it simply replaces the node at the
     * top of the stack with n.
     */
    public void pokeNode(Node n, boolean clearNodeScope) {
        if (clearNodeScope) {
            clearNodeScope();
            nodes.add(n);
        } else {
        	nodes.set(nodes.size()-1, n);
        }
    }

	/** Returns the number of children on the stack in the current node
	 * scope. 
	 */
	  
    public int nodeArity() {
        return nodes.size() - mark;
    }


    public void clearNodeScope() {
        while (nodes.size() > mark) {
            popNode();
        }
    }
    
    public void openNodeScope(Node n) {
        marks.add(mark);
        mark = nodes.size();
        n.open();
    }


	/* A definite node is constructed from a specified number of
	 * children.  That number of nodes are popped from the stack and
	 * made the children of the definite node.  Then the definite node
	 * is pushed on to the stack. */
	 
    public void closeNodeScope(Node n, int num) {
        mark = marks.remove(marks.size() - 1);
        java.util.ArrayList<Node> nodes = new java.util.ArrayList<Node>();
        for (int i=0;i<num;i++) {
           nodes.add(popNode());
        }
        java.util.Collections.reverse(nodes);
        for (Node child : nodes) {
        	if (specialTokensAreNodes && (child instanceof Token)) {
        	    Token token = (Token) child;
        	    Token specialToken = token;
        	    while (specialToken != null) {
        	        specialToken = specialToken.specialToken;
        	    }
        	    while (specialToken !=null && specialToken != token) {
        	        n.addChild(specialToken);
        	        specialToken = specialToken.next;
        	    }
        	}
            n.addChild(child);
        }
        n.close();
        pushNode(n);
        node_created = true;
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
            int a = nodeArity();
            mark = marks.remove(marks.size() - 1);
            java.util.ArrayList<Node> nodes = new java.util.ArrayList<Node>();
            while (a-- > 0) {
                nodes.add(popNode());
            }
            java.util.Collections.reverse(nodes);
            for (Node child : nodes) {
	        	if (specialTokensAreNodes && (child instanceof Token)) {
	        	    Token token = (Token) child;
	        	    Token specialToken = token;
	        	    while (specialToken.specialToken !=null) {
	        	        specialToken = specialToken.specialToken;
	        	    }
	        	    while (specialToken !=null && specialToken != token) {
	        	        n.addChild(specialToken);
	        	        specialToken = specialToken.next;
	        	    }
	        	}
                n.addChild(child);
            }
            n.close();
            pushNode(n);
            node_created = true;
        } else {
            mark = marks.remove(marks.size() - 1);
            node_created = false;
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
