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
 * Mark the delegate (real-subject) field of a Proxy-shaped class as
 * {@code final} so the wrapped real subject cannot be silently
 * swapped out after construction.
 *
 * <p>Triggers only on classes that look like a proxy:</p>
 * <ul>
 *   <li>concrete (non-interface, non-abstract);</li>
 *   <li>class name contains a proxy-semantic hint (proxy / stub /
 *       surrogate / caching / lazy / auth / remote / logging /
 *       tracing) AND does NOT contain "decorator" / "wrapper"
 *       (which belong to {@code DecoratorMakeWrappedFinal});</li>
 *   <li>implements / extends some target type AND has a non-static
 *       field whose declared type matches that target.</li>
 * </ul>
 *
 * <p>Idempotent: classes whose delegate field is already
 * {@code final} are left untouched. Atomic: pure modifier addition
 * on the field, no body changes, no import management.</p>
 */
public final class ProxyMakeSubjectFinal implements PatternRefactoring {

    private static final Set<String> PROXY_HINTS = Set.of(
        "proxy", "stub", "surrogate", "caching", "lazy", "auth",
        "authentic", "authoriz", "remote", "logging", "tracing"
    );

    @Override
    public RefactoringId id() {
        return RefactoringId.PROXY_MAKE_SUBJECT_FINAL;
    }

    @Override
    public RefactoringResult apply(CompilationUnit unit) {
        List<String> changes = new ArrayList<>();

        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (cls.isInterface() || cls.isAbstract()) return;
            String name = cls.getNameAsString();
            String nameLower = name.toLowerCase();

            // Defer Decorator-shaped names to the Decorator refactoring.
            if (nameLower.contains("decorator") || nameLower.contains("wrapper")) return;
            boolean hintedName = PROXY_HINTS.stream().anyMatch(nameLower::contains);
            if (!hintedName) return;

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

                    f.addModifier(Modifier.Keyword.FINAL);
                    int line = f.getBegin().map(p -> p.line).orElse(-1);
                    changes.add(name + ": delegate field '" + v.getNameAsString() +
                        "' (real subject of type " + tname + ") at line " + line + " marked final");
                    break;
                }
            }
        });

        String newSource = LexicalPreservingPrinter.print(unit);
        return new RefactoringResult(id(), newSource, !changes.isEmpty(), changes);
    }
}
