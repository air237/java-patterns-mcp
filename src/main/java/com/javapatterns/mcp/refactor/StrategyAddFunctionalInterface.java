package com.javapatterns.mcp.refactor;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.util.ArrayList;
import java.util.List;

/**
 * Annotate a Strategy contract interface with
 * {@code @FunctionalInterface} when it qualifies as one — i.e. it
 * declares exactly one abstract method.
 *
 * <p>This is useful in two ways:</p>
 * <ul>
 *   <li>The compiler will reject any future change that adds a second
 *       abstract method to the interface, protecting the
 *       "swappable single algorithm" contract of Strategy.</li>
 *   <li>It signals to callers that {@code Type s = x -> ...;}
 *       lambda-form usage is intentional and supported.</li>
 * </ul>
 *
 * <p>Triggers only on Strategy-shaped contracts: interfaces whose
 * name ends with {@code "Strategy"} and that declare exactly one
 * non-default, non-static method. Other interfaces (including
 * multi-method ones) are left alone.</p>
 *
 * <p>Idempotent: if the interface already carries the annotation,
 * the source is left untouched.</p>
 */
public final class StrategyAddFunctionalInterface implements PatternRefactoring {

    private static final String FUNCTIONAL_INTERFACE = "FunctionalInterface";

    @Override
    public RefactoringId id() {
        return RefactoringId.STRATEGY_ADD_FUNCTIONAL_INTERFACE;
    }

    @Override
    public RefactoringResult apply(CompilationUnit unit) {
        List<String> changes = new ArrayList<>();

        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (!cls.isInterface()) return;
            String name = cls.getNameAsString();
            if (!name.endsWith("Strategy")) return;

            // Count the abstract single-abstract-method (SAM) candidates.
            // For an interface, an "abstract" method is one that is
            // neither default nor static. We do NOT count private
            // helpers (private methods in interfaces are valid since
            // Java 9 but are implementation details).
            long sam = cls.getMethods().stream()
                .filter(m -> !m.isDefault() && !m.isStatic() && !m.isPrivate())
                .count();
            if (sam != 1) return;

            // Already annotated? skip — idempotent.
            boolean alreadyAnnotated = cls.getAnnotations().stream()
                .anyMatch(a -> a.getNameAsString().equals(FUNCTIONAL_INTERFACE)
                    || a.getNameAsString().equals("java.lang." + FUNCTIONAL_INTERFACE));
            if (alreadyAnnotated) return;

            cls.addMarkerAnnotation(FUNCTIONAL_INTERFACE);
            int line = cls.getBegin().map(p -> p.line).orElse(-1);
            String samName = cls.getMethods().stream()
                .filter(m -> !m.isDefault() && !m.isStatic() && !m.isPrivate())
                .findFirst()
                .map(MethodDeclaration::getNameAsString)
                .orElse("?");
            changes.add(name + ": added @FunctionalInterface (SAM = " + samName +
                "()) at line " + line);
        });

        String newSource = LexicalPreservingPrinter.print(unit);
        return new RefactoringResult(id(), newSource, !changes.isEmpty(), changes);
    }
}
