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
 * Recognises the State pattern.
 *
 * <p>Signals (single source file scope):</p>
 * <ul>
 *   <li>A {@code sealed} or {@code abstract} state hierarchy
 *       ({@code interface} or {@code abstract class}) — name suffix
 *       "State" is a strong hint.</li>
 *   <li>A "context" concrete class that holds a field of the state
 *       hierarchy's type and whose name does NOT itself end with
 *       "State" (we don't want to count the states themselves as
 *       contexts).</li>
 *   <li>At least 2 concrete subclasses / implementors of the state
 *       hierarchy in the same file.</li>
 * </ul>
 *
 * <p>Confidence: 1.0 when all three signals fire; 0.66 with just the
 * hierarchy + context but only one concrete state.</p>
 */
public final class StateDetector implements PatternDetector {

    @Override
    public Pattern pattern() { return Pattern.STATE; }

    @Override
    public List<DetectedPattern> detect(CompilationUnit unit) {
        List<DetectedPattern> hits = new ArrayList<>();

        // 1) Find candidate state hierarchies.
        List<ClassOrInterfaceDeclaration> stateHierarchies = new ArrayList<>();
        for (ClassOrInterfaceDeclaration t : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            boolean stateLike =
                (t.isInterface() || t.isAbstract())
                && t.getNameAsString().endsWith("State");
            if (stateLike) stateHierarchies.add(t);
        }
        if (stateHierarchies.isEmpty()) return List.of();

        for (ClassOrInterfaceDeclaration stateRoot : stateHierarchies) {
            String stateType = stateRoot.getNameAsString();

            // 2) Count concrete implementors/subclasses.
            Set<String> implementors = new HashSet<>();
            for (ClassOrInterfaceDeclaration t : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (t.isInterface() || t.isAbstract()) continue;
                boolean impl = t.getImplementedTypes().stream()
                    .anyMatch(x -> x.getNameAsString().equals(stateType))
                    || t.getExtendedTypes().stream()
                    .anyMatch(x -> x.getNameAsString().equals(stateType));
                if (impl) implementors.add(t.getNameAsString());
            }

            // 3) Find a context — concrete class with a field of stateType,
            //    name doesn't end with "State".
            String context = null;
            int contextLine = -1;
            for (ClassOrInterfaceDeclaration t : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (t.isInterface() || t.isAbstract()) continue;
                if (t.getNameAsString().endsWith("State")) continue;
                boolean carriesState = false;
                for (FieldDeclaration f : t.getFields()) {
                    if (f.isStatic()) continue;
                    for (VariableDeclarator v : f.getVariables()) {
                        if (v.getType() instanceof ClassOrInterfaceType cit
                            && cit.getNameAsString().equals(stateType)) {
                            carriesState = true; break;
                        }
                    }
                    if (carriesState) break;
                }
                if (carriesState) {
                    context = t.getNameAsString();
                    contextLine = t.getBegin().map(p -> p.line).orElse(-1);
                    break;
                }
            }
            if (context == null) continue;

            double confidence = implementors.size() >= 2 ? 1.0 : 0.66;
            int line = stateRoot.getBegin().map(p -> p.line).orElse(contextLine);
            List<String> evidence = List.of(
                "state hierarchy: " + stateType + " (" +
                    (stateRoot.isInterface() ? "interface" : "abstract class") + ")",
                "context class " + context + " holds a " + stateType + " field",
                implementors.size() + " concrete state(s): " + implementors
            );
            hits.add(new DetectedPattern(Pattern.STATE, stateType, line, confidence, evidence));
        }
        return hits;
    }
}
