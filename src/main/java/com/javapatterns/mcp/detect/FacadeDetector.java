package com.javapatterns.mcp.detect;

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
 * Recognises the Facade pattern.
 *
 * <p>Signal — a thin layer over a multi-subsystem call chain:</p>
 * <ul>
 *   <li>A concrete class (typically named {@code *Facade}) that
 *       <i>does NOT</i> implement an interface;</li>
 *   <li>has ≥2 non-static fields of <b>distinct reference types</b>
 *       (the subsystems);</li>
 *   <li>has ≥1 public method whose body calls methods on at least 2
 *       of those distinct subsystem fields — the actual "hide the
 *       multi-step workflow behind one call" behaviour.</li>
 * </ul>
 *
 * <p>Confidence: 1.0 when the class name ends with {@code Facade} or
 * {@code Service}; 0.66 otherwise (the structure is right but the
 * intent is invisible at the call site).</p>
 *
 * <p>Classes that implement an interface AND have a delegate field
 * of that same interface are owned by Decorator / Proxy detectors,
 * not this one.</p>
 */
public final class FacadeDetector implements PatternDetector {

    @Override
    public Pattern pattern() { return Pattern.FACADE; }

    @Override
    public List<DetectedPattern> detect(CompilationUnit unit) {
        List<DetectedPattern> hits = new ArrayList<>();
        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (cls.isInterface() || cls.isAbstract()) return;
            // Don't claim Decorator/Proxy territory.
            if (!cls.getImplementedTypes().isEmpty()) return;

            String name = cls.getNameAsString();

            // Collect distinct non-static field types (reference types only).
            Set<String> subsystemTypes = new HashSet<>();
            for (FieldDeclaration f : cls.getFields()) {
                if (f.isStatic()) continue;
                for (VariableDeclarator v : f.getVariables()) {
                    if (!(v.getType() instanceof ClassOrInterfaceType cit)) continue;
                    String t = cit.getNameAsString();
                    // Skip plain-data subsystems — strings / collections / boxed primitives.
                    if (isJdkValue(t)) continue;
                    subsystemTypes.add(t);
                }
            }
            if (subsystemTypes.size() < 2) return;

            // Map field name → declared type so we can tell which call
            // hits which subsystem.
            java.util.Map<String, String> fieldType = new java.util.HashMap<>();
            for (FieldDeclaration f : cls.getFields()) {
                if (f.isStatic()) continue;
                for (VariableDeclarator v : f.getVariables()) {
                    if (!(v.getType() instanceof ClassOrInterfaceType cit)) continue;
                    String t = cit.getNameAsString();
                    if (isJdkValue(t)) continue;
                    fieldType.put(v.getNameAsString(), t);
                }
            }

            // ≥1 public method should call methods on ≥2 distinct subsystems.
            int line = cls.getBegin().map(p -> p.line).orElse(-1);
            Set<String> distinctSubsystemsHit = new HashSet<>();
            String workflowMethod = null;
            for (MethodDeclaration m : cls.getMethods()) {
                if (!m.isPublic() || m.isStatic()) continue;
                Set<String> hit = new HashSet<>();
                for (MethodCallExpr call : m.findAll(MethodCallExpr.class)) {
                    if (call.getScope().isEmpty()) continue;
                    String scope = call.getScope().get().toString();
                    // Strip leading "this.".
                    if (scope.startsWith("this.")) scope = scope.substring(5);
                    String t = fieldType.get(scope);
                    if (t != null) hit.add(t);
                }
                if (hit.size() >= 2 && hit.size() > distinctSubsystemsHit.size()) {
                    distinctSubsystemsHit = hit;
                    workflowMethod = m.getNameAsString();
                }
            }
            if (distinctSubsystemsHit.size() < 2) return;

            String nameLower = name.toLowerCase();
            boolean hintedName = nameLower.endsWith("facade") || nameLower.endsWith("service");
            double confidence = hintedName ? 1.0 : 0.66;

            List<String> evidence = new ArrayList<>();
            evidence.add("class " + name + " holds " + subsystemTypes.size() +
                " distinct subsystem field type(s): " + subsystemTypes);
            evidence.add("method " + workflowMethod + "() coordinates " +
                distinctSubsystemsHit.size() + " subsystem(s) in one call");
            if (!hintedName) {
                evidence.add("name does not end with 'Facade' / 'Service' — intent invisible at call site");
            }
            hits.add(new DetectedPattern(Pattern.FACADE, name, line, confidence, evidence));
        });
        return hits;
    }

    /** Common JDK types that look like fields but are really payloads, not subsystems. */
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
