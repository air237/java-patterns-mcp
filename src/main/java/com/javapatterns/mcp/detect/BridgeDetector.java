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
 * Recognises the Bridge pattern.
 *
 * <p>Signal — the "abstraction × implementor" two-dimensional split:</p>
 * <ul>
 *   <li>An abstract class A (the "abstraction") holds a non-static
 *       field of an interface (or abstract) type I (the "implementor")
 *       — the bridge between the two hierarchies.</li>
 *   <li>≥1 concrete subclass of A in the same file (a "refined
 *       abstraction").</li>
 *   <li>≥1 concrete implementor of I in the same file.</li>
 * </ul>
 *
 * <p>The implementor field cannot itself be of type A (that would be
 * Composite / Decorator, not Bridge), and A and I must be different
 * types.</p>
 *
 * <p>Confidence: 1.0 when all three signals fire; 0.66 when the
 * abstraction + bridge field are present but only one of "concrete
 * abstraction" or "concrete implementor" is visible in the file.</p>
 */
public final class BridgeDetector implements PatternDetector {

    @Override
    public Pattern pattern() { return Pattern.BRIDGE; }

    @Override
    public List<DetectedPattern> detect(CompilationUnit unit) {
        List<DetectedPattern> hits = new ArrayList<>();
        List<ClassOrInterfaceDeclaration> all = unit.findAll(ClassOrInterfaceDeclaration.class);

        // Index interfaces (and abstract classes) — these are the candidate Implementor types.
        Set<String> abstractionsAndInterfaces = new HashSet<>();
        for (ClassOrInterfaceDeclaration t : all) {
            if (t.isInterface() || t.isAbstract()) {
                abstractionsAndInterfaces.add(t.getNameAsString());
            }
        }

        for (ClassOrInterfaceDeclaration abstraction : all) {
            // Abstraction = abstract class (not interface).
            if (abstraction.isInterface() || !abstraction.isAbstract()) continue;
            String aName = abstraction.getNameAsString();

            // Find a bridge field — non-static, type is a different
            // abstraction / interface declared in this file.
            String implementorType = null;
            for (FieldDeclaration f : abstraction.getFields()) {
                if (f.isStatic()) continue;
                for (VariableDeclarator v : f.getVariables()) {
                    if (!(v.getType() instanceof ClassOrInterfaceType cit)) continue;
                    String tname = cit.getNameAsString();
                    if (tname.equals(aName)) continue; // self-ref → Composite-ish, not Bridge
                    if (!abstractionsAndInterfaces.contains(tname)) continue;
                    implementorType = tname;
                    break;
                }
                if (implementorType != null) break;
            }
            if (implementorType == null) continue;

            // Count concrete refined abstractions (subclasses of A) and
            // concrete implementors of I in the file.
            final String iName = implementorType;
            int concreteAbstractions = 0;
            int concreteImplementors = 0;
            for (ClassOrInterfaceDeclaration s : all) {
                if (s == abstraction) continue;
                if (s.isInterface() || s.isAbstract()) continue;
                boolean extendsAbstraction = s.getExtendedTypes().stream()
                    .anyMatch(t -> t.getNameAsString().equals(aName));
                if (extendsAbstraction) concreteAbstractions++;
                boolean implementsImplementor = s.getImplementedTypes().stream()
                    .anyMatch(t -> t.getNameAsString().equals(iName))
                    || s.getExtendedTypes().stream()
                    .anyMatch(t -> t.getNameAsString().equals(iName));
                if (implementsImplementor) concreteImplementors++;
            }

            boolean strong = concreteAbstractions >= 1 && concreteImplementors >= 1;
            boolean weak = concreteAbstractions >= 1 || concreteImplementors >= 1;
            if (!weak) continue;

            double confidence = strong ? 1.0 : 0.66;
            int line = abstraction.getBegin().map(p -> p.line).orElse(-1);
            List<String> evidence = new ArrayList<>();
            evidence.add("abstract class " + aName + " holds a bridge field of type " + iName);
            evidence.add(concreteAbstractions + " refined abstraction(s) extend " + aName);
            evidence.add(concreteImplementors + " concrete implementor(s) of " + iName);
            hits.add(new DetectedPattern(Pattern.BRIDGE, aName, line, confidence, evidence));
        }
        return hits;
    }
}
