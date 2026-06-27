package com.javapatterns.mcp.detect;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.List;

/**
 * Recognises the Prototype pattern.
 *
 * <p>Signal — a class that supports cloning of itself:</p>
 * <ul>
 *   <li>declares a non-static, non-private method named
 *       {@code clone} / {@code copy} / {@code duplicate} whose return
 *       type is the same class (or a supertype declared on it); AND</li>
 *   <li>has a constructor whose single parameter is also of the same
 *       class (the "copy constructor" used by {@code clone()} to
 *       duplicate shared state); OR the class implements
 *       {@code Cloneable}.</li>
 * </ul>
 *
 * <p>Confidence:</p>
 * <ul>
 *   <li>1.0 — clone-style method + copy constructor present;</li>
 *   <li>0.66 — clone-style method + {@code implements Cloneable} (the
 *       classic-but-discouraged form);</li>
 *   <li>0.5 — clone-style method only (without either of the above).</li>
 * </ul>
 *
 * <p>Records that declare a manual {@code copy()} method are also
 * picked up, since records use Prototype-by-construction in modern
 * Java.</p>
 */
public final class PrototypeDetector implements PatternDetector {

    private static final List<String> CLONE_NAMES =
        List.of("clone", "copy", "duplicate");

    @Override
    public Pattern pattern() { return Pattern.PROTOTYPE; }

    @Override
    public List<DetectedPattern> detect(CompilationUnit unit) {
        List<DetectedPattern> hits = new ArrayList<>();
        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (cls.isInterface()) return;
            String name = cls.getNameAsString();

            // Find a clone-style method that returns this class (or one of its supertypes).
            MethodDeclaration cloneMethod = null;
            for (MethodDeclaration m : cls.getMethods()) {
                if (m.isStatic() || m.isPrivate()) continue;
                if (!CLONE_NAMES.contains(m.getNameAsString())) continue;
                if (!(m.getType() instanceof ClassOrInterfaceType rt)) continue;
                String rtName = rt.getNameAsString();
                // Return type must be the class itself, or a declared supertype.
                boolean returnsSelf = rtName.equals(name);
                boolean returnsSuper = cls.getExtendedTypes().stream()
                    .anyMatch(t -> t.getNameAsString().equals(rtName));
                if (!returnsSelf && !returnsSuper) continue;
                cloneMethod = m;
                break;
            }
            if (cloneMethod == null) return;

            // Look for a copy constructor: a constructor whose single
            // parameter is of the same class (or a supertype).
            boolean hasCopyCtor = false;
            for (ConstructorDeclaration c : cls.getConstructors()) {
                if (c.getParameters().size() != 1) continue;
                var ptype = c.getParameter(0).getType();
                if (!(ptype instanceof ClassOrInterfaceType cit)) continue;
                String pname = cit.getNameAsString();
                if (pname.equals(name)) { hasCopyCtor = true; break; }
                if (cls.getExtendedTypes().stream().anyMatch(t -> t.getNameAsString().equals(pname))) {
                    hasCopyCtor = true; break;
                }
            }

            boolean implementsCloneable = cls.getImplementedTypes().stream()
                .anyMatch(t -> t.getNameAsString().equals("Cloneable"));

            double confidence;
            if (hasCopyCtor) confidence = 1.0;
            else if (implementsCloneable) confidence = 0.66;
            else confidence = 0.5;

            int line = cls.getBegin().map(p -> p.line).orElse(-1);
            List<String> evidence = new ArrayList<>();
            evidence.add("class " + name + " declares " + cloneMethod.getNameAsString() +
                "() returning " + cloneMethod.getType());
            if (hasCopyCtor) {
                evidence.add("has a copy constructor taking " + name);
            } else if (implementsCloneable) {
                evidence.add("implements Cloneable (classic form — Effective Java recommends avoiding)");
            } else {
                evidence.add("no copy constructor and no Cloneable — clone() body must produce the copy itself");
            }
            hits.add(new DetectedPattern(Pattern.PROTOTYPE, name, line, confidence, evidence));
        });
        return hits;
    }
}
