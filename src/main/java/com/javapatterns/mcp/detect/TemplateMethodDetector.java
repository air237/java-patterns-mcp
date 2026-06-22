package com.javapatterns.mcp.detect;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.List;

/**
 * Recognises the Template Method pattern.
 *
 * <p>Signal: an abstract class that has at least one {@code final}
 * concrete method (the "template") which calls one or more
 * {@code abstract} methods declared on the same class.</p>
 *
 * <p>Confidence: 1.0 when the template is {@code final} <i>and</i>
 * calls an abstract sibling; 0.5 when the template is non-final but
 * still calls an abstract sibling (the structure works, the contract
 * is just weaker).</p>
 */
public final class TemplateMethodDetector implements PatternDetector {

    @Override
    public Pattern pattern() { return Pattern.TEMPLATE_METHOD; }

    @Override
    public List<DetectedPattern> detect(CompilationUnit unit) {
        List<DetectedPattern> hits = new ArrayList<>();
        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (cls.isInterface()) return;
            if (!cls.isAbstract()) return;
            String name = cls.getNameAsString();

            // Collect names of abstract methods declared on this class.
            java.util.Set<String> abstractMethodNames = new java.util.HashSet<>();
            for (MethodDeclaration m : cls.getMethods()) {
                if (m.isAbstract()) abstractMethodNames.add(m.getNameAsString());
            }
            if (abstractMethodNames.isEmpty()) return;

            // Find a concrete method that calls at least one abstract sibling.
            for (MethodDeclaration m : cls.getMethods()) {
                if (m.isAbstract()) continue;
                if (m.isStatic()) continue;
                boolean callsAbstract = m.findAll(MethodCallExpr.class).stream()
                    .anyMatch(call -> abstractMethodNames.contains(call.getNameAsString()));
                if (!callsAbstract) continue;

                double confidence = m.isFinal() ? 1.0 : 0.5;
                int line = m.getBegin().map(p -> p.line).orElse(-1);
                List<String> evidence = new ArrayList<>();
                evidence.add((m.isFinal() ? "final " : "") + "method " + m.getNameAsString() +
                    "() in abstract class " + name);
                evidence.add("calls abstract sibling method(s): " + abstractMethodNames);
                hits.add(new DetectedPattern(Pattern.TEMPLATE_METHOD, name, line, confidence, evidence));
                break; // one report per class is enough
            }
        });
        return hits;
    }
}
