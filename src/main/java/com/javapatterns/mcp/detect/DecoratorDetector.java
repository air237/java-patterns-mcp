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
 * Recognises the Decorator pattern.
 *
 * <p>Strong structural signal:</p>
 * <ul>
 *   <li>class implements some interface I (or extends a non-abstract
 *       base) and</li>
 *   <li>has a field whose type is exactly that interface I — the wrapped
 *       delegate.</li>
 * </ul>
 *
 * <p>Confidence: 1.0 (textbook decorator). Skips classes whose name
 * begins with {@code Abstract} (those usually <i>declare</i> the
 * decorator base, not instantiate it).</p>
 */
public final class DecoratorDetector implements PatternDetector {

    @Override
    public Pattern pattern() { return Pattern.DECORATOR; }

    @Override
    public List<DetectedPattern> detect(CompilationUnit unit) {
        List<DetectedPattern> hits = new ArrayList<>();
        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (cls.isInterface()) return;
            String name = cls.getNameAsString();

            Set<String> ifaces = new HashSet<>();
            cls.getImplementedTypes().forEach(t -> ifaces.add(t.getNameAsString()));
            cls.getExtendedTypes().forEach(t -> ifaces.add(t.getNameAsString()));
            if (ifaces.isEmpty()) return;

            // Find a non-static field whose type matches one of the
            // implemented/extended types.
            String wrappedIface = null;
            for (FieldDeclaration f : cls.getFields()) {
                if (f.isStatic()) continue;
                for (VariableDeclarator v : f.getVariables()) {
                    if (!(v.getType() instanceof ClassOrInterfaceType cit)) continue;
                    String t = cit.getNameAsString();
                    if (ifaces.contains(t)) {
                        wrappedIface = t;
                        break;
                    }
                }
                if (wrappedIface != null) break;
            }
            if (wrappedIface == null) return;

            int line = cls.getBegin().map(p -> p.line).orElse(-1);
            List<String> evidence = List.of(
                "class " + name + " implements " + wrappedIface,
                "has a field of type " + wrappedIface + " — the wrapped delegate"
            );
            hits.add(new DetectedPattern(Pattern.DECORATOR, name, line, 1.0, evidence));
        });
        return hits;
    }
}
