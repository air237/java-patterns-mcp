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
 * Mark the adaptee field of an Adapter-shaped class as {@code final}.
 *
 * <p>Triggers only on classes that look like an object adapter: a
 * concrete non-abstract class that implements or extends at least one
 * <i>target</i> type AND has at least one instance field whose declared
 * type is <b>different</b> from the target(s). That field is the
 * adaptee — and it should be final so the wrapped instance cannot be
 * silently swapped out after construction.</p>
 *
 * <p>Idempotent: if the adaptee field is already {@code final}, the
 * source is left untouched.</p>
 */
public final class AdapterMakeAdapteeFinal implements PatternRefactoring {

    private static final Set<String> JDK_CONTAINERS = Set.of(
        "List", "Map", "Set", "Collection", "String", "Optional", "Queue", "Deque");

    @Override
    public RefactoringId id() { return RefactoringId.ADAPTER_MAKE_ADAPTEE_FINAL; }

    @Override
    public RefactoringResult apply(CompilationUnit unit) {
        List<String> changes = new ArrayList<>();

        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (cls.isInterface() || cls.isAbstract()) return;
            String name = cls.getNameAsString();
            if (name.startsWith("Abstract")) return;

            // Targets = implemented + extended types. If none, this is not adapter-shaped.
            Set<String> targets = new HashSet<>();
            cls.getImplementedTypes().forEach(t -> targets.add(t.getNameAsString()));
            cls.getExtendedTypes().forEach(t -> targets.add(t.getNameAsString()));
            if (targets.isEmpty()) return;

            for (FieldDeclaration f : cls.getFields()) {
                if (f.isStatic()) continue;
                if (f.isFinal()) continue; // already final — idempotent
                // Find the first variable whose type is a non-target, non-JDK-container ref type.
                for (VariableDeclarator v : f.getVariables()) {
                    if (!(v.getType() instanceof ClassOrInterfaceType cit)) continue;
                    String tname = cit.getNameAsString();
                    if (JDK_CONTAINERS.contains(tname)) continue;
                    if (targets.contains(tname)) continue;

                    // This is an adaptee-shaped field. Promote it to final.
                    f.addModifier(Modifier.Keyword.FINAL);
                    int line = f.getBegin().map(p -> p.line).orElse(-1);
                    changes.add(name + ": adaptee field '" + v.getNameAsString() +
                        "' (type " + tname + ") at line " + line + " marked final");
                    break; // one promotion per field declaration is enough
                }
            }
        });

        String newSource = LexicalPreservingPrinter.print(unit);
        return new RefactoringResult(id(), newSource, !changes.isEmpty(), changes);
    }
}
