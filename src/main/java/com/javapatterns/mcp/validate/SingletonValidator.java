package com.javapatterns.mcp.validate;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates {@link Pattern#SINGLETON} implementations.
 *
 * <p>Rules:</p>
 * <ul>
 *   <li><b>ERROR</b> — class has a {@code public} constructor (defeats
 *       the pattern; anyone can {@code new} a second instance).</li>
 *   <li><b>ERROR</b> — eager double-init: a public static method that
 *       {@code new}-s a fresh instance every call (no holder, no
 *       memoised field).</li>
 *   <li><b>WARNING</b> — class implements {@code Serializable} but
 *       has no {@code readResolve()} method — deserialisation will
 *       create a second instance, breaking the singleton invariant.</li>
 *   <li><b>WARNING</b> — direct lazy init without {@code synchronized},
 *       {@code volatile}, an inner holder class, or {@code enum} —
 *       race condition under contention.</li>
 *   <li><b>INFO</b> — reflective constructor protection missing: a
 *       private constructor that does not throw on second invocation
 *       can still be bypassed via {@code setAccessible(true)}.</li>
 * </ul>
 */
public final class SingletonValidator implements PatternValidator {

    @Override
    public Pattern pattern() { return Pattern.SINGLETON; }

    @Override
    public List<ValidationIssue> validate(CompilationUnit unit) {
        List<ValidationIssue> issues = new ArrayList<>();
        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (cls.isInterface()) return;
            String name = cls.getNameAsString();
            int line = cls.getBegin().map(p -> p.line).orElse(-1);

            // We only validate types that LOOK like a Singleton (have at
            // least a private ctor or a getInstance method) — otherwise
            // every regular class would generate noise.
            boolean privateCtor = cls.getConstructors().stream()
                .anyMatch(ConstructorDeclaration::isPrivate);
            boolean hasGetInstance = cls.getMethods().stream()
                .anyMatch(m -> m.isStatic() && m.getNameAsString().equals("getInstance"));
            if (!privateCtor && !hasGetInstance) return;

            // ─── ERROR: public constructor present ───────────────
            cls.getConstructors().stream()
                .filter(ConstructorDeclaration::isPublic)
                .findFirst()
                .ifPresent(c -> issues.add(new ValidationIssue(
                    Pattern.SINGLETON, name,
                    c.getBegin().map(p -> p.line).orElse(line),
                    Severity.ERROR,
                    "Singleton declares a public constructor; callers can bypass the singleton.",
                    "Make the constructor private."
                )));

            // ─── ERROR: getInstance() returns 'new ClassName()' ───
            //     with no static field memoising the instance.
            boolean hasInstanceField = cls.getFields().stream()
                .filter(FieldDeclaration::isStatic)
                .flatMap(f -> f.getVariables().stream())
                .anyMatch(v -> isOfType(v, name));
            boolean hasHolder = cls.getMembers().stream()
                .filter(m -> m instanceof ClassOrInterfaceDeclaration)
                .map(m -> (ClassOrInterfaceDeclaration) m)
                .filter(c -> c.isStatic() && !c.isInterface())
                .anyMatch(c -> c.getFields().stream()
                    .flatMap(f -> f.getVariables().stream())
                    .anyMatch(v -> isOfType(v, name)));
            if (hasGetInstance && !hasInstanceField && !hasHolder) {
                cls.getMethods().stream()
                    .filter(m -> m.isStatic() && m.getNameAsString().equals("getInstance"))
                    .findFirst()
                    .ifPresent(m -> issues.add(new ValidationIssue(
                        Pattern.SINGLETON, name,
                        m.getBegin().map(p -> p.line).orElse(line),
                        Severity.ERROR,
                        "getInstance() has neither a static instance field nor a holder " +
                        "class — every call probably constructs a new object.",
                        "Cache the instance in a private static (volatile) field, or use the " +
                        "Bill-Pugh holder idiom: a private static nested class holding " +
                        "private static final " + name + " INSTANCE = new " + name + "()."
                    )));
            }

            // ─── WARNING: Serializable without readResolve() ─────
            boolean isSerializable = cls.getImplementedTypes().stream()
                .anyMatch(t -> t.getNameAsString().equals("Serializable"));
            boolean hasReadResolve = cls.getMethods().stream()
                .anyMatch(m -> m.getNameAsString().equals("readResolve"));
            if (isSerializable && !hasReadResolve) {
                issues.add(new ValidationIssue(
                    Pattern.SINGLETON, name, line, Severity.WARNING,
                    "Singleton implements Serializable but defines no readResolve() — " +
                    "deserialisation creates a second instance.",
                    "Add private Object readResolve() { return getInstance(); }, or do not " +
                    "make the singleton Serializable. Consider using an enum singleton instead."
                ));
            }

            // ─── WARNING: direct lazy init without synchronisation ─
            //     (signal: an instance field of the same type that is NOT
            //      assigned at declaration AND no holder class is present)
            boolean lazyMutableField = cls.getFields().stream()
                .filter(FieldDeclaration::isStatic)
                .filter(f -> !f.isFinal())
                .flatMap(f -> f.getVariables().stream())
                .anyMatch(v -> isOfType(v, name) && v.getInitializer().isEmpty());
            if (lazyMutableField && !hasHolder) {
                boolean hasSyncOrVolatile = cls.getFields().stream()
                    .filter(FieldDeclaration::isStatic)
                    .anyMatch(FieldDeclaration::isVolatile)
                    || cls.getMethods().stream()
                    .filter(m -> m.getNameAsString().equals("getInstance"))
                    .anyMatch(MethodDeclaration::isSynchronized);
                if (!hasSyncOrVolatile) {
                    issues.add(new ValidationIssue(
                        Pattern.SINGLETON, name, line, Severity.WARNING,
                        "Lazy Singleton without volatile / synchronized / holder idiom — " +
                        "two threads can each see INSTANCE == null and construct twice.",
                        "Use the Bill-Pugh holder idiom (private static nested class) or " +
                        "mark the field volatile with a double-checked-locking getInstance()."
                    ));
                }
            }

            // ─── INFO: reflective protection missing ─────────────
            cls.getConstructors().stream()
                .filter(ConstructorDeclaration::isPrivate)
                .findFirst()
                .ifPresent(c -> {
                    boolean throwsOnReentry = c.getBody().toString().contains("IllegalStateException")
                        || c.getBody().toString().contains("throw new");
                    if (!throwsOnReentry) {
                        issues.add(new ValidationIssue(
                            Pattern.SINGLETON, name,
                            c.getBegin().map(p -> p.line).orElse(line),
                            Severity.INFO,
                            "Private constructor does not defend against reflective re-entry — " +
                            "setAccessible(true) can still construct a second instance.",
                            "Throw an IllegalStateException in the constructor if the singleton " +
                            "is already constructed (check the holder field), or use an enum singleton."
                        ));
                    }
                });
        });
        return issues;
    }

    private static boolean isOfType(VariableDeclarator v, String typeName) {
        return v.getType() instanceof ClassOrInterfaceType cit
            && cit.getNameAsString().equals(typeName);
    }
}
