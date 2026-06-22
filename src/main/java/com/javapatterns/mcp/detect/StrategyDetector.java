package com.javapatterns.mcp.detect;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recognises the Strategy pattern.
 *
 * <p>Looks for one interface with exactly one method, plus two or more
 * concrete classes in the same compilation unit that implement it.
 * Two or more impls → 0.66; three or more → 1.0.</p>
 */
public final class StrategyDetector implements PatternDetector {

    @Override
    public Pattern pattern() { return Pattern.STRATEGY; }

    @Override
    public List<DetectedPattern> detect(CompilationUnit unit) {
        // Find single-method interfaces
        Map<String, ClassOrInterfaceDeclaration> oneShotInterfaces = new HashMap<>();
        for (ClassOrInterfaceDeclaration t : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            if (t.isInterface()) {
                long methodCount = t.getMethods().stream().count();
                if (methodCount == 1) {
                    oneShotInterfaces.put(t.getNameAsString(), t);
                }
            }
        }
        if (oneShotInterfaces.isEmpty()) return List.of();

        // Count concrete impls per interface
        Map<String, List<String>> implementorsByIface = new HashMap<>();
        for (ClassOrInterfaceDeclaration t : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            if (t.isInterface()) continue;
            for (ClassOrInterfaceType impl : t.getImplementedTypes()) {
                String name = impl.getNameAsString();
                if (oneShotInterfaces.containsKey(name)) {
                    implementorsByIface
                        .computeIfAbsent(name, k -> new ArrayList<>())
                        .add(t.getNameAsString());
                }
            }
        }

        List<DetectedPattern> hits = new ArrayList<>();
        implementorsByIface.forEach((iface, impls) -> {
            if (impls.size() < 2) return;
            double confidence = impls.size() >= 3 ? 1.0 : 0.66;
            List<String> evidence = List.of(
                "single-method interface " + iface,
                impls.size() + " concrete implementations: " + impls
            );
            ClassOrInterfaceDeclaration ifaceDecl = oneShotInterfaces.get(iface);
            int line = ifaceDecl.getBegin().map(p -> p.line).orElse(-1);
            hits.add(new DetectedPattern(Pattern.STRATEGY, iface, line, confidence, evidence));
        });
        return hits;
    }
}
