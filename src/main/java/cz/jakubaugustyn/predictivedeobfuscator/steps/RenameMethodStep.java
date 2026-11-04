package cz.jakubaugustyn.predictivedeobfuscator.steps;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import cz.jakubaugustyn.predictivedeobfuscator.StepContext;

import java.util.List;

public class RenameMethodStep extends Step {
    private String className;
    // TODO Make sure it is present in the JSON being parsed (make it required)
    private List<String> signature;
    private String oldName;
    private String newName;

    public RenameMethodStep() {
    }

    public RenameMethodStep(String className, List<String> signature, String oldName, String newName, String reasoning) {
        super(reasoning);
        this.className = className;
        this.signature = signature;
        this.oldName = oldName;
        this.newName = newName;
    }

    @Override
    public String getDescription() {
        return "Rename " + className + " class's " + oldName + "() method to " + newName;
    }

    public String getClassName() {
        return className;
    }

    public List<String> getSignature() {
        return signature;
    }

    public String getOldName() {
        return oldName;
    }

    public String getNewName() {
        return newName;
    }

    /*public static String getMethodArgumentSignature(MethodDeclaration method) {
        // ZLjava.lang.String;LExample;
        return method
                .getParameters()
                .stream()
                .map(parameter -> {
                    Type type = parameter.getType();
                    String typeName = null;
                    if (type instanceof ClassOrInterfaceType classOrInterfaceType) {
                        ResolvedType resolvedType = classOrInterfaceType.resolve();
                        if (resolvedType.isReferenceType()) {
                            Optional<ResolvedReferenceTypeDeclaration> declaration = ((ReferenceTypeImpl) resolvedType).getTypeDeclaration();
                            if (declaration.isEmpty())
                                throw new RuntimeException("Failed to resolve type's declaration");
                            typeName = "L" + declaration.get().getQualifiedName() + ";";
                        } else
                            throw new RuntimeException("Type is not a reference type");
                    } else if (type instanceof PrimitiveType primitiveType) {
                        typeName = primitiveType.getType().toDescriptor();
                    }

                    if (typeName == null)
                        throw new RuntimeException("Failed to get argument type for '" + type + "'");
                    return typeName;
                })
                .reduce((a, b) -> a + b)
                .orElse("");
    }*/
    public static String getMethodSignature(MethodDeclaration method) {
        // Example.test(boolean, java.lang.String, Example)
        return method.resolve().getQualifiedSignature();
    }

    public static String getMethodSignature(String className, String methodName, List<String> arguments) {
        return className + "." + methodName + "(" + String.join(", ", arguments) + ")";
    }

    public String getMethodSignature() {
        return getMethodSignature(className, oldName, signature);
    }

    public MethodDeclaration getMatchingMethodDeclaration(CompilationUnit code) {
        String signature = getMethodSignature();

        return code.findAll(MethodDeclaration.class).stream()
                .filter(method -> {
                    String methodSignature = getMethodSignature(method);
                    return method.getNameAsString().equals(oldName) && methodSignature.equals(signature);
                })
                // https://stackoverflow.com/questions/22694884/filter-java-stream-to-1-and-only-1-element#answer-34663949
                .reduce((a, b) -> {
                    throw new IllegalStateException("Multiple methods found with the provided name and signature '" + signature + "': " + a + ", " + b);
                })
                .orElseThrow(() -> new IllegalStateException("No method found with the provided name and signature '" + signature + "'"));
    }

    @Override
    public void execute(StepContext context) {
        // https://www.javaguides.net/2024/05/guide-to-javaparser-library-in-java.html#:~:text=Adding%20a%20Method%20to%20a%20Class
        // ClassOrInterfaceDeclaration classDecl = context.getClass("Example").getClassByName("Example").get();
        // MethodDeclaration method = classDecl.addMethod("greet", Modifier.Keyword.PUBLIC);
        // JavaParser javaParser = new JavaParser(context.getSourceRoot().getParserConfiguration());
        // method.setBody(javaParser.parseBlock("{ System.out.println(\"Hello, world!\"); }").getResult().get());

        // https://www.javaguides.net/2024/05/guide-to-javaparser-library-in-java.html#:~:text=the%20class%20name.-,Renaming%20a%20Method,-You%20can%20rename
        String signature = getMethodSignature();
        CompilationUnit code = context.getClass(className);
        MethodDeclaration method = getMatchingMethodDeclaration(code);
        // System.out.println("Matching method: " + method);

        for (CompilationUnit code2 : context.getCompilationUnits()) {
            code2.findAll(MethodCallExpr.class).forEach(methodCall -> {
                String calleeSignature = methodCall.resolve().getQualifiedSignature();
                if (!calleeSignature.equals(signature)) return;

                methodCall.setName(newName);
            });
        }

        // Renaming it before resolving the calls would break the resolution
        method.setName(newName);
        // System.out.println("YAML:" + new YamlPrinter(true).output(code));
    }
}