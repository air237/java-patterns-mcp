package com.javapatterns.mcp.validate;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates {@link Pattern#CHAIN_OF_RESPONSIBILITY} hierarchies.
 *
 * <p>Shape filter (mirrors {@code ChainOfResponsibilityDetector}):
 * a handler abstraction H (abstract class or interface) that
 * declares a handle / process / dispatch method AND has a
 * self-referential {@code next} field (on H itself for abstract
 * classes, or on a concrete subclass for interfaces).</p>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li><b>ERROR</b> — the base handler's handle method body neither
 *       null-checks {@code next} nor calls {@code super.handle(...)}.
 *       Without one of those, the last link in the chain blows up
 *       with a {@link NullPointerException} on the first unhandled
 *       request.</li>
 *   <li><b>WARNING</b> — no public setNext / setSuccessor /
 *       linkTo method on the base. Without one, callers cannot
 *       assemble chains at runtime — the whole point of the pattern.</li>
 *   <li><b>INFO</b> — the {@code next} field is {@code private}.
 *       Concrete subclasses cannot reach it, so they have to go
 *       through {@code super.handle(...)} for every fallthrough.
 *       Works, but the canonical form makes the link
 *       {@code protected}.</li>
 * </ul>
 */
public final class ChainOfResponsibilityValidator implements PatternValidator {

    private static final List<String> HANDLE_PREFIXES =
        List.of("handle", "process", "dispatch");

    private static final List<String> SET_NEXT_NAMES =
        List.of("setnext", "setsuccessor", "linkto", "withnext", "andthen");

    @Override
    public Pattern pattern() { return Pattern.CHAIN_OF_RESPONSIBILITY; }

    @Override
    public List<ValidationIssue> validate(CompilationUnit unit) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<ClassOrInterfaceDeclaration> all = unit.findAll(ClassOrInterfaceDeclaration.class);

        for (ClassOrInterfaceDeclaration handler : all) {
            if (!(handler.isInterface() || handler.isAbstract())) continue;
            String name = handler.getNameAsString();
            int line = handler.getBegin().map(p -> p.line).orElse(-1);

            // Must declare a handle*() method.
            MethodDeclaration handleMethod = null;
            for (MethodDeclaration m : handler.getMethods()) {
                String n = m.getNameAsString();
                if (HANDLE_PREFIXES.stream().anyMatch(n::startsWith)) {
                    handleMethod = m;
                    break;
                }
            }
            if (handleMethod == null) continue;

            // Self-referential next field — on the handler itself or on a concrete subclass.
            FieldDeclaration nextField = null;
            VariableDeclarator nextVar = null;
            ClassOrInterfaceDeclaration nextOwner = null;

            for (FieldDeclaration f : handler.getFields()) {
                if (f.isStatic()) continue;
                for (VariableDeclarator v : f.getVariables()) {
                    if (v.getType() instanceof ClassOrInterfaceType cit
                        && cit.getNameAsString().equals(name)) {
                        nextField = f;
                        nextVar = v;
                        nextOwner = handler;
                        break;
                    }
                }
                if (nextField != null) break;
            }
            if (nextField == null) {
                for (ClassOrInterfaceDeclaration s : all) {
                    if (s == handler) continue;
                    boolean impl = s.getImplementedTypes().stream()
                        .anyMatch(t -> t.getNameAsString().equals(name))
                        || s.getExtendedTypes().stream()
                        .anyMatch(t -> t.getNameAsString().equals(name));
                    if (!impl) continue;
                    for (FieldDeclaration f : s.getFields()) {
                        if (f.isStatic()) continue;
                        for (VariableDeclarator v : f.getVariables()) {
                            if (v.getType() instanceof ClassOrInterfaceType cit
                                && cit.getNameAsString().equals(name)) {
                                nextField = f;
                                nextVar = v;
                                nextOwner = s;
                                break;
                            }
                        }
                        if (nextField != null) break;
                    }
                    if (nextField != null) break;
                }
            }
            if (nextField == null) continue;

            String nextFieldName = nextVar.getNameAsString();

            // ─── ERROR: handle body neither null-checks next nor super.handle ─
            // Only check this when the base handler itself defines a default
            // (non-abstract) handle body — interface methods or abstract
            // declarations don't get a body to check.
            if (!handleMethod.isAbstract() && handleMethod.getBody().isPresent()) {
                String body = handleMethod.getBody().get().toString();
                boolean nullChecked = body.contains(nextFieldName + " != null")
                    || body.contains(nextFieldName + " == null")
                    || (body.contains(nextFieldName) && body.contains("Optional"));
                boolean delegatesToSuper = body.contains("super.handle")
                    || body.contains("super.process")
                    || body.contains("super.dispatch");
                if (!nullChecked && !delegatesToSuper) {
                    int hLine = handleMethod.getBegin().map(p -> p.line).orElse(line);
                    issues.add(new ValidationIssue(
                        Pattern.CHAIN_OF_RESPONSIBILITY, name, hLine, Severity.ERROR,
                        "Base handler " + name + "." + handleMethod.getNameAsString() +
                        "() body neither null-checks '" + nextFieldName + "' nor falls back " +
                        "to super.handle(...) — the last link in the chain will NPE on the " +
                        "first unhandled request.",
                        "Guard the forward: 'if (" + nextFieldName + " != null) return " +
                        nextFieldName + "." + handleMethod.getNameAsString() + "(request); " +
                        "return defaultResponse;' (or have concrete handlers always end with super)."
                    ));
                }
            }

            // ─── WARNING: no setNext / linkTo on the base ──────────
            boolean hasSetter = handler.getMethods().stream().anyMatch(m -> {
                String n = m.getNameAsString().toLowerCase();
                return SET_NEXT_NAMES.contains(n)
                    && m.getParameters().size() == 1
                    && m.getParameter(0).getType() instanceof ClassOrInterfaceType cit
                    && cit.getNameAsString().equals(name);
            });
            if (!hasSetter) {
                issues.add(new ValidationIssue(
                    Pattern.CHAIN_OF_RESPONSIBILITY, name, line, Severity.WARNING,
                    "No public setNext / setSuccessor / linkTo method on " + name +
                    " — callers cannot assemble chains at runtime.",
                    "Add 'public " + name + " setNext(" + name + " next) { this." +
                    nextFieldName + " = next; return next; }' (the fluent return makes " +
                    "callsite chains readable)."
                ));
            }

            // ─── INFO: next field is private ───────────────────────
            if (nextField.isPrivate() && nextOwner == handler) {
                int fLine = nextField.getBegin().map(p -> p.line).orElse(line);
                issues.add(new ValidationIssue(
                    Pattern.CHAIN_OF_RESPONSIBILITY, name, fLine, Severity.INFO,
                    "Field '" + nextFieldName + "' on " + name + " is private — concrete " +
                    "subclasses cannot read it directly and must always delegate via " +
                    "super.handle(...) for fallthrough.",
                    "Mark the field 'protected' to match the canonical Chain-of-Responsibility " +
                    "form, or keep it private and rely strictly on super-delegation."
                ));
            }
        }
        return issues;
    }
}
