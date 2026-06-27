package com.javapatterns.mcp.validate;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates {@link Pattern#FACADE} implementations.
 *
 * <p>Shape filter (mirrors {@code FacadeDetector}): a concrete class
 * that does NOT implement an interface (which would put it in
 * Decorator / Proxy territory) AND holds ≥2 non-static fields of
 * distinct non-JDK reference types (the subsystems). Other shapes
 * are ignored.</p>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li><b>ERROR</b> — a subsystem field is not {@code private}. The
 *       whole point of Facade is hiding the subsystems behind one
 *       call; a non-private subsystem field leaks the complexity
 *       the facade was supposed to absorb.</li>
 *   <li><b>WARNING</b> — a public method returns one of the
 *       subsystem types. A {@code public InventoryService inventory()}
 *       accessor lets clients reach past the facade and operate on
 *       the subsystem directly, defeating the pattern.</li>
 *   <li><b>INFO</b> — no public method coordinates ≥2 subsystems.
 *       The class is structurally a Facade but behaviourally just
 *       a proxy / wrapper to one subsystem at a time.</li>
 * </ul>
 */
public final class FacadeValidator implements PatternValidator {

    @Override
    public Pattern pattern() { return Pattern.FACADE; }

    @Override
    public List<ValidationIssue> validate(CompilationUnit unit) {
        List<ValidationIssue> issues = new ArrayList<>();
        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (cls.isInterface() || cls.isAbstract()) return;
            if (!cls.getImplementedTypes().isEmpty()) return; // Decorator/Proxy territory

            String name = cls.getNameAsString();
            int classLine = cls.getBegin().map(p -> p.line).orElse(-1);

            // Collect non-static fields whose declared type is a non-JDK reference type.
            // Map field name → declared type to look call scopes up by field name.
            Map<String, String> subsystemFields = new HashMap<>();
            Map<String, Integer> subsystemFieldLines = new HashMap<>();
            Map<String, Boolean> subsystemFieldPrivate = new HashMap<>();
            for (FieldDeclaration f : cls.getFields()) {
                if (f.isStatic()) continue;
                for (VariableDeclarator v : f.getVariables()) {
                    if (!(v.getType() instanceof ClassOrInterfaceType cit)) continue;
                    String t = cit.getNameAsString();
                    if (isJdkValue(t)) continue;
                    subsystemFields.put(v.getNameAsString(), t);
                    subsystemFieldLines.put(v.getNameAsString(),
                        f.getBegin().map(p -> p.line).orElse(classLine));
                    subsystemFieldPrivate.put(v.getNameAsString(), f.isPrivate());
                }
            }
            // Need ≥2 distinct subsystem types to qualify as Facade.
            Set<String> distinctTypes = new HashSet<>(subsystemFields.values());
            if (distinctTypes.size() < 2) return;

            // ─── ERROR: subsystem field is not private ─────────────
            for (var entry : subsystemFields.entrySet()) {
                String fName = entry.getKey();
                String fType = entry.getValue();
                if (Boolean.TRUE.equals(subsystemFieldPrivate.get(fName))) continue;
                int line = subsystemFieldLines.getOrDefault(fName, classLine);
                issues.add(new ValidationIssue(
                    Pattern.FACADE, name, line, Severity.ERROR,
                    "Facade " + name + " exposes subsystem field '" + fName +
                    "' (type " + fType + ") with non-private visibility — the subsystem " +
                    "is supposed to be hidden behind the facade.",
                    "Mark the field 'private'. Clients should reach the subsystem only " +
                    "through facade methods that coordinate the workflow."
                ));
            }

            // ─── WARNING: public method returns a subsystem type ───
            for (MethodDeclaration m : cls.getMethods()) {
                if (!m.isPublic() || m.isStatic()) continue;
                if (!(m.getType() instanceof ClassOrInterfaceType rt)) continue;
                String rtName = rt.getNameAsString();
                if (!distinctTypes.contains(rtName)) continue;
                int line = m.getBegin().map(p -> p.line).orElse(classLine);
                issues.add(new ValidationIssue(
                    Pattern.FACADE, name, line, Severity.WARNING,
                    "Facade method '" + m.getNameAsString() + "()' returns subsystem type " +
                    rtName + " — clients can reach past the facade and operate on the " +
                    "subsystem directly.",
                    "Return a primitive, a domain-level result type, or 'void' from facade " +
                    "methods. If callers really need the subsystem, the facade is the wrong " +
                    "abstraction for this use case."
                ));
            }

            // ─── INFO: no method coordinates ≥2 subsystems ─────────
            int maxSubsystemsCoordinated = 0;
            for (MethodDeclaration m : cls.getMethods()) {
                if (!m.isPublic() || m.isStatic()) continue;
                Set<String> typesHit = new HashSet<>();
                for (MethodCallExpr call : m.findAll(MethodCallExpr.class)) {
                    if (call.getScope().isEmpty()) continue;
                    String scope = call.getScope().get().toString();
                    if (scope.startsWith("this.")) scope = scope.substring(5);
                    String t = subsystemFields.get(scope);
                    if (t != null) typesHit.add(t);
                }
                if (typesHit.size() > maxSubsystemsCoordinated) {
                    maxSubsystemsCoordinated = typesHit.size();
                }
            }
            if (maxSubsystemsCoordinated < 2) {
                issues.add(new ValidationIssue(
                    Pattern.FACADE, name, classLine, Severity.INFO,
                    "Facade " + name + " holds " + distinctTypes.size() + " subsystem types " +
                    "but no public method coordinates ≥2 of them — structurally a facade, " +
                    "behaviourally a proxy / wrapper.",
                    "Add a workflow method that drives multiple subsystems in one call, " +
                    "or drop the facade and let the client orchestrate the subsystems directly."
                ));
            }
        });
        return issues;
    }

    private static boolean isJdkValue(String t) {
        return switch (t) {
            case "String", "Integer", "Long", "Double", "Float", "Boolean",
                "Byte", "Short", "Character", "Number", "BigDecimal", "BigInteger",
                "List", "Set", "Map", "Collection", "Queue", "Deque", "Optional",
                "Object" -> true;
            default -> false;
        };
    }
}
