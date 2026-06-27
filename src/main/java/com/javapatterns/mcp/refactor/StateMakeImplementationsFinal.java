package com.javapatterns.mcp.refactor;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.util.ArrayList;
import java.util.List;

/**
 * Mark every concrete implementor of a State hierarchy as
 * {@code final} so deeper subclassing cannot turn well-defined
 * transitions into ambiguous "which subtype am I really in?"
 * questions.
 *
 * <p>Triggers only when a State-named contract is present in the
 * source: an interface or abstract class whose name ends with
 * {@code "State"}. Then every concrete (non-abstract, non-interface)
 * class implementing or extending that contract is marked
 * {@code final}.</p>
 *
 * <p>Idempotent: classes already declared {@code final} are left
 * untouched. Atomic: pure modifier addition on the class header,
 * no body changes, no import management.</p>
 */
public final class StateMakeImplementationsFinal implements PatternRefactoring {

    @Override
    public RefactoringId id() {
        return RefactoringId.STATE_MAKE_IMPLEMENTATIONS_FINAL;
    }

    @Override
    public RefactoringResult apply(CompilationUnit unit) {
        List<String> changes = new ArrayList<>();
        List<ClassOrInterfaceDeclaration> all = unit.findAll(ClassOrInterfaceDeclaration.class);

        // Find all State-named abstractions.
        List<String> stateTypes = new ArrayList<>();
        for (ClassOrInterfaceDeclaration t : all) {
            if (!t.getNameAsString().endsWith("State")) continue;
            if (!(t.isInterface() || t.isAbstract())) continue;
            stateTypes.add(t.getNameAsString());
        }
        if (stateTypes.isEmpty()) {
            return new RefactoringResult(id(), LexicalPreservingPrinter.print(unit), false, List.of());
        }

        for (ClassOrInterfaceDeclaration cls : all) {
            if (cls.isInterface() || cls.isAbstract()) continue;
            if (cls.isFinal()) continue;

            boolean implementsState = cls.getImplementedTypes().stream()
                .anyMatch(t -> stateTypes.contains(t.getNameAsString()))
                || cls.getExtendedTypes().stream()
                .anyMatch(t -> stateTypes.contains(t.getNameAsString()));
            if (!implementsState) continue;

            cls.addModifier(Modifier.Keyword.FINAL);
            int line = cls.getBegin().map(p -> p.line).orElse(-1);
            changes.add(cls.getNameAsString() + ": concrete state at line " + line + " marked final");
        }

        return new RefactoringResult(id(), LexicalPreservingPrinter.print(unit), !changes.isEmpty(), changes);
    }
}
