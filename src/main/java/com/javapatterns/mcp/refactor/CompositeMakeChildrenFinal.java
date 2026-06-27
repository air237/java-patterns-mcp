package com.javapatterns.mcp.refactor;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mark the children collection field of a Composite-shaped class as
 * {@code final} so the backing collection cannot be silently swapped
 * out at runtime.
 *
 * <p>Triggers only when the source contains at least one component
 * interface implemented by a concrete class whose non-static field
 * type contains either {@code <Component>} or {@code Component[]}
 * (the children collection). That class's children field is then
 * promoted to {@code final}.</p>
 *
 * <p>Idempotent: classes whose children field is already
 * {@code final} are left untouched. Atomic: pure modifier addition
 * on the field, no body changes, no import management.</p>
 */
public final class CompositeMakeChildrenFinal implements PatternRefactoring {

    @Override
    public RefactoringId id() {
        return RefactoringId.COMPOSITE_MAKE_CHILDREN_FINAL;
    }

    @Override
    public RefactoringResult apply(CompilationUnit unit) {
        List<String> changes = new ArrayList<>();
        List<ClassOrInterfaceDeclaration> all = unit.findAll(ClassOrInterfaceDeclaration.class);

        // Collect candidate component interfaces.
        Set<String> componentTypes = new HashSet<>();
        for (ClassOrInterfaceDeclaration t : all) {
            if (t.isInterface()) componentTypes.add(t.getNameAsString());
        }
        if (componentTypes.isEmpty()) {
            return new RefactoringResult(id(), LexicalPreservingPrinter.print(unit), false, List.of());
        }

        for (ClassOrInterfaceDeclaration cls : all) {
            if (cls.isInterface() || cls.isAbstract()) continue;
            String name = cls.getNameAsString();

            // The class must implement a component interface to qualify.
            boolean implementsComponent = cls.getImplementedTypes().stream()
                .anyMatch(t -> componentTypes.contains(t.getNameAsString()));
            if (!implementsComponent) continue;

            // Look for the children field (collection-of-component type).
            for (FieldDeclaration f : cls.getFields()) {
                if (f.isStatic()) continue;
                if (f.isFinal()) continue; // already final — idempotent
                for (VariableDeclarator v : f.getVariables()) {
                    String t = v.getType().toString();
                    String matched = null;
                    for (String comp : componentTypes) {
                        if (t.contains("<" + comp + ">") || t.contains(comp + "[]")) {
                            matched = comp;
                            break;
                        }
                    }
                    if (matched == null) continue;

                    f.addModifier(Modifier.Keyword.FINAL);
                    int line = f.getBegin().map(p -> p.line).orElse(-1);
                    changes.add(name + ": children field '" + v.getNameAsString() +
                        "' (collection of " + matched + ") at line " + line + " marked final");
                    break; // one field per declaration
                }
            }
        }

        return new RefactoringResult(id(), LexicalPreservingPrinter.print(unit), !changes.isEmpty(), changes);
    }
}
