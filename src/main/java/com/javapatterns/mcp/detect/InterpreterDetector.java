package com.javapatterns.mcp.detect;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Recognises the Interpreter pattern.
 *
 * <p>Signal — a grammar represented as an AST class hierarchy:</p>
 * <ul>
 *   <li>An expression abstraction E (interface or abstract class) that
 *       declares a method named {@code interpret} / {@code evaluate}
 *       / {@code eval} accepting one parameter (the context); AND</li>
 *   <li>≥2 concrete implementors of E in the same source file. The
 *       implementors can be top-level classes or nested classes
 *       inside a utility holder (the {@code Expressions} idiom in
 *       the bundled example).</li>
 * </ul>
 *
 * <p>Confidence: 1.0 with ≥3 concrete expressions (terminal +
 * non-terminals); 0.66 with exactly 2.</p>
 */
public final class InterpreterDetector implements PatternDetector {

    private static final List<String> INTERPRET_NAMES =
        List.of("interpret", "evaluate", "eval");

    @Override
    public Pattern pattern() { return Pattern.INTERPRETER; }

    @Override
    public List<DetectedPattern> detect(CompilationUnit unit) {
        List<DetectedPattern> hits = new ArrayList<>();
        List<ClassOrInterfaceDeclaration> all = unit.findAll(ClassOrInterfaceDeclaration.class);

        for (ClassOrInterfaceDeclaration expr : all) {
            if (!(expr.isInterface() || expr.isAbstract())) continue;
            String exprName = expr.getNameAsString();

            // Must declare an interpret/evaluate/eval method with one parameter.
            boolean hasInterpret = expr.getMethods().stream().anyMatch(m -> {
                String n = m.getNameAsString();
                if (!INTERPRET_NAMES.contains(n)) return false;
                return m.getParameters().size() == 1;
            });
            if (!hasInterpret) continue;

            // Count concrete implementors — top-level + nested.
            Set<String> implementors = new HashSet<>();
            for (ClassOrInterfaceDeclaration s : all) {
                if (s == expr) continue;
                // Top-level concrete implementor.
                boolean implTopLevel = !s.isInterface() && !s.isAbstract()
                    && s.getImplementedTypes().stream()
                        .anyMatch(t -> t.getNameAsString().equals(exprName));
                if (implTopLevel) implementors.add(s.getNameAsString());

                // Nested concrete implementors.
                for (var nested : s.findAll(ClassOrInterfaceDeclaration.class)) {
                    if (nested == s) continue;
                    if (nested.isInterface() || nested.isAbstract()) continue;
                    boolean nestedImpl = nested.getImplementedTypes().stream()
                        .anyMatch(t -> t.getNameAsString().equals(exprName))
                        || nested.getExtendedTypes().stream()
                        .anyMatch(t -> t.getNameAsString().equals(exprName));
                    if (nestedImpl) implementors.add(nested.getNameAsString());
                }
            }
            if (implementors.size() < 2) continue;

            double confidence = implementors.size() >= 3 ? 1.0 : 0.66;
            int line = expr.getBegin().map(p -> p.line).orElse(-1);
            List<String> evidence = new ArrayList<>();
            evidence.add((expr.isInterface() ? "interface " : "abstract class ") + exprName +
                " declares an interpret / evaluate / eval method");
            evidence.add(implementors.size() + " concrete expression(s): " + implementors);
            hits.add(new DetectedPattern(Pattern.INTERPRETER, exprName, line, confidence, evidence));
        }
        return hits;
    }
}
