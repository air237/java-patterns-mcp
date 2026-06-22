package com.javapatterns.mcp.detect;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Recognises the Proxy pattern.
 *
 * <p>Structurally Proxy looks like Decorator (implements interface +
 * has delegate field of same interface), so we use the <b>class name</b>
 * as the disambiguator: anything containing "Proxy", "Stub",
 * "Surrogate", "Caching", "Lazy", "Auth", "Remote", "Logging" — these
 * are the strong Proxy-style hints.</p>
 *
 * <p>Confidence: 1.0 if both structure + hint name; 0.66 if only the
 * structure but with no Decorator-style "Decorator" / "Wrapper" name
 * (in which case it could go either way).</p>
 */
public final class ProxyDetector implements PatternDetector {

    private static final Set<String> PROXY_HINTS = Set.of(
        "proxy", "stub", "surrogate", "caching", "lazy", "auth",
        "authentic", "authoriz", "remote", "logging", "tracing"
    );

    @Override
    public Pattern pattern() { return Pattern.PROXY; }

    @Override
    public List<DetectedPattern> detect(CompilationUnit unit) {
        List<DetectedPattern> hits = new ArrayList<>();
        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (cls.isInterface()) return;
            String name = cls.getNameAsString();
            String nameLower = name.toLowerCase();

            Set<String> ifaces = new HashSet<>();
            cls.getImplementedTypes().forEach(t -> ifaces.add(t.getNameAsString()));
            cls.getExtendedTypes().forEach(t -> ifaces.add(t.getNameAsString()));
            if (ifaces.isEmpty()) return;

            String wrappedIface = null;
            for (FieldDeclaration f : cls.getFields()) {
                if (f.isStatic()) continue;
                for (VariableDeclarator v : f.getVariables()) {
                    if (!(v.getType() instanceof ClassOrInterfaceType cit)) continue;
                    String t = cit.getNameAsString();
                    if (ifaces.contains(t)) { wrappedIface = t; break; }
                }
                if (wrappedIface != null) break;
            }
            if (wrappedIface == null) return;

            boolean hintedName = PROXY_HINTS.stream().anyMatch(nameLower::contains);
            // If the name screams "Decorator" / "Wrapper", let the
            // Decorator detector own this one.
            boolean decoratorName = nameLower.contains("decorator")
                || nameLower.contains("wrapper");
            if (decoratorName) return;

            double confidence = hintedName ? 1.0 : 0.66;
            int line = cls.getBegin().map(p -> p.line).orElse(-1);
            List<String> evidence = new ArrayList<>();
            evidence.add("class " + name + " implements " + wrappedIface);
            evidence.add("has a delegate field of type " + wrappedIface);
            if (hintedName) {
                evidence.add("name suggests proxy semantics (caching / auth / lazy / remote / …)");
            } else {
                evidence.add("could also be a Decorator — structurally indistinguishable");
            }
            hits.add(new DetectedPattern(Pattern.PROXY, name, line, confidence, evidence));
        });
        return hits;
    }
}
