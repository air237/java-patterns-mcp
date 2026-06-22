package com.javapatterns.mcp.validate;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates {@link Pattern#STRATEGY} hierarchies.
 *
 * <p>Rules:</p>
 * <ul>
 *   <li><b>ERROR</b> — the strategy "interface" is a concrete class
 *       (clients are now coupled to a specific implementation).</li>
 *   <li><b>WARNING</b> — fewer than two concrete implementations of the
 *       strategy in the file. A single-implementation strategy is a
 *       smell (over-abstraction / YAGNI).</li>
 *   <li><b>WARNING</b> — the strategy contract declares more than one
 *       abstract method. A multi-method strategy starts behaving like
 *       a mini-interface and is harder to swap; consider splitting.</li>
 *   <li><b>INFO</b> — concrete strategy is not {@code final}; a
 *       deeper subclass hierarchy makes algorithm swapping confusing.</li>
 * </ul>
 *
 * <p>A type is treated as a "strategy candidate" if its name ends with
 * {@code "Strategy"}.</p>
 */
public final class StrategyValidator implements PatternValidator {

    @Override
    public Pattern pattern() { return Pattern.STRATEGY; }

    @Override
    public List<ValidationIssue> validate(CompilationUnit unit) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<ClassOrInterfaceDeclaration> all = unit.findAll(ClassOrInterfaceDeclaration.class);

        for (ClassOrInterfaceDeclaration cand : all) {
            String name = cand.getNameAsString();
            if (!name.endsWith("Strategy")) continue;
            // We only inspect the "contract" type — the abstraction.
            boolean isContract = cand.isInterface() || cand.isAbstract();
            int contractLine = cand.getBegin().map(p -> p.line).orElse(-1);

            // ── ERROR: contract is concrete ────────────────────
            if (!isContract) {
                issues.add(new ValidationIssue(
                    Pattern.STRATEGY, name, contractLine, Severity.ERROR,
                    "Strategy contract " + name + " is a concrete class — clients " +
                    "are now coupled to a single implementation.",
                    "Convert " + name + " to an interface (or abstract class) so " +
                    "alternative algorithms can be substituted."
                ));
                continue; // remaining checks need an abstraction
            }

            // ── WARNING: contract has > 1 abstract method ──────
            long abstractMethods = cand.getMethods().stream()
                .filter(m -> cand.isInterface() ? !m.isDefault() && !m.isStatic() : m.isAbstract())
                .count();
            if (abstractMethods > 1) {
                issues.add(new ValidationIssue(
                    Pattern.STRATEGY, name, contractLine, Severity.WARNING,
                    "Strategy contract " + name + " declares " + abstractMethods +
                    " abstract methods. Multi-method strategies are hard to swap " +
                    "and start behaving like mini-interfaces.",
                    "Split " + name + " into separate single-method strategies, " +
                    "one per varying algorithm."
                ));
            }

            // ── Count implementors / count ≥ 2 expected ────────
            Set<String> impls = new HashSet<>();
            for (ClassOrInterfaceDeclaration s : all) {
                if (s == cand) continue;
                if (s.isInterface() || s.isAbstract()) continue;
                boolean impl = s.getImplementedTypes().stream()
                    .anyMatch(x -> x.getNameAsString().equals(name))
                    || s.getExtendedTypes().stream()
                    .anyMatch(x -> x.getNameAsString().equals(name));
                if (impl) impls.add(s.getNameAsString());
            }
            if (impls.size() < 2) {
                issues.add(new ValidationIssue(
                    Pattern.STRATEGY, name, contractLine, Severity.WARNING,
                    "Strategy " + name + " has " + impls.size() + " implementation(s) " +
                    "in this file. A single-implementation strategy is usually " +
                    "over-engineering (YAGNI).",
                    "Either add a second meaningful implementation, or drop the " +
                    "abstraction and inline the algorithm."
                ));
            }

            // ── INFO: implementor not final ────────────────────
            for (ClassOrInterfaceDeclaration s : all) {
                if (s == cand) continue;
                if (s.isInterface() || s.isAbstract()) continue;
                boolean impl = s.getImplementedTypes().stream()
                    .anyMatch(x -> x.getNameAsString().equals(name))
                    || s.getExtendedTypes().stream()
                    .anyMatch(x -> x.getNameAsString().equals(name));
                if (impl && !s.isFinal()) {
                    issues.add(new ValidationIssue(
                        Pattern.STRATEGY, name,
                        s.getBegin().map(p -> p.line).orElse(contractLine),
                        Severity.INFO,
                        "Strategy implementation " + s.getNameAsString() + " is not final — " +
                        "deeper subclassing can make algorithm swapping ambiguous.",
                        "Mark concrete strategies final unless you have a clear reason " +
                        "to extend them."
                    ));
                }
            }
        }
        return issues;
    }
}
