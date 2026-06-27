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

/**
 * Recognises the Flyweight pattern.
 *
 * <p>Signal — an interning factory:</p>
 * <ul>
 *   <li>a class that holds a {@code static} {@code Map}-typed field
 *       (the cache); AND</li>
 *   <li>declares a {@code static} method whose body contains either
 *       {@code computeIfAbsent} on that map, or a manual
 *       {@code if (cache.containsKey)} / {@code map.put(...)} pattern;
 *       AND</li>
 *   <li>the method returns a reference type other than {@code void} or
 *       {@code Map} itself.</li>
 * </ul>
 *
 * <p>Confidence: 1.0 when {@code computeIfAbsent} (the textbook
 * interning idiom) is present; 0.66 for the manual containsKey / put
 * variant.</p>
 *
 * <p>The detector deliberately does NOT report every class that holds
 * a {@code Map} field — only the ones whose static method actually
 * uses the map as a cache.</p>
 */
public final class FlyweightDetector implements PatternDetector {

    @Override
    public Pattern pattern() { return Pattern.FLYWEIGHT; }

    @Override
    public List<DetectedPattern> detect(CompilationUnit unit) {
        List<DetectedPattern> hits = new ArrayList<>();
        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (cls.isInterface()) return;
            String name = cls.getNameAsString();

            // Find a static Map field — the cache.
            String cacheFieldName = null;
            String cacheValueType = null;
            for (FieldDeclaration f : cls.getFields()) {
                if (!f.isStatic()) continue;
                for (VariableDeclarator v : f.getVariables()) {
                    if (!(v.getType() instanceof ClassOrInterfaceType cit)) continue;
                    if (!"Map".equals(cit.getNameAsString())
                        && !"ConcurrentMap".equals(cit.getNameAsString())
                        && !"HashMap".equals(cit.getNameAsString())) continue;
                    cacheFieldName = v.getNameAsString();
                    // Pull out the Map<?, V> value type for the evidence string.
                    cacheValueType = cit.getTypeArguments()
                        .filter(ta -> ta.size() == 2)
                        .map(ta -> ta.get(1).toString())
                        .orElse("?");
                    break;
                }
                if (cacheFieldName != null) break;
            }
            if (cacheFieldName == null) return;

            // Look for a static method whose body talks to that cache.
            int line = cls.getBegin().map(p -> p.line).orElse(-1);
            String factoryMethod = null;
            boolean usesComputeIfAbsent = false;
            for (MethodDeclaration m : cls.getMethods()) {
                if (!m.isStatic()) continue;
                if (m.getType().isVoidType()) continue;
                if (m.getBody().isEmpty()) continue;
                String body = m.getBody().get().toString();
                if (body.contains(cacheFieldName + ".computeIfAbsent")) {
                    factoryMethod = m.getNameAsString();
                    usesComputeIfAbsent = true;
                    break;
                }
                boolean manualInterning =
                    (body.contains(cacheFieldName + ".containsKey")
                        || body.contains(cacheFieldName + ".get("))
                    && body.contains(cacheFieldName + ".put(");
                if (manualInterning) {
                    factoryMethod = m.getNameAsString();
                }
            }
            if (factoryMethod == null) return;

            double confidence = usesComputeIfAbsent ? 1.0 : 0.66;
            List<String> evidence = new ArrayList<>();
            evidence.add("class " + name + " holds a static Map cache '" + cacheFieldName +
                "' (value type " + cacheValueType + ")");
            evidence.add("static method " + factoryMethod + "() interns instances via " +
                (usesComputeIfAbsent ? "computeIfAbsent" : "manual containsKey/put"));
            hits.add(new DetectedPattern(Pattern.FLYWEIGHT, name, line, confidence, evidence));
        });
        return hits;
    }
}
