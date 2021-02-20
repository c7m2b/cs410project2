package com.program.analysis.app.representation;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// TODO: handle private classes inside a class? we should ignore these private classes?
public class ClassCollector extends VoidVisitorAdapter<Object> {
    private String name;
    private Set<Field> fields;
    private String type;
    private Set<String> inheritance;
    private Set<String> implementation;
    private Set<String> typeParameter;

    public ClassCollector() {
        name = "";
        fields = new HashSet<Field>();
        type = "";
        inheritance = new HashSet<String>();
        implementation = new HashSet<String>();
        typeParameter = new HashSet<String>();
    }


    public String getType() {
        return type;
    }

    public String getName(){
        return name;
    }

    public Set<Field> getFields() {
        return fields;
    }

    public Set<String> getInheritance() {
        return inheritance;
    }

    public Set<String> getImplementation() {
        return implementation;
    }

    public Set<String> getTypeParameter() {
        return typeParameter;
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration n, Object arg) {
        // Currently we may not need to do further processing or than a list of dependency if front-end can handle the dependency with implementation/inheritance conflict
        this.name = n.getNameAsString();
        type = n.isInterface()? "interface" : getType(n.getModifiers());
        for (TypeParameter tp: n.getTypeParameters()) {
            typeParameter.add(tp.getNameAsString());
        }
        for (ClassOrInterfaceType ci: n.getExtendedTypes()) {
            inheritance.add(ci.getNameAsString());
        }
        for (ClassOrInterfaceType ci: n.getImplementedTypes()) {
            implementation.add(ci.getNameAsString());
        }
        for (FieldDeclaration fd: n.getFields()) {
            fields.add(new Field(fd));
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(EnumDeclaration n, Object arg) {
        //enum class is only for declaration of enum constants
        this.name = n.getNameAsString();
        this.type = "enum";
        for(FieldDeclaration fd: n.getFields()) {
            fields.add(new Field(fd));
        }
        for(EnumConstantDeclaration ed: n.getEntries()){
            fields.add(new Field(ed));
        }
        super.visit(n, arg);
    }

    private String getType(List<Modifier> modifiers) {
        for (Modifier mod: modifiers) {
            if (mod.equals(Modifier.abstractModifier())) {
                return mod.getKeyword().asString();
            }
        }
        return "class";
    }
}
