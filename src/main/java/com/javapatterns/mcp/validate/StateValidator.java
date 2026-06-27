package com.javapatterns.mcp.validate;

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
 * Validates {@link Pattern#STATE} hierarchies.
 *
 * <p>Shape filter (mirrors {@code StateDetector}): a state hierarchy is
 * any interface or abstract class whose name ends with {@code "State"};
 * a context is any concrete class (whose name does not end with
 * {@code "State"}) that holds a field of the state type.</p>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li><b>ERROR</b> — the state contract is a concrete class
 *       (no interface, not abstract, not sealed). State variation
 *       through inheritance only makes sense from an abstraction;
 *       a concrete class as the root locks the hierarchy.</li>
 *   <li><b>ERROR</b> — the context's state field is not {@code private}.
 *       The whole point of State is that transitions happen through
 *       the context's methods; a non-private state field lets callers
 *       reassign the state directly and bypass the transition logic.</li>
 *   <li><b>WARNING</b> — at least one concrete state implementation
 *       is not {@code final}. Deeper subclassing turns simple
 *       transitions into ambiguous "which subtype am I really in?"
 *       questions.</li>
 *   <li><b>INFO</b> — the context exposes no method that visibly
 *       changes the state (no public setter, no {@code transitionTo},
 *       no method that assigns to the state field). The class may
 *       still work via state objects calling back into the context,
 *       but a missing transition surface is worth flagging.</li>
 * </ul>
 */
public final class StateValidator implements PatternValidator {

    @Override
    public Pattern pattern() { return Pattern.STATE; }

    @Override
    public List<ValidationIssue> validate(CompilationUnit unit) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<ClassOrInterfaceDeclaration> all = unit.findAll(ClassOrInterfaceDeclaration.class);

        // 1) Find State-named candidates.
        List<ClassOrInterfaceDeclaration> stateRoots = new ArrayList<>();
        for (ClassOrInterfaceDeclaration t : all) {
            if (!t.getNameAsString().endsWith("State")) continue;
            stateRoots.add(t);
        }
        if (stateRoots.isEmpty()) return issues;

        for (ClassOrInterfaceDeclaration stateRoot : stateRoots) {
            String stateType = stateRoot.getNameAsString();
            int rootLine = stateRoot.getBegin().map(p -> p.line).orElse(-1);
            boolean isAbstraction = stateRoot.isInterface() || stateRoot.isAbstract();

            // Only flag the "concrete state contract" case when there are concrete
            // implementors / extenders in the file — otherwise *State is just a
            // value-object class name, not a State pattern artefact.
            boolean hasImplementors = all.stream()
                .filter(t -> t != stateRoot)
                .filter(t -> !t.isInterface() && !t.isAbstract())
                .anyMatch(t -> t.getImplementedTypes().stream()
                    .anyMatch(x -> x.getNameAsString().equals(stateType))
                    || t.getExtendedTypes().stream()
                    .anyMatch(x -> x.getNameAsString().equals(stateType)));

            // ─── ERROR: concrete State contract ────────────────────
            if (!isAbstraction && hasImplementors) {
                issues.add(new ValidationIssue(
                    Pattern.STATE, stateType, rootLine, Severity.ERROR,
                    "State contract " + stateType + " is a concrete class — " +
                    "variants cannot polymorphically replace it through clean inheritance.",
                    "Convert " + stateType + " to an interface (or a sealed / abstract class). " +
                    "States are the canonical case for sealed types in modern Java."
                ));
                continue; // remaining state-side checks don't apply
            }
            if (!isAbstraction) continue; // not a State pattern role

            // Collect concrete implementors.
            List<ClassOrInterfaceDeclaration> impls = new ArrayList<>();
            for (ClassOrInterfaceDeclaration t : all) {
                if (t == stateRoot) continue;
                if (t.isInterface() || t.isAbstract()) continue;
                boolean impl = t.getImplementedTypes().stream()
                    .anyMatch(x -> x.getNameAsString().equals(stateType))
                    || t.getExtendedTypes().stream()
                    .anyMatch(x -> x.getNameAsString().equals(stateType));
                if (impl) impls.add(t);
            }
            if (impls.isEmpty()) continue;

            // ─── WARNING: non-final concrete state ──────────────────
            for (ClassOrInterfaceDeclaration s : impls) {
                if (s.isFinal()) continue;
                int line = s.getBegin().map(p -> p.line).orElse(rootLine);
                issues.add(new ValidationIssue(
                    Pattern.STATE, s.getNameAsString(), line, Severity.WARNING,
                    "Concrete state " + s.getNameAsString() + " is not final — " +
                    "subclassing a state turns transitions into ambiguous type questions.",
                    "Mark " + s.getNameAsString() + " final unless you have a clear reason " +
                    "to allow further subclassing."
                ));
            }

            // Find a context — concrete class with a field of stateType,
            // name doesn't end with "State".
            ClassOrInterfaceDeclaration context = null;
            FieldDeclaration stateField = null;
            VariableDeclarator stateVar = null;
            for (ClassOrInterfaceDeclaration t : all) {
                if (t.isInterface() || t.isAbstract()) continue;
                if (t.getNameAsString().endsWith("State")) continue;
                for (FieldDeclaration f : t.getFields()) {
                    if (f.isStatic()) continue;
                    for (VariableDeclarator v : f.getVariables()) {
                        if (v.getType() instanceof ClassOrInterfaceType cit
                            && cit.getNameAsString().equals(stateType)) {
                            context = t;
                            stateField = f;
                            stateVar = v;
                            break;
                        }
                    }
                    if (context != null) break;
                }
                if (context != null) break;
            }
            if (context == null) continue;

            String contextName = context.getNameAsString();
            int contextLine = context.getBegin().map(p -> p.line).orElse(-1);

            // ─── ERROR: state field is not private ─────────────────
            if (!stateField.isPrivate()) {
                int line = stateField.getBegin().map(p -> p.line).orElse(contextLine);
                issues.add(new ValidationIssue(
                    Pattern.STATE, contextName, line, Severity.ERROR,
                    "Context " + contextName + " exposes state field '" + stateVar.getNameAsString() +
                    "' with non-private visibility — callers can bypass transitions and assign any " +
                    "state value directly.",
                    "Mark the state field private. Expose transitions only through explicit context " +
                    "methods so the State pattern's invariants are enforceable."
                ));
            }

            // ─── INFO: no observable transition surface on the context ─
            //     Heuristic: at least one of the context's non-static methods
            //     should either be a setter for the state field or contain an
            //     assignment to the state field. If none does, the only way
            //     for state to change is through a state object calling back
            //     into the context — possible, but worth a note.
            String stateFieldName = stateVar.getNameAsString();
            Set<String> stateFieldRefs = new HashSet<>();
            stateFieldRefs.add(stateFieldName);
            stateFieldRefs.add("this." + stateFieldName);
            boolean transitionSurface = context.getMethods().stream()
                .filter(m -> !m.isStatic())
                .anyMatch(m -> m.getBody().map(b -> {
                    String txt = b.toString();
                    return stateFieldRefs.stream().anyMatch(ref ->
                        txt.contains(ref + " =")
                            || txt.contains(ref + "="));
                }).orElse(false));
            if (!transitionSurface) {
                issues.add(new ValidationIssue(
                    Pattern.STATE, contextName, contextLine, Severity.INFO,
                    "Context " + contextName + " has no method that assigns to the '" +
                    stateFieldName + "' field — transitions are only possible via the state " +
                    "objects calling back into the context (or not at all).",
                    "Consider exposing an explicit transition method (e.g. " +
                    "'void transitionTo(" + stateType + " next)') so callers can see where the " +
                    "state changes happen."
                ));
            }
        }

        return issues;
    }
}
