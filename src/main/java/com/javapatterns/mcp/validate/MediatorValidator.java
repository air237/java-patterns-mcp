package com.javapatterns.mcp.validate;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates {@link Pattern#MEDIATOR} hierarchies.
 *
 * <p>Shape filter (mirrors {@code MediatorDetector}): an abstraction
 * M (interface or abstract class) that declares BOTH a fan-out
 * method (send / notify / dispatch / emit / broadcast) AND a
 * register / add / subscribe / join method, plus ≥1 colleague type
 * that holds a non-static field of M (colleagues talk through the
 * mediator, not to each other).</p>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li><b>ERROR</b> — a colleague's mediator field is not
 *       {@code final}. A mutable mediator field lets the colleague
 *       be silently re-routed to a different hub at runtime,
 *       breaking the "centralized communication" invariant of the
 *       pattern.</li>
 *   <li><b>WARNING</b> — a colleague holds a non-static field of
 *       another colleague type. Direct peer references defeat the
 *       Mediator: communication is supposed to flow through the
 *       hub, not via colleague-to-colleague pointers.</li>
 *   <li><b>INFO</b> — the concrete mediator's register method does
 *       not null-check the incoming colleague. A null participant
 *       crashes the next fan-out call, not the registration call
 *       — failing eagerly is friendlier.</li>
 * </ul>
 */
public final class MediatorValidator implements PatternValidator {

    private static final List<String> SEND_PREFIXES =
        List.of("send", "notify", "dispatch", "emit", "broadcast");
    private static final List<String> REGISTER_PREFIXES =
        List.of("register", "add", "subscribe", "join");

    @Override
    public Pattern pattern() { return Pattern.MEDIATOR; }

    @Override
    public List<ValidationIssue> validate(CompilationUnit unit) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<ClassOrInterfaceDeclaration> all = unit.findAll(ClassOrInterfaceDeclaration.class);

        for (ClassOrInterfaceDeclaration mediator : all) {
            if (!(mediator.isInterface() || mediator.isAbstract())) continue;
            String mName = mediator.getNameAsString();
            int mLine = mediator.getBegin().map(p -> p.line).orElse(-1);

            boolean hasSend = mediator.getMethods().stream().anyMatch(m -> {
                String n = m.getNameAsString();
                return SEND_PREFIXES.stream().anyMatch(n::startsWith);
            });
            boolean hasRegister = mediator.getMethods().stream().anyMatch(m -> {
                String n = m.getNameAsString();
                return REGISTER_PREFIXES.stream().anyMatch(n::startsWith);
            });
            if (!(hasSend && hasRegister)) continue;

            // Collect colleagues — types holding a non-static M-typed field.
            List<ClassOrInterfaceDeclaration> colleagues = new ArrayList<>();
            for (ClassOrInterfaceDeclaration s : all) {
                if (s == mediator) continue;
                boolean hasMediatorField = s.getFields().stream()
                    .filter(f -> !f.isStatic())
                    .flatMap(f -> f.getVariables().stream())
                    .anyMatch(v -> v.getType() instanceof ClassOrInterfaceType cit
                        && cit.getNameAsString().equals(mName));
                if (hasMediatorField) colleagues.add(s);
            }
            if (colleagues.isEmpty()) continue;

            Set<String> colleagueNames = new HashSet<>();
            for (ClassOrInterfaceDeclaration c : colleagues) colleagueNames.add(c.getNameAsString());

            // ─── ERROR: colleague mediator field is not final ──────
            for (ClassOrInterfaceDeclaration colleague : colleagues) {
                for (FieldDeclaration f : colleague.getFields()) {
                    if (f.isStatic() || f.isFinal()) continue;
                    for (VariableDeclarator v : f.getVariables()) {
                        if (!(v.getType() instanceof ClassOrInterfaceType cit)) continue;
                        if (!cit.getNameAsString().equals(mName)) continue;
                        int line = f.getBegin().map(p -> p.line).orElse(-1);
                        issues.add(new ValidationIssue(
                            Pattern.MEDIATOR, colleague.getNameAsString(), line, Severity.ERROR,
                            "Colleague " + colleague.getNameAsString() + " holds mediator " +
                            "field '" + v.getNameAsString() + "' (type " + mName + ") that " +
                            "is not final — the mediator can be silently swapped at runtime, " +
                            "breaking the 'centralized communication' invariant.",
                            "Mark the field 'final'. A colleague should be bound to one " +
                            "mediator for its entire lifetime."
                        ));
                    }
                }
            }

            // ─── WARNING: colleague holds a direct peer reference ──
            for (ClassOrInterfaceDeclaration colleague : colleagues) {
                String myName = colleague.getNameAsString();
                for (FieldDeclaration f : colleague.getFields()) {
                    if (f.isStatic()) continue;
                    for (VariableDeclarator v : f.getVariables()) {
                        if (!(v.getType() instanceof ClassOrInterfaceType cit)) continue;
                        String t = cit.getNameAsString();
                        if (t.equals(myName)) continue;     // self-reference is fine
                        if (t.equals(mName)) continue;      // mediator-ref is fine
                        if (!colleagueNames.contains(t)) continue;
                        int line = f.getBegin().map(p -> p.line).orElse(-1);
                        issues.add(new ValidationIssue(
                            Pattern.MEDIATOR, myName, line, Severity.WARNING,
                            "Colleague " + myName + " holds a direct reference to another " +
                            "colleague '" + v.getNameAsString() + "' (type " + t + ") — peer-" +
                            "to-peer pointers defeat the Mediator pattern; communication is " +
                            "supposed to flow through the hub.",
                            "Replace the peer reference with a call through the mediator: " +
                            "this.mediator.send(myName, \"...\"); then let the mediator dispatch " +
                            "to the right participant."
                        ));
                    }
                }
            }

            // ─── INFO: concrete mediator register() does not null-check ─
            for (ClassOrInterfaceDeclaration s : all) {
                if (s.isInterface() || s.isAbstract()) continue;
                boolean isConcreteMediator = s.getImplementedTypes().stream()
                    .anyMatch(t -> t.getNameAsString().equals(mName))
                    || s.getExtendedTypes().stream()
                    .anyMatch(t -> t.getNameAsString().equals(mName));
                if (!isConcreteMediator) continue;

                for (MethodDeclaration m : s.getMethods()) {
                    String n = m.getNameAsString();
                    if (REGISTER_PREFIXES.stream().noneMatch(n::startsWith)) continue;
                    if (m.getParameters().size() != 1) continue;
                    if (m.getBody().isEmpty()) continue;
                    String body = m.getBody().get().toString();
                    boolean nullChecked = body.contains("Objects.requireNonNull")
                        || (body.contains("null") && body.contains("throw"));
                    if (nullChecked) continue;

                    int line = m.getBegin().map(p -> p.line).orElse(-1);
                    issues.add(new ValidationIssue(
                        Pattern.MEDIATOR, s.getNameAsString(), line, Severity.INFO,
                        "Concrete mediator " + s.getNameAsString() + "." + n + "() does not " +
                        "null-check the registered participant — a null colleague will crash " +
                        "the next fan-out call instead of the registration call itself.",
                        "Reject nulls eagerly: 'Objects.requireNonNull(colleague, \"colleague\");' " +
                        "at the top of " + n + "()."
                    ));
                    break; // one INFO per concrete mediator is enough
                }
            }

            // Only report once per mediator type — break after handling.
            break;
        }
        return issues;
    }
}
