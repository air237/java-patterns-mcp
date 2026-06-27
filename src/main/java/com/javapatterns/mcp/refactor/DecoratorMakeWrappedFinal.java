package com.javapatterns.mcp.refactor;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mark the wrapped (delegate) field of a Decorator-shaped class as
 * {@code final} so the wrapped instance cannot be silently swapped
 * out after construction.
 *
 * <p>Triggers only on concrete classes that look like a decorator:
 * implements / extends some target type AND have a non-static field
 * whose declared type matches one of those targets. Abstract bases
 * are intentionally skipped — they are scaffolding, and their
 * {@code protected} field is meant to be the storage location for
 * subclasses (still typically final, but already correct in
 * canonical implementations).</p>
 *
 * <p>Idempotent: if the wrapped field is already {@code final}, the
 * source is left untouched. Atomic: pure modifier swap, no name
 * resolution, no import management.</p>
 */
public final class DecoratorMakeWrappedFinal implements PatternRefactoring {

    @Override
    public RefactoringId id() {
        return RefactoringId.DECORATOR_MAKE_WRAPPED_FINAL;
    }

    @Override
    public RefactoringResult apply(CompilationUnit unit) {
        List<String> changes = new ArrayList<>();

        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (cls.isInterface() || cls.isAbstract()) return;
            String name = cls.getNameAsString();

            Set<String> targets = new HashSet<>();
            cls.getImplementedTypes().forEach(t -> targets.add(t.getNameAsString()));
            cls.getExtendedTypes().forEach(t -> targets.add(t.getNameAsString()));
            if (targets.isEmpty()) return;

            for (FieldDeclaration f : cls.getFields()) {
                if (f.isStatic()) continue;
                if (f.isFinal()) continue; // already final — idempotent
                for (VariableDeclarator v : f.getVariables()) {
                    if (!(v.getType() instanceof ClassOrInterfaceType cit)) continue;
                    String tname = cit.getNameAsString();
                    if (!targets.contains(tname)) continue;

                    // Decorator-shaped wrapped field. Promote it.
                    f.addModifier(Modifier.Keyword.FINAL);
                    int line = f.getBegin().map(p -> p.line).orElse(-1);
                    changes.add(name + ": wrapped field '" + v.getNameAsString() +
                        "' (type " + tname + ") at line " + line + " marked final");
                    break;
                }
            }
        });

        String newSource = LexicalPreservingPrinter.print(unit);
        return new RefactoringResult(id(), newSource, !changes.isEmpty(), changes);
    }
}
