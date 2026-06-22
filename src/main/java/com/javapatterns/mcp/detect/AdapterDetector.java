package com.javapatterns.mcp.detect;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Recognises the Adapter pattern.
 *
 * <p>Signal: a class that <i>implements</i> or <i>extends</i> some
 * target type AND has at least one private field whose declared type is
 * <b>different</b> from the implemented one — that field is the
 * "adaptee" being wrapped.</p>
 *
 * <p>Confidence:</p>
 * <ul>
 *   <li>1.0 — implements a target + has a non-target adaptee field;</li>
 *   <li>0.66 — extends a target + has a non-target adaptee field.</li>
 * </ul>
 *
 * <p>To minimise false positives the detector skips classes whose name
 * starts with {@code Abstract} or that are themselves abstract.</p>
 */
public final class AdapterDetector implements PatternDetector {

    @Override
    public Pattern pattern() { return Pattern.ADAPTER; }

    @Override
    public List<DetectedPattern> detect(CompilationUnit unit) {
        List<DetectedPattern> hits = new ArrayList<>();
        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (cls.isInterface() || cls.isAbstract()) return;
            String name = cls.getNameAsString();
            if (name.startsWith("Abstract")) return;

            // Collect target types (implements / extends).
            Set<String> targets = new java.util.HashSet<>();
            cls.getImplementedTypes().forEach(t -> targets.add(t.getNameAsString()));
            cls.getExtendedTypes().forEach(t -> targets.add(t.getNameAsString()));
            if (targets.isEmpty()) return;

            // Look for an adaptee field — declared type NOT in targets.
            VariableDeclarator adaptee = null;
            for (FieldDeclaration f : cls.getFields()) {
                if (f.isStatic()) continue;
                for (VariableDeclarator v : f.getVariables()) {
                    if (!(v.getType() instanceof ClassOrInterfaceType cit)) continue;
                    String tname = cit.getNameAsString();
                    // Skip JDK collection-ish wrappers — these are usually
                    // composition, not adaptation.
                    if (tname.equals("List") || tname.equals("Map") || tname.equals("Set")
                        || tname.equals("Collection") || tname.equals("String")) continue;
                    if (!targets.contains(tname)) {
                        adaptee = v;
                        break;
                    }
                }
                if (adaptee != null) break;
            }
            if (adaptee == null) return;

            boolean implementsTarget = !cls.getImplementedTypes().isEmpty();
            double confidence = implementsTarget ? 1.0 : 0.66;
            int line = cls.getBegin().map(p -> p.line).orElse(-1);
            String adapteeType = ((ClassOrInterfaceType) adaptee.getType()).getNameAsString();
            List<String> evidence = List.of(
                (implementsTarget ? "implements " : "extends ") + targets,
                "wraps adaptee field of type " + adapteeType
            );
            hits.add(new DetectedPattern(Pattern.ADAPTER, name, line, confidence, evidence));
        });
        return hits;
    }
}
