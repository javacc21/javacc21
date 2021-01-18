/* Copyright (c) 2008-2020 Jonathan Revusky, revusky@javacc.com
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

package com.javacc.output.java;

import java.util.*;

import com.javacc.parser.*;
import com.javacc.parser.tree.*;

/**
 * Class to hold the code that comes from the grammar file
 * and is later "injected" into the output source files 
 */
class CodeInjector {
    
    private String parserPackage, nodePackage, parserClassName, lexerClassName, constantsClassName, baseNodeClassName;
    
    private Map<String, TypeDeclaration> types = new HashMap<>();
    private Map<String, Set<ImportDeclaration>> injectedImportsMap = new HashMap<>();
    private Map<String, Set<Annotation>> injectedAnnotationsMap = new HashMap<>();
    private Map<String, List<ObjectType>> extendsLists = new HashMap<>();
    private Map<String, List<ObjectType>> implementsLists = new HashMap<>();
    private Map<String, TypeParameters> typeParameterLists = new HashMap<>();
    private Map<String, List<ClassOrInterfaceBodyDeclaration>> bodyDeclarations = new HashMap<>();
    private Set<String> overriddenMethods = new HashSet<>();
    private Set<String> typeNames = new HashSet<>();
    private Map<String, String> explicitPackages = new HashMap<>();
    private Set<String> interfaces = new HashSet<>();
    
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
                add(name, ci.importDeclarations, ci.annotations, ci.extendsList, ci.implementsList, ci.body, ci.isInterface);
            } else if (n instanceof TokenManagerDecls) {
                for (Iterator<Node> it = n.iterator(); it.hasNext();) {
                    Node child  = it.next();
                    if (child instanceof ClassOrInterfaceBody) {
                        ClassOrInterfaceBody body = (ClassOrInterfaceBody) child;       
                        add(lexerClassName, null, null, null, null, body, false);
                    }
                }
            }
        } 
    }
    
    private boolean isInNodePackage(String classname) {
        return !classname.equals(parserClassName)
             && !classname.equals(lexerClassName)
             && !classname.equals(constantsClassName)
             && !classname.equals(baseNodeClassName)
             && !classname.equals("ParseException")
             && !classname.equals("Token")
             && !classname.equals("InvalidToken")
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
            if (dec instanceof InterfaceDeclaration) {
                interfaces.add(name);
            }
            if (!importdecls.isEmpty()) {
                Set<ImportDeclaration> injectedImports = injectedImportsMap.get(name);
                if (injectedImports == null) {
                    injectedImports = new HashSet<ImportDeclaration>();
                    injectedImportsMap.put(name, injectedImports);
                }
                injectedImports.addAll(importdecls);
            }
            List<ObjectType> extendsList = dec.getExtendsList() == null ? new ArrayList<>() : dec.getExtendsList().getTypes();
            List<ObjectType> existingOne = extendsLists.get(name);
            if (existingOne == null) {
                extendsLists.put(name, extendsList);
            } else {
                for (ObjectType type : extendsList) {
                    existingOne.add(type);
                }
            }
            List<ObjectType> implementsList = dec.getImplementsList() == null ? new ArrayList<>() : dec.getImplementsList().getTypes();
            List<ObjectType> existing = implementsLists.get(name);
            if (existing == null) {
                implementsLists.put(name, implementsList);
            } else {
                for (ObjectType type : implementsList) {
                    existing.add(type);
                }
            }
            TypeParameters typeParameters = dec.getTypeParameters();
            if (typeParameters != null) {
                TypeParameters injectedList = typeParameterLists.get(name);
                if (injectedList == null) {
                    typeParameterLists.put(name, typeParameters);
                } else {
                    injectedList.add(typeParameters);
                }
            }
            List<ClassOrInterfaceBodyDeclaration> injectedCode = new ArrayList<ClassOrInterfaceBodyDeclaration>(); 
            for (Iterator<Node> it = dec.getBody().iterator(); it.hasNext();) {
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
    
    private void add(String name, List<ImportDeclaration> importDeclarations, List<Annotation> annotations, List<ObjectType> extendsList, 
            List<ObjectType> implementsList, ClassOrInterfaceBody body, boolean isInterface) 
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
            Set<ImportDeclaration> existingImports = injectedImportsMap.get(name);
            if (existingImports == null) {
                existingImports = new HashSet<ImportDeclaration>();
                injectedImportsMap.put(name, existingImports);
            }
            for (ImportDeclaration importDecl : importDeclarations) {
                existingImports.add(importDecl);
            }
        }
        if (annotations != null && !annotations.isEmpty()) {
        	Set<Annotation> existingAnnotations = injectedAnnotationsMap.get(name);
        	if (existingAnnotations == null) {
        		 existingAnnotations = new HashSet<Annotation>();
        		 injectedAnnotationsMap.put(name, existingAnnotations);
        	}
        	for (Annotation annotation : annotations) {
        		existingAnnotations.add(annotation);
        	}
        }
        if (extendsList != null) {
            List<ObjectType> existingExtendsList = extendsLists.get(name);
            if (existingExtendsList == null) {
                extendsLists.put(name, extendsList);
            } else {
                for (ObjectType type : extendsList) {
                    existingExtendsList.add(type);
                }
            }
        }
        if (implementsList != null) {
            List<ObjectType> existingImplementsList = implementsLists.get(name);
            if (existingImplementsList == null) {
                implementsLists.put(name, implementsList);
            } else {
                for (ObjectType type : implementsList) {
                    existingImplementsList.add(type);
                }
            }
        }
        List<ClassOrInterfaceBodyDeclaration> existingDecls = bodyDeclarations.get(name);
        if (existingDecls == null) {
            existingDecls = new ArrayList<ClassOrInterfaceBodyDeclaration>();
            bodyDeclarations.put(name, existingDecls);
        }
        if (body != null) {
        	existingDecls.addAll(body.childrenOfType(ClassOrInterfaceBodyDeclaration.class));
        }
        
    }
    
    void injectCode(CompilationUnit jcu) {
        String packageName = jcu.getPackageName();
        Set<ImportDeclaration> allInjectedImports = new HashSet<ImportDeclaration>();
        for (TypeDeclaration typedecl : jcu.getTypeDeclarations()) {
            String fullName = typedecl.getName();
            if (packageName !=null) {
                fullName = packageName + "." + fullName;
            }
            Set<ImportDeclaration> injectedImports = injectedImportsMap.get(fullName);
            if (injectedImports != null) {
                allInjectedImports.addAll(injectedImports);
            }
            List<ObjectType> injectedExtends = extendsLists.get(fullName);
            if (injectedExtends != null) {
                for (ObjectType type : injectedExtends) {
                    typedecl.addExtends(type);
                }
            }
            List<ObjectType> injectedImplements = implementsLists.get(fullName);
            if (injectedImplements != null) {
                for (ObjectType type : injectedImplements) {
                    typedecl.addImplements(type);
                }
            }
            TypeParameters injectedTypeParameters = typeParameterLists.get(fullName);
            if (injectedTypeParameters != null) {
                TypeParameters typeParameters = typedecl.getTypeParameters();
                typeParameters.add(injectedTypeParameters);
            }
            Set<Annotation> annotations = this.injectedAnnotationsMap.get(fullName);
            if (annotations != null) {
            	typedecl.addAnnotations(annotations);
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
