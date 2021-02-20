package com.program.analysis.app.representation;

import com.fasterxml.jackson.databind.util.JSONPObject;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.util.*;

public class ProjectCollector {
    private static ProjectCollector instance = new ProjectCollector();
    private Set<CompilationUnit> listOfUnits;
    private Set<GraphElement> listOfResults;

    private ProjectCollector() {
        this.listOfResults = new HashSet<GraphElement>();
        this.listOfUnits = new HashSet<CompilationUnit>();
    }

    public static ProjectCollector getInstance() {
        return instance;
    }

    public Set<GraphElement> getClasses() {
        return this.listOfResults;
    }

    public void parseFiles(File [] files) throws FileNotFoundException, ProjectParseException {
        clean();
        parseJavaFiles(files);
        setDependency();
    }

    private void clean() {
        this.listOfUnits.clear();
        this.listOfResults.clear();
    }

    /*
        This method traverse all java files in the directory recursively. And it stores the
        results. Note: clear() should always be called before calling this function
     */
    private void parseJavaFiles(File [] files) throws FileNotFoundException, ProjectParseException {
        JavaParser javaParser = new JavaParser();
        for (File f: files) {
            if (f.isDirectory() && !f.getName().contains("MACOSX")) {
                parseJavaFiles(f.listFiles());
            } else if (f.getName().endsWith(".java")) {
                ParseResult<CompilationUnit> compilationUnit = javaParser.parse(f);
                if (compilationUnit.getResult().equals(Optional.empty())) {
                    throw new ProjectParseException("Failed to parse project, make sure there are java files in the project and in correct format");
                }
                CompilationUnit compileResult = compilationUnit.getResult().get();
                this.listOfUnits.add(compileResult);
                this.listOfResults.add(new GraphElement(compileResult));
            }
        }
    }

    /*
    Extends and Implements will be kept anyway even though the targeted class is not in the project directory.
    Normal dependency has to be check if the dependent class exists in the project directory, if not exists,
    it will be ignored.
     */
    private void setDependency() {
        for (GraphElement g: listOfResults) {
            Set<Field> listF = g.getFields();
            for (Field f: listF) {
                for (GraphElement gTemp: listOfResults) {
                    if (f.getType().equals(gTemp.getClassName())) {
                        g.addDep(gTemp.getClassName());
                    }
                }
            }
        }
    }



    public JSONArray getSeqDiagramInfo(String className, String methodName) {
        JSONArray jarray = new JSONArray();
        Set<GraphElement> classes = this.getClasses();
        GraphElement ge = getTargetClass(classes, className);
        if(ge==null){return jarray;}
        Method m = getTargetMethod(ge.getListOfMethods(),methodName,null);
        if(m==null){return jarray;}
        for(Node node:m.getMethodCall()){
            if(node instanceof MethodCallExpr){
                JSONObject json = initSeqJSON(node,m,ge);
                if(json!=null && json.has("sep")){
                    JSONArray sep = (JSONArray) json.get("sep");
                    while(sep.length()!=0){
                        if(checkJSONFullSize((JSONObject) sep.get(0))){
                            jarray.put(sep.remove(0));
                        }
                    }
                }else{
                    if(json!=null){
                        if(checkJSONFullSize(json)){
                            jarray.put(json);
                        }
                    }
                }
            }else{
                JSONObject temp = initSeqJSON(node, m, ge);
                if(temp!=null){
                    if(temp.has("sep")){
                        JSONArray sep = (JSONArray) temp.get("sep");
                        while(sep.length()!=0){
                            if(checkJSONFullSize((JSONObject) sep.get(0))){
                                jarray.put(sep.remove(0));
                            }
                        }
                    }else if(temp.has("cond")){
                        JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                        while(nextLevelCond.length()!=0){
                            if(checkJSONFullSize((JSONObject) nextLevelCond.get(0))){
                                jarray.put(nextLevelCond.remove(0));
                            }
                        }
                        if(temp.has("stmt")){
                            jarray.put(temp.get("stmt"));
                        }
                    }else{
                        if(checkJSONFullSize(temp)){
                            jarray.put(temp);
                        }
                    }
                }
            }
        }
        return jarray;
    }

    /*
    get the GraphElement object by name
     */
    private GraphElement getTargetClass(Set<GraphElement> set, String name){
        for(GraphElement obj:set){
            if(name!=null && name.equals(obj.getClassName())){
                return obj;
            }
        }
        return null;
    }

    /*
    get the Method object by name inside of current class
     */
    private Method getTargetMethod(Set<Method> set, String name, GraphElement providedClass){
        Method result = null;
        for(Method obj:set){
            if(name!=null && name.equals(obj.getName())){
                result = obj;
                break;
            }
        }
        if (result == null && providedClass != null && providedClass.getInh().size() > 0) {
            // check extended class for the method
            Set<String> extnededClassName = providedClass.getInh();
            GraphElement extendedClass = getTargetClass(this.getClasses(), extnededClassName.iterator().next());
            return getTargetMethod(extendedClass.getListOfMethods(), name, extendedClass);
        }

        return result;
    }

    /*
    recursive method to retrieve all function calls from one block of statement/expression
     */
    private JSONObject initSeqJSON(Node node, Method m, GraphElement ge){
        JSONObject json = new JSONObject();
        if(node instanceof MethodCallExpr){
            int level = countCalls(node);
            JSONArray temp ;
            if(level==0){
                temp = getJSONFuncCall((MethodCallExpr) node, m, ge);
            }else{
                temp = getJSONNestedFuncCall( node, m, ge, level);
            }
            if(temp!=null&&temp.length()!=0){
                json.put("sep",temp);
                return json;
            }
            return null;
        }else if(node instanceof ForEachStmt){
            return getJSONForEach(node,m,ge);
        }else if(node instanceof ForStmt){
            return getJSONFor(node,m,ge);
        }else if(node instanceof IfStmt){
            return getJSONIf(node,m,ge);
        }else if(node instanceof WhileStmt){
            return getJSONWhile(node,m,ge);
        }else if(node instanceof SwitchStmt){
            return getJSONSwitch(node,m,ge);
        }else if(node instanceof VariableDeclarationExpr){
            return getVarDeclaCall(node,m,ge);
        }else if(node instanceof ThrowStmt){
            return getThrowCall(node,m,ge);
        }else if(node instanceof Expression){
            if(node.getChildNodes()!=null && !node.getChildNodes().equals(Collections.emptyList())){
                for(Node n: node.getChildNodes()){
                    if(n instanceof MethodCallExpr || n instanceof Statement){
                        return initSeqJSON(n,m,ge);
                    }
                }
            }
        }
        //we can not support else
        //return null indicates the errors like no such class in the project directory, no such method exist etc
        return null;
    }

    /*
    recursive method look for initial object call
     */
    private String normalCall(Node m){
        if(m instanceof MethodCallExpr){
            if(((MethodCallExpr) m).getScope().equals(Optional.empty())){
                return m.toString();
            }
            return normalCall(((MethodCallExpr) m).getScope().get());
        }
        return m.toString();
    }

    /*
    look if variable is from method parameters
    if yes, return the callee class name
    if no, look from 1. local variables; 2. name is the Class name;
     */
    private String checkVarFromParam(String name, Method m, GraphElement ge){
        if(name.contains("this.")){
            name = name.substring(5,name.length());
        }
        for(String par:m.getParam()){
            String paramArr []= par.split(AnalysisConstants.PARAM_SEPARATOR);
            if(paramArr[1].equals(name)){
                return paramArr[0];
            }
        }
        //no case 1: check local variables
        for(Field f:ge.getFields()){
            if(f.getName().equals(name)){
                //found in local variables
                return f.getType();
            }
        }
        //case: local instance of other class
        Node allstate = m.getAllStatements();
        if(allstate!=null){
            for(Node lines:allstate.getChildNodes()){
                if(lines!=null && lines instanceof ExpressionStmt){
                    ExpressionStmt exps = (ExpressionStmt)lines;
                    if(exps.getExpression()!=null){
                        Expression exp = exps.getExpression();
                        if(exp instanceof VariableDeclarationExpr){
                            for(Node nnn:exp.getChildNodes()){
                                if(nnn!=null && nnn instanceof VariableDeclarator){
                                    if (((VariableDeclarator) nnn).getNameAsString().equals(name)){
                                        return ((VariableDeclarator) nnn).getTypeAsString();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        //no case 2: check from whole project
        for(GraphElement g:this.listOfResults){
            if(g.getClassName().equals(name)){
                return g.getClassName();
            }
        }
        //not exist in the current project, ignore
        return null;
    }

    /*
    this method is called only for normal function call(type is MethodCallExp)
    check is the method self-recursive
     */
    private boolean isRecursive(MethodCallExpr mc, Method m){
        return mc.getNameAsString().equals(m.getName());
    }

    /*
    recursive helper method to retrieve the method name from Node
     */
    private String helpGetCallerName(String variableName, Node node){
        if(node instanceof MethodCallExpr){
            if(((MethodCallExpr) node).getScope()==null|| ((MethodCallExpr) node).getScope().equals(Optional.empty())){
                return null;
            }
            if(((MethodCallExpr) node).getScope().get().toString().equals(variableName)){
                return ((MethodCallExpr) node).getNameAsString();
            }
            return helpGetCallerName(variableName,((MethodCallExpr) node).getScope().get());
        }
        return null;
    }

    /*
    helper method for generating "param":{ "type":type, "name":name }
     */
    private JSONArray getParam(Method m){
        JSONArray jarr = new JSONArray();
        if(m==null){return null;}
        for(String s:m.getParam()){
            String tempoarr[] = s.split(AnalysisConstants.PARAM_SEPARATOR);
            JSONObject json = new JSONObject();
            json.put("type",tempoarr[0]);
            json.put("name",tempoarr[1]);
            jarr.put(json);
        }
        return jarr;
    }

    /*
    helper method to count the number of times nested method being called
    the nested count depends on the "." on the line statement
    *e.g.
    0 represents the case of foo();
    1 represents the case of a.foo() or foo().boo()
     */
    private int countCalls(Node m){
        if(m instanceof MethodCallExpr){
            if(((MethodCallExpr) m).getScope().equals(Optional.empty())){
                return 0;
            }
            return countCalls(((MethodCallExpr) m).getScope().get())+1;
        }
        return 0;
    }

    /*
    specific helper for case a.foo() and foo().boo() etc.
    collect the json object after the first "." for nested call
     */
    private JSONObject getNextLevelCallJSON(GraphElement currClass, GraphElement nextClass, Method currMethod, Method nextMethod, MethodCallExpr mc){
        JSONObject json = new JSONObject();
        json.put("type",(isRecursive(mc,currMethod))?"self":"none");
        json.put("callerClass",currClass.getClassName());
        json.put("calleeClass",nextClass.getClassName());
        if(json.has("callerClass") && !json.has("calleeClass")){return null;}
        json.put("callerName", nextMethod.getName());
        json.put("returnType",(nextMethod.getReturnType().equals("void"))?"":nextMethod.getReturnType());
        json.put("param", getParam(nextMethod));
        if(nextMethod.getMethodCall().size()!=0){
            JSONArray nextCallArr = new JSONArray();
            for(Node newNode:nextMethod.getMethodCall()){
                JSONObject temp = initSeqJSON(newNode,nextMethod,nextClass);
                if(temp.has("sep")){
                    JSONArray jj = (JSONArray) temp.get("sep");
                    while(jj.length()!=0){
                        nextCallArr.put(jj.remove(0));
                    }
                }else{
                    if(temp.has("cond")){
                        JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                        while(nextLevelCond.length()!=0){
                            nextCallArr.put(nextLevelCond.remove(0));
                        }
                        if(temp.has("stmt")){
                            nextCallArr.put(temp.get("stmt"));
                        }
                    }else{
                        if(temp.has("sep")){
                            JSONArray sep = (JSONArray) temp.get("sep");
                            while(sep.length()!=0){
                                nextCallArr.put(sep.remove(0));
                            }
                        }else{
                            if(checkJSONFullSize(temp)){
                                nextCallArr.put(temp);
                            }
                        }
                    }
                }
            }
            json.put("call",nextCallArr);
        }else{
            json.put("call",new JSONArray());
        }

        return json;
    }

    /*
    specific helper method for case of System.out.println() etc. which the callee class is not
    part of the project directory but a valid function call
     */
    private JSONArray traverseTreeSys(Node node,Method m, GraphElement ge){
        JSONArray reverseArr = new JSONArray();
        Node child = node;
        while(child instanceof BinaryExpr || child instanceof MethodCallExpr){
            if(child instanceof MethodCallExpr){
                JSONObject temp= initSeqJSON(child,m,ge);
                if(temp!=null){
                    if(temp.has("sep")){
                        JSONArray jj = (JSONArray) temp.get("sep");
                        while (jj.length()!=0){
                            if(checkJSONFullSize((JSONObject) jj.get(0))){
                                reverseArr.put(jj.remove(0));
                            }
                        }
                    }else{
                        if(checkJSONFullSize(temp)){
                            reverseArr.put(temp);
                        }
                    }
                }
            }else if(child.getChildNodes().get(1)instanceof MethodCallExpr){
                JSONObject temp= initSeqJSON(child.getChildNodes().get(1),m,ge);
                if(temp!=null){
                    if(temp.has("sep")){
                        JSONArray jj = (JSONArray) temp.get("sep");
                        while (jj.length()!=0){
                            reverseArr.put(jj.remove(0));
                        }
                    }else{
                        if(checkJSONFullSize(temp)){
                            reverseArr.put(temp);
                        }
                    }
                }
            }else if(child.getChildNodes().get(0)instanceof MethodCallExpr){
                JSONObject temp= initSeqJSON(child.getChildNodes().get(0),m,ge);
                if(temp!=null){
                    if(temp.has("sep")){
                        JSONArray jj = (JSONArray) temp.get("sep");
                        while (jj.length()!=0){
                            if(checkJSONFullSize((JSONObject) jj.get(0))){
                                reverseArr.put(jj.remove(0));
                            }
                        }
                    }else{
                        if(checkJSONFullSize(temp)){
                            reverseArr.put(temp);
                        }
                    }
                }
            }
            child=child.getChildNodes().get(0);
        }
        return reverseArr;
    }

    /*
    specific helper for function calls in condition case
     */
    private JSONArray traverseTreeCond(Node node,Method m, GraphElement ge){
        JSONArray reverseArr = new JSONArray();
        for(Node child:node.getChildNodes()){
            while(child instanceof BinaryExpr || child instanceof MethodCallExpr){
                if(child instanceof MethodCallExpr){
                    JSONObject temp= initSeqJSON(child,m,ge);
                    if(temp!=null&&temp.has("sep")){
                        JSONArray jj = (JSONArray) temp.get("sep");
                        while(jj.length()!=0){
                            reverseArr.put(jj.remove(0));
                        }
                    }
                }else if(child.getChildNodes().get(1)instanceof MethodCallExpr){
                    JSONObject temp= initSeqJSON(child.getChildNodes().get(1),m,ge);
                    if(temp!=null&&temp.has("sep")){
                        JSONArray jj = (JSONArray) temp.get("sep");
                        while(jj.length()!=0){
                            reverseArr.put(jj.remove(0));
                        }
                    }
                }else if(child.getChildNodes().get(0)instanceof MethodCallExpr){
                    JSONObject temp= initSeqJSON(child.getChildNodes().get(0),m,ge);
                    if(temp!=null&&temp.has("sep")){
                        JSONArray jj = (JSONArray) temp.get("sep");
                        while(jj.length()!=0){
                            reverseArr.put(jj.remove(0));
                        }
                    }
                }
                
                child=child.getChildNodes().get(0);
            }
        }
        return reverseArr;
    }

    /*
    retrieve all if condition function call
     */
    private JSONArray findAllIfConditions(Node node,Method m, GraphElement ge){
        JSONArray reverseArr = new JSONArray();
        for(Node child:node.getChildNodes()){
            if(child instanceof BinaryExpr || child instanceof IfStmt){
                JSONArray jarr = retrieveFuncCallinsideIfCond(child,m,ge);
                JSONArray j = handleIfRecursion(jarr);
                while(j.length()!=0){
                    reverseArr.put(j.remove(0));
                }
            }else if(child instanceof MethodCallExpr){
                JSONObject temp = initSeqJSON(child,m,ge);
                if(temp!=null&&temp.has("sep")){
                    JSONArray jj = (JSONArray) temp.get("sep");
                    while (jj.length()!=0){
                        if(checkJSONFullSize((JSONObject) jj.get(0))){
                            reverseArr.put(jj.remove(0));
                        }
                    }
                }
            }
        }
        return reverseArr;
    }

    /*
    helper of above method
     */
    private JSONArray retrieveFuncCallinsideIfCond(Node node, Method m, GraphElement ge){
        JSONArray jarr = new JSONArray();
        for(Node child:node.getChildNodes()){
            if(child instanceof BinaryExpr || child instanceof MethodCallExpr){
                if(child instanceof MethodCallExpr){
                    if(initSeqJSON(child,m,ge)!=null){
                        JSONObject temp = initSeqJSON(child,m,ge);
                        if(temp!=null&&temp.has("sep")){
                            JSONArray jj = (JSONArray) temp.get("sep");
                            while (jj.length()!=0){
                                if(checkJSONFullSize((JSONObject) jj.get(0))){
                                    jarr.put(jj.remove(0));
                                }
                            }
                        }
                    }
                }else{
                    for(Node methodNode:child.getChildNodes()){
                        if(methodNode instanceof MethodCallExpr){
                            if(initSeqJSON(methodNode,m,ge)!=null){
                                JSONObject temp = initSeqJSON(methodNode,m,ge);
                                if(temp!=null&&temp.has("sep")){
                                    JSONArray jj = (JSONArray) temp.get("sep");
                                    while (jj.length()!=0){
                                        if(checkJSONFullSize((JSONObject) jj.get(0))){
                                            jarr.put(jj.remove(0));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }else if(child instanceof IfStmt){
                if(retrieveFuncCallinsideIfCond(child,m,ge)!=null){
                    jarr.put(retrieveFuncCallinsideIfCond(child,m,ge));
                }
            }else if(child instanceof MethodCallExpr){
                if(initSeqJSON(child,m,ge)!=null){
                    JSONObject temp = initSeqJSON(child,m,ge);
                    if(temp.has("sep")){
                        JSONArray jj = (JSONArray) temp.get("sep");
                        while (jj.length()!=0){
                            if(checkJSONFullSize((JSONObject) jj.get(0))){
                                jarr.put(jj.remove(0));
                            }
                        }
                    }
                }
            }
        }
        return jarr;
    }

    private JSONArray handleIfRecursion(JSONArray jarr){
        JSONArray collection = new JSONArray();
        while(jarr.length()!=0){
            if(jarr.get(0)instanceof JSONObject){
                collection.put(jarr.remove(0));
            }else{
                JSONArray j = (JSONArray) jarr.remove(0);
                j=handleIfRecursion(j);
                while(j.length()!=0){
                    collection.put(j.remove(0));
                }
            }
        }
        return collection;
    }

    /*
    retrieve all function calls for if or else statement, boolean then indicates which statement we want
    then->true: retrieve then statement
    then->false: retrieve else statement
     */
    private JSONArray getThenAndElseStmtCondFunc(Node node, Method m, GraphElement ge, boolean then){
        //condition has function calls
        List<Node> list = null;
        if(then){
            list = ((IfStmt) node).getThenStmt().getChildNodes();
        }else{
            if(((IfStmt)node).getElseStmt()!=null&& !((IfStmt)node).getElseStmt().equals(Optional.empty())){
                list = ((IfStmt) node).getElseStmt().get().getChildNodes();
            }else{
                return new JSONArray();
            }
        }
        JSONArray jarr = new JSONArray();
        for(Node n:list){
            if(n instanceof IfStmt || n instanceof SwitchStmt || n instanceof WhileStmt
                    || n instanceof ForEachStmt || n instanceof  ForStmt || n instanceof ThrowStmt){
                JSONObject temp = initSeqJSON(n,m,ge);
                if(temp!=null){
                    if(temp.has("cond")){
                        JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                        while(nextLevelCond.length()!=0){
                            jarr.put(nextLevelCond.remove(0));
                        }
                        if(temp.has("stmt")){
                            jarr.put(temp.get("stmt"));
                        }
                    }else{
                        if(temp.has("sep")){
                            JSONArray sep = (JSONArray) temp.get("sep");
                            while(sep.length()!=0){
                                jarr.put(sep.remove(0));
                            }
                        }else{
                            jarr.put(temp);
                        }
                    }
                }
            }else{
                if(!n.getChildNodes().equals(Collections.emptyList())){
                    JSONObject temp = initSeqJSON(n.getChildNodes().get(0),m,ge);
                    if(temp!=null){
                        if(temp.has("cond")){
                            JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                            while(nextLevelCond.length()!=0){
                                jarr.put(nextLevelCond.remove(0));
                            }
                            if(temp.has("stmt")){
                                jarr.put(temp.get("stmt"));
                            }
                        }else{
                            if(temp.has("sep")){
                                JSONArray sep = (JSONArray) temp.get("sep");
                                while(sep.length()!=0){
                                    jarr.put(sep.remove(0));
                                }
                            }else{
                                jarr.put(temp);
                            }
                        }
                    }
                }
            }
        }
        return jarr;
    }

    /*
    helper method to handle foreach statement, return json object of the whole foreach statement block
     */
    private JSONObject getJSONForEach(Node node, Method m,GraphElement ge){
        JSONObject json = new JSONObject();
        ForEachStmt fe = (ForEachStmt)node;
        JSONArray cond = checkIterable(fe,m,ge);
        if(cond.length()!=0){
            //function calls happened inside of foreach condition
            JSONArray condCall = new JSONArray();
            while(cond.length()!=0){
                condCall.put(cond.remove(0));
            }
            json.put("cond",condCall);
            JSONObject nj = new JSONObject();
            nj.put("type","loop");
            JSONArray jarr = new JSONArray();
            for(Node n:((ForEachStmt) node).getBody().getChildNodes()){
                if(n instanceof IfStmt || n instanceof SwitchStmt || n instanceof WhileStmt
                        || n instanceof ForEachStmt || n instanceof  ForStmt){
                    JSONObject temp = initSeqJSON(n,m,ge);
                    if(temp!=null){
                        if(temp.has("cond")){
                            JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                            while(nextLevelCond.length()!=0){
                                if(checkJSONFullSize((JSONObject) nextLevelCond.get(0))){
                                    jarr.put(nextLevelCond.remove(0));
                                }
                            }
                            if(temp.has("stmt")){
                                jarr.put(temp.get("stmt"));
                            }
                        }else{
                            if(temp.has("sep")){
                                JSONArray sep = (JSONArray) temp.get("sep");
                                while(sep.length()!=0){
                                    if(checkJSONFullSize((JSONObject) sep.get(0))){
                                        jarr.put(sep.remove(0));
                                    }
                                }
                            }else{
                                if(checkJSONFullSize(temp)){
                                    jarr.put(temp);
                                }
                            }
                        }
                    }
                }else {
                    if(n.getChildNodes()!=null && !n.getChildNodes().equals(Collections.emptyList())){
                        JSONObject temp = initSeqJSON(n.getChildNodes().get(0),m,ge);
                        if(temp!=null){
                            if(temp.has("cond")){
                                JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                                while(nextLevelCond.length()!=0){
                                    if(checkJSONFullSize((JSONObject) nextLevelCond.get(0))){
                                        jarr.put(nextLevelCond.remove(0));
                                    }
                                }
                                if(temp.has("stmt")){
                                    jarr.put(temp.get("stmt"));
                                }
                            }else{
                                if(temp.has("sep")){
                                    JSONArray sep = (JSONArray) temp.get("sep");
                                    while(sep.length()!=0){
                                        if(checkJSONFullSize((JSONObject) sep.get(0))){
                                            jarr.put(sep.remove(0));
                                        }
                                    }
                                }else{
                                    if(checkJSONFullSize(temp)){
                                        jarr.put(temp);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if(jarr.length()!=0){
                nj.put("call",jarr);
                json.put("stmt",nj);
                return json;
            }else{
                return json;
            }
        }else{
            json.put("type","loop");
            JSONArray jarr = new JSONArray();
            for(Node n:((ForEachStmt) node).getBody().getChildNodes()){
                if(n instanceof IfStmt || n instanceof SwitchStmt || n instanceof WhileStmt
                        || n instanceof ForEachStmt || n instanceof  ForStmt){
                    JSONObject temp = initSeqJSON(n,m,ge);
                    if(temp!=null){
                        if(temp.has("cond")){
                            JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                            while(nextLevelCond.length()!=0){
                                if(checkJSONFullSize((JSONObject) nextLevelCond.get(0))){
                                    jarr.put(nextLevelCond.remove(0));
                                }
                            }
                            if(temp.has("stmt")){
                                jarr.put(temp.get("stmt"));
                            }
                        }else{
                            if(temp.has("sep")){
                                JSONArray sep = (JSONArray) temp.get("sep");
                                while(sep.length()!=0){
                                    if(checkJSONFullSize((JSONObject) sep.get(0))){
                                        jarr.put(sep.remove(0));
                                    }
                                }
                            }else{
                                if(checkJSONFullSize(temp)){
                                    jarr.put(temp);
                                }
                            }
                        }
                    }
                }else{
                    if(n.getChildNodes()!=null && !n.getChildNodes().equals(Collections.emptyList())){
                        JSONObject temp = initSeqJSON(n.getChildNodes().get(0),m,ge);
                        if(temp!=null){
                            if(temp.has("cond")){
                                JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                                while(nextLevelCond.length()!=0){
                                    if(checkJSONFullSize((JSONObject) nextLevelCond.get(0))){
                                        jarr.put(nextLevelCond.remove(0));
                                    }
                                }
                                if(temp.has("stmt")){
                                    jarr.put(temp.get("stmt"));
                                }
                            }else {
                                if(temp.has("sep")){
                                    JSONArray sep = (JSONArray) temp.get("sep");
                                    while(sep.length()!=0){
                                        if(checkJSONFullSize((JSONObject) sep.get(0))){
                                            jarr.put(sep.remove(0));
                                        }
                                    }
                                }else{
                                    if(checkJSONFullSize(temp)){
                                        jarr.put(temp);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if(jarr.length()!=0){
                json.put("call",jarr);
                return json;
            }else{
                return json;
            }
        }
    }

    /*
    helper method to handle while statement, return json object of the whole while statement block
     */
    private JSONObject getJSONWhile(Node node, Method m, GraphElement ge){
        JSONObject json = new JSONObject();
        WhileStmt fe = (WhileStmt) node;
        JSONArray cond = traverseTreeCond(fe,m,ge);
        if(cond.length()!=0){
            //function calls happened inside of for condition
            JSONArray condCall = new JSONArray();
            while(cond.length()!=0){
                condCall.put(cond.remove(0));
            }
            json.put("cond",condCall);
            JSONObject nj = new JSONObject();
            nj.put("type","loop");
            JSONArray jarr = new JSONArray();
            for(Node n:((WhileStmt) node).getBody().getChildNodes()){
                if(n instanceof IfStmt || n instanceof SwitchStmt || n instanceof WhileStmt
                        || n instanceof ForEachStmt || n instanceof  ForStmt){
                    JSONObject temp = initSeqJSON(n,m,ge);
                    if(temp!=null){
                        if(temp.has("cond")){
                            JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                            while(nextLevelCond.length()!=0){
                                if(checkJSONFullSize((JSONObject) nextLevelCond.get(0))){
                                    jarr.put(nextLevelCond.remove(0));
                                }
                            }
                            if(temp.has("stmt")){
                                jarr.put(temp.get("stmt"));
                            }
                        }else{
                            if(temp.has("sep")){
                                JSONArray sep = (JSONArray) temp.get("sep");
                                while(sep.length()!=0){
                                    if(checkJSONFullSize((JSONObject) sep.get(0))){
                                        jarr.put(sep.remove(0));
                                    }
                                }
                            }else{
                                if(checkJSONFullSize(temp)){
                                    jarr.put(temp);
                                }
                            }
                        }
                    }
                }else{
                    if(!n.getChildNodes().equals(Collections.emptyList())){
                        JSONObject temp = initSeqJSON(n.getChildNodes().get(0),m,ge);
                        if(temp!=null){
                            if(temp.has("cond")){
                                JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                                while(nextLevelCond.length()!=0){
                                    jarr.put(nextLevelCond.remove(0));
                                }
                                if(temp.has("stmt")){
                                    jarr.put(temp.get("stmt"));
                                }
                            }else{
                                if(temp.has("sep")){
                                    JSONArray sep = (JSONArray) temp.get("sep");
                                    while(sep.length()!=0){
                                        if(checkJSONFullSize((JSONObject) sep.get(0))){
                                            jarr.put(sep.remove(0));
                                        }
                                    }
                                }else{
                                    if(checkJSONFullSize(temp)){
                                        jarr.put(temp);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if(jarr.length()!=0){
                nj.put("call",jarr);
                json.put("stmt", nj);
                return json;
            }else{
                return json;
            }
        }else{
            json.put("type","loop");
            JSONArray jarr = new JSONArray();
            for(Node n:((WhileStmt) node).getBody().getChildNodes()){
                if(n instanceof IfStmt || n instanceof SwitchStmt || n instanceof WhileStmt
                        || n instanceof ForEachStmt || n instanceof  ForStmt){
                    JSONObject temp = initSeqJSON(n,m,ge);
                    if(temp!=null){
                        if(temp.has("cond")){
                            JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                            while(nextLevelCond.length()!=0){
                                if(checkJSONFullSize((JSONObject) nextLevelCond.get(0))){
                                    jarr.put(nextLevelCond.remove(0));
                                }
                            }
                            if(temp.has("stmt")){
                                jarr.put(temp.get("stmt"));
                            }
                        }else{
                            if(temp.has("sep")){
                                JSONArray sep = (JSONArray) temp.get("sep");
                                while(sep.length()!=0){
                                    if(checkJSONFullSize((JSONObject) sep.get(0))){
                                        jarr.put(sep.remove(0));
                                    }
                                }
                            }else{
                                if(checkJSONFullSize(temp)){
                                    jarr.put(temp);
                                }
                            }
                        }
                    }
                }else{
                    if(!n.getChildNodes().equals(Collections.emptyList())){
                        JSONObject temp = initSeqJSON(n.getChildNodes().get(0),m,ge);
                        if(temp!=null){
                            if(temp.has("cond")){
                                JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                                while(nextLevelCond.length()!=0){
                                    if(checkJSONFullSize((JSONObject) nextLevelCond.get(0))){
                                        jarr.put(nextLevelCond.remove(0));
                                    }
                                }
                                if(temp.has("stmt")){
                                    jarr.put(temp.get("stmt"));
                                }
                            }else{
                                if(temp.has("sep")){
                                    JSONArray sep = (JSONArray) temp.get("sep");
                                    while(sep.length()!=0){
                                        if(checkJSONFullSize((JSONObject) sep.get(0))){
                                            jarr.put(sep.remove(0));
                                        }
                                    }
                                }else{
                                    if(checkJSONFullSize(json)){
                                        jarr.put(temp);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if(jarr.length()!=0){
                json.put("call",jarr);
                return json;
            }else{
                return json;
            }
        }
    }

    /*
    helper method to handle if statement, return json object of the whole if statement block
    In sequence diagram, if statement with no else case is considered as "opt"; if statement
    with else case or multiple else if statement considered as "alt"
     */
    private JSONObject getJSONIf(Node node, Method m, GraphElement ge){
        JSONObject json  = new JSONObject();
        IfStmt fe = (IfStmt) node;
        JSONArray cond = findAllIfConditions(fe,m,ge);
        if(cond.length()!=0){
            //function calls happened inside of if condition
            JSONArray condCall = new JSONArray();
            while(cond.length()!=0){
                condCall.put(cond.remove(0));
            }
            json.put("cond",condCall);
            JSONObject nj = new JSONObject();
            String type = (((IfStmt) node).getElseStmt().equals(Optional.empty()))?"opt":"alt";
            nj.put("type", type);
            if(type.equals("opt")){
                JSONArray outsider = new JSONArray();
                JSONArray jarr = new JSONArray();
                for(Node n:((IfStmt) node).getThenStmt().getChildNodes()){
                    if(n instanceof IfStmt || n instanceof SwitchStmt || n instanceof WhileStmt
                            || n instanceof ForEachStmt || n instanceof  ForStmt){
                        JSONObject temp = initSeqJSON(n,m,ge);
                        if(temp!=null){
                            if(temp.has("cond")){
                                JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                                while(nextLevelCond.length()!=0){
                                    if(checkJSONFullSize((JSONObject) nextLevelCond.get(0))){
                                        jarr.put(nextLevelCond.remove(0));
                                    }
                                }
                                if(temp.has("stmt")){
                                    JSONArray arr = (JSONArray) temp.get("stmt");
                                    if (arr.length()!=0){
                                        if(checkJSONFullSize((JSONObject) arr.get(0))){
                                            jarr.put(arr.remove(0));
                                        }
                                    }
                                }
                            }else{
                                if(temp.has("sep")){
                                    JSONArray sep = (JSONArray) temp.get("sep");
                                    while(sep.length()!=0){
                                        if(checkJSONFullSize((JSONObject) sep.get(0))){
                                            jarr.put(sep.remove(0));
                                        }
                                    }
                                }else{
                                    if(checkJSONFullSize(temp)){
                                        jarr.put(temp);
                                    }
                                }
                            }
                        }
                    }else{
                        if(!n.getChildNodes().equals(Collections.emptyList())){
                            JSONObject temp = initSeqJSON(n.getChildNodes().get(0),m,ge);
                            if(temp!=null){
                                if(temp.has("cond")){
                                    JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                                    while(nextLevelCond.length()!=0){
                                        if(checkJSONFullSize((JSONObject) nextLevelCond.get(0))){
                                            jarr.put(nextLevelCond.remove(0));
                                        }
                                    }
                                    if(temp.has("stmt")){
                                        JSONArray arr = (JSONArray) temp.get("stmt");
                                        if(arr.length()!=0){
                                            if(checkJSONFullSize((JSONObject) arr.get(0))){
                                                jarr.put(arr.remove(0));
                                            }
                                        }
                                    }
                                }else{
                                    if(temp.has("sep")){
                                        JSONArray sep = (JSONArray) temp.get("sep");
                                        while(sep.length()!=0){
                                            if(checkJSONFullSize((JSONObject) sep.get(0))){
                                                jarr.put(sep.remove(0));
                                            }
                                        }
                                    }else{
                                        if(checkJSONFullSize(temp)){
                                            jarr.put(temp);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if(jarr.length()!=0){
                    outsider.put(jarr);
                    nj.put("call",outsider);
                    json.put("stmt",nj);
                    return json;
                }else{
                    return json;
                }
            }else{
                //case alt
                JSONArray outsider = new JSONArray();
                while (!((IfStmt) node).getElseStmt().equals(Optional.empty()) &&((IfStmt) node).getElseStmt().get() instanceof IfStmt){
                    JSONArray jarr = getThenAndElseStmtCondFunc(node,m,ge,true);
                    if(jarr.length()!=0){
                        outsider.put(jarr);
                    }
                    node = ((IfStmt) node).getElseStmt().get();
                }
                //the bottom level then and else case need to retrieve
                JSONArray jarr1 = getThenAndElseStmtCondFunc(node,m,ge,true);
                JSONArray jarr2 = getThenAndElseStmtCondFunc(node,m,ge,false);
                if(jarr1.length()!=0){
                    outsider.put(jarr1);
                }
                if(jarr2.length()!=0){
                    outsider.put(jarr2);
                }
                if(outsider.length()!=0){
                    nj.put("call",outsider);
                    json.put("stmt",nj);
                    return json;
                }
                return json;
            }
        }else{
            String type = (((IfStmt) node).getElseStmt().equals(Optional.empty()))?"opt":"alt";
            json.put("type", type);
            if(type.equals("opt")){
                JSONArray outsider = new JSONArray();
                JSONArray jarr = new JSONArray();
                for(Node n:((IfStmt) node).getThenStmt().getChildNodes()){
                    if(n instanceof IfStmt || n instanceof SwitchStmt || n instanceof WhileStmt
                            || n instanceof ForEachStmt || n instanceof  ForStmt){
                        JSONObject temp = initSeqJSON(n,m,ge);
                        if(temp!=null){
                            if(temp.has("cond")){
                                JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                                while(nextLevelCond.length()!=0){
                                    if(checkJSONFullSize((JSONObject) nextLevelCond.get(0))){
                                        jarr.put(nextLevelCond.remove(0));
                                    }
                                }
                                if(temp.has("stmt")){
                                    JSONArray arr = (JSONArray) temp.get("stmt");
                                    if(arr.length()!=0){
                                        if(checkJSONFullSize((JSONObject) arr.get(0))){
                                            jarr.put(arr.remove(0));
                                        }
                                    }
                                }
                            }else{
                                if(temp.has("sep")){
                                    JSONArray sep = (JSONArray) temp.get("sep");
                                    while(sep.length()!=0){
                                        if(checkJSONFullSize((JSONObject)sep.get(0))){
                                            jarr.put(sep.remove(0));
                                        }
                                    }
                                }else{
                                    if(checkJSONFullSize(temp)){
                                        jarr.put(temp);
                                    }
                                }
                            }
                        }
                    }else{
                        if(!n.getChildNodes().equals(Collections.emptyList())){
                            JSONObject temp = initSeqJSON(n.getChildNodes().get(0),m,ge);
                            if(temp!=null){
                                if(temp.has("cond")){
                                    JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                                    while(nextLevelCond.length()!=0){
                                        if(checkJSONFullSize((JSONObject) nextLevelCond.get(0))){
                                            jarr.put(nextLevelCond.remove(0));
                                        }
                                    }
                                    if(temp.has("stmt")){
                                        JSONArray arr = (JSONArray) temp.get("stmt");
                                        if(arr.length()!=0){
                                            if(checkJSONFullSize((JSONObject) arr.get(0))){
                                                jarr.put(arr.remove(0));
                                            }
                                        }
                                    }
                                }else{
                                    if(temp.has("sep")){
                                        JSONArray sep = (JSONArray) temp.get("sep");
                                        while(sep.length()!=0){
                                            if(checkJSONFullSize((JSONObject) sep.get(0))){
                                                jarr.put(sep.remove(0));
                                            }
                                        }
                                    }else{
                                        if(checkJSONFullSize(temp)){
                                            jarr.put(temp);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if(jarr.length()!=0){
                    outsider.put(jarr);
                    json.put("call",outsider);
                    return json;
                }else{
                    return json;
                }
            }else{
                //case alt
                JSONArray outsider = new JSONArray();
                while (!((IfStmt) node).getElseStmt().equals(Optional.empty()) &&((IfStmt) node).getElseStmt().get() instanceof IfStmt){
                    JSONArray jarr = getThenAndElseStmtCondFunc(node,m,ge,true);
                    if(jarr.length()!=0){
                        if(checkJSONFullSize((JSONObject) outsider.get(0))){
                            outsider.put(jarr);
                        }
                    }
                    node = ((IfStmt) node).getElseStmt().get();
                }
                //the bottom level then and else case need to retrieve
                JSONArray jarr1 = getThenAndElseStmtCondFunc(node,m,ge,true);
                JSONArray jarr2 = getThenAndElseStmtCondFunc(node,m,ge,false);
                if(jarr1.length()!=0){
                    outsider.put(jarr1);
                }
                if(jarr2.length()!=0){
                    outsider.put(jarr2);
                }
                if(outsider.length()!=0){
                    json.put("call",outsider);
                    return json;
                }
                return json;
            }
        }
    }

    /*
    helper method to handle for statement, return json object of the whole for statement block
     */
    private JSONObject getJSONFor(Node node, Method m, GraphElement ge){
        JSONObject json = new JSONObject();
        ForStmt fe = (ForStmt) node;
        JSONArray cond = traverseTreeCond(fe,m,ge);
        if(cond.length()!=0){
            //function calls happened inside of for condition
            JSONArray condCall = new JSONArray();
            while(cond.length()!=0){
                condCall.put(cond.remove(0));
            }
            json.put("cond",condCall);
            JSONObject nj = new JSONObject();
            nj.put("type","loop");
            JSONArray jarr = new JSONArray();
            for(Node n:((ForStmt) node).getBody().getChildNodes()){
                if(n instanceof IfStmt || n instanceof SwitchStmt || n instanceof WhileStmt
                        || n instanceof ForEachStmt || n instanceof  ForStmt){
                    JSONObject temp = initSeqJSON(n,m,ge);
                    if(temp!=null){
                        if(temp.has("cond")){
                            JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                            while(nextLevelCond.length()!=0){
                                jarr.put(nextLevelCond.remove(0));
                            }
                            if(temp.has("stmt")){
                                jarr.put(temp.get("stmt"));
                            }
                        }else{
                            if(temp.has("sep")){
                                JSONArray sep = (JSONArray) temp.get("sep");
                                while(sep.length()!=0){
                                    if(checkJSONFullSize((JSONObject) sep.get(0))){
                                        jarr.put(sep.remove(0));
                                    }
                                }
                            }else{
                                if(checkJSONFullSize(temp)){
                                    jarr.put(temp);
                                }
                            }
                        }
                    }
                }else{
                    if(n.getChildNodes()!=null && !n.getChildNodes().equals(Collections.emptyList())){
                        JSONObject temp = initSeqJSON(n.getChildNodes().get(0),m,ge);
                        if(temp!=null){
                            if(temp.has("cond")){
                                JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                                while(nextLevelCond.length()!=0){
                                    jarr.put(nextLevelCond.remove(0));
                                }
                                if(temp.has("stmt")){
                                    jarr.put(temp.get("stmt"));
                                }
                            }else{
                                if(temp.has("sep")){
                                    JSONArray sep = (JSONArray) temp.get("sep");
                                    while(sep.length()!=0){
                                        if(checkJSONFullSize((JSONObject) sep.get(0))){
                                            jarr.put(sep.remove(0));
                                        }
                                    }
                                }else{
                                    if(checkJSONFullSize(temp)){
                                        jarr.put(temp);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if(jarr.length()!=0){
                nj.put("call",jarr);
                json.put("stmt",nj);
                return json;
            }else{
                return json;
            }
        }else{
            json.put("type","loop");
            JSONArray jarr = new JSONArray();
            for(Node n:((ForStmt) node).getBody().getChildNodes()){
                if(n.getChildNodes()!=null && !n.getChildNodes().equals(Collections.emptyList())){
                    JSONObject temp = initSeqJSON(n.getChildNodes().get(0),m,ge);
                    if(temp!=null){
                        if(temp.has("cond")){
                            JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                            while(nextLevelCond.length()!=0){
                                jarr.put(nextLevelCond.remove(0));
                            }
                            if(temp.has("stmt")){
                                jarr.put(temp.get("stmt"));
                            }
                        }else{
                            if(temp.has("sep")){
                                JSONArray sep = (JSONArray) temp.get("sep");
                                while(sep.length()!=0){
                                    if(checkJSONFullSize((JSONObject) sep.get(0))){
                                        jarr.put(sep.remove(0));
                                    }
                                }
                            }else{
                                if(checkJSONFullSize(temp)){
                                    jarr.put(temp);
                                }
                            }
                        }
                    }
                }
            }
            if(jarr.length()!=0){
                json.put("call",jarr);
                return json;
            }else{
                return null;
            }
        }
    }

    /*
    helper method to handle switch statement, return json object of the whole switch statement block
     */
    private JSONObject getJSONSwitch(Node node, Method m, GraphElement ge){
        JSONObject json = new JSONObject();
        SwitchStmt fe = (SwitchStmt) node;
        JSONArray cond = traverseTreeSwitch(fe,m,ge);
        if(cond.length()!=0){
            //function calls happened inside of for condition
            JSONArray condCall = new JSONArray();
            while(cond.length()!=0){
                condCall.put(cond.remove(0));
            }
            json.put("cond",condCall);
            JSONObject nj =new JSONObject();
            nj.put("type", "alt");
            JSONArray jarr = new JSONArray();
            for(SwitchEntry n:((SwitchStmt) node).getEntries()){
                JSONArray insider= new JSONArray();
                for(Statement s:n.getStatements()){
                    if(s instanceof IfStmt || s instanceof SwitchStmt || s instanceof WhileStmt
                            || s instanceof ForEachStmt || s instanceof  ForStmt){
                        JSONObject temp = initSeqJSON(s,m,ge);
                        if(temp!=null){
                            if(temp.has("cond")){
                                JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                                while(nextLevelCond.length()!=0){
                                    insider.put(nextLevelCond.remove(0));
                                }
                                if(temp.has("stmt")){
                                    insider.put(temp.get("stmt"));
                                }
                            }else{
                                if(temp.has("sep")){
                                    JSONArray sep = (JSONArray) temp.get("sep");
                                    while(sep.length()!=0){
                                        if(checkJSONFullSize((JSONObject) sep.get(0))){
                                            insider.put(sep.remove(0));
                                        }
                                    }
                                }else{
                                    if(checkJSONFullSize(temp)){
                                        insider.put(temp);
                                    }
                                }
                            }
                        }
                    }else{
                        if(!s.getChildNodes().equals(Collections.emptyList())){
                            for(Node child:s.getChildNodes()){
                                JSONObject temp = initSeqJSON(child,m,ge);
                                if(temp!=null){
                                    if(temp.has("cond")){
                                        JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                                        while(nextLevelCond.length()!=0){
                                            insider.put(nextLevelCond.remove(0));
                                        }
                                        if(temp.has("stmt")){
                                            insider.put(temp.get("stmt"));
                                        }
                                    }else{
                                        if(temp.has("sep")){
                                            JSONArray sep = (JSONArray) temp.get("sep");
                                            while(sep.length()!=0){
                                                if(checkJSONFullSize((JSONObject) sep.get(0))){
                                                    insider.put(sep.remove(0));
                                                }
                                            }
                                        }else{
                                            if(checkJSONFullSize(temp)){
                                                insider.put(temp);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if(insider.length()!=0){
                    jarr.put(insider);
                }
            }
            if(jarr.length()!=0){
                nj.put("call",jarr);
                json.put("stmt",nj);
                return json;
            }else{
                return json;
            }
        }else {
            json.put("type", "alt");
            JSONArray jarr = new JSONArray();
            for(SwitchEntry n:((SwitchStmt) node).getEntries()){
                JSONArray insider= new JSONArray();
                for(Statement s:n.getStatements()){
                    JSONObject temp;
                    if(s instanceof IfStmt || s instanceof SwitchStmt || s instanceof WhileStmt
                            || s instanceof ForEachStmt || s instanceof  ForStmt){
                        temp = initSeqJSON(s,m,ge);
                        if(temp!=null){
                            if(temp.has("cond")){
                                JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                                while(nextLevelCond.length()!=0){
                                    if(checkJSONFullSize((JSONObject) nextLevelCond.get(0))){
                                        insider.put(nextLevelCond.remove(0));
                                    }
                                }
                                if(temp.has("stmt")){
                                    insider.put(temp.get("stmt"));
                                }
                            }else{
                                if(temp.has("sep")){
                                    JSONArray sep = (JSONArray) temp.get("sep");
                                    while(sep.length()!=0){
                                        if(checkJSONFullSize((JSONObject) sep.get(0))){
                                            insider.put(sep.remove(0));
                                        }
                                    }
                                }else{
                                    if(checkJSONFullSize(temp)){
                                        insider.put(temp);
                                    }
                                }
                            }
                        }
                    }else {
                        for(Node child:s.getChildNodes()){
                            temp = initSeqJSON(child,m,ge);
                            if(temp!=null){
                                if(temp.has("cond")){
                                    JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                                    while(nextLevelCond.length()!=0){
                                        if(checkJSONFullSize((JSONObject) nextLevelCond.get(0))){
                                            insider.put(nextLevelCond.remove(0));
                                        }
                                    }
                                    if(temp.has("stmt")){
                                        insider.put(temp.get("stmt"));
                                    }
                                }else{
                                    if(temp.has("sep")){
                                        JSONArray sep = (JSONArray) temp.get("sep");
                                        while(sep.length()!=0){
                                            if(checkJSONFullSize((JSONObject) sep.get(0))){
                                                insider.put(sep.remove(0));
                                            }
                                        }
                                    }else{
                                        if(checkJSONFullSize(temp)){
                                            insider.put(temp);
                                        }
                                    }
                                }
                            }
                        }

                    }

                }
                if(insider.length()!=0){
                    jarr.put(insider);
                }
            }
            if(jarr.length()!=0){
                json.put("call",jarr);
                return json;
            }else{
                return null;
            }
        }
    }

    /*
    helper method to handle normal function call statement, return json object of the whole foreach statement block
     */
    private JSONArray getJSONFuncCall(MethodCallExpr mc,Method m,GraphElement ge){
        JSONArray jarr = new JSONArray();
        JSONObject json = new JSONObject();
        for (Node n:((MethodCallExpr)mc).getChildNodes()){
            if((n instanceof MethodCallExpr|| n instanceof IfStmt) && moreThanOneMethodCallExp(mc, (MethodCallExpr) n)){
                JSONObject tt = initSeqJSON(n,m,ge);
                if(tt!=null&&tt.has("sep")){
                    JSONArray tj = (JSONArray) tt.get("sep");
                    while(tj.length()!=0){
                        if(checkJSONFullSize((JSONObject) tj.get(0))){
                            jarr.put(tj.remove(0));
                        }
                    }
                }
            }
        }
        if(isRecursive(mc,m)){
            //case recursive
            json.put("type","self");
            json.put("callerClass",ge.getClassName());
            json.put("calleeClass",ge.getClassName());
            json.put("callerName",m.getName());
            json.put("returnType",(m.getReturnType().equals("void"))?"":m.getReturnType());
            json.put("param",getParam(m));
            json.put("call",new JSONArray());
            jarr.put(json);
            return jarr;
        }
        json.put("type","none");
        json.put("callerClass",ge.getClassName());
        String variableName = normalCall(mc);
        String calleeClass = checkVarFromParam(variableName,m,ge);
        if(calleeClass==null){
            //case local normal method call: foo()
            String potentialMethodName = mc.getNameAsString();//possible method name
            for(Method mt:ge.getListOfMethods()){
                if(potentialMethodName.equals(mt.getName())){
                    json.put("calleeClass",ge.getClassName());
                    json.put("callerName", mt.getName());
                    json.put("returnType",(mt.getReturnType().equals("void"))?"":mt.getReturnType());
                    json.put("param",getParam(mt));
                    if(mt.getMethodCall().size()!=0){
                        //inside the method, it will call and redirect us to other classes/methodCall
                        JSONArray nextCallArr = new JSONArray();
                        for(Node newNode:mt.getMethodCall()){
                            JSONObject temp = initSeqJSON(newNode,mt,ge);
                            if(temp!=null){
                                if(temp.has("sep")){
                                    JSONArray sep = (JSONArray) temp.get("sep");
                                    while(sep.length()!=0){
                                        if(checkJSONFullSize((JSONObject) sep.get(0))){
                                            nextCallArr.put(sep.remove(0));
                                        }
                                    }
                                }else{
                                    if(temp.has("cond")){
                                        JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                                        while(nextLevelCond.length()!=0){
                                            if(checkJSONFullSize((JSONObject) nextLevelCond.get(0))){
                                                nextCallArr.put(nextLevelCond.remove(0));
                                            }
                                        }
                                        if(temp.has("stmt")){
                                            nextCallArr.put(temp.get("stmt"));
                                        }
                                    }else{
                                        if(temp.has("sep")){
                                            JSONArray sep = (JSONArray) temp.get("sep");
                                            while(sep.length()!=0){
                                                if(checkJSONFullSize((JSONObject) sep.get(0))){
                                                    nextCallArr.put(sep.remove(0));
                                                }
                                            }
                                        }else{
                                            if(checkJSONFullSize(temp)){
                                                nextCallArr.put(temp);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        json.put("call",nextCallArr);
                    }else{
                        json.put("call",new JSONArray());
                    }
                    if(checkJSONFullSize(json)){
                        jarr.put(json);
                    }
                    return jarr;
                }
            }
        }
        if(calleeClass!=null && calleeClass.contains("<")){
            calleeClass = removeBracket(calleeClass);
        }
        json.put("calleeClass",calleeClass);
        String callerName = helpGetCallerName(variableName, mc);
        json.put("callerName",callerName);
        GraphElement nextClass = getTargetClass(this.listOfResults, calleeClass);
        if(nextClass!=null){//case car.a()
            json = baseHelper(json,nextClass,callerName);
        }else {
            //case where new method declaration
            for(Node n:mc.getChildNodes()){
                if(n instanceof ObjectCreationExpr){
                    calleeClass = ((ObjectCreationExpr) n).getTypeAsString();
                    callerName = helpGetCallerName(variableName, mc);
                    json.remove("calleeClass");
                    json.remove("callerName");
                    json.put("callerName",callerName);
                    json.put("calleeClass",calleeClass);
                    nextClass = getTargetClass(this.listOfResults, calleeClass);
                }
            }
            if(nextClass!=null){
                json = baseHelper(json, nextClass, callerName);
            }
            //handle System.out.println(foo());
            if(!mc.getChildNodes().equals(Collections.emptyList())){
                for(Node n:mc.getChildNodes()){
                    if(n instanceof MethodCallExpr){
                        JSONObject temp = initSeqJSON(n,m,ge);
                        if(temp!=null){
                            JSONArray temparr = new JSONArray();
                            if(temp.has("sep")){
                                JSONArray jj = (JSONArray) temp.get("sep");
                                while(jj.length()!=0){
                                    if(checkJSONFullSize((JSONObject) jj.get(0))){
                                        temparr.put(jj.remove(0));
                                    }
                                }
                                json.put("call",temparr);
                            }else{
                                if(temp.has("cond")){
                                    JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                                    while(nextLevelCond.length()!=0){
                                        temparr.put(nextLevelCond.remove(0));
                                    }
                                    if(temp.has("stmt")){
                                        temparr.put(temp.get("stmt"));
                                    }
                                }else{
                                    if(temp.has("sep")){
                                        JSONArray sep = (JSONArray) temp.get("sep");
                                        while(sep.length()!=0){
                                            if(checkJSONFullSize((JSONObject) sep.get(0))){
                                                temparr.put(sep.remove(0));
                                            }
                                        }
                                    }else{
                                        if(checkJSONFullSize(temp)){
                                            temparr.put(temp);
                                        }
                                    }
                                }
                                json.put("call",temparr);
                            }
                        }
                    }else if(n instanceof BinaryExpr){
                        JSONArray sys = traverseTreeSys(n,m,ge);
                        if(sys.length()!=0){
                            json.put("call",sys);
                        }
                    }
                }
            } else{
                json.put("call",new JSONArray());
            }
        }
        if(json.has("callerClass") && !json.has("calleeClass")){
            if(json.has("call")){
                if(json.get("call") instanceof JSONArray){
                    return (JSONArray) json.get("call");
                }

            }
            return null;
        }
        if(checkJSONFullSize(json)){
            jarr.put(json);
        }
        return jarr;
    }

    /*
    helper method for nested function call: a.foo() and foo().boo()
     */
    private JSONArray getJSONNestedFuncCall(Node node,Method m,GraphElement ge, int level){
        JSONArray jarray = new JSONArray();
        Node child = node;
        GraphElement currClass = ge;
        GraphElement nextClass = null;
        Method currMethod = m;
        Method nextMethod = null;
        for(int j = 1; j<level;j++){
            if(child.getChildNodes()!=null && !child.getChildNodes().equals(Collections.emptyList())){
                child = child.getChildNodes().get(0);
            }
        }
        for(int i =0;i<level;i++){
            if(i==0){
                JSONArray temp = getJSONFuncCall((MethodCallExpr) node, m, ge);
                if(temp!=null&&temp.length()!=0){
                    while(temp.length()!=0){
                        if(checkJSONFullSize((JSONObject) temp.get(0))){
                            jarray.put(temp.remove(0));
                        }
                    }
                }
                if(!child.getParentNode().equals(Optional.empty())){
                    child = child.getParentNode().get();
                }
                if(child!=null && child instanceof MethodCallExpr){
                    String variableName = normalCall(node);
                    String callerName = helpGetCallerName(variableName, node);
                    String calleeClass = checkVarFromParam(variableName,m,ge);
                    if(calleeClass!=null && calleeClass.contains("<")){calleeClass= removeBracket(calleeClass);};
                    nextClass = getTargetClass(this.listOfResults, calleeClass);
                    if(nextClass!=null){
                        currMethod = getTargetMethod(nextClass.getListOfMethods(),callerName,null);
                        if(currMethod==null){
                            for(String s:nextClass.getInh()){
                                GraphElement tempClass = getTargetClass(this.listOfResults,s);
                                currMethod = getTargetMethod(tempClass.getListOfMethods(),callerName,null);
                            }
                            if(currMethod==null){
                                for(String s:nextClass.getImp()){
                                    GraphElement tempClass = getTargetClass(this.listOfResults,s);
                                    currMethod = getTargetMethod(tempClass.getListOfMethods(),callerName,null);
                                }
                                if(currMethod==null){return null;}
                            }
                        }
                        String nextClassName = currMethod.getReturnType();
                        String nextMethodName = ((MethodCallExpr) child).getNameAsString();
                        nextClass = getTargetClass(listOfResults,nextClassName);
                        if(nextClass!=null){
                            nextMethod = getTargetMethod(nextClass.getListOfMethods(),nextMethodName,nextClass);
                        }
                    }
                }
            }else{
                if(child!=null && child instanceof MethodCallExpr && nextMethod != null){
                    JSONObject temp = getNextLevelCallJSON(currClass, nextClass, currMethod, nextMethod, (MethodCallExpr) child);
                    if(temp!=null){
                        if(temp.has("sep")){
                            JSONArray sep = (JSONArray) temp.get("sep");
                            while(sep.length()!=0){
                                if(checkJSONFullSize((JSONObject) sep.get(0))){
                                    jarray.put(sep.remove(0));
                                }
                            }
                        }else{
                            if(checkJSONFullSize(temp)){
                                jarray.put(temp);
                            }
                        }
                    }
                    String nextClassName = nextMethod.getReturnType();
                    String nextMethodName = ((MethodCallExpr) child).getNameAsString();
                    nextClass = getTargetClass(listOfResults,nextClassName);
                    if(nextClass!=null){
                        nextMethod = getTargetMethod(nextClass.getListOfMethods(),nextMethodName,null);
                    }
                    if(!child.getParentNode().equals(Optional.empty())){
                        child = child.getParentNode().get();
                    }
                }
            }
        }

        return jarray;
    }

    private JSONObject baseHelper(JSONObject json, GraphElement nextClass, String callerName){
        Method nextMethod = getTargetMethod(nextClass.getListOfMethods(),callerName,null);
        if(nextMethod!=null && nextMethod.getMethodCall().size()!=0){
            String rtype = getTargetMethod(nextClass.getListOfMethods(),callerName,null).getReturnType();
            json.put("returnType",(rtype.equals("void"))?"":rtype);
            json.put("param", getParam(nextMethod));
            JSONArray nextCallArr = new JSONArray();
            for(Node newNode:nextMethod.getMethodCall()){
                JSONObject temp = initSeqJSON(newNode,nextMethod,nextClass);
                if(temp!=null){
                    if(temp.has("sep")){
                        JSONArray sep = (JSONArray) temp.get("sep");
                        while(sep.length()!=0){
                            if(checkJSONFullSize((JSONObject) sep.get(0))){
                                nextCallArr.put(sep.remove(0));
                            }
                        }
                    }else{
                        if(temp.has("cond")){
                            JSONArray nextLevelCond = (JSONArray) temp.get("cond");
                            while(nextLevelCond.length()!=0){
                                nextCallArr.put(nextLevelCond.remove(0));
                            }
                            if(temp.has("stmt")){
                                nextCallArr.put(temp.get("stmt"));
                            }
                        }else{
                            if(checkJSONFullSize(temp)){
                                nextCallArr.put(temp);
                            }
                        }
                    }
                }
            }
            json.put("call",nextCallArr);
        }else if(nextMethod!=null){
            json.put("returnType",(nextMethod.getReturnType().equals("void"))?"":nextMethod.getReturnType());
            json.put("param", getParam(nextMethod));
            json.put("call",new JSONArray());
        }else{
            //extended method
            for(String s:nextClass.getInh()){
                GraphElement tempClass = getTargetClass(this.listOfResults,s);
                nextMethod = getTargetMethod(tempClass.getListOfMethods(),callerName,null);
                if(nextMethod!=null){
                    nextClass = tempClass;
                }
            }
            for(String s:nextClass.getImp()){
                GraphElement tempClass = getTargetClass(this.listOfResults,s);
                nextMethod = getTargetMethod(tempClass.getListOfMethods(),callerName,null);
                if(nextMethod!=null){
                    nextClass = tempClass;
                }
            }
            if(nextMethod==null){
                return json;
            }
            json.put("returnType",(nextMethod.getReturnType().equals("void"))?"":nextMethod.getReturnType());
            json.put("param", getParam(nextMethod));
            if(nextMethod.getMethodCall().size()!=0){
                JSONArray nextCallArr = new JSONArray();
                for(Node newNode:nextMethod.getMethodCall()){
                    JSONObject temp = initSeqJSON(newNode,nextMethod,nextClass);
                    if(temp!=null){
                        if(temp.has("sep")){
                            JSONArray sep = (JSONArray) temp.get("sep");
                            while(sep.length()!=0){
                                if(checkJSONFullSize((JSONObject) sep.get(0))){
                                    nextCallArr.put(sep.remove(0));
                                }
                            }
                        }else{
                            if(checkJSONFullSize(temp)){
                                nextCallArr.put(temp);
                            }
                        }
                    }
                }
                json.put("call",nextCallArr);
            }else{
                json.put("call",new JSONArray());
            }
        }
        return json;
    }

    /*
    traverse and retrieve all function calls from switch statement condition
     */
    private JSONArray traverseTreeSwitch(Node node,Method m, GraphElement ge){
        JSONArray reverseArr = new JSONArray();
        for(Node child:node.getChildNodes()){
            if(child instanceof MethodCallExpr){
                JSONObject temp= initSeqJSON(child,m,ge);
                if(temp!=null&&temp.has("sep")){
                    JSONArray jj = (JSONArray) temp.get("sep");
                    while(jj.length()!=0){
                        if(checkJSONFullSize((JSONObject) jj.get(0))){
                            reverseArr.put(jj.remove(0));
                        }
                    }
                }
            }
        }
        return reverseArr;
    }

    /*
    checker method for nested function calls. Check for number of function calls in one MethodCallExp
    if the number of MethodCallExp in this child nodes is greater than 1, the argument of this function
    call is also a function call
     */
    private boolean moreThanOneMethodCallExp(MethodCallExpr mc,MethodCallExpr n){

        return !mc.getNameAsString().equals(n.getNameAsString());
    }

    /*
    helper method to retrieve all function calls in foreach statement's condition
     */
    private JSONArray checkIterable(ForEachStmt node,Method m, GraphElement ge){
        JSONArray reverseArr = new JSONArray();
        Node child = node.getIterable();
        JSONObject temp= initSeqJSON(child,m,ge);
        if(temp!=null&&temp.has("sep")){
            JSONArray jj = (JSONArray) temp.get("sep");
            while(jj.length()!=0){
                if(checkJSONFullSize((JSONObject) jj.get(0))){
                    reverseArr.put(jj.remove(0));
                }
            }
        }
        return reverseArr;
    }

    private JSONObject getVarDeclaCall(Node node,Method m ,GraphElement ge){
        JSONArray jar=findAllMethodCall(node,m,ge);
        if(jar.length()!=0){
            JSONObject json = new JSONObject();
            json.put("sep",jar);
            return json;
        }
        return new JSONObject();
    }

    private JSONArray findAllMethodCall(Node node, Method m,GraphElement ge){
        JSONArray jarr = new JSONArray();
        for(Node n:node.getChildNodes()){
            if(n instanceof VariableDeclarationExpr || n instanceof VariableDeclarator){
                JSONArray temp = findAllMethodCall(n,m,ge);
                while (temp.length()!=0){
                    if(checkJSONFullSize((JSONObject) temp.get(0))){
                        jarr.put(temp.remove(0));
                    }
                }
            }else if(n instanceof MethodCallExpr){
                JSONObject json = initSeqJSON(n,m,ge);
                if(json!=null&&json.has("sep")){
                    JSONArray jj = (JSONArray) json.get("sep");
                    while(jj.length()!=0){
                        if(checkJSONFullSize((JSONObject) jj.get(0))){
                            jarr.put(jj.remove(0));
                        }
                    }
                }
            }
        }
        return jarr;
    }

    private JSONObject getThrowCall(Node node, Method m,GraphElement ge){
        JSONArray jar=traverseThrowCall(node,m,ge);
        if(jar.length()!=0){
            JSONObject json = new JSONObject();
            json.put("sep",jar);
            return json;
        }
        return new JSONObject();
    }

    private JSONArray traverseThrowCall(Node node, Method m,GraphElement ge){
        JSONArray jar = new JSONArray();
        for(Node n:node.getChildNodes()){
            if(n instanceof BinaryExpr || n instanceof ObjectCreationExpr){
                JSONArray temp = traverseThrowCall(n,m,ge);
                while(temp.length()!=0){
                    if(checkJSONFullSize((JSONObject) temp.get(0))){
                        jar.put(temp.remove(0));
                    }
                }
            }else if(n instanceof MethodCallExpr){
                JSONObject json = initSeqJSON(n,m,ge);
                if(json!=null&&json.has("sep")){
                    JSONArray jj = (JSONArray) json.get("sep");
                    while(jj.length()!=0){
                        if(checkJSONFullSize((JSONObject) jj.get(0))){
                            jar.put(jj.remove(0));
                        }
                    }
                }
            }
        }
        return jar;
    }

    private boolean checkJSONFullSize(JSONObject json){
        return (json!=null&&json.has("type")&&json.has("callerClass")&&json.has("calleeClass")&&
                json.has("callerName")&& json.has("returnType")&&json.has("call")&& json.has("param"))
                || (json!=null&&json.has("type")&&json.has("call")&&(json.get("type").equals("loop")||json.get("type").equals("alt")||
                json.get("type").equals("opt")));
    }

    private String removeBracket(String name){
        int start = name.indexOf("<");
        name = name.substring(0,start);
        return name;
    }
}
