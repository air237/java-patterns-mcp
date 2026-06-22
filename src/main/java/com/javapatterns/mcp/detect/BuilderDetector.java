package com.javapatterns.mcp.detect;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.List;

/**
 * Recognises the Builder pattern.
 *
 * <p>Signals (each contributes 1/3 of confidence):</p>
 * <ol>
 *   <li>An outer class with a static nested class named {@code Builder}.</li>
 *   <li>The nested {@code Builder} has a {@code build()} method whose
 *       return type matches the outer class.</li>
 *   <li>The outer class has at least one constructor (private or
 *       package-private) that takes the {@code Builder} as a parameter.</li>
 * </ol>
 *
 * <p>2/3 signals → 0.66 reported. The third raises it to a textbook 1.0.</p>
 */
public final class BuilderDetector implements PatternDetector {

    @Override
    public Pattern pattern() { return Pattern.BUILDER; }

    @Override
    public List<DetectedPattern> detect(CompilationUnit unit) {
        List<DetectedPattern> hits = new ArrayList<>();
        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(outer -> {
            if (outer.isInterface()) return;
            String outerName = outer.getNameAsString();
            List<String> evidence = new ArrayList<>();
            int signals = 0;

            // 1) static nested class named Builder
            var nestedBuilder = outer.getMembers().stream()
                .filter(m -> m instanceof ClassOrInterfaceDeclaration)
                .map(m -> (ClassOrInterfaceDeclaration) m)
                .filter(c -> c.isStatic() && c.getNameAsString().equals("Builder") && !c.isInterface())
                .findFirst();
            if (nestedBuilder.isPresent()) {
                signals++;
                evidence.add("static nested class named Builder");

                // 2) Builder.build() returns the outer type
                boolean buildReturnsOuter = nestedBuilder.get().getMethods().stream()
                    .filter(m -> m.getNameAsString().equals("build"))
                    .anyMatch(m -> m.getType().toString().equals(outerName));
                if (buildReturnsOuter) {
                    signals++;
                    evidence.add("Builder.build() returns " + outerName);
                }
            }

            // 3) constructor of outer takes Builder as parameter
            boolean ctorTakesBuilder = outer.getConstructors().stream()
                .anyMatch(ctor -> ctor.getParameters().stream()
                    .anyMatch(p -> p.getType().toString().equals("Builder")
                                || p.getType().toString().equals(outerName + ".Builder")));
            if (ctorTakesBuilder) {
                signals++;
                evidence.add(outerName + " constructor takes a Builder argument");
            }

            if (signals >= 2) {
                double confidence = signals / 3.0;
                int line = outer.getBegin().map(p -> p.line).orElse(-1);
                hits.add(new DetectedPattern(Pattern.BUILDER, outerName, line, confidence, evidence));
            }
        });
        return hits;
    }
}
