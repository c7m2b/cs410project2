package com.program.analysis.app.representation;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.Statement;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class Method {
    private String name;
    private String access;
    private Set<String> param;
    private String returnType;
    private String body;
    private NodeList<Node> methodCall;
    private Node allStatements;

    public Method(String name, String access, String returnType, String body, Set<MethodCallExpr> methodCall) {
        this.name = name;
        this.access = access;
        this.returnType = returnType;
        this.body = body;
        this.methodCall = new NodeList<>();
        this.param = new HashSet<String>();
        this.methodCall = new NodeList<>();
    }

    public Method(MethodDeclaration declaration) {
        if (declaration.getBody().equals(Optional.empty())) {
            this.body = "";
        } else {
            this.body = declaration.getBody().toString();
        }
        this.param = new HashSet<String>();
        this.returnType = declaration.getTypeAsString();
        this.access = declaration.getAccessSpecifier().asString();
        this.name = declaration.getNameAsString();
        this.methodCall = new NodeList<>();
        NodeList<Parameter> param = declaration.getParameters();
        if (param.size() != 0) {
            for (Parameter p: param) {
                this.param.add(p.getTypeAsString() + AnalysisConstants.PARAM_SEPARATOR + p.getNameAsString());
            }
        }
    }

    public void setParam(Set<String> param) {
        this.param = param;
    }

    public String getName() {
        return name;
    }

    public String getAccess() {
        return access;
    }

    public Set<String> getParam() {
        return param;
    }

    public String getReturnType() {
        return returnType;
    }

    public String getBody() {
        return body;
    }

    public void addStmt(Node stmt) {
        methodCall.add(stmt);
    }

    public NodeList<Node> getMethodCall(){
        return methodCall;
    }

    public void setLineStmt(Node s){
        this.allStatements = s;
    }

    public Node getAllStatements() {
        return allStatements;
    }
}
