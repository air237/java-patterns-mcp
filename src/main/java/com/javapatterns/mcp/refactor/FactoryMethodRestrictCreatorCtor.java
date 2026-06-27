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
 * Demote every {@code public} constructor of a concrete Creator (a
 * Factory-Method-shaped class) to {@code protected}, so callers cannot
 * bypass the factory method by instantiating the Creator directly.
 *
 * <p>Triggers only on classes that look like a concrete Creator: a
 * non-abstract, non-interface class that declares at least one
 * non-static method whose name starts with {@code create}, {@code make},
 * {@code new}, or {@code build} and returns a reference type. Builder
 * shapes are skipped (a {@code Builder} class is not a Factory Method
 * Creator).</p>
 *
 * <p>Idempotent: if no public constructor remains on the Creator, the
 * source is left untouched and {@code changed=false} is returned.
 * Atomic: this is a pure modifier swap, no name resolution, no import
 * management.</p>
 */
public final class FactoryMethodRestrictCreatorCtor implements PatternRefactoring {

    private static final List<String> FACTORY_PREFIXES =
        List.of("create", "make", "new", "build");

    @Override
    public RefactoringId id() {
        return RefactoringId.FACTORY_METHOD_RESTRICT_CREATOR_CTOR;
    }

    @Override
    public RefactoringResult apply(CompilationUnit unit) {
        List<String> changes = new ArrayList<>();

        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (cls.isInterface() || cls.isAbstract()) return;
            String name = cls.getNameAsString();

            // Skip Builder pattern artefacts — a Builder#build() looks
            // like a factory method but is its own pattern.
            if (name.equals("Builder") || name.endsWith("Builder")) return;

            // Must have a factory-method-shaped declaration to qualify
            // as a Creator. Otherwise this is just a regular class with
            // a public ctor and we should leave it alone.
            boolean hasFactoryMethod = false;
            for (MethodDeclaration m : cls.getMethods()) {
                if (m.isStatic()) continue;
                if (m.getType().isVoidType() || m.getType().isPrimitiveType()) continue;
                String n = m.getNameAsString();
                if (FACTORY_PREFIXES.stream().anyMatch(n::startsWith)) {
                    hasFactoryMethod = true;
                    break;
                }
            }
            if (!hasFactoryMethod) return;

            for (ConstructorDeclaration ctor : cls.getConstructors()) {
                if (!ctor.isPublic()) continue;
                // Swap public → protected by removing PUBLIC and adding PROTECTED.
                ctor.removeModifier(Modifier.Keyword.PUBLIC);
                ctor.addModifier(Modifier.Keyword.PROTECTED);
                int line = ctor.getBegin().map(p -> p.line).orElse(-1);
                changes.add(name + ": Creator constructor at line " + line +
                    " demoted from public to protected");
            }
        });

        String newSource = LexicalPreservingPrinter.print(unit);
        return new RefactoringResult(id(), newSource, !changes.isEmpty(), changes);
    }
}
