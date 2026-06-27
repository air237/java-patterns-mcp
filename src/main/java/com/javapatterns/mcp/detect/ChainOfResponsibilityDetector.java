package com.javapatterns.mcp.detect;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Recognises the Chain of Responsibility pattern.
 *
 * <p>Signal — a self-referential linear chain:</p>
 * <ul>
 *   <li>A handler abstraction H (abstract class or interface) that
 *       has a non-static field whose type is also {@code H} (the
 *       {@code next} link) AND declares a method whose name starts
 *       with {@code handle} / {@code process} / {@code dispatch}.</li>
 *   <li>≥1 concrete subclass of {@code H} in the same file.</li>
 * </ul>
 *
 * <p>An interface H qualifies if a concrete subclass holds the
 * {@code next} field (since interfaces cannot hold state). For
 * abstract-class H, the field must live on H itself.</p>
 *
 * <p>Confidence: 1.0 with ≥1 concrete handler; 0.66 when the
 * abstraction is there but no concrete handler is visible.</p>
 */
public final class ChainOfResponsibilityDetector implements PatternDetector {

    private static final List<String> HANDLE_PREFIXES =
        List.of("handle", "process", "dispatch");

    @Override
    public Pattern pattern() { return Pattern.CHAIN_OF_RESPONSIBILITY; }

    @Override
    public List<DetectedPattern> detect(CompilationUnit unit) {
        List<DetectedPattern> hits = new ArrayList<>();
        List<ClassOrInterfaceDeclaration> all = unit.findAll(ClassOrInterfaceDeclaration.class);

        for (ClassOrInterfaceDeclaration handler : all) {
            // Must be an abstraction.
            if (!(handler.isInterface() || handler.isAbstract())) continue;
            String name = handler.getNameAsString();

            // Must declare a handle*() method.
            boolean hasHandleMethod = handler.getMethods().stream().anyMatch(m -> {
                String mname = m.getNameAsString();
                return HANDLE_PREFIXES.stream().anyMatch(mname::startsWith);
            });
            if (!hasHandleMethod) continue;

            // For abstract classes: the `next` self-reference lives on the
            // class itself. For interfaces: walk concrete subclasses.
            boolean hasSelfRefField = false;
            if (handler.isAbstract() && !handler.isInterface()) {
                hasSelfRefField = handler.getFields().stream()
                    .filter(f -> !f.isStatic())
                    .flatMap(f -> f.getVariables().stream())
                    .anyMatch(v -> v.getType() instanceof ClassOrInterfaceType cit
                        && cit.getNameAsString().equals(name));
            }

            // Collect concrete subclasses / implementors.
            Set<String> concreteHandlers = new HashSet<>();
            for (ClassOrInterfaceDeclaration s : all) {
                if (s == handler) continue;
                if (s.isInterface() || s.isAbstract()) continue;
                boolean impl = s.getImplementedTypes().stream()
                    .anyMatch(t -> t.getNameAsString().equals(name))
                    || s.getExtendedTypes().stream()
                    .anyMatch(t -> t.getNameAsString().equals(name));
                if (impl) concreteHandlers.add(s.getNameAsString());

                // For interface H: the self-ref `next` field can live on a concrete handler.
                if (!hasSelfRefField && handler.isInterface() && impl) {
                    boolean selfRefOnSubclass = s.getFields().stream()
                        .filter(f -> !f.isStatic())
                        .flatMap(f -> f.getVariables().stream())
                        .anyMatch(v -> v.getType() instanceof ClassOrInterfaceType cit
                            && cit.getNameAsString().equals(name));
                    if (selfRefOnSubclass) hasSelfRefField = true;
                }
            }
            if (!hasSelfRefField) continue;

            double confidence = concreteHandlers.isEmpty() ? 0.66 : 1.0;
            int line = handler.getBegin().map(p -> p.line).orElse(-1);
            List<String> evidence = new ArrayList<>();
            evidence.add((handler.isInterface() ? "interface " : "abstract class ") + name +
                " declares a handle / process / dispatch method");
            evidence.add("self-reference field of type " + name + " creates the chain link");
            if (concreteHandlers.isEmpty()) {
                evidence.add("no concrete handler visible in this file");
            } else {
                evidence.add(concreteHandlers.size() + " concrete handler(s): " + concreteHandlers);
            }
            hits.add(new DetectedPattern(Pattern.CHAIN_OF_RESPONSIBILITY, name, line, confidence, evidence));
        }
        return hits;
    }
}
