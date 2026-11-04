package cz.jakubaugustyn.predictivedeobfuscator.steps;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import cz.jakubaugustyn.predictivedeobfuscator.StepContext;

import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;

public class RenameFieldStep extends Step {
    private String className;
    private String oldName;
    private String newName;

    public RenameFieldStep() {
    }

    public RenameFieldStep(String className, String oldName, String newName, String reasoning) {
        super(reasoning);
        this.className = className;
        this.oldName = oldName;
        this.newName = newName;
    }

    @Override
    public String getDescription() {
        return "Rename " + className + " class's " + oldName + " field to " + newName;
    }

    public String getClassName() {
        return className;
    }

    public String getOldName() {
        return oldName;
    }

    public String getNewName() {
        return newName;
    }

    public VariableDeclarator getMatchingFieldDeclaration(CompilationUnit code) {
        return code.findAll(FieldDeclaration.class).stream()
                .map(field -> field
                        .getVariables()
                        .stream()
                        .filter(var -> var.getNameAsString().equals(oldName))
                        .reduce((a, b) -> {
                            throw new IllegalStateException("Multiple fields found with the provided name: " + a + ", " + b);
                        })
                )
                .filter(Optional::isPresent)
                .map(Optional::get)
                .reduce((a, b) -> {
                    throw new IllegalStateException("Multiple fields found with the provided name: " + a + ", " + b);
                })
                .orElseThrow(() -> new IllegalStateException("No field found with the provided name"));
    }

    @Override
    public void execute(StepContext context) {
        // System.out.println("Renaming " + className + "." + oldName + " to " + newName);
        CompilationUnit code = context.getClass(className);
        VariableDeclarator field = getMatchingFieldDeclaration(code);
        // System.out.println("Matching field: " + field);

        for (CompilationUnit code2 : context.getCompilationUnits()) {
            Stream.concat(
                    code2.findAll(NameExpr.class).stream(),
                    code2.findAll(FieldAccessExpr.class).stream()
            ).forEach(identifier -> {
                if (!identifier.getName().asString().equals(oldName)) return;
                ResolvedValueDeclaration declaration = identifier.resolve();
                if (!(declaration instanceof JavaParserFieldDeclaration fieldDeclaration)) return;
                if (!fieldDeclaration.declaringType().getQualifiedName().equals(className)) return;

                identifier.setName(newName);
            });
        }

        field.setName(newName);
    }
}