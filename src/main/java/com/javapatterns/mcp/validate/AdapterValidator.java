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
import java.util.Optional;
import java.util.Set;

/**
 * Validates {@link Pattern#ADAPTER} implementations.
 *
 * <p>This validator only fires on classes that look like an Adapter
 * (i.e. implement / extend at least one type AND wrap a non-target
 * field of a different type — the same signal {@code AdapterDetector}
 * uses). For everything else it stays silent so unrelated code does
 * not generate noise.</p>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li><b>ERROR</b> — the adaptee field is not {@code final}. A
 *       mutable wrapper opens the door to re-targeting the adapter at
 *       runtime, breaking the invariant that an Adapter has one fixed
 *       adaptee.</li>
 *   <li><b>ERROR</b> — none of the public forwarding methods actually
 *       calls the adaptee. If the adapter implements the target but
 *       never forwards to its wrapped object, it's a Decorator-style
 *       override, not an Adapter — caller is probably confused about
 *       intent.</li>
 *   <li><b>WARNING</b> — the constructor does not null-check the
 *       adaptee. A {@code null} adaptee turns the adapter into a
 *       latent NPE; the contract should be enforced eagerly.</li>
 *   <li><b>INFO</b> — class adapter variant (extends the adaptee
 *       instead of composing it). Works, but violates "favour
 *       composition over inheritance" and limits the adapter to one
 *       concrete adaptee subtype.</li>
 * </ul>
 */
public final class AdapterValidator implements PatternValidator {

    @Override
    public Pattern pattern() { return Pattern.ADAPTER; }

    @Override
    public List<ValidationIssue> validate(CompilationUnit unit) {
        List<ValidationIssue> issues = new ArrayList<>();
        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (cls.isInterface() || cls.isAbstract()) return;
            String name = cls.getNameAsString();
            if (name.startsWith("Abstract")) return;

            int classLine = cls.getBegin().map(p -> p.line).orElse(-1);

            // Collect target types (implements + extends).
            Set<String> implementedTargets = new HashSet<>();
            cls.getImplementedTypes().forEach(t -> implementedTargets.add(t.getNameAsString()));
            Set<String> extendedTargets = new HashSet<>();
            cls.getExtendedTypes().forEach(t -> extendedTargets.add(t.getNameAsString()));
            Set<String> allTargets = new HashSet<>();
            allTargets.addAll(implementedTargets);
            allTargets.addAll(extendedTargets);
            if (allTargets.isEmpty()) return;

            // Find the adaptee field (non-static field whose declared type is
            // NOT one of the targets and is not a JDK collection wrapper).
            FieldDeclaration adapteeField = null;
            VariableDeclarator adapteeVar = null;
            String adapteeType = null;
            for (FieldDeclaration f : cls.getFields()) {
                if (f.isStatic()) continue;
                for (VariableDeclarator v : f.getVariables()) {
                    if (!(v.getType() instanceof ClassOrInterfaceType cit)) continue;
                    String tname = cit.getNameAsString();
                    if (isJdkContainer(tname)) continue;
                    if (!allTargets.contains(tname)) {
                        adapteeField = f;
                        adapteeVar = v;
                        adapteeType = tname;
                        break;
                    }
                }
                if (adapteeField != null) break;
            }
            if (adapteeField == null) return; // not adapter-shaped

            String adapteeFieldName = adapteeVar.getNameAsString();
            int adapteeLine = adapteeField.getBegin().map(p -> p.line).orElse(classLine);

            // ─── ERROR: adaptee field is not final ─────────────────
            if (!adapteeField.isFinal()) {
                issues.add(new ValidationIssue(
                    Pattern.ADAPTER, name, adapteeLine, Severity.ERROR,
                    "Adapter's adaptee field '" + adapteeFieldName +
                    "' is not final — the wrapped instance can be reassigned later.",
                    "Mark the field 'final'. An Adapter should hold one fixed adaptee for its entire lifetime."
                ));
            }

            // ─── ERROR: none of the public forwarding methods calls the adaptee ─
            //     If we implement(s) a target but never delegate, this is not
            //     an adapter — it's a stub / override / decorator-without-target.
            if (!implementedTargets.isEmpty()) {
                boolean anyForwards = false;
                for (MethodDeclaration m : cls.getMethods()) {
                    if (!m.isPublic()) continue;
                    if (m.isStatic()) continue;
                    if (callsField(m, adapteeFieldName)) { anyForwards = true; break; }
                }
                if (!anyForwards) {
                    issues.add(new ValidationIssue(
                        Pattern.ADAPTER, name, classLine, Severity.ERROR,
                        "Adapter '" + name + "' implements " + implementedTargets +
                        " but no public method delegates to its '" + adapteeFieldName +
                        "' field — it does not actually adapt the wrapped " + adapteeType + ".",
                        "Forward each target method to the adaptee (translating signatures where needed). " +
                        "If the intent is to extend behaviour around the same target type, this is the " +
                        "Decorator pattern, not Adapter."
                    ));
                }
            }

            // ─── WARNING: constructor does not null-check the adaptee ─
            final String fieldNameFinal = adapteeFieldName;
            Optional<ConstructorDeclaration> ctor = cls.getConstructors().stream()
                .filter(c -> c.getParameters().stream()
                    .anyMatch(p -> p.getType() instanceof ClassOrInterfaceType cit
                        && !isJdkContainer(cit.getNameAsString())
                        && !allTargets.contains(cit.getNameAsString())))
                .findFirst();
            ctor.ifPresent(c -> {
                String body = c.getBody().toString();
                boolean nullChecked = body.contains("Objects.requireNonNull")
                    || (body.contains(fieldNameFinal) && body.contains("null")
                        && (body.contains("throw") || body.contains("if")));
                if (!nullChecked) {
                    int line = c.getBegin().map(p -> p.line).orElse(classLine);
                    issues.add(new ValidationIssue(
                        Pattern.ADAPTER, name, line, Severity.WARNING,
                        "Adapter constructor accepts the adaptee without a null-check; " +
                        "a null adaptee turns every forwarded call into a NullPointerException at use time.",
                        "Validate eagerly: this." + fieldNameFinal + " = Objects.requireNonNull(" +
                        fieldNameFinal + ", \"" + fieldNameFinal + "\");"
                    ));
                }
            });

            // ─── INFO: class adapter (extends the adaptee directly) ─
            //     Heuristic: there is an 'extends' clause AND the extended
            //     type is not also an interface implemented elsewhere as
            //     a target — i.e. the parent class IS the adaptee.
            if (!extendedTargets.isEmpty() && !implementedTargets.isEmpty()) {
                issues.add(new ValidationIssue(
                    Pattern.ADAPTER, name, classLine, Severity.INFO,
                    "Class adapter detected: '" + name + "' extends " + extendedTargets +
                    " AND implements " + implementedTargets + ". " +
                    "Class adapters lock the adapter to one concrete adaptee subtype and violate " +
                    "'favour composition over inheritance'.",
                    "Prefer the object-adapter variant: drop the 'extends' clause and forward to a " +
                    "wrapped " + adapteeType + " field instead."
                ));
            }
        });
        return issues;
    }

    private static boolean isJdkContainer(String typeName) {
        return typeName.equals("List") || typeName.equals("Map") || typeName.equals("Set")
            || typeName.equals("Collection") || typeName.equals("String")
            || typeName.equals("Optional") || typeName.equals("Queue") || typeName.equals("Deque");
    }

    /** True if the method body invokes any method on the given field (i.e. {@code field.foo(...)}). */
    private static boolean callsField(MethodDeclaration m, String fieldName) {
        return m.findAll(MethodCallExpr.class).stream().anyMatch(call -> {
            var scope = call.getScope().orElse(null);
            if (scope instanceof NameExpr ne) {
                return ne.getNameAsString().equals(fieldName);
            }
            // this.field.foo() — FieldAccessExpr over ThisExpr
            if (scope != null) {
                String s = scope.toString();
                return s.equals(fieldName)
                    || s.equals("this." + fieldName);
            }
            return false;
        });
    }
}
