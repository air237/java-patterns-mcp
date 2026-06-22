package com.javapatterns.mcp.validate;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates {@link Pattern#FACTORY_METHOD} implementations.
 *
 * <p>Rules:</p>
 * <ul>
 *   <li><b>ERROR</b> — the "Creator" exposes a public constructor that
 *       lets callers bypass the factory method and instantiate the
 *       product directly through subclassing-by-constructor abuse.
 *       Either the Creator should be abstract, or its constructor
 *       should not be {@code public}.</li>
 *   <li><b>WARNING</b> — the factory method's return type is a
 *       concrete class instead of the product abstraction. Returning a
 *       concrete type defeats the substitutability the pattern is
 *       supposed to provide.</li>
 *   <li><b>WARNING</b> — the factory method's body instantiates
 *       multiple <i>different</i> concrete classes by name. The whole
 *       point is to push that choice to a subclass; doing it inline
 *       turns the method back into a {@code switch}-based "simple
 *       factory".</li>
 *   <li><b>INFO</b> — the factory method is not declared
 *       {@code protected abstract} on an abstract Creator (Gang of Four
 *       canonical form). Public or package-private is acceptable but
 *       worth flagging.</li>
 * </ul>
 *
 * <p>A method qualifies as the "factory method" when it is non-static,
 * returns a reference type, and its name starts with one of
 * {@code create}, {@code make}, {@code new}, or {@code build}.</p>
 */
public final class FactoryMethodValidator implements PatternValidator {

    private static final List<String> FACTORY_PREFIXES = List.of("create", "make", "new", "build");

    @Override
    public Pattern pattern() { return Pattern.FACTORY_METHOD; }

    @Override
    public List<ValidationIssue> validate(CompilationUnit unit) {
        List<ValidationIssue> issues = new ArrayList<>();
        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(creator -> {
            if (creator.isInterface()) return;
            String creatorName = creator.getNameAsString();
            // Skip Builder pattern artefacts — a Builder#build() method
            // looks like a factory method (returns the product, may
            // `new` it inside), but it's the BUILDER pattern, not
            // FACTORY_METHOD. Avoid false positives by skipping classes
            // named "Builder" or ending in "Builder".
            if (creatorName.equals("Builder") || creatorName.endsWith("Builder")) return;

            int creatorLine = creator.getBegin().map(p -> p.line).orElse(-1);

            // Find a factory method.
            MethodDeclaration factory = null;
            for (MethodDeclaration m : creator.getMethods()) {
                if (m.isStatic()) continue;
                String n = m.getNameAsString();
                if (FACTORY_PREFIXES.stream().anyMatch(n::startsWith)) {
                    // must return a reference type (not void / primitive)
                    if (!m.getType().isVoidType() && !m.getType().isPrimitiveType()) {
                        factory = m;
                        break;
                    }
                }
            }
            if (factory == null) return; // not a Factory Method candidate

            int factoryLine = factory.getBegin().map(p -> p.line).orElse(creatorLine);

            // ── ERROR: public concrete Creator constructor lets callers skip the factory.
            if (!creator.isAbstract()) {
                for (ConstructorDeclaration c : creator.getConstructors()) {
                    if (c.isPublic()) {
                        issues.add(new ValidationIssue(
                            Pattern.FACTORY_METHOD, creatorName,
                            c.getBegin().map(p -> p.line).orElse(creatorLine),
                            Severity.ERROR,
                            "Creator " + creatorName + " has a public constructor — " +
                            "callers can instantiate it directly and bypass the factory method.",
                            "Make " + creatorName + " abstract, or restrict its constructors " +
                            "to protected/package-private so subclasses must drive the factory method."
                        ));
                        break;
                    }
                }
            }

            // ── WARNING: returning a concrete class hard-codes the product type.
            // Search the WHOLE compilation unit, not just inside the Creator —
            // products typically live next to (not inside) the Creator.
            String returnType = factory.getType().toString();
            boolean concreteReturn = unit.findAll(ClassOrInterfaceDeclaration.class).stream()
                .anyMatch(t -> t.getNameAsString().equals(returnType)
                            && !t.isInterface() && !t.isAbstract());
            if (concreteReturn) {
                issues.add(new ValidationIssue(
                    Pattern.FACTORY_METHOD, creatorName, factoryLine, Severity.WARNING,
                    "Factory method " + factory.getNameAsString() + "() returns concrete type " +
                    returnType + " — clients are coupled to a specific product class.",
                    "Return an interface or abstract base instead of the concrete product."
                ));
            }

            // ── WARNING: inline switch on multiple new XYZ() — turns factory method into "simple factory".
            // Exclude exception types — they are error-path constructs, not the
            // factory's product set.
            java.util.Set<String> newedTypes = new java.util.HashSet<>();
            factory.getBody().ifPresent(body -> body.findAll(ObjectCreationExpr.class).forEach(oc -> {
                String tname = oc.getType().getNameAsString();
                if (tname.endsWith("Exception") || tname.endsWith("Error")) return;
                newedTypes.add(tname);
            }));
            if (newedTypes.size() > 1) {
                issues.add(new ValidationIssue(
                    Pattern.FACTORY_METHOD, creatorName, factoryLine, Severity.WARNING,
                    "Factory method " + factory.getNameAsString() + "() instantiates " +
                    newedTypes.size() + " different concrete classes " + newedTypes +
                    " inline — this is a \"simple factory\", not a Factory Method.",
                    "Push the choice of concrete product to subclasses by making " +
                    factory.getNameAsString() + "() abstract on " + creatorName + " " +
                    "and instantiating one concrete product per subclass."
                ));
            }

            // ── INFO: prefer the GoF canonical form (abstract Creator + protected abstract factory).
            if (creator.isAbstract() && !factory.isAbstract()) {
                issues.add(new ValidationIssue(
                    Pattern.FACTORY_METHOD, creatorName, factoryLine, Severity.INFO,
                    "Creator " + creatorName + " is abstract but the factory method " +
                    factory.getNameAsString() + "() is concrete — the canonical Factory Method " +
                    "form is `protected abstract " + returnType + " " + factory.getNameAsString() + "()` " +
                    "with subclasses overriding it.",
                    "Either mark " + factory.getNameAsString() + "() abstract and override it in " +
                    "subclasses, or drop the abstract modifier from " + creatorName + "."
                ));
            }
        });
        return issues;
    }
}
