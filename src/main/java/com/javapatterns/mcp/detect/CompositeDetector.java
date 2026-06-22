package com.javapatterns.mcp.detect;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Recognises the Composite pattern.
 *
 * <p>Heuristic: a single interface implemented both by a "leaf" class
 * (no collection field of its own type) and a "composite" class (has a
 * field of type {@code List<Component>} / collection of the same
 * interface).</p>
 *
 * <p>Both kinds present → confidence 1.0. Only composite → 0.5.</p>
 */
public final class CompositeDetector implements PatternDetector {

    @Override
    public Pattern pattern() { return Pattern.COMPOSITE; }

    @Override
    public List<DetectedPattern> detect(CompilationUnit unit) {
        Set<String> interfaces = new HashSet<>();
        for (ClassOrInterfaceDeclaration t : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            if (t.isInterface()) interfaces.add(t.getNameAsString());
        }
        if (interfaces.isEmpty()) return List.of();

        List<DetectedPattern> hits = new ArrayList<>();
        for (String iface : interfaces) {
            boolean hasLeaf = false;
            boolean hasComposite = false;
            for (ClassOrInterfaceDeclaration cls : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (cls.isInterface()) continue;
                boolean implementsIface = cls.getImplementedTypes().stream()
                    .anyMatch(t -> t.getNameAsString().equals(iface));
                if (!implementsIface) continue;
                boolean carriesChildren = cls.getFields().stream()
                    .flatMap(f -> f.getVariables().stream())
                    .map(v -> v.getType().toString())
                    .anyMatch(t -> t.contains("<" + iface + ">") || t.contains(iface + "[]"));
                if (carriesChildren) hasComposite = true;
                else hasLeaf = true;
            }
            if (hasComposite) {
                double conf = hasLeaf ? 1.0 : 0.5;
                List<String> evidence = new ArrayList<>();
                evidence.add("interface " + iface + " is implemented by a composite class (field of " + iface + " children)");
                if (hasLeaf) evidence.add("interface " + iface + " is also implemented by a leaf class");
                // Anchor at the interface declaration itself.
                int line = unit.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .filter(d -> d.isInterface() && d.getNameAsString().equals(iface))
                    .findFirst()
                    .flatMap(d -> d.getBegin())
                    .map(p -> p.line)
                    .orElse(-1);
                hits.add(new DetectedPattern(Pattern.COMPOSITE, iface, line, conf, evidence));
            }
        }
        return hits;
    }
}
