package com.javapatterns.mcp.validate;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Validates {@link Pattern#BUILDER} implementations.
 *
 * <p>Rules:</p>
 * <ul>
 *   <li><b>ERROR</b> — the outer "value" object exposes setters; the
 *       result is mutable and Builder pointless.</li>
 *   <li><b>ERROR</b> — at least one field of the outer object is not
 *       {@code final} (without it, a stray method or reflection can
 *       mutate the supposedly-immutable result).</li>
 *   <li><b>WARNING</b> — Builder's setter-like methods don't return
 *       {@code this} (fluent chaining broken).</li>
 *   <li><b>WARNING</b> — Builder's setters are not {@code public}.</li>
 *   <li><b>INFO</b> — Builder's fields are not {@code private}.</li>
 * </ul>
 */
public final class BuilderValidator implements PatternValidator {

    @Override
    public Pattern pattern() { return Pattern.BUILDER; }

    @Override
    public List<ValidationIssue> validate(CompilationUnit unit) {
        List<ValidationIssue> issues = new ArrayList<>();
        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(outer -> {
            if (outer.isInterface()) return;
            String outerName = outer.getNameAsString();
            int outerLine = outer.getBegin().map(p -> p.line).orElse(-1);

            Optional<ClassOrInterfaceDeclaration> nestedBuilder = outer.getMembers().stream()
                .filter(m -> m instanceof ClassOrInterfaceDeclaration)
                .map(m -> (ClassOrInterfaceDeclaration) m)
                .filter(c -> c.isStatic() && c.getNameAsString().equals("Builder"))
                .findFirst();
            if (nestedBuilder.isEmpty()) return; // not a Builder candidate

            // ─── ERROR: outer exposes setters → not immutable ────
            outer.getMethods().stream()
                .filter(MethodDeclaration::isPublic)
                .filter(m -> m.getNameAsString().startsWith("set"))
                .findFirst()
                .ifPresent(m -> issues.add(new ValidationIssue(
                    Pattern.BUILDER, outerName,
                    m.getBegin().map(p -> p.line).orElse(outerLine),
                    Severity.ERROR,
                    outerName + " has a public setter (" + m.getNameAsString() + ") — " +
                    "the Builder's promise of immutability is broken.",
                    "Remove all setters from the value object. State changes must go " +
                    "through a fresh Builder and produce a new " + outerName + "."
                )));

            // ─── ERROR: outer has non-final instance fields ──────
            outer.getFields().stream()
                .filter(f -> !f.isStatic())
                .filter(f -> !f.isFinal())
                .findFirst()
                .ifPresent(f -> issues.add(new ValidationIssue(
                    Pattern.BUILDER, outerName,
                    f.getBegin().map(p -> p.line).orElse(outerLine),
                    Severity.ERROR,
                    "Field of " + outerName + " is not final; the value object can be mutated " +
                    "after construction, defeating the Builder's contract.",
                    "Mark every instance field of " + outerName + " as final and initialise " +
                    "it from the Builder in the private constructor."
                )));

            // ─── Builder internals ───────────────────────────────
            ClassOrInterfaceDeclaration builder = nestedBuilder.get();
            String builderTypeName = "Builder";
            int builderLine = builder.getBegin().map(p -> p.line).orElse(outerLine);

            // WARNING: fluent setter not returning this
            for (MethodDeclaration m : builder.getMethods()) {
                if (m.isStatic()) continue;
                if (m.getNameAsString().equals("build")) continue;
                // A "fluent setter" returns Builder. If the return type is Builder
                // but the body doesn't `return this`, it's almost certainly a bug.
                if (m.getType().toString().equals(builderTypeName)
                    || m.getType().toString().equals(outerName + ".Builder")) {
                    boolean returnsThis = m.getBody()
                        .map(b -> b.findAll(ReturnStmt.class).stream()
                            .anyMatch(r -> r.getExpression()
                                .map(e -> e.toString().equals("this")).orElse(false)))
                        .orElse(false);
                    if (!returnsThis) {
                        issues.add(new ValidationIssue(
                            Pattern.BUILDER, outerName,
                            m.getBegin().map(p -> p.line).orElse(builderLine),
                            Severity.WARNING,
                            "Builder method " + m.getNameAsString() + "() returns Builder but " +
                            "never `return this` — fluent chaining is broken.",
                            "End the method body with `return this;`."
                        ));
                    }
                }
            }

            // WARNING: setters not public
            for (MethodDeclaration m : builder.getMethods()) {
                if (m.isStatic()) continue;
                if (m.getNameAsString().equals("build")) continue;
                if (!m.isPublic()) {
                    issues.add(new ValidationIssue(
                        Pattern.BUILDER, outerName,
                        m.getBegin().map(p -> p.line).orElse(builderLine),
                        Severity.WARNING,
                        "Builder method " + m.getNameAsString() + "() is not public — clients " +
                        "outside the package cannot use the Builder.",
                        "Mark the Builder methods public."
                    ));
                    break; // one is enough; don't spam
                }
            }

            // INFO: Builder fields not private
            for (FieldDeclaration f : builder.getFields()) {
                if (!f.isPrivate() && !f.isStatic()) {
                    issues.add(new ValidationIssue(
                        Pattern.BUILDER, outerName,
                        f.getBegin().map(p -> p.line).orElse(builderLine),
                        Severity.INFO,
                        "Builder field " + f.getVariable(0).getNameAsString() +
                        " is not private — encapsulation violation.",
                        "Mark Builder fields private."
                    ));
                    break;
                }
            }
        });
        return issues;
    }
}
