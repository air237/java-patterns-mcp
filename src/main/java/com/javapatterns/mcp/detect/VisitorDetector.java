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
 * Recognises the Visitor pattern.
 *
 * <p>Signal (the classic double-dispatch shape):</p>
 * <ul>
 *   <li>An "element" type — interface or abstract class — declaring
 *       an {@code accept(...)} method whose single parameter is a
 *       reference to a "visitor" type.</li>
 *   <li>The visitor type is itself an interface (or abstract class)
 *       and declares ≥2 overloaded {@code visit(...)} methods —
 *       one per concrete element family.</li>
 *   <li>≥1 concrete element class implements {@code accept(...)} —
 *       this is the place where the double-dispatch happens.</li>
 * </ul>
 *
 * <p>Confidence: 1.0 when all three signals fire; 0.66 when the
 * element + visitor types exist but no concrete element is visible
 * in the file.</p>
 */
public final class VisitorDetector implements PatternDetector {

    @Override
    public Pattern pattern() { return Pattern.VISITOR; }

    @Override
    public List<DetectedPattern> detect(CompilationUnit unit) {
        List<DetectedPattern> hits = new ArrayList<>();
        List<ClassOrInterfaceDeclaration> all = unit.findAll(ClassOrInterfaceDeclaration.class);

        for (ClassOrInterfaceDeclaration element : all) {
            // Element must be an abstraction.
            if (!(element.isInterface() || element.isAbstract())) continue;
            String elementName = element.getNameAsString();

            // Find an accept(...) declaration that takes one reference-typed parameter.
            MethodDeclaration acceptDecl = null;
            String visitorType = null;
            for (MethodDeclaration m : element.getMethods()) {
                if (!"accept".equals(m.getNameAsString())) continue;
                if (m.getParameters().size() != 1) continue;
                var ptype = m.getParameter(0).getType();
                if (!ptype.isClassOrInterfaceType()) continue;
                acceptDecl = m;
                visitorType = ptype.asClassOrInterfaceType().getNameAsString();
                break;
            }
            if (acceptDecl == null || visitorType.equals(elementName)) continue;

            // Visitor must be an interface or abstract class with ≥2 visit(...) methods.
            ClassOrInterfaceDeclaration visitor = null;
            for (ClassOrInterfaceDeclaration t : all) {
                if (!t.getNameAsString().equals(visitorType)) continue;
                if (!(t.isInterface() || t.isAbstract())) continue;
                visitor = t;
                break;
            }
            if (visitor == null) continue;

            long visitMethods = visitor.getMethods().stream()
                .filter(mm -> "visit".equals(mm.getNameAsString())
                    || mm.getNameAsString().startsWith("visit"))
                .count();
            if (visitMethods < 2) continue;

            // Count concrete element implementors.
            Set<String> concreteElements = new HashSet<>();
            for (ClassOrInterfaceDeclaration s : all) {
                if (s.isInterface() || s.isAbstract()) continue;
                boolean impl = s.getImplementedTypes().stream()
                    .anyMatch(t -> t.getNameAsString().equals(elementName))
                    || s.getExtendedTypes().stream()
                    .anyMatch(t -> t.getNameAsString().equals(elementName));
                if (impl) concreteElements.add(s.getNameAsString());

                // Inner / nested elements (Shapes.Circle pattern) — match
                // classes declared inside a class that themselves implement
                // the element abstraction.
                for (var nested : s.findAll(ClassOrInterfaceDeclaration.class)) {
                    if (nested == s) continue;
                    if (nested.isInterface() || nested.isAbstract()) continue;
                    boolean nestedImpl = nested.getImplementedTypes().stream()
                        .anyMatch(t -> t.getNameAsString().equals(elementName))
                        || nested.getExtendedTypes().stream()
                        .anyMatch(t -> t.getNameAsString().equals(elementName));
                    if (nestedImpl) concreteElements.add(nested.getNameAsString());
                }
            }

            double confidence = concreteElements.isEmpty() ? 0.66 : 1.0;
            int line = element.getBegin().map(p -> p.line).orElse(-1);
            List<String> evidence = new ArrayList<>();
            evidence.add((element.isInterface() ? "interface " : "abstract class ") + elementName +
                " declares accept(" + visitorType + ")");
            evidence.add("visitor " + visitorType + " declares " + visitMethods + " visit*() methods");
            if (concreteElements.isEmpty()) {
                evidence.add("no concrete element implementor visible in this file");
            } else {
                evidence.add(concreteElements.size() + " concrete element(s): " + concreteElements);
            }
            hits.add(new DetectedPattern(Pattern.VISITOR, elementName, line, confidence, evidence));
        }
        return hits;
    }
}
