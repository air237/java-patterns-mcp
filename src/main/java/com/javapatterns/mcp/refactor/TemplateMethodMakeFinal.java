package com.javapatterns.mcp.refactor;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mark the template method of a Template-Method-shaped class as
 * {@code final} so subclasses cannot bypass the locked algorithm
 * skeleton.
 *
 * <p>Triggers only on classes that look like the canonical
 * (inheritance-based) Template Method: an abstract class declaring at
 * least one abstract hook AND at least one concrete, non-static
 * method that calls one of those hooks (same shape filter as the
 * detector and validator).</p>
 *
 * <p>Idempotent: if the template method is already {@code final},
 * the source is left untouched.</p>
 */
public final class TemplateMethodMakeFinal implements PatternRefactoring {

    @Override
    public RefactoringId id() { return RefactoringId.TEMPLATE_METHOD_MAKE_FINAL; }

    @Override
    public RefactoringResult apply(CompilationUnit unit) {
        List<String> changes = new ArrayList<>();

        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (cls.isInterface()) return;
            if (!cls.isAbstract()) return;
            String name = cls.getNameAsString();

            // Collect names of abstract methods declared on this class — these are the hooks.
            Set<String> abstractHookNames = new HashSet<>();
            for (MethodDeclaration m : cls.getMethods()) {
                if (m.isAbstract()) abstractHookNames.add(m.getNameAsString());
            }
            if (abstractHookNames.isEmpty()) return;

            // For every non-abstract, non-static, non-final concrete method whose body
            // calls at least one abstract hook on this — promote it to final.
            for (MethodDeclaration m : cls.getMethods()) {
                if (m.isAbstract()) continue;
                if (m.isStatic()) continue;
                if (m.isFinal()) continue; // already final — idempotent
                if (!callsAnyHook(m, abstractHookNames)) continue;

                m.addModifier(Modifier.Keyword.FINAL);
                int line = m.getBegin().map(p -> p.line).orElse(-1);
                changes.add(name + ": template method '" + m.getNameAsString() +
                    "()' at line " + line + " marked final");
            }
        });

        String newSource = LexicalPreservingPrinter.print(unit);
        return new RefactoringResult(id(), newSource, !changes.isEmpty(), changes);
    }

    /** True if the method body calls a same-class method whose name matches any of the given hook names. */
    private static boolean callsAnyHook(MethodDeclaration m, Set<String> hookNames) {
        return m.findAll(MethodCallExpr.class).stream().anyMatch(call -> {
            boolean sameClass = call.getScope().isEmpty()
                || call.getScope().filter(s -> s.toString().equals("this")).isPresent();
            return sameClass && hookNames.contains(call.getNameAsString());
        });
    }
}
