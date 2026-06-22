package com.javapatterns.mcp.refactor;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.util.ArrayList;
import java.util.List;

/**
 * Replace an uncached {@code getInstance()} (one that {@code new}-s every
 * call) with the Bill-Pugh holder idiom.
 *
 * <p>Triggers only when:</p>
 * <ul>
 *   <li>the class has a {@code static getInstance()} that returns the
 *       enclosing class;</li>
 *   <li>there is no static instance field of the same type;</li>
 *   <li>there is no nested holder class already.</li>
 * </ul>
 *
 * <p>Adds a private static nested {@code Holder} class with the
 * {@code INSTANCE} field, and rewrites the body of {@code getInstance()}
 * to {@code return Holder.INSTANCE;}.</p>
 */
public final class SingletonAddHolderIdiom implements PatternRefactoring {

    @Override
    public RefactoringId id() { return RefactoringId.SINGLETON_ADD_HOLDER_IDIOM; }

    @Override
    public RefactoringResult apply(CompilationUnit unit) {
        List<String> changes = new ArrayList<>();

        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (cls.isInterface()) return;
            String className = cls.getNameAsString();

            // Find a static getInstance() returning the enclosing class.
            MethodDeclaration getInstance = cls.getMethods().stream()
                .filter(MethodDeclaration::isStatic)
                .filter(m -> m.getNameAsString().equals("getInstance"))
                .filter(m -> m.getType().toString().equals(className))
                .findFirst()
                .orElse(null);
            if (getInstance == null) return;

            // Already has a static field of the enclosing type? Skip.
            boolean hasInstanceField = cls.getFields().stream()
                .filter(FieldDeclaration::isStatic)
                .flatMap(f -> f.getVariables().stream())
                .anyMatch(v -> isOfType(v, className));
            if (hasInstanceField) return;

            // Already has a Holder class? Skip (idempotency).
            boolean hasHolder = cls.getMembers().stream()
                .filter(m -> m instanceof ClassOrInterfaceDeclaration)
                .map(m -> (ClassOrInterfaceDeclaration) m)
                .anyMatch(c -> c.getNameAsString().equals("Holder"));
            if (hasHolder) return;

            // Build the Holder class as a parsed AST fragment so it
            // integrates cleanly with the lexical printer.
            String holderSrc =
                "private static final class Holder {\n" +
                "    private static final " + className + " INSTANCE = new " + className + "();\n" +
                "    private Holder() {}\n" +
                "}";
            BodyDeclaration<?> holder = StaticJavaParser.parseBodyDeclaration(holderSrc);
            cls.getMembers().add(holder);

            // Rewrite getInstance() body to `return Holder.INSTANCE;`.
            getInstance.setBody(StaticJavaParser.parseBlock(
                "{ return Holder.INSTANCE; }"));

            int line = cls.getBegin().map(p -> p.line).orElse(-1);
            changes.add(className + ": added Bill-Pugh Holder class and rewrote " +
                "getInstance() body at line " + line);
        });

        String newSource = LexicalPreservingPrinter.print(unit);
        return new RefactoringResult(id(), newSource, !changes.isEmpty(), changes);
    }

    private static boolean isOfType(VariableDeclarator v, String typeName) {
        return v.getType() instanceof ClassOrInterfaceType cit
            && cit.getNameAsString().equals(typeName);
    }
}
