package com.javapatterns.mcp.validate;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates {@link Pattern#COMPOSITE} hierarchies.
 *
 * <p>Shape filter (mirrors {@code CompositeDetector}): a single
 * interface I that is implemented by at least one "composite" class —
 * a concrete class that has a field of a collection-of-{@code I}
 * type. Other shapes are ignored.</p>
 *
 * <p>Rules (all fire on the composite class, not on the leaves):</p>
 * <ul>
 *   <li><b>ERROR</b> — the children field is not {@code final}. A
 *       reassignable children collection means the composite's
 *       backing storage can be silently swapped out at runtime,
 *       breaking the "one fixed structure" invariant the pattern
 *       relies on.</li>
 *   <li><b>ERROR</b> — the children getter returns the live internal
 *       collection (no defensive copy / unmodifiable wrapper).
 *       External callers can mutate the composite's structure
 *       directly, bypassing whatever invariants {@code add()} /
 *       {@code remove()} enforce.</li>
 *   <li><b>WARNING</b> — the {@code add()} (or equivalent) method
 *       does not null-check the new child. A null child propagates
 *       silently until {@code measure()} / traversal NPEs.</li>
 *   <li><b>INFO</b> — no leaf implementor of the component interface
 *       is present in the file. Composite without a leaf class is
 *       just a tree-shaped data structure; the pattern only earns
 *       its keep when clients treat leaves and composites uniformly.</li>
 * </ul>
 */
public final class CompositeValidator implements PatternValidator {

    @Override
    public Pattern pattern() { return Pattern.COMPOSITE; }

    @Override
    public List<ValidationIssue> validate(CompilationUnit unit) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<ClassOrInterfaceDeclaration> all = unit.findAll(ClassOrInterfaceDeclaration.class);

        // Collect candidate component interfaces.
        for (ClassOrInterfaceDeclaration iface : all) {
            if (!iface.isInterface()) continue;
            String ifaceName = iface.getNameAsString();

            // Find composite + leaf implementors.
            ClassOrInterfaceDeclaration composite = null;
            FieldDeclaration childrenField = null;
            VariableDeclarator childrenVar = null;
            boolean hasLeaf = false;

            for (ClassOrInterfaceDeclaration cls : all) {
                if (cls.isInterface() || cls.isAbstract()) continue;
                boolean implementsIface = cls.getImplementedTypes().stream()
                    .anyMatch(t -> t.getNameAsString().equals(ifaceName));
                if (!implementsIface) continue;

                // Is there a children collection field of the same interface type?
                FieldDeclaration childrenF = null;
                VariableDeclarator childrenV = null;
                for (FieldDeclaration f : cls.getFields()) {
                    if (f.isStatic()) continue;
                    for (VariableDeclarator v : f.getVariables()) {
                        String t = v.getType().toString();
                        if (t.contains("<" + ifaceName + ">") || t.contains(ifaceName + "[]")) {
                            childrenF = f;
                            childrenV = v;
                            break;
                        }
                    }
                    if (childrenF != null) break;
                }
                if (childrenF != null) {
                    composite = cls;
                    childrenField = childrenF;
                    childrenVar = childrenV;
                } else {
                    hasLeaf = true;
                }
            }

            if (composite == null) continue; // not composite-shaped

            String compositeName = composite.getNameAsString();
            int compositeLine = composite.getBegin().map(p -> p.line).orElse(-1);
            String childrenName = childrenVar.getNameAsString();
            int childrenLine = childrenField.getBegin().map(p -> p.line).orElse(compositeLine);

            // ─── ERROR: children field not final ───────────────────
            if (!childrenField.isFinal()) {
                issues.add(new ValidationIssue(
                    Pattern.COMPOSITE, compositeName, childrenLine, Severity.ERROR,
                    "Composite '" + compositeName + "' children field '" + childrenName +
                    "' is not final — the backing collection can be silently reassigned.",
                    "Mark the field 'final'. The composite should manage its children list " +
                    "via add() / remove() methods, not by swapping the collection wholesale."
                ));
            }

            // ─── ERROR: children getter returns live internal list ─
            //     Find a public, non-static method that returns the
            //     children field directly (no copyOf, no
            //     unmodifiableList).
            final String childrenNameFinal = childrenName;
            for (MethodDeclaration m : composite.getMethods()) {
                if (!m.isPublic()) continue;
                if (m.isStatic()) continue;
                if (m.getType().isVoidType() || m.getType().isPrimitiveType()) continue;
                if (m.getBody().isEmpty()) continue;
                String body = m.getBody().get().toString();
                // Returns the raw field reference somewhere?
                boolean returnsField = m.findAll(ReturnStmt.class).stream()
                    .map(rs -> rs.getExpression().orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .anyMatch(e -> {
                        if (e instanceof NameExpr ne) {
                            return ne.getNameAsString().equals(childrenNameFinal);
                        }
                        String s = e.toString();
                        return s.equals(childrenNameFinal)
                            || s.equals("this." + childrenNameFinal);
                    });
                if (!returnsField) continue;

                boolean defensive = body.contains("copyOf")
                    || body.contains("unmodifiableList")
                    || body.contains("unmodifiableCollection")
                    || body.contains("new ArrayList")
                    || body.contains("Collections.unmodifiable");
                if (defensive) continue;

                int mLine = m.getBegin().map(p -> p.line).orElse(compositeLine);
                issues.add(new ValidationIssue(
                    Pattern.COMPOSITE, compositeName, mLine, Severity.ERROR,
                    "Composite '" + compositeName + "' method '" + m.getNameAsString() +
                    "()' returns the live '" + childrenName + "' collection — callers can mutate the " +
                    "composite's internal structure directly.",
                    "Return a defensive snapshot: 'return List.copyOf(" + childrenName + ");' " +
                    "(immutable) or 'return Collections.unmodifiableList(" + childrenName + ");' " +
                    "(read-only view)."
                ));
                break; // one report per composite is enough
            }

            // ─── WARNING: add() does not null-check the child ──────
            for (MethodDeclaration m : composite.getMethods()) {
                if (!m.isPublic()) continue;
                if (m.isStatic()) continue;
                String n = m.getNameAsString();
                if (!(n.equals("add") || n.equals("attach") || n.equals("addChild"))) continue;
                if (m.getBody().isEmpty()) continue;
                String body = m.getBody().get().toString();
                boolean nullChecked = body.contains("Objects.requireNonNull")
                    || (body.contains("null") && body.contains("throw"));
                if (nullChecked) continue;

                int mLine = m.getBegin().map(p -> p.line).orElse(compositeLine);
                issues.add(new ValidationIssue(
                    Pattern.COMPOSITE, compositeName, mLine, Severity.WARNING,
                    "Composite '" + compositeName + "' method '" + n + "()' does not null-check the " +
                    "child argument; a null child will propagate silently until traversal NPEs.",
                    "Reject nulls eagerly: 'Objects.requireNonNull(child, \"child\");' before " +
                    "appending to the backing collection."
                ));
                break;
            }

            // ─── INFO: composite present but no leaf class ─────────
            if (!hasLeaf) {
                issues.add(new ValidationIssue(
                    Pattern.COMPOSITE, compositeName, compositeLine, Severity.INFO,
                    "Component interface '" + ifaceName + "' has a composite '" + compositeName +
                    "' but no leaf implementor in this file. Without a leaf the pattern is just " +
                    "a tree-shaped data structure.",
                    "Introduce at least one leaf implementor of " + ifaceName + " so clients can " +
                    "treat leaves and composites uniformly through the same interface."
                ));
            }
        }
        return issues;
    }
}
