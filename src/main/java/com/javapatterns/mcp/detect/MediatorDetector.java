package com.javapatterns.mcp.detect;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Recognises the Mediator pattern.
 *
 * <p>Signal — central hub for many-to-many communication:</p>
 * <ul>
 *   <li>A mediator abstraction M (interface or abstract class) that
 *       declares a method whose name starts with {@code send},
 *       {@code notify}, {@code dispatch} or {@code emit} (the fan-out
 *       method) AND a register/add/subscribe method.</li>
 *   <li>≥1 "colleague" type in the same file — concrete or abstract —
 *       that holds a non-static field of type M (so colleagues talk
 *       through the mediator, not to each other).</li>
 * </ul>
 *
 * <p>Confidence: 1.0 with all signals; 0.66 when the mediator
 * abstraction is right but no colleague with an M-typed field is
 * visible in the file (colleagues may live elsewhere).</p>
 */
public final class MediatorDetector implements PatternDetector {

    private static final List<String> SEND_PREFIXES =
        List.of("send", "notify", "dispatch", "emit", "broadcast");
    private static final List<String> REGISTER_PREFIXES =
        List.of("register", "add", "subscribe", "join");

    @Override
    public Pattern pattern() { return Pattern.MEDIATOR; }

    @Override
    public List<DetectedPattern> detect(CompilationUnit unit) {
        List<DetectedPattern> hits = new ArrayList<>();
        List<ClassOrInterfaceDeclaration> all = unit.findAll(ClassOrInterfaceDeclaration.class);

        for (ClassOrInterfaceDeclaration mediator : all) {
            if (!(mediator.isInterface() || mediator.isAbstract())) continue;
            String mName = mediator.getNameAsString();

            // Look for fan-out + register methods on the mediator contract.
            boolean hasSend = mediator.getMethods().stream().anyMatch(m -> {
                String n = m.getNameAsString();
                return SEND_PREFIXES.stream().anyMatch(n::startsWith);
            });
            boolean hasRegister = mediator.getMethods().stream().anyMatch(m -> {
                String n = m.getNameAsString();
                return REGISTER_PREFIXES.stream().anyMatch(n::startsWith);
            });
            if (!(hasSend && hasRegister)) continue;

            // Count colleagues — types holding a non-static field whose
            // declared type equals mName.
            int colleagueCount = 0;
            List<String> colleagueNames = new ArrayList<>();
            for (ClassOrInterfaceDeclaration s : all) {
                if (s == mediator) continue;
                boolean hasMediatorField = s.getFields().stream()
                    .filter(f -> !f.isStatic())
                    .flatMap(f -> f.getVariables().stream())
                    .anyMatch(v -> v.getType() instanceof ClassOrInterfaceType cit
                        && cit.getNameAsString().equals(mName));
                if (hasMediatorField) {
                    colleagueCount++;
                    colleagueNames.add(s.getNameAsString());
                }
            }

            double confidence = colleagueCount >= 1 ? 1.0 : 0.66;
            int line = mediator.getBegin().map(p -> p.line).orElse(-1);
            List<String> evidence = new ArrayList<>();
            evidence.add((mediator.isInterface() ? "interface " : "abstract class ") + mName +
                " declares both send/notify and register/add methods");
            if (colleagueCount > 0) {
                evidence.add(colleagueCount + " colleague(s) hold a " + mName +
                    " field: " + colleagueNames);
            } else {
                evidence.add("no colleague with a " + mName + " field is visible in this file");
            }
            hits.add(new DetectedPattern(Pattern.MEDIATOR, mName, line, confidence, evidence));
        }
        return hits;
    }
}
