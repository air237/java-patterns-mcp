package com.javapatterns.mcp.detect;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.List;

/**
 * Recognises the Memento pattern.
 *
 * <p>Signal — the originator-memento-caretaker triad:</p>
 * <ul>
 *   <li>An originator class O that has both a method whose name starts
 *       with {@code save} / {@code createMemento} / {@code snapshot}
 *       returning some type M, AND a method whose name starts with
 *       {@code restore} / {@code setMemento} / {@code rollback}
 *       accepting a parameter of type M.</li>
 *   <li>A memento class M visible in the same file (typically named
 *       {@code *Memento} / {@code *Snapshot} / {@code *State}). Even
 *       if M is not name-suffixed, the originator's matched method
 *       signatures pin it down.</li>
 * </ul>
 *
 * <p>Confidence: 1.0 when both originator methods are present and
 * the memento type is visible in the file; 0.66 when only one of the
 * two methods is present (save without restore or vice versa).</p>
 */
public final class MementoDetector implements PatternDetector {

    private static final List<String> SAVE_PREFIXES =
        List.of("save", "creatememento", "snapshot", "capture");
    private static final List<String> RESTORE_PREFIXES =
        List.of("restore", "setmemento", "rollback", "revert");

    @Override
    public Pattern pattern() { return Pattern.MEMENTO; }

    @Override
    public List<DetectedPattern> detect(CompilationUnit unit) {
        List<DetectedPattern> hits = new ArrayList<>();
        List<ClassOrInterfaceDeclaration> all = unit.findAll(ClassOrInterfaceDeclaration.class);

        for (ClassOrInterfaceDeclaration originator : all) {
            if (originator.isInterface() || originator.isAbstract()) continue;
            String name = originator.getNameAsString();

            // Find a save-style method whose return type is a class declared in this file.
            MethodDeclaration saveMethod = null;
            String mementoType = null;
            for (MethodDeclaration m : originator.getMethods()) {
                if (m.isStatic()) continue;
                String n = m.getNameAsString().toLowerCase();
                if (SAVE_PREFIXES.stream().noneMatch(n::startsWith)) continue;
                if (!(m.getType() instanceof ClassOrInterfaceType rt)) continue;
                if (m.getParameters().size() > 0) continue;
                saveMethod = m;
                mementoType = rt.getNameAsString();
                break;
            }
            if (saveMethod == null) continue;

            // Memento type must be declared in this file too.
            final String mementoTypeFinal = mementoType;
            boolean mementoVisible = all.stream()
                .anyMatch(t -> t.getNameAsString().equals(mementoTypeFinal)
                    && !t.isInterface());
            if (!mementoVisible) continue;

            // Find a restore-style method that takes M as its sole parameter.
            MethodDeclaration restoreMethod = null;
            for (MethodDeclaration m : originator.getMethods()) {
                if (m.isStatic()) continue;
                String n = m.getNameAsString().toLowerCase();
                if (RESTORE_PREFIXES.stream().noneMatch(n::startsWith)) continue;
                if (m.getParameters().size() != 1) continue;
                Parameter p = m.getParameter(0);
                if (!(p.getType() instanceof ClassOrInterfaceType pt)) continue;
                if (!pt.getNameAsString().equals(mementoType)) continue;
                restoreMethod = m;
                break;
            }

            double confidence = restoreMethod != null ? 1.0 : 0.66;
            int line = originator.getBegin().map(p -> p.line).orElse(-1);
            List<String> evidence = new ArrayList<>();
            evidence.add("originator " + name + " has save-style method '" +
                saveMethod.getNameAsString() + "()' returning " + mementoType);
            if (restoreMethod != null) {
                evidence.add("and restore-style method '" + restoreMethod.getNameAsString() +
                    "(" + mementoType + ")'");
            } else {
                evidence.add("no matching restore method — capture-only memento");
            }
            evidence.add("memento type " + mementoType + " is declared in the same file");
            hits.add(new DetectedPattern(Pattern.MEMENTO, name, line, confidence, evidence));
        }
        return hits;
    }
}
