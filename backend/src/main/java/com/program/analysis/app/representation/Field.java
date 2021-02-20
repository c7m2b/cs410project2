package com.program.analysis.app.representation;

import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;

public class Field {
    private String access;
    private String type;
    private String name;

    public Field(FieldDeclaration declaration) {
        // we need to make sure we are retriving the correct data
        this.access = declaration.getAccessSpecifier().asString();
        this.type = declaration.getCommonType().asString();
        this.name = declaration.getVariables().get(0).getNameAsString();
    }

    public Field(EnumConstantDeclaration declaration) {
        this.access = "";
        this.type = "";
        this.name = declaration.getNameAsString();
    }

    public String getAccess() {
        return access;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

}
