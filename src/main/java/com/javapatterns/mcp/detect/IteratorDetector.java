package com.javapatterns.mcp.detect;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Recognises the Iterator pattern.
 *
 * <p>Signal — a custom iterator paired with an aggregate that
 * produces one:</p>
 * <ul>
 *   <li>An iterator abstraction I (interface or abstract class) that
 *       declares both {@code hasNext()} and a {@code next()} method
 *       (the same two-method SAM as {@code java.util.Iterator}); AND</li>
 *   <li>An aggregate abstraction A (interface or abstract class) that
 *       declares an {@code iterator()} method whose return type is
 *       {@code I}; AND</li>
 *   <li>≥1 concrete aggregate implementing {@code A} in the same
 *       file.</li>
 * </ul>
 *
 * <p>To avoid noise the detector intentionally ignores the JDK's
 * built-in {@code java.util.Iterator} / {@code java.lang.Iterable} —
 * users implementing these are using the JDK, not the pattern per se.
 * A class that builds its own iterator hierarchy is the interesting
 * case.</p>
 *
 * <p>Confidence: 1.0 when all three signals fire; 0.66 when the
 * iterator + aggregate abstractions exist but no concrete aggregate
 * is visible.</p>
 */
public final class IteratorDetector implements PatternDetector {

    @Override
    public Pattern pattern() { return Pattern.ITERATOR; }

    @Override
    public List<DetectedPattern> detect(CompilationUnit unit) {
        List<DetectedPattern> hits = new ArrayList<>();
        List<ClassOrInterfaceDeclaration> all = unit.findAll(ClassOrInterfaceDeclaration.class);

        // First locate custom iterator types — must declare both hasNext + next.
        Set<String> iteratorTypes = new HashSet<>();
        for (ClassOrInterfaceDeclaration t : all) {
            if (!(t.isInterface() || t.isAbstract())) continue;
            String n = t.getNameAsString();
            // Skip the JDK's own Iterator / Iterable — pattern detection wants
            // the user's hand-rolled abstraction, not the JDK contract.
            if ("Iterator".equals(n) || "Iterable".equals(n)) continue;
            boolean hasHasNext = t.getMethods().stream()
                .anyMatch(m -> "hasNext".equals(m.getNameAsString()) && m.getParameters().isEmpty());
            boolean hasNext = t.getMethods().stream()
                .anyMatch(m -> "next".equals(m.getNameAsString()) && m.getParameters().isEmpty());
            if (hasHasNext && hasNext) iteratorTypes.add(n);
        }
        if (iteratorTypes.isEmpty()) return hits;

        // Find aggregate abstractions that produce one of those iterator types.
        for (ClassOrInterfaceDeclaration agg : all) {
            if (!(agg.isInterface() || agg.isAbstract())) continue;
            String aggName = agg.getNameAsString();

            MethodDeclaration iterMethod = null;
            String iterReturnType = null;
            for (MethodDeclaration m : agg.getMethods()) {
                if (!"iterator".equals(m.getNameAsString())) continue;
                if (!m.getParameters().isEmpty()) continue;
                String rt = m.getType().asString();
                // Strip generics so "CustomIterator<String>" → "CustomIterator".
                String base = rt.contains("<") ? rt.substring(0, rt.indexOf('<')) : rt;
                if (iteratorTypes.contains(base)) {
                    iterMethod = m;
                    iterReturnType = rt;
                    break;
                }
            }
            if (iterMethod == null) continue;

            // Count concrete aggregates implementing this abstraction.
            Set<String> concreteAggregates = new HashSet<>();
            for (ClassOrInterfaceDeclaration s : all) {
                if (s == agg) continue;
                if (s.isInterface() || s.isAbstract()) continue;
                boolean impl = s.getImplementedTypes().stream()
                    .anyMatch(t -> t.getNameAsString().equals(aggName))
                    || s.getExtendedTypes().stream()
                    .anyMatch(t -> t.getNameAsString().equals(aggName));
                if (impl) concreteAggregates.add(s.getNameAsString());
            }

            double confidence = concreteAggregates.isEmpty() ? 0.66 : 1.0;
            int line = agg.getBegin().map(p -> p.line).orElse(-1);
            List<String> evidence = new ArrayList<>();
            evidence.add((agg.isInterface() ? "interface " : "abstract class ") + aggName +
                " declares iterator() returning " + iterReturnType);
            evidence.add("custom iterator abstraction(s) with hasNext + next: " + iteratorTypes);
            if (concreteAggregates.isEmpty()) {
                evidence.add("no concrete aggregate visible in this file");
            } else {
                evidence.add(concreteAggregates.size() + " concrete aggregate(s): " + concreteAggregates);
            }
            hits.add(new DetectedPattern(Pattern.ITERATOR, aggName, line, confidence, evidence));
        }
        return hits;
    }
}
