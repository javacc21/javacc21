/* Copyright (c) 2022 Jonathan Revusky, revusky@congocc.org
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

package com.javacc.core;

import java.util.*;
import com.javacc.parser.BaseNode;
import com.javacc.parser.Token;
import com.javacc.parser.tree.*;
import static com.javacc.parser.JavaCCConstants.TokenType.*;

public class BNFProduction extends BaseNode {
    private Expansion expansion, recoveryExpansion;
    private String lexicalState, name, leadingComments = "";
    private boolean implicitReturnType;
    
    public Expansion getExpansion() {
        return expansion;
    }

    public void setExpansion(Expansion expansion) {
        this.expansion = expansion;
    }

    public Expansion getRecoveryExpansion() {return recoveryExpansion;}

    public void setRecoveryExpansion(Expansion recoveryExpansion) {this.recoveryExpansion = recoveryExpansion;}

    public String getLexicalState() {
        return lexicalState;
    }

    public void setLexicalState(String lexicalState) { 
        this.lexicalState = lexicalState; 
    }

    public String getName() {
        return name;
    }

    public String getFirstSetVarName() {
        return getName() + "_FIRST_SET";
    }
    
    public void setName(String name) {
        this.name = name;
    }

    public boolean isImplicitReturnType() {return implicitReturnType;}

    public void setImplicitReturnType(boolean implicitReturnType) {
        this.implicitReturnType = implicitReturnType;
    }

    public TreeBuildingAnnotation getTreeNodeBehavior() {
        return firstChildOfType(TreeBuildingAnnotation.class);
    }

    public TreeBuildingAnnotation getTreeBuildingAnnotation() {
        return firstChildOfType(TreeBuildingAnnotation.class);
    }

    public boolean getHasExplicitLookahead() {
        return expansion.getLookahead() != null;
    }

    public Lookahead getLookahead() {
        return expansion.getLookahead();
    }

    public CodeBlock getJavaCode() {
       return firstChildOfType(CodeBlock.class);
    }

    /**
     * Can this production be matched by an empty string?
     */
    public boolean isPossiblyEmpty() {
        return getExpansion().isPossiblyEmpty();
    }

    public boolean isOnlyForLookahead() {
        TreeBuildingAnnotation tba = getTreeBuildingAnnotation();
        return tba!=null && "scan".equals(tba.getNodeName());
    }

    public String getLookaheadMethodName() {
        return getGrammar().generateIdentifierPrefix("check") + name;
    }

    public String getNodeName() {
        TreeBuildingAnnotation tba = getTreeBuildingAnnotation();
        if (tba != null) {
             String nodeName = tba.getNodeName();
             if (nodeName != null && !nodeName.equals("abstract") 
                 && !nodeName.equals("interface")
                 && !nodeName.equals("void")
                 && !nodeName.equals("scan")) {
                return nodeName;
             }
        }
        return this.getName();
    }

    public ThrowsList getThrowsList() {
        return firstChildOfType(ThrowsList.class);
    }
    
    public FormalParameters getParameterList() {
        return firstChildOfType(FormalParameters.class);
    }

    public String getLeadingComments() {
        return leadingComments;
    }


    public String getReturnType() {
        if (isImplicitReturnType()) {
            return getNodeName();
        }
        ReturnType rt = firstChildOfType(ReturnType.class);
        return rt == null ? "void" : rt.getAsString();
    }

    public String getAccessModifier() {
        for (Token t : childrenOfType(Token.class)) {
           TokenType type = t.getType();
           if (type == PRIVATE) {
               return "private";
           }
           else if (type == PROTECTED) {
               return "protected";
           }
           else if (type == PACKAGE) {
               return "";
           }
        }
        return "public";
    }

    
    public void adjustFirstToken(Token t) {
        //FIXME later. Not very urgent.
/*        
        Token firstToken = firstChildOfType(Token.class);
        if (firstToken != t) {

        }
        if (firstChildOfType(Token.class) !== t)
        this.leadingComments = t.getLeadingComments();
*/
    }

    private TokenSet firstSet, finalSet;
    
    public TokenSet getFirstSet() {
        if (firstSet == null) {
           firstSet = getExpansion().getFirstSet();
        }
        return firstSet;
    }

    public TokenSet getFinalSet() {
          if (finalSet == null) {
              finalSet = getExpansion().getFinalSet();
          }
          return finalSet;
    }

    /**
     * Does this production potentially have left recursion?
     */
    public boolean isLeftRecursive() {
        return getExpansion().potentiallyStartsWith(getName());
    }
}