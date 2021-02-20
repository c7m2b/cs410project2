package com.program.analysis.app.representation;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.util.HashSet;
import java.util.Set;

// This class is the representation of the data to be sent to front-end
public class GraphElement {
    private String className;
    private String classType;
    private Set<Field> fields;
    private Set<Method> listOfMethods;
    private Set<String> inh;
    private Set<String> imp;
    private Set<String> dep = new HashSet<String>();;
    private Set<String> tp;

    /*
        This constructor manages all visitors to retrieve data from parsed data(CompilationUnit)
    */
    public GraphElement(CompilationUnit compilationUnit) {
        ClassCollector classesCollector = new ClassCollector();
        classesCollector.visit(compilationUnit, null);
        this.className = classesCollector.getName();
        this.classType = classesCollector.getType();
        this.fields = classesCollector.getFields();
        this.inh = classesCollector.getInheritance();
        this.imp = classesCollector.getImplementation();
        this.tp = classesCollector.getTypeParameter();
        
        MethodCollector methodCollector = new MethodCollector();
        methodCollector.visit(compilationUnit, null);
        this.listOfMethods = methodCollector.getMethodObj();
    }

    public String getClassType() {
        return classType;
    }

    public String getClassName() {
        return className;
    }

    public Set<Field> getFields() {
        return fields;
    }

    public Set<Method> getListOfMethods() {
        return listOfMethods;
    }

    public Set<String> getInh() {
        return inh;
    }

    public Set<String> getImp() {
        return imp;
    }

    public Set<String> getDep() {
        return dep;
    }

    public Set<String> getTypeParameters() {
        return tp;
    }

    public void addDep(String s) {
        this.dep.add(s);
    }
}
