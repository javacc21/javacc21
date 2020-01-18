/* Copyright (c) 2008-2019 Jonathan Revusky, revusky@javacc.com
 * Copyright (c) 2006, Sun Microsystems Inc.
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

package javacc.output.java;

import java.util.*;

import javacc.parser.*;
import javacc.parser.tree.*;

/**
 * Class to hold the code that comes from the grammar file
 * and is later "injected" into the output source files 
 */
class CodeInjector {
    
    private String parserPackage, nodePackage, parserClassName, lexerClassName, constantsClassName, baseNodeClassName;
    
    private Map<String, TypeDeclaration> types = new HashMap<String, TypeDeclaration>();
    private Map<String, Set<ImportDeclaration>> imports = new HashMap<String, Set<ImportDeclaration>>();
    private Map<String, ExtendsList> extendsLists = new HashMap<String, ExtendsList>();
    private Map<String, ImplementsList> implementsLists = new HashMap<String, ImplementsList>();
    private Map<String, TypeParameterList> typeParameterLists = new HashMap<String, TypeParameterList>();
    private Map<String, List<ClassOrInterfaceBodyDeclaration>> bodyDeclarations = new HashMap<String, List<ClassOrInterfaceBodyDeclaration>>();
    private Set<String> overriddenMethods = new HashSet<String>();
    private Set<String> typeNames = new HashSet<String>();
    private Map<String, String> explicitPackages = new HashMap<String, String>();
    private Set<String> interfaces = new HashSet<String>();
    
    CodeInjector(String parserClassName,
                 String lexerClassName,
                 String constantsClassName,
                 String baseNodeClassName,
                 String parserPackage, 
                 String nodePackage, 
                 List<Node> codeInjections) {
        this.parserClassName = parserClassName;
        this.lexerClassName = lexerClassName;
        this.constantsClassName = constantsClassName;
        this.baseNodeClassName = baseNodeClassName;
        if (parserPackage == null) {
            parserPackage = "";
        }
        this.parserPackage = parserPackage;
        if (nodePackage == null) {
            nodePackage = parserPackage;
        }
        this.nodePackage = nodePackage;
        for (Node n : codeInjections) {
            if (n instanceof CompilationUnit) {
                add((CompilationUnit) n);
            } else if (n instanceof CodeInjection) {
                CodeInjection ci = (CodeInjection) n;
                String name = ci.name;
                add(name, ci.importDeclarations, ci.extendsList, ci.implementsList, ci.body, ci.isInterface);
            } else if (n instanceof TokenManagerDecls) {
                for (Iterator<Node> it = Nodes.iterator(n); it.hasNext();) {
                    Node child  = it.next();
                    if (child instanceof ClassOrInterfaceBody) {
                        ClassOrInterfaceBody body = (ClassOrInterfaceBody) child;       
                        add(lexerClassName, null, null, null, body, false);
                    }
                }
            }
        } 
    }
    
    void addAll(CodeInjector other) {
        types.putAll(other.types);
        imports.putAll(other.imports);
        extendsLists.putAll(other.extendsLists);
        implementsLists.putAll(other.implementsLists);
        typeParameterLists.putAll(other.typeParameterLists);
        bodyDeclarations.putAll(other.bodyDeclarations);
        overriddenMethods.addAll(other.overriddenMethods);
        typeNames.addAll(other.typeNames);
    }
    
    private boolean isInNodePackage(String classname) {
        return !classname.equals(parserClassName)
             && !classname.equals(lexerClassName)
             && !classname.equals(constantsClassName)
             && !classname.equals(baseNodeClassName)
             && !classname.equals("JavaCharStream")
             && !classname.equals("SimpleCharStream")
             && !classname.equals("LexicalException")
             && !classname.equals("ParseException")
             && !classname.equals("Token")
             && !classname.equals("Node");
    }
    
    String getExplicitlyDeclaredPackage(String className) {
        return explicitPackages.get(className);
    }

    
    private void add(CompilationUnit jcu) {
        String explicitPackageName = jcu.getPackageName();
        List<ImportDeclaration> importdecls = new ArrayList<ImportDeclaration>();
        importdecls.addAll(jcu.getImportDeclarations());
        for (TypeDeclaration dec : jcu.getTypeDeclarations()) {
            String name = dec.getName();
            typeNames.add(name);
            if (explicitPackageName != null) {
                explicitPackages.put(name, explicitPackageName);
                name = explicitPackageName + "." + name;
            } else {
                String packageName = isInNodePackage(name) ? nodePackage : parserPackage;
                if (packageName.length() > 0) {
                    name = packageName + "." + name;
                }
            }
            types.put(name, dec);
            if (dec.getInterface()) {
                interfaces.add(name);
            }
            if (!importdecls.isEmpty()) {
                Set<ImportDeclaration> injectedImports = imports.get(name);
                if (injectedImports == null) {
                    injectedImports = new HashSet<ImportDeclaration>();
                    imports.put(name, injectedImports);
                }
                injectedImports.addAll(importdecls);
            }
            ExtendsList extendsList = dec.getExtendsList();
            ExtendsList existingOne = extendsLists.get(name);
            if (existingOne == null) {
                extendsLists.put(name, extendsList);
            } else {
                for (ObjectType type : extendsList.getTypes()) {
                    existingOne.addType(type, interfaces.contains(name));
                }
            }
            ImplementsList implementsList = dec.getImplementsList();
            ImplementsList existing = implementsLists.get(name);
            if (existing == null) {
                implementsLists.put(name, implementsList);
            } else {
                for (ObjectType type : implementsList.getTypes()) {
                    existing.addType(type);
                }
            }
            TypeParameterList typeParameterList = dec.getTypeParameterList();
            if (typeParameterList != null) {
                TypeParameterList injectedList = typeParameterLists.get(name);
                if (injectedList == null) {
                    typeParameterLists.put(name, typeParameterList);
                } else {
                    injectedList.add(typeParameterList);
                }
            }
            List<ClassOrInterfaceBodyDeclaration> injectedCode = new ArrayList<ClassOrInterfaceBodyDeclaration>(); 
            for (Iterator<Node> it = Nodes.iterator(dec.getBody()); it.hasNext();) {
                Node n = it.next();
                if (n instanceof ClassOrInterfaceBodyDeclaration) {
                    injectedCode.add((ClassOrInterfaceBodyDeclaration)n);
                }
            }
            List<ClassOrInterfaceBodyDeclaration> existingCode = bodyDeclarations.get(name);
            if (existingCode == null) {
                bodyDeclarations.put(name, injectedCode);
            } else {
                existingCode.addAll(injectedCode);
            }
            for (ClassOrInterfaceBodyDeclaration decl : injectedCode) {
                String key = decl.getFullNameSignatureIfMethod();
                if (key != null) {
                    if (explicitPackageName != null && explicitPackageName.length()>0) {
                        key = explicitPackageName + "." + key;
                    }
                    overriddenMethods.add(key);
                }
            }
        }
    }
    
    private void add(String name, List<ImportDeclaration> importDeclarations, ExtendsList extendsList, 
            ImplementsList implementsList, ClassOrInterfaceBody body, boolean isInterface) 
    {
        typeNames.add(name);
        if (isInterface) {
            interfaces.add(name);
        }
        String packageName = isInNodePackage(name) ? nodePackage : parserPackage;
        if (packageName.length() >0) {
            name = packageName + "." + name;
        }
        if (importDeclarations !=null && !importDeclarations.isEmpty()) {
            Set<ImportDeclaration> existingImports = imports.get(name);
            if (existingImports == null) {
                existingImports = new HashSet<ImportDeclaration>();
                imports.put(name, existingImports);
            }
            for (ImportDeclaration importDecl : importDeclarations) {
                existingImports.add(importDecl);
            }
        }
        if (extendsList != null) {
            ExtendsList existingExtendsList = extendsLists.get(name);
            if (existingExtendsList == null) {
                extendsLists.put(name, extendsList);
            } else {
                for (ObjectType type : extendsList.getTypes()) {
                    existingExtendsList.addType(type, isInterface);
                }
            }
        }
        if (implementsList != null) {
            ImplementsList existingImplementsList = implementsLists.get(name);
            if (existingImplementsList == null) {
                implementsLists.put(name, implementsList);
            } else {
                for (ObjectType type : implementsList.getTypes()) {
                    existingImplementsList.addType(type);
                }
            }
        }
        List<ClassOrInterfaceBodyDeclaration> existingDecls = bodyDeclarations.get(name);
        if (existingDecls == null) {
            existingDecls = new ArrayList<ClassOrInterfaceBodyDeclaration>();
            bodyDeclarations.put(name, existingDecls);
        }
        existingDecls.addAll(Nodes.childrenOfType(body, ClassOrInterfaceBodyDeclaration.class));
        
    }
    
    void injectCode(CompilationUnit jcu) {
        Set<ImportDeclaration> allInjectedImports = new HashSet<ImportDeclaration>();
        for (TypeDeclaration typedecl : jcu.getTypeDeclarations()) {
            String fullName = typedecl.getFullName();
            Set<ImportDeclaration> injectedImports = imports.get(fullName);
            if (injectedImports != null) {
                allInjectedImports.addAll(injectedImports);
            }
            ExtendsList injectedExtends = extendsLists.get(fullName);
            if (injectedExtends != null) {
                for (ObjectType type : injectedExtends.getTypes()) {
                    typedecl.addExtends(type);
                }
            }
            ImplementsList injectedImplements = implementsLists.get(fullName);
            if (injectedImplements != null) {
                for (ObjectType type : injectedImplements.getTypes()) {
                    typedecl.addImplements(type);
                }
            }
            
            TypeParameterList injectedTypeParameters = typeParameterLists.get(fullName);
            if (injectedTypeParameters != null) {
                TypeParameterList typeParameters = typedecl.getTypeParameterList();
		/*                if (typeParameters == null) {
                    ListIterator<Node> iterator = typedecl.iterator();
                    Node n = null;
                    while (n.getId() != JavaCCConstants.IDENTIFIER) {
                        n = iterator.next();
                    }
                    iterator.add(typeParameters);
		    } else {*/
                 typeParameters.add(injectedTypeParameters);
		    //                }
            }
            List<ClassOrInterfaceBodyDeclaration> injectedCode = bodyDeclarations.get(fullName);
            if (injectedCode != null) {
                typedecl.addElements(injectedCode);
            }
        }
        injectImportDeclarations(jcu, allInjectedImports);
    }
    
    private void injectImportDeclarations(CompilationUnit jcu, Collection<ImportDeclaration> importdecls) {
        List<ImportDeclaration> importDeclarations = jcu.getImportDeclarations();
        for (ImportDeclaration importdecl : importdecls) {
            if (!importDeclarations.contains(importdecl)) {
                jcu.addImportDeclaration(importdecl);
            }
        }
    }
    
    boolean hasInjectedCode(String typename) {
        return typeNames.contains(typename);
    }
    
}
