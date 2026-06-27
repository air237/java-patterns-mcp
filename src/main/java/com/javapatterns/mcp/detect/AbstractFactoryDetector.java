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
 * Recognises the Abstract Factory pattern.
 *
 * <p>Signal: an interface (or abstract class) that declares two or
 * more abstract methods whose names start with {@code create},
 * {@code make}, {@code new} or {@code build} AND whose return types
 * are all different (a family of related products). At least one
 * concrete class must implement the same interface in the same file
 * (a "concrete factory").</p>
 *
 * <p>Confidence:</p>
 * <ul>
 *   <li>1.0 — the contract has ≥2 differently-typed product methods
 *       and at least one concrete factory in the file.</li>
 *   <li>0.66 — the contract has the product methods but no concrete
 *       factory is visible (the implementation may live elsewhere).</li>
 * </ul>
 *
 * <p>The "different return types" guard avoids reporting plain
 * Factory-Method shapes ({@code create*()} methods that all return
 * the same product) as Abstract Factory.</p>
 */
public final class AbstractFactoryDetector implements PatternDetector {

    private static final List<String> CREATE_PREFIXES =
        List.of("create", "make", "new", "build");

    @Override
    public Pattern pattern() { return Pattern.ABSTRACT_FACTORY; }

    @Override
    public List<DetectedPattern> detect(CompilationUnit unit) {
        List<DetectedPattern> hits = new ArrayList<>();
        List<ClassOrInterfaceDeclaration> all = unit.findAll(ClassOrInterfaceDeclaration.class);

        for (ClassOrInterfaceDeclaration cand : all) {
            // Contract must be an abstraction.
            boolean isAbstraction = cand.isInterface() || cand.isAbstract();
            if (!isAbstraction) continue;
            String name = cand.getNameAsString();

            // Collect "product creation" abstract methods.
            Set<String> productTypes = new HashSet<>();
            List<String> productMethods = new ArrayList<>();
            for (MethodDeclaration m : cand.getMethods()) {
                boolean isAbstractMethod = cand.isInterface()
                    ? (!m.isDefault() && !m.isStatic() && !m.isPrivate())
                    : m.isAbstract();
                if (!isAbstractMethod) continue;
                String mname = m.getNameAsString();
                if (CREATE_PREFIXES.stream().noneMatch(mname::startsWith)) continue;
                String rt = m.getType().toString();
                // void / primitives are not products; the create-family is
                // about producing object instances.
                if ("void".equals(rt) || m.getType().isPrimitiveType()) continue;
                productTypes.add(rt);
                productMethods.add(mname + "() : " + rt);
            }

            // Need at least TWO distinct product types to qualify as Abstract Factory
            // (one type = ordinary Factory Method).
            if (productTypes.size() < 2) continue;

            // Look for ≥1 concrete implementor in the same file.
            int concreteFactories = 0;
            for (ClassOrInterfaceDeclaration s : all) {
                if (s == cand) continue;
                if (s.isInterface() || s.isAbstract()) continue;
                boolean impl = s.getImplementedTypes().stream()
                    .anyMatch(t -> t.getNameAsString().equals(name))
                    || s.getExtendedTypes().stream()
                    .anyMatch(t -> t.getNameAsString().equals(name));
                if (impl) concreteFactories++;
            }

            double confidence = concreteFactories >= 1 ? 1.0 : 0.66;
            int line = cand.getBegin().map(p -> p.line).orElse(-1);
            List<String> evidence = new ArrayList<>();
            evidence.add((cand.isInterface() ? "interface " : "abstract class ") + name +
                " declares " + productMethods.size() + " product-creation methods: " + productMethods);
            evidence.add("product family has " + productTypes.size() + " distinct types: " + productTypes);
            if (concreteFactories > 0) {
                evidence.add(concreteFactories + " concrete factor" + (concreteFactories == 1 ? "y" : "ies") +
                    " implement(s) " + name);
            } else {
                evidence.add("no concrete factory visible in this file");
            }
            hits.add(new DetectedPattern(Pattern.ABSTRACT_FACTORY, name, line, confidence, evidence));
        }
        return hits;
    }
}
