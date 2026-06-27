package com.javapatterns.mcp.refactor;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.util.ArrayList;
import java.util.List;

/**
 * Mark every concrete implementor of a Command contract as
 * {@code final}. Subclassing a Command usually indicates that the
 * wrong abstraction is being extended; final commands prevent the
 * accidental hierarchy growth.
 *
 * <p>Triggers when an interface or abstract class declares
 * {@code execute()} (the Command contract). Every concrete class
 * implementing or extending that contract in the same source is
 * marked {@code final}.</p>
 *
 * <p>Idempotent: classes already declared {@code final} are left
 * untouched. Atomic: pure modifier addition on the class header,
 * no body changes, no import management.</p>
 */
public final class CommandMakeImplementationsFinal implements PatternRefactoring {

    @Override
    public RefactoringId id() {
        return RefactoringId.COMMAND_MAKE_IMPLEMENTATIONS_FINAL;
    }

    @Override
    public RefactoringResult apply(CompilationUnit unit) {
        List<String> changes = new ArrayList<>();
        List<ClassOrInterfaceDeclaration> all = unit.findAll(ClassOrInterfaceDeclaration.class);

        // Find Command contract candidates — abstract / interface, declares execute().
        List<String> commandTypes = new ArrayList<>();
        for (ClassOrInterfaceDeclaration t : all) {
            if (!(t.isInterface() || t.isAbstract())) continue;
            boolean hasExecute = t.getMethods().stream()
                .map(MethodDeclaration::getNameAsString)
                .anyMatch("execute"::equals);
            if (!hasExecute) continue;
            commandTypes.add(t.getNameAsString());
        }
        if (commandTypes.isEmpty()) {
            return new RefactoringResult(id(), LexicalPreservingPrinter.print(unit), false, List.of());
        }

        for (ClassOrInterfaceDeclaration cls : all) {
            if (cls.isInterface() || cls.isAbstract()) continue;
            if (cls.isFinal()) continue;

            boolean implementsCommand = cls.getImplementedTypes().stream()
                .anyMatch(t -> commandTypes.contains(t.getNameAsString()))
                || cls.getExtendedTypes().stream()
                .anyMatch(t -> commandTypes.contains(t.getNameAsString()));
            if (!implementsCommand) continue;

            cls.addModifier(Modifier.Keyword.FINAL);
            int line = cls.getBegin().map(p -> p.line).orElse(-1);
            changes.add(cls.getNameAsString() + ": concrete command at line " + line + " marked final");
        }

        return new RefactoringResult(id(), LexicalPreservingPrinter.print(unit), !changes.isEmpty(), changes);
    }
}
