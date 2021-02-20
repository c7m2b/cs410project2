package com.program.analysis.app.representation;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;


public class MethodCollector extends VoidVisitorAdapter<Object> {
    private HashSet<MethodDeclaration> methods;
    private HashSet<Method> methodInfo;
    private HashSet<String> param;
    private NodeList<MethodCallExpr> callExp;
    private Method temp;

    public MethodCollector() {
        methodInfo = new HashSet<Method>();
        methods = new HashSet<MethodDeclaration>();
        param = new HashSet<String>();
        callExp = new NodeList<>();
    }

    public HashSet<MethodDeclaration> getMethods() {
        return methods;
    }

    public HashSet<String> getMethodsString() {
        HashSet<String> methodNames = new HashSet<String>();
        for (MethodDeclaration md: methods) {
            methodNames.add(md.getDeclarationAsString());
        }
        return methodNames;
    }

    public HashSet<String> getMethodBody() {
        HashSet<String> methodBody = new HashSet<String>();
        for (MethodDeclaration md: methods) {
            methodBody.add(md.getBody().get().toString());
        }
        return methodBody;
    }

    public HashSet<Method> getMethodObj() {
        return methodInfo;
    }

    @Override
    public void visit(MethodDeclaration n, Object arg) {
        methods.add(n);
        temp = new Method(n);
        for (Node node:n.getChildNodes()) {
            if (node instanceof BlockStmt && temp != null) {
                temp.setLineStmt(node);
            }
        }
        super.visit(n, arg);
        methodInfo.add(temp);
    }


    @Override
    public void visit(MethodCallExpr n, Object arg) {
        if (temp != null) { temp.addStmt(n); }
    }

    @Override
    public void visit(IfStmt n, Object arg) {
        if (temp != null) { temp.addStmt(n); }
    }

    @Override
    public void visit(ForStmt n, Object arg) {
        if (temp != null) { temp.addStmt(n); }
    }

    @Override
    public void visit(WhileStmt n, Object arg) {
        if (temp != null){ temp.addStmt(n); }
    }

    @Override
    public void visit(ForEachStmt n, Object arg) {
        if (temp != null) { temp.addStmt(n); }
    }

    @Override
    public void visit(SwitchStmt n, Object arg) {
        if (temp != null) { temp.addStmt(n); }
    }

}
