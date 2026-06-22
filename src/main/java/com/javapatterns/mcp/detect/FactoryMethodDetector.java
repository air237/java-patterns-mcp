package com.javapatterns.mcp.detect;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.List;

/**
 * Recognises the Factory Method pattern.
 *
 * <p>Looks for an abstract class (or interface) with:</p>
 * <ol>
 *   <li>at least one abstract method whose name starts with {@code create}
 *       or {@code make} (heuristic — the textbook name is "factory method"),</li>
 *   <li>at least one concrete method (non-abstract) — the algorithm the
 *       factory method participates in.</li>
 * </ol>
 *
 * <p>Both signals together → confidence 1.0. Just the abstract create*()
 * alone → 0.5 (might be plain abstract factory metadata).</p>
 */
public final class FactoryMethodDetector implements PatternDetector {

    @Override
    public Pattern pattern() { return Pattern.FACTORY_METHOD; }

    @Override
    public List<DetectedPattern> detect(CompilationUnit unit) {
        List<DetectedPattern> hits = new ArrayList<>();
        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (!cls.isAbstract() && !cls.isInterface()) return; // factory method needs the open superclass
            String className = cls.getNameAsString();
            List<String> evidence = new ArrayList<>();

            List<MethodDeclaration> abstractCreators = cls.getMethods().stream()
                .filter(MethodDeclaration::isAbstract)
                .filter(m -> {
                    String n = m.getNameAsString();
                    return n.startsWith("create") || n.startsWith("make");
                })
                .toList();
            if (abstractCreators.isEmpty()) return;

            evidence.add("abstract factory method(s): " +
                abstractCreators.stream().map(MethodDeclaration::getNameAsString).toList());
            double confidence = 0.5;

            boolean hasConcreteMethod = cls.getMethods().stream()
                .filter(m -> !m.isAbstract())
                .filter(m -> !m.isStatic())
                .filter(m -> !m.isPrivate())
                .findAny()
                .isPresent();
            if (hasConcreteMethod) {
                evidence.add("at least one concrete method that may consume the factory");
                confidence = 1.0;
            }

            int line = cls.getBegin().map(p -> p.line).orElse(-1);
            hits.add(new DetectedPattern(Pattern.FACTORY_METHOD, className, line, confidence, evidence));
        });
        return hits;
    }
}
