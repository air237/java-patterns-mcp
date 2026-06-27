package com.javapatterns.mcp.validate;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
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
 * Validates {@link Pattern#DECORATOR} implementations.
 *
 * <p>Shape filter (mirrors {@code DecoratorDetector}): a non-interface
 * class that implements / extends some target type AND has a non-static
 * field whose declared type is exactly that target — the wrapped
 * delegate. Anything else is ignored.</p>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li><b>ERROR</b> — the wrapped field is not {@code final}. A
 *       mutable wrapper opens the door to re-targeting the decorator
 *       at runtime, which breaks the "fixed wrap" invariant.</li>
 *   <li><b>ERROR</b> — none of the implemented-interface methods
 *       forwards to the wrapped delegate. If every method overrides
 *       without delegating, the class is not a decorator; it is a
 *       Strategy or an Adapter-style replacement of the contract.</li>
 *   <li><b>WARNING</b> — the constructor accepts the wrapped delegate
 *       without a null-check; a null delegate turns every forwarded
 *       call into a {@link NullPointerException} at use time.</li>
 *   <li><b>INFO</b> — the decorator is not derived from an abstract
 *       base decorator (it implements the target interface directly).
 *       For a single decorator this is fine; once you have several
 *       decorators an {@code AbstractXxxDecorator} base eliminates
 *       the forwarding boilerplate.</li>
 * </ul>
 */
public final class DecoratorValidator implements PatternValidator {

    @Override
    public Pattern pattern() { return Pattern.DECORATOR; }

    @Override
    public List<ValidationIssue> validate(CompilationUnit unit) {
        List<ValidationIssue> issues = new ArrayList<>();
        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (cls.isInterface()) return;
            String name = cls.getNameAsString();
            int classLine = cls.getBegin().map(p -> p.line).orElse(-1);

            // Collect candidate target types (implements + extends).
            Set<String> targets = new HashSet<>();
            cls.getImplementedTypes().forEach(t -> targets.add(t.getNameAsString()));
            cls.getExtendedTypes().forEach(t -> targets.add(t.getNameAsString()));
            if (targets.isEmpty()) return;

            // Find the wrapped field — non-static, type matches one of the targets.
            FieldDeclaration wrappedField = null;
            VariableDeclarator wrappedVar = null;
            String wrappedType = null;
            for (FieldDeclaration f : cls.getFields()) {
                if (f.isStatic()) continue;
                for (VariableDeclarator v : f.getVariables()) {
                    if (!(v.getType() instanceof ClassOrInterfaceType cit)) continue;
                    String t = cit.getNameAsString();
                    if (targets.contains(t)) {
                        wrappedField = f;
                        wrappedVar = v;
                        wrappedType = t;
                        break;
                    }
                }
                if (wrappedField != null) break;
            }
            if (wrappedField == null) return; // not decorator-shaped

            String wrappedName = wrappedVar.getNameAsString();
            int wrappedLine = wrappedField.getBegin().map(p -> p.line).orElse(classLine);

            // Skip abstract decorator bases — `AbstractXxxDecorator` is a scaffolding
            // type for concrete decorators, not itself a decorator that should be
            // validated as one.
            if (cls.isAbstract() || name.startsWith("Abstract")) return;

            // ─── ERROR: wrapped field is not final ─────────────────
            if (!wrappedField.isFinal()) {
                issues.add(new ValidationIssue(
                    Pattern.DECORATOR, name, wrappedLine, Severity.ERROR,
                    "Decorator's wrapped field '" + wrappedName +
                    "' is not final — the wrapped delegate can be reassigned later.",
                    "Mark the field 'final'. A Decorator should hold one fixed delegate for its entire lifetime."
                ));
            }

            // ─── ERROR: nothing forwards to the wrapped delegate ───
            //     If no public method calls anything on the wrapped field,
            //     this is not a decorator — it's a Strategy / replacement.
            boolean anyForwards = false;
            for (MethodDeclaration m : cls.getMethods()) {
                if (!m.isPublic()) continue;
                if (m.isStatic()) continue;
                if (callsField(m, wrappedName)) { anyForwards = true; break; }
            }
            if (!anyForwards) {
                issues.add(new ValidationIssue(
                    Pattern.DECORATOR, name, classLine, Severity.ERROR,
                    "Decorator '" + name + "' implements " + targets + " but no public method " +
                    "forwards to its '" + wrappedName + "' field — it does not decorate the wrapped " +
                    wrappedType + ".",
                    "Each decorator method should call the same method on the wrapped delegate and add " +
                    "behaviour around the forwarding. If the intent is to replace the algorithm " +
                    "wholesale, this is the Strategy pattern, not Decorator."
                ));
            }

            // ─── WARNING: constructor lacks a null-check ───────────
            final String wrappedNameFinal = wrappedName;
            cls.getConstructors().stream()
                .filter(c -> c.getParameters().stream()
                    .anyMatch(p -> p.getType() instanceof ClassOrInterfaceType cit
                        && targets.contains(cit.getNameAsString())))
                .findFirst()
                .ifPresent(c -> {
                    String body = c.getBody().toString();
                    boolean nullChecked = body.contains("Objects.requireNonNull")
                        || (body.contains(wrappedNameFinal) && body.contains("null")
                            && (body.contains("throw") || body.contains("if")));
                    if (!nullChecked) {
                        int line = c.getBegin().map(p -> p.line).orElse(classLine);
                        issues.add(new ValidationIssue(
                            Pattern.DECORATOR, name, line, Severity.WARNING,
                            "Decorator constructor accepts the wrapped delegate without a null-check; " +
                            "a null delegate turns every forwarded call into a NullPointerException at use time.",
                            "Validate eagerly: this." + wrappedNameFinal + " = Objects.requireNonNull(" +
                            wrappedNameFinal + ", \"" + wrappedNameFinal + "\");"
                        ));
                    }
                });

            // ─── INFO: no abstract base decorator in the hierarchy ─
            //     A concrete decorator extending only the interface (no
            //     Abstract*Decorator base) is fine for a one-off; with
            //     several decorators a base eliminates duplicated forwarding.
            boolean hasAbstractDecoratorBase = cls.getExtendedTypes().stream()
                .map(t -> t.getNameAsString())
                .anyMatch(n -> n.startsWith("Abstract") && n.endsWith("Decorator"));
            if (!hasAbstractDecoratorBase) {
                issues.add(new ValidationIssue(
                    Pattern.DECORATOR, name, classLine, Severity.INFO,
                    "Decorator '" + name + "' implements " + wrappedType + " directly, without an " +
                    "Abstract" + wrappedType + "Decorator base class.",
                    "If you plan to add multiple decorators, introduce an Abstract" + wrappedType +
                    "Decorator that holds the wrapped field and provides default forwarding; concrete " +
                    "decorators then only override the methods they actually want to modify."
                ));
            }
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
