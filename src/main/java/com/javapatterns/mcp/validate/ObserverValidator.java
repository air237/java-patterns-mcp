package com.javapatterns.mcp.validate;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates {@link Pattern#OBSERVER} implementations (subject side).
 *
 * <p>Rules:</p>
 * <ul>
 *   <li><b>ERROR</b> — class has a publish-like method but no
 *       unsubscribe-like method; observers can never detach, every
 *       subscription is a memory leak.</li>
 *   <li><b>WARNING</b> — publish iterates the live subscriber list
 *       (heuristic: body mentions {@code List.copyOf}, {@code new
 *       ArrayList} or {@code stream}). If none of these is found, a
 *       re-entrant subscribe/unsubscribe during dispatch can throw
 *       ConcurrentModificationException.</li>
 *   <li><b>INFO</b> — subject keeps strong references; for long-lived
 *       buses, consider {@code WeakReference} to avoid leaks.</li>
 * </ul>
 */
public final class ObserverValidator implements PatternValidator {

    private static final Set<String> SUBSCRIBE = Set.of(
        "subscribe", "register", "addlistener", "addobserver", "addsubscriber");
    private static final Set<String> UNSUBSCRIBE = Set.of(
        "unsubscribe", "unregister", "removelistener", "removeobserver", "removesubscriber");
    private static final Set<String> PUBLISH_PREFIX = Set.of(
        "publish", "notify", "fire", "dispatch", "emit");

    @Override
    public Pattern pattern() { return Pattern.OBSERVER; }

    @Override
    public List<ValidationIssue> validate(CompilationUnit unit) {
        List<ValidationIssue> issues = new ArrayList<>();
        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (cls.isInterface()) return;
            String name = cls.getNameAsString();
            int line = cls.getBegin().map(p -> p.line).orElse(-1);

            boolean hasSubscribe = cls.getMethods().stream()
                .anyMatch(m -> SUBSCRIBE.contains(m.getNameAsString().toLowerCase()));
            MethodDeclaration publishMethod = cls.getMethods().stream()
                .filter(m -> PUBLISH_PREFIX.stream()
                    .anyMatch(pref -> m.getNameAsString().toLowerCase().startsWith(pref)))
                .findFirst().orElse(null);
            if (!hasSubscribe || publishMethod == null) return;
            boolean hasUnsubscribe = cls.getMethods().stream()
                .anyMatch(m -> UNSUBSCRIBE.contains(m.getNameAsString().toLowerCase()));

            // ─── ERROR: no unsubscribe → memory leak ─────────────
            if (!hasUnsubscribe) {
                issues.add(new ValidationIssue(
                    Pattern.OBSERVER, name, line, Severity.ERROR,
                    name + " offers subscribe + publish but no way to unsubscribe — every " +
                    "subscriber is a memory leak.",
                    "Add an unsubscribe() / removeListener() method that removes the observer " +
                    "from the internal list."
                ));
            }

            // ─── WARNING: publish iterates live list ─────────────
            String publishBody = publishMethod.getBody().map(Object::toString).orElse("");
            boolean safelyCopies = publishBody.contains("copyOf")
                || publishBody.contains("new ArrayList")
                || publishBody.contains(".stream()");
            if (!safelyCopies) {
                issues.add(new ValidationIssue(
                    Pattern.OBSERVER, name,
                    publishMethod.getBegin().map(p -> p.line).orElse(line),
                    Severity.WARNING,
                    publishMethod.getNameAsString() + "() appears to iterate the live " +
                    "subscriber list — a listener that subscribes or unsubscribes during " +
                    "dispatch will throw ConcurrentModificationException.",
                    "Iterate over a snapshot, e.g. `for (var l : List.copyOf(listeners)) ...`."
                ));
            }
        });
        return issues;
    }
}
