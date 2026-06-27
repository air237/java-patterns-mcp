package com.javapatterns.mcp.validate;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates {@link Pattern#VISITOR} implementations.
 *
 * <p>Shape filter (mirrors {@code VisitorDetector}): an element
 * abstraction E declares {@code accept(Visitor)}; the Visitor type
 * is itself an abstraction with ≥2 {@code visit(...)} methods; ≥1
 * concrete element implements {@code accept(...)}.</p>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li><b>ERROR</b> — a concrete element's {@code accept(V v)}
 *       body does not call {@code v.visit(this)} (the canonical
 *       double-dispatch). Hardcoded returns or calls on something
 *       other than {@code this} short-circuit the pattern: the
 *       visitor never sees the right overload and the whole
 *       point of double-dispatch is lost.</li>
 *   <li><b>WARNING</b> — every {@code visit(...)} method on the
 *       visitor returns {@code void}. Modern Java Visitors usually
 *       return a result ({@code R visit(...)}); a void visitor
 *       forces side-effects on the visitor itself instead of
 *       letting callers compose visit calls.</li>
 *   <li><b>INFO</b> — the {@code accept(...)} method is not
 *       {@code public}. Clients can't drive the traversal from
 *       outside the element's package.</li>
 * </ul>
 */
public final class VisitorValidator implements PatternValidator {

    @Override
    public Pattern pattern() { return Pattern.VISITOR; }

    @Override
    public List<ValidationIssue> validate(CompilationUnit unit) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<ClassOrInterfaceDeclaration> all = unit.findAll(ClassOrInterfaceDeclaration.class);

        for (ClassOrInterfaceDeclaration element : all) {
            if (!(element.isInterface() || element.isAbstract())) continue;
            String elementName = element.getNameAsString();

            // Find accept(Visitor) on the element abstraction.
            MethodDeclaration acceptDecl = null;
            String visitorType = null;
            for (MethodDeclaration m : element.getMethods()) {
                if (!"accept".equals(m.getNameAsString())) continue;
                if (m.getParameters().size() != 1) continue;
                var ptype = m.getParameter(0).getType();
                if (!ptype.isClassOrInterfaceType()) continue;
                acceptDecl = m;
                visitorType = ptype.asClassOrInterfaceType().getNameAsString();
                break;
            }
            if (acceptDecl == null || visitorType.equals(elementName)) continue;

            // Visitor must be an interface/abstract with ≥2 visit(...) overloads.
            final String vName = visitorType;
            ClassOrInterfaceDeclaration visitor = all.stream()
                .filter(t -> t.getNameAsString().equals(vName))
                .filter(t -> t.isInterface() || t.isAbstract())
                .findFirst().orElse(null);
            if (visitor == null) continue;
            List<MethodDeclaration> visitMethods = visitor.getMethods().stream()
                .filter(m -> "visit".equals(m.getNameAsString()) || m.getNameAsString().startsWith("visit"))
                .toList();
            if (visitMethods.size() < 2) continue;

            // ─── ERROR: concrete accept does not call v.visit(this) ─
            checkConcreteAcceptBodies(unit, elementName, issues);

            // ─── WARNING: visitor methods are all void ─────────────
            boolean allVoid = visitMethods.stream().allMatch(m -> m.getType().isVoidType());
            if (allVoid) {
                int vLine = visitor.getBegin().map(p -> p.line).orElse(-1);
                issues.add(new ValidationIssue(
                    Pattern.VISITOR, vName, vLine, Severity.WARNING,
                    "Visitor " + vName + " declares only void visit(...) methods — visitors " +
                    "cannot return a result and callers must rely on side-effects on the " +
                    "visitor itself.",
                    "Declare a result type 'R' on " + vName + " (e.g. 'R visit(...)') so " +
                    "visit results compose. Use 'Void' as R if you really want side-effects only."
                ));
            }

            // ─── INFO: accept is not public ─────────────────────────
            if (!acceptDecl.isPublic() && !element.isInterface()) {
                int aLine = acceptDecl.getBegin().map(p -> p.line).orElse(-1);
                issues.add(new ValidationIssue(
                    Pattern.VISITOR, elementName, aLine, Severity.INFO,
                    "accept(...) on " + elementName + " is not public — clients outside " +
                    "the element's package cannot drive the traversal.",
                    "Mark accept(...) public unless you have a deliberate package-private design."
                ));
            }
        }
        return issues;
    }

    /**
     * Walk every concrete class (top-level or nested) implementing
     * {@code elementName}; flag every one whose accept() body does not
     * call {@code v.visit(this)} on its parameter.
     */
    private static void checkConcreteAcceptBodies(
        CompilationUnit unit,
        String elementName,
        List<ValidationIssue> issues
    ) {
        for (ClassOrInterfaceDeclaration cls : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            if (cls.isInterface() || cls.isAbstract()) continue;
            boolean impl = cls.getImplementedTypes().stream()
                .anyMatch(t -> t.getNameAsString().equals(elementName))
                || cls.getExtendedTypes().stream()
                .anyMatch(t -> t.getNameAsString().equals(elementName));
            if (!impl) continue;

            for (MethodDeclaration m : cls.getMethods()) {
                if (!"accept".equals(m.getNameAsString())) continue;
                if (m.getParameters().size() != 1) continue;
                String paramName = m.getParameter(0).getNameAsString();
                if (m.getBody().isEmpty()) continue;

                // Look for v.visit(this) — the param's scope, named "visit",
                // with a "this" argument anywhere.
                boolean correctDispatch = m.findAll(MethodCallExpr.class).stream().anyMatch(call -> {
                    if (!"visit".equals(call.getNameAsString())) return false;
                    if (call.getScope().isEmpty()) return false;
                    if (!paramName.equals(call.getScope().get().toString())) return false;
                    // At least one argument is `this`.
                    return call.getArguments().stream().anyMatch(arg -> arg instanceof ThisExpr);
                });
                if (correctDispatch) continue;

                int line = m.getBegin().map(p -> p.line).orElse(-1);
                issues.add(new ValidationIssue(
                    Pattern.VISITOR, cls.getNameAsString(), line, Severity.ERROR,
                    "accept(...) in " + cls.getNameAsString() + " does not call '" +
                    paramName + ".visit(this)' — the double-dispatch is broken and the " +
                    "visitor will never see this element through the right overload.",
                    "Replace the body with 'return " + paramName + ".visit(this);' so the " +
                    "compiler picks the correct visit(<this-type>) overload at compile time."
                ));
            }
        }
    }
}
