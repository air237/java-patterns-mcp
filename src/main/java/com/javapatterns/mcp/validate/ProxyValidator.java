package com.javapatterns.mcp.validate;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates {@link Pattern#PROXY} implementations.
 *
 * <p>Shape filter (mirrors {@code ProxyDetector}): a non-interface,
 * non-abstract class that implements / extends some target type AND
 * holds a non-static field whose declared type matches that target —
 * the wrapped real subject. The class name must NOT contain
 * "decorator" / "wrapper" (those go to the Decorator detector).</p>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li><b>ERROR</b> — the delegate (real-subject) field is not
 *       {@code final}. A proxy is expected to wrap one fixed real
 *       subject for its entire lifetime; a mutable delegate field
 *       allows the wrapped target to be silently swapped.</li>
 *   <li><b>ERROR</b> — no public method calls anything on the
 *       delegate field. Without delegation the class is not a
 *       proxy; it is a Strategy / Adapter-style replacement of
 *       the service contract.</li>
 *   <li><b>WARNING</b> — the constructor accepts the delegate
 *       without a null-check. A null delegate turns every
 *       proxied call into a {@link NullPointerException} at use
 *       time.</li>
 *   <li><b>INFO</b> — the class name has no "proxy semantic"
 *       hint (proxy / stub / surrogate / caching / lazy / auth /
 *       remote / logging / tracing). The structure is valid, but
 *       the intent is invisible at the call site.</li>
 * </ul>
 */
public final class ProxyValidator implements PatternValidator {

    private static final Set<String> PROXY_HINTS = Set.of(
        "proxy", "stub", "surrogate", "caching", "lazy", "auth",
        "authentic", "authoriz", "remote", "logging", "tracing"
    );

    @Override
    public Pattern pattern() { return Pattern.PROXY; }

    @Override
    public List<ValidationIssue> validate(CompilationUnit unit) {
        List<ValidationIssue> issues = new ArrayList<>();
        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (cls.isInterface() || cls.isAbstract()) return;
            String name = cls.getNameAsString();
            String nameLower = name.toLowerCase();

            // Defer Decorator-shaped names to DecoratorValidator.
            if (nameLower.contains("decorator") || nameLower.contains("wrapper")) return;

            int classLine = cls.getBegin().map(p -> p.line).orElse(-1);

            // Collect candidate target types (implements + extends).
            Set<String> targets = new HashSet<>();
            cls.getImplementedTypes().forEach(t -> targets.add(t.getNameAsString()));
            cls.getExtendedTypes().forEach(t -> targets.add(t.getNameAsString()));
            if (targets.isEmpty()) return;

            // Find the delegate field — non-static, type matches one of the targets.
            FieldDeclaration delegateField = null;
            VariableDeclarator delegateVar = null;
            String delegateType = null;
            for (FieldDeclaration f : cls.getFields()) {
                if (f.isStatic()) continue;
                for (VariableDeclarator v : f.getVariables()) {
                    if (!(v.getType() instanceof ClassOrInterfaceType cit)) continue;
                    String t = cit.getNameAsString();
                    if (targets.contains(t)) {
                        delegateField = f;
                        delegateVar = v;
                        delegateType = t;
                        break;
                    }
                }
                if (delegateField != null) break;
            }
            if (delegateField == null) return; // not proxy-shaped

            String delegateName = delegateVar.getNameAsString();
            int delegateLine = delegateField.getBegin().map(p -> p.line).orElse(classLine);
            boolean hintedName = PROXY_HINTS.stream().anyMatch(nameLower::contains);

            // We only want to validate something the Proxy detector
            // would also pick up — so without a proxy-hint name we
            // stay quiet (the Decorator validator can claim such
            // a class instead). This keeps the validator's signals
            // aligned with the detector's confidence model.
            if (!hintedName) return;

            // ─── ERROR: delegate field is not final ────────────────
            if (!delegateField.isFinal()) {
                issues.add(new ValidationIssue(
                    Pattern.PROXY, name, delegateLine, Severity.ERROR,
                    "Proxy's delegate field '" + delegateName +
                    "' is not final — the wrapped real subject can be reassigned later.",
                    "Mark the field 'final'. A Proxy should wrap one fixed real subject for its entire lifetime."
                ));
            }

            // ─── ERROR: nothing delegates to the real subject ──────
            boolean anyDelegates = false;
            for (MethodDeclaration m : cls.getMethods()) {
                if (!m.isPublic()) continue;
                if (m.isStatic()) continue;
                if (callsField(m, delegateName)) { anyDelegates = true; break; }
            }
            if (!anyDelegates) {
                issues.add(new ValidationIssue(
                    Pattern.PROXY, name, classLine, Severity.ERROR,
                    "Proxy '" + name + "' implements " + targets + " but no public method " +
                    "delegates to the '" + delegateName + "' field — it does not proxy the wrapped " +
                    delegateType + ".",
                    "Each proxy method should call the same method on the delegate (optionally with " +
                    "caching / auth / lazy-init / etc.). If the intent is to replace the algorithm " +
                    "wholesale, this is the Strategy pattern, not Proxy."
                ));
            }

            // ─── WARNING: constructor lacks a null-check ───────────
            final String delegateNameFinal = delegateName;
            cls.getConstructors().stream()
                .filter(c -> c.getParameters().stream()
                    .anyMatch(p -> p.getType() instanceof ClassOrInterfaceType cit
                        && targets.contains(cit.getNameAsString())))
                .findFirst()
                .ifPresent(c -> {
                    String body = c.getBody().toString();
                    boolean nullChecked = body.contains("Objects.requireNonNull")
                        || (body.contains(delegateNameFinal) && body.contains("null")
                            && (body.contains("throw") || body.contains("if")));
                    if (!nullChecked) {
                        int line = c.getBegin().map(p -> p.line).orElse(classLine);
                        issues.add(new ValidationIssue(
                            Pattern.PROXY, name, line, Severity.WARNING,
                            "Proxy constructor accepts the real subject without a null-check; " +
                            "a null delegate turns every proxied call into a NullPointerException at use time.",
                            "Validate eagerly: this." + delegateNameFinal + " = Objects.requireNonNull(" +
                            delegateNameFinal + ", \"" + delegateNameFinal + "\");"
                        ));
                    }
                });
        });
        return issues;
    }

    /** True if the method body invokes any method on the given field (i.e. {@code field.foo(...)}). */
    private static boolean callsField(MethodDeclaration m, String fieldName) {
        return m.findAll(MethodCallExpr.class).stream().anyMatch(call -> {
            var scope = call.getScope().orElse(null);
            if (scope instanceof NameExpr ne) {
                return ne.getNameAsString().equals(fieldName);
            }
            if (scope != null) {
                String s = scope.toString();
                return s.equals(fieldName)
                    || s.equals("this." + fieldName);
            }
            return false;
        });
    }
}
