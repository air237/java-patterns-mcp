package com.javapatterns.mcp.validate;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates {@link Pattern#BRIDGE} hierarchies.
 *
 * <p>Shape filter (mirrors {@code BridgeDetector}): an abstract class
 * A holds a non-static field of an interface (or abstract) type I,
 * with at least one concrete refined abstraction (extending A) AND
 * at least one concrete implementor (of I) in the same file.</p>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li><b>ERROR</b> — the bridge field is not {@code final}. A
 *       mutable bridge lets the implementor be re-targeted at
 *       runtime, breaking the "two independent hierarchies" promise
 *       of the pattern.</li>
 *   <li><b>WARNING</b> — the bridge field is {@code public}. Even
 *       package-private leaks the implementor; the canonical form
 *       uses {@code protected final} so refined abstractions
 *       access it but external code does not.</li>
 *   <li><b>WARNING</b> — the constructor accepts the implementor
 *       without a null-check. A null bridge turns every delegation
 *       into a {@link NullPointerException} at use time.</li>
 *   <li><b>INFO</b> — only one concrete abstraction OR only one
 *       concrete implementor is visible in the file. Bridge earns
 *       its keep when the abstraction × implementor grid is at
 *       least 2×2; a single variant on either side is
 *       over-engineering.</li>
 * </ul>
 */
public final class BridgeValidator implements PatternValidator {

    @Override
    public Pattern pattern() { return Pattern.BRIDGE; }

    @Override
    public List<ValidationIssue> validate(CompilationUnit unit) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<ClassOrInterfaceDeclaration> all = unit.findAll(ClassOrInterfaceDeclaration.class);

        // Build the set of all interface / abstract types declared in the file —
        // these are the candidate Implementor types for the bridge field.
        Set<String> abstractionsAndInterfaces = new HashSet<>();
        for (ClassOrInterfaceDeclaration t : all) {
            if (t.isInterface() || t.isAbstract()) {
                abstractionsAndInterfaces.add(t.getNameAsString());
            }
        }

        for (ClassOrInterfaceDeclaration abstraction : all) {
            if (abstraction.isInterface() || !abstraction.isAbstract()) continue;
            String aName = abstraction.getNameAsString();
            int aLine = abstraction.getBegin().map(p -> p.line).orElse(-1);

            // Find a bridge field — non-static, type is a different
            // abstraction/interface declared in the file.
            FieldDeclaration bridgeField = null;
            VariableDeclarator bridgeVar = null;
            String implementorType = null;
            for (FieldDeclaration f : abstraction.getFields()) {
                if (f.isStatic()) continue;
                for (VariableDeclarator v : f.getVariables()) {
                    if (!(v.getType() instanceof ClassOrInterfaceType cit)) continue;
                    String tname = cit.getNameAsString();
                    if (tname.equals(aName)) continue;
                    if (!abstractionsAndInterfaces.contains(tname)) continue;
                    bridgeField = f;
                    bridgeVar = v;
                    implementorType = tname;
                    break;
                }
                if (bridgeField != null) break;
            }
            if (bridgeField == null) continue;

            final String iName = implementorType;
            String fieldName = bridgeVar.getNameAsString();
            int fieldLine = bridgeField.getBegin().map(p -> p.line).orElse(aLine);

            // Count concrete refined abstractions + concrete implementors
            // in the same file.
            int concreteAbstractions = 0;
            int concreteImplementors = 0;
            for (ClassOrInterfaceDeclaration s : all) {
                if (s == abstraction) continue;
                if (s.isInterface() || s.isAbstract()) continue;
                boolean extendsAbstraction = s.getExtendedTypes().stream()
                    .anyMatch(t -> t.getNameAsString().equals(aName));
                if (extendsAbstraction) concreteAbstractions++;
                boolean implementsImplementor = s.getImplementedTypes().stream()
                    .anyMatch(t -> t.getNameAsString().equals(iName))
                    || s.getExtendedTypes().stream()
                    .anyMatch(t -> t.getNameAsString().equals(iName));
                if (implementsImplementor) concreteImplementors++;
            }
            // Require the full bridge shape — otherwise it's just an abstract
            // class with a typed field, not a Bridge.
            if (concreteAbstractions < 1 && concreteImplementors < 1) continue;

            // ─── ERROR: bridge field not final ─────────────────────
            if (!bridgeField.isFinal()) {
                issues.add(new ValidationIssue(
                    Pattern.BRIDGE, aName, fieldLine, Severity.ERROR,
                    "Bridge field '" + fieldName + "' (type " + iName + ") is not final — " +
                    "the implementor can be silently re-targeted at runtime.",
                    "Mark the field 'final'. A Bridge abstraction should hold one fixed " +
                    "implementor for its entire lifetime."
                ));
            }

            // ─── WARNING: bridge field is public ───────────────────
            if (bridgeField.isPublic()) {
                issues.add(new ValidationIssue(
                    Pattern.BRIDGE, aName, fieldLine, Severity.WARNING,
                    "Bridge field '" + fieldName + "' is public — external code can bypass " +
                    "the abstraction and talk to the implementor directly.",
                    "Lower the visibility to 'protected' (so refined abstractions still see " +
                    "it) or 'private' (so even subclasses go through accessors)."
                ));
            }

            // ─── WARNING: constructor without null-check on the implementor ─
            final String fieldNameFinal = fieldName;
            abstraction.getConstructors().stream()
                .filter(c -> c.getParameters().stream()
                    .anyMatch(p -> p.getType() instanceof ClassOrInterfaceType cit
                        && cit.getNameAsString().equals(iName)))
                .findFirst()
                .ifPresent(c -> {
                    String body = c.getBody().toString();
                    boolean nullChecked = body.contains("Objects.requireNonNull")
                        || (body.contains(fieldNameFinal) && body.contains("null")
                            && (body.contains("throw") || body.contains("if")));
                    if (!nullChecked) {
                        int line = c.getBegin().map(p -> p.line).orElse(aLine);
                        issues.add(new ValidationIssue(
                            Pattern.BRIDGE, aName, line, Severity.WARNING,
                            "Bridge constructor accepts the implementor without a null-check; " +
                            "a null implementor turns every delegated call into a NullPointerException at use time.",
                            "Validate eagerly: this." + fieldNameFinal + " = Objects.requireNonNull(" +
                            fieldNameFinal + ", \"" + fieldNameFinal + "\");"
                        ));
                    }
                });

            // ─── INFO: 1×N or N×1 grid → not yet worth the pattern ─
            if (concreteAbstractions == 1 || concreteImplementors == 1) {
                issues.add(new ValidationIssue(
                    Pattern.BRIDGE, aName, aLine, Severity.INFO,
                    "Bridge has " + concreteAbstractions + " refined abstraction(s) and " +
                    concreteImplementors + " concrete implementor(s) — a 1×N or N×1 grid " +
                    "is over-engineering; the pattern earns its keep at 2×2 or larger.",
                    "Add a second variant on the under-populated side, or collapse the " +
                    "hierarchy until you really have two independent axes of variation."
                ));
            }
        }

        return issues;
    }
}
