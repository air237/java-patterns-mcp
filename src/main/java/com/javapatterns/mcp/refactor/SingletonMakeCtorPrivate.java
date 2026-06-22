package com.javapatterns.mcp.refactor;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.util.ArrayList;
import java.util.List;

/**
 * Turn public constructors of Singleton-shaped classes into private ones.
 *
 * <p>A class is considered "Singleton-shaped" iff it has a {@code static}
 * {@code getInstance()} method (any access modifier) — we don't want to
 * touch random classes that just happen to have a public constructor.</p>
 *
 * <p>If the constructor is already private, this refactoring does nothing
 * (idempotent).</p>
 */
public final class SingletonMakeCtorPrivate implements PatternRefactoring {

    @Override
    public RefactoringId id() { return RefactoringId.SINGLETON_MAKE_CTOR_PRIVATE; }

    @Override
    public RefactoringResult apply(CompilationUnit unit) {
        List<String> changes = new ArrayList<>();

        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (cls.isInterface()) return;

            boolean hasGetInstance = cls.getMethods().stream()
                .filter(MethodDeclaration::isStatic)
                .anyMatch(m -> m.getNameAsString().equals("getInstance"));
            if (!hasGetInstance) return; // not a Singleton candidate

            for (ConstructorDeclaration ctor : cls.getConstructors()) {
                if (ctor.isPublic()) {
                    ctor.removeModifier(Modifier.Keyword.PUBLIC);
                    ctor.addModifier(Modifier.Keyword.PRIVATE);
                    int line = ctor.getBegin().map(p -> p.line).orElse(-1);
                    changes.add(cls.getNameAsString() + ": constructor at line "
                        + line + " made private");
                }
            }
        });

        String newSource = LexicalPreservingPrinter.print(unit);
        return new RefactoringResult(id(), newSource, !changes.isEmpty(), changes);
    }
}
