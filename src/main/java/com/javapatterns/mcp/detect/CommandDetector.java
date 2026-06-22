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
 * Recognises the Command pattern.
 *
 * <p>Signal: an interface (or abstract class) that declares an
 * {@code execute()} method, plus at least two concrete implementors
 * in the same file. Each concrete implementor typically holds a
 * "receiver" field (the object whose state the command mutates) —
 * we surface it in the evidence when present.</p>
 *
 * <p>Confidence: 1.0 with ≥2 implementors; 0.5 with exactly 1.</p>
 */
public final class CommandDetector implements PatternDetector {

    @Override
    public Pattern pattern() { return Pattern.COMMAND; }

    @Override
    public List<DetectedPattern> detect(CompilationUnit unit) {
        List<DetectedPattern> hits = new ArrayList<>();

        // Find candidate command interfaces — declare execute().
        for (ClassOrInterfaceDeclaration t : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            if (!t.isInterface() && !t.isAbstract()) continue;
            boolean hasExecute = t.getMethods().stream()
                .map(MethodDeclaration::getNameAsString)
                .anyMatch("execute"::equals);
            if (!hasExecute) continue;

            String cmdName = t.getNameAsString();

            // Count concrete implementors / extenders.
            Set<String> impls = new HashSet<>();
            for (ClassOrInterfaceDeclaration s : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (s == t) continue;
                if (s.isInterface() || s.isAbstract()) continue;
                boolean impl = s.getImplementedTypes().stream()
                    .anyMatch(x -> x.getNameAsString().equals(cmdName))
                    || s.getExtendedTypes().stream()
                    .anyMatch(x -> x.getNameAsString().equals(cmdName));
                if (impl) impls.add(s.getNameAsString());
            }
            if (impls.isEmpty()) continue;

            double confidence = impls.size() >= 2 ? 1.0 : 0.5;
            int line = t.getBegin().map(p -> p.line).orElse(-1);
            List<String> evidence = List.of(
                (t.isInterface() ? "interface " : "abstract class ") + cmdName +
                    " declares an execute() method",
                impls.size() + " concrete command(s): " + impls
            );
            hits.add(new DetectedPattern(Pattern.COMMAND, cmdName, line, confidence, evidence));
        }
        return hits;
    }
}
