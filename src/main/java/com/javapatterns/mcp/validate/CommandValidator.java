package com.javapatterns.mcp.validate;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates {@link Pattern#COMMAND} hierarchies.
 *
 * <p>Shape filter (mirrors {@code CommandDetector}): a Command contract
 * is any interface or abstract class that declares an {@code execute()}
 * method, with at least one concrete implementor in the same file.
 * Other types are ignored.</p>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li><b>ERROR</b> — the Command contract is a concrete class
 *       (no interface, not abstract). Commands have to be
 *       substitutable polymorphic objects; a concrete root locks
 *       callers to a single implementation.</li>
 *   <li><b>WARNING</b> — a concrete command class is not
 *       {@code final}. Subclassing a Command is almost always a
 *       sign that the wrong abstraction is being extended.</li>
 *   <li><b>WARNING</b> — a concrete command has no non-static
 *       fields. A stateless command should usually have been
 *       written as a lambda; if state really is needed, missing
 *       receiver / payload fields are a smell.</li>
 *   <li><b>INFO</b> — the Command contract does not declare
 *       {@code undo()}. Many Command implementations benefit from
 *       a paired undo for journalling / time-travel; adding it
 *       early is cheaper than retrofitting later.</li>
 * </ul>
 */
public final class CommandValidator implements PatternValidator {

    @Override
    public Pattern pattern() { return Pattern.COMMAND; }

    @Override
    public List<ValidationIssue> validate(CompilationUnit unit) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<ClassOrInterfaceDeclaration> all = unit.findAll(ClassOrInterfaceDeclaration.class);

        // Find Command contract candidates — declare execute().
        for (ClassOrInterfaceDeclaration cmd : all) {
            boolean hasExecute = cmd.getMethods().stream()
                .map(MethodDeclaration::getNameAsString)
                .anyMatch("execute"::equals);
            if (!hasExecute) continue;

            String cmdName = cmd.getNameAsString();
            int cmdLine = cmd.getBegin().map(p -> p.line).orElse(-1);
            boolean isAbstraction = cmd.isInterface() || cmd.isAbstract();

            // Find concrete implementors / extenders.
            List<ClassOrInterfaceDeclaration> impls = new ArrayList<>();
            for (ClassOrInterfaceDeclaration s : all) {
                if (s == cmd) continue;
                if (s.isInterface() || s.isAbstract()) continue;
                boolean impl = s.getImplementedTypes().stream()
                    .anyMatch(x -> x.getNameAsString().equals(cmdName))
                    || s.getExtendedTypes().stream()
                    .anyMatch(x -> x.getNameAsString().equals(cmdName));
                if (impl) impls.add(s);
            }
            if (impls.isEmpty()) continue;

            // ─── ERROR: concrete Command contract ──────────────────
            if (!isAbstraction) {
                issues.add(new ValidationIssue(
                    Pattern.COMMAND, cmdName, cmdLine, Severity.ERROR,
                    "Command contract " + cmdName + " is a concrete class — callers " +
                    "are locked to a single implementation and cannot substitute alternatives.",
                    "Convert " + cmdName + " to an interface (or abstract class) so " +
                    "alternative commands can be substituted polymorphically."
                ));
                continue; // remaining rules need an abstraction
            }

            // ─── WARNING: non-final concrete command ───────────────
            for (ClassOrInterfaceDeclaration s : impls) {
                if (s.isFinal()) continue;
                int line = s.getBegin().map(p -> p.line).orElse(cmdLine);
                issues.add(new ValidationIssue(
                    Pattern.COMMAND, s.getNameAsString(), line, Severity.WARNING,
                    "Concrete command " + s.getNameAsString() + " is not final — " +
                    "subclassing a command usually means the wrong abstraction is being extended.",
                    "Mark " + s.getNameAsString() + " final unless you have a clear reason " +
                    "to allow further subclassing."
                ));
            }

            // ─── WARNING: concrete command has no instance fields ──
            //     A stateless command is usually best expressed as a
            //     lambda implementing the Command interface; a class
            //     with no fields and a non-trivial execute() body is
            //     a smell.
            for (ClassOrInterfaceDeclaration s : impls) {
                boolean hasInstanceField = s.getFields().stream()
                    .anyMatch(f -> !f.isStatic());
                if (hasInstanceField) continue;
                int line = s.getBegin().map(p -> p.line).orElse(cmdLine);
                issues.add(new ValidationIssue(
                    Pattern.COMMAND, s.getNameAsString(), line, Severity.WARNING,
                    "Concrete command " + s.getNameAsString() + " has no instance fields — " +
                    "a stateless command would usually be a lambda, not a class.",
                    "Either capture the receiver / arguments in private final fields, " +
                    "or replace the class with a lambda: '" + cmdName + " x = () -> { ... };'."
                ));
            }

            // ─── INFO: contract has no undo() method ───────────────
            boolean hasUndo = cmd.getMethods().stream()
                .map(MethodDeclaration::getNameAsString)
                .anyMatch("undo"::equals);
            if (!hasUndo) {
                issues.add(new ValidationIssue(
                    Pattern.COMMAND, cmdName, cmdLine, Severity.INFO,
                    "Command contract " + cmdName + " does not declare undo() — " +
                    "the pattern is most powerful when commands are reversible.",
                    "Consider adding 'String undo();' (or similar) to " + cmdName + " so " +
                    "callers can support journalling, time-travel and undo/redo on top of " +
                    "the same command object."
                ));
            }
        }
        return issues;
    }
}
