package com.javapatterns.mcp.refactor;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.util.ArrayList;
import java.util.List;

/**
 * Mark every non-final instance field of a Builder's outer class as
 * {@code final}.
 *
 * <p>Triggers only when the outer class has a static nested class named
 * {@code Builder} (the marker that identifies a Builder candidate). All
 * other classes are left alone.</p>
 */
public final class BuilderMakeFieldsFinal implements PatternRefactoring {

    @Override
    public RefactoringId id() { return RefactoringId.BUILDER_MAKE_FIELDS_FINAL; }

    @Override
    public RefactoringResult apply(CompilationUnit unit) {
        List<String> changes = new ArrayList<>();

        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(outer -> {
            if (outer.isInterface()) return;

            boolean hasNestedBuilder = outer.getMembers().stream()
                .filter(m -> m instanceof ClassOrInterfaceDeclaration)
                .map(m -> (ClassOrInterfaceDeclaration) m)
                .anyMatch(c -> c.isStatic() && c.getNameAsString().equals("Builder"));
            if (!hasNestedBuilder) return;

            for (FieldDeclaration f : outer.getFields()) {
                if (f.isStatic() || f.isFinal()) continue;
                f.addModifier(Modifier.Keyword.FINAL);
                int line = f.getBegin().map(p -> p.line).orElse(-1);
                String fieldNames = f.getVariables().stream()
                    .map(v -> v.getNameAsString())
                    .reduce((a, b) -> a + ", " + b).orElse("?");
                changes.add(outer.getNameAsString() + ": field(s) " + fieldNames +
                    " at line " + line + " marked final");
            }
        });

        String newSource = LexicalPreservingPrinter.print(unit);
        return new RefactoringResult(id(), newSource, !changes.isEmpty(), changes);
    }
}
