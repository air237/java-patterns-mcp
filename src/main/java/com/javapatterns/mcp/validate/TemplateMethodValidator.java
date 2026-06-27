package com.javapatterns.mcp.validate;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates {@link Pattern#TEMPLATE_METHOD} implementations.
 *
 * <p>This validator only fires on classes that look like a Template
 * Method (i.e. an abstract class that declares at least one abstract
 * method AND has at least one concrete method that calls an abstract
 * sibling — the same signal {@code TemplateMethodDetector} uses).
 * For everything else it stays silent so unrelated code does not
 * generate noise.</p>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li><b>ERROR</b> — the template method is not {@code final}. A
 *       non-final template can be overridden in a subclass, which
 *       rearranges the algorithm and defeats the entire point of the
 *       pattern. If the variable step really needs to be replaced
 *       wholesale, switch to <b>Strategy</b>; if just the steps
 *       should vary, keep Template Method but lock the flow with
 *       {@code final}.</li>
 *   <li><b>ERROR</b> — a constructor calls an abstract hook on
 *       {@code this}. At construction time the subclass fields are
 *       not yet initialised, so the overridden hook sees a
 *       half-built object — typically NPEs or surprising defaults
 *       (Effective Java item 19). Defer the call to a post-init
 *       method or use a factory method.</li>
 *   <li><b>WARNING</b> — an abstract hook is {@code public}. Hooks
 *       are implementation details meant to be called <i>only</i>
 *       from the template method, not from clients. Mark them
 *       {@code protected} so the contract surface stays small.</li>
 *   <li><b>INFO</b> — every hook is abstract; no non-abstract hook
 *       with a sensible default is declared. Defaulted hooks make
 *       the pattern much more reusable because subclasses only
 *       override what they need.</li>
 * </ul>
 */
public final class TemplateMethodValidator implements PatternValidator {

    @Override
    public Pattern pattern() { return Pattern.TEMPLATE_METHOD; }

    @Override
    public List<ValidationIssue> validate(CompilationUnit unit) {
        List<ValidationIssue> issues = new ArrayList<>();
        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (cls.isInterface()) return;
            if (!cls.isAbstract()) return;
            String name = cls.getNameAsString();
            int classLine = cls.getBegin().map(p -> p.line).orElse(-1);

            // Collect abstract methods declared on THIS class — these are the hooks.
            Set<String> abstractHookNames = new HashSet<>();
            List<MethodDeclaration> abstractHooks = new ArrayList<>();
            int nonAbstractMethodCount = 0;
            for (MethodDeclaration m : cls.getMethods()) {
                if (m.isAbstract()) {
                    abstractHookNames.add(m.getNameAsString());
                    abstractHooks.add(m);
                } else if (!m.isStatic()) {
                    nonAbstractMethodCount++;
                }
            }
            if (abstractHookNames.isEmpty()) return; // no hooks → not template method

            // Find the template method(s): non-abstract, non-static, calls at least one abstract sibling.
            List<MethodDeclaration> templateMethods = new ArrayList<>();
            for (MethodDeclaration m : cls.getMethods()) {
                if (m.isAbstract()) continue;
                if (m.isStatic()) continue;
                if (callsAnyHook(m, abstractHookNames)) {
                    templateMethods.add(m);
                }
            }

            // A class is Template-Method-shaped if EITHER it has a concrete method that
            // calls an abstract hook (the canonical case), OR a constructor calls one
            // (the EJ-19 anti-pattern variant — we still want to flag it). If neither
            // is true the class is just an ordinary abstract class with hooks waiting
            // to be called from a future subclass; nothing to validate here.
            boolean anyCtorCallsHook = cls.getConstructors().stream().anyMatch(c ->
                c.getBody() != null && c.findAll(MethodCallExpr.class).stream().anyMatch(call -> {
                    boolean sameClass = call.getScope().isEmpty()
                        || call.getScope().filter(s -> s.toString().equals("this")).isPresent();
                    return sameClass && abstractHookNames.contains(call.getNameAsString());
                }));
            if (templateMethods.isEmpty() && !anyCtorCallsHook) return;

            // ─── ERROR: template method is not final ───────────────
            for (MethodDeclaration tm : templateMethods) {
                if (tm.isFinal()) continue;
                int line = tm.getBegin().map(p -> p.line).orElse(classLine);
                issues.add(new ValidationIssue(
                    Pattern.TEMPLATE_METHOD, name, line, Severity.ERROR,
                    "Template method '" + tm.getNameAsString() + "()' is not final — " +
                    "a subclass can override it and bypass the locked algorithm skeleton.",
                    "Mark the template method as 'final'. If subclasses really need to replace " +
                    "the variable step wholesale (not just override hooks), switch to the Strategy " +
                    "pattern; Template Method is about locking the flow, not parameterising it."
                ));
            }

            // ─── ERROR: constructor calls an abstract hook on this ─
            //     Reads to overridable methods from constructors are an
            //     Effective-Java-19 anti-pattern (half-initialised subclass).
            for (ConstructorDeclaration ctor : cls.getConstructors()) {
                if (ctor.getBody() == null) continue;
                List<MethodCallExpr> calls = ctor.findAll(MethodCallExpr.class);
                for (MethodCallExpr call : calls) {
                    // Only same-class calls: no scope (== this) OR scope literally "this".
                    boolean noScope = call.getScope().isEmpty();
                    boolean thisScope = call.getScope().filter(s -> s.toString().equals("this")).isPresent();
                    if (!noScope && !thisScope) continue;

                    String calledName = call.getNameAsString();
                    if (!abstractHookNames.contains(calledName)) continue;

                    int line = call.getBegin().map(p -> p.line).orElse(classLine);
                    issues.add(new ValidationIssue(
                        Pattern.TEMPLATE_METHOD, name, line, Severity.ERROR,
                        "Constructor of '" + name + "' calls abstract hook '" + calledName + "()' on this — " +
                        "at construction time the subclass fields are not yet initialised, " +
                        "so the override sees a half-built object (Effective Java item 19).",
                        "Do not call overridable methods from constructors. Defer the call to a " +
                        "post-construction initialiser the client invokes explicitly, or use a static " +
                        "factory method that constructs and initialises in two steps."
                    ));
                }
            }

            // ─── WARNING: abstract hook is public ──────────────────
            for (MethodDeclaration hook : abstractHooks) {
                if (!hook.isPublic()) continue;
                int line = hook.getBegin().map(p -> p.line).orElse(classLine);
                issues.add(new ValidationIssue(
                    Pattern.TEMPLATE_METHOD, name, line, Severity.WARNING,
                    "Abstract hook '" + hook.getNameAsString() + "()' is public — " +
                    "clients can bypass the template method and call the hook directly.",
                    "Mark the hook 'protected'. Hooks are implementation details; only the template " +
                    "method should call them."
                ));
            }

            // ─── INFO: every hook is abstract (no defaulted hook) ──
            //     Heuristic: at least one non-abstract instance method other than the
            //     template method(s) themselves means a defaulted hook exists.
            int templateMethodCount = templateMethods.size();
            int hookWithDefaultCount = nonAbstractMethodCount - templateMethodCount;
            if (hookWithDefaultCount <= 0 && abstractHooks.size() >= 2) {
                issues.add(new ValidationIssue(
                    Pattern.TEMPLATE_METHOD, name, classLine, Severity.INFO,
                    "Every hook in '" + name + "' is abstract — every subclass must implement all " +
                    abstractHooks.size() + " of them. No non-abstract hook with a sensible default is offered.",
                    "Consider providing at least one non-abstract hook with a default implementation. " +
                    "It makes the pattern much more reusable: subclasses only override what they need."
                ));
            }
        });
        return issues;
    }

    /** True if the method body calls a same-class method whose name matches any of the given hook names. */
    private static boolean callsAnyHook(MethodDeclaration m, Set<String> hookNames) {
        return m.findAll(MethodCallExpr.class).stream().anyMatch(call -> {
            // Only same-class calls: no scope OR scope == "this".
            boolean sameClass = call.getScope().isEmpty()
                || call.getScope().filter(s -> s.toString().equals("this")).isPresent();
            return sameClass && hookNames.contains(call.getNameAsString());
        });
    }
}
