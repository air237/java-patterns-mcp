package com.javapatterns.mcp.refactor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Wires the registered {@link PatternRefactoring}s together and parses
 * incoming sources with lexical preservation enabled so the printer can
 * keep all whitespace and comments untouched.
 */
public final class PatternRefactoringEngine {

    private static final class Holder {
        private static final PatternRefactoringEngine INSTANCE = new PatternRefactoringEngine();
    }

    public static PatternRefactoringEngine getInstance() {
        return Holder.INSTANCE;
    }

    private final JavaParser parser;
    private final Map<RefactoringId, PatternRefactoring> byId;

    private PatternRefactoringEngine() {
        ParserConfiguration cfg = new ParserConfiguration();
        cfg.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        this.parser = new JavaParser(cfg);

        this.byId = new EnumMap<>(RefactoringId.class);
        register(new SingletonMakeCtorPrivate());
        register(new SingletonAddHolderIdiom());
        register(new SingletonAddReadResolve());
        register(new BuilderMakeFieldsFinal());
        register(new ObserverSnapshotIteration());
        register(new AdapterMakeAdapteeFinal());
    }

    private void register(PatternRefactoring r) {
        if (byId.put(r.id(), r) != null) {
            throw new IllegalStateException("Duplicate refactoring registered: " + r.id());
        }
    }

    public Set<RefactoringId> supported() {
        return byId.keySet();
    }

    /**
     * Apply the given refactoring to a source string.
     *
     * @throws RefactoringException if the source does not parse, or the
     *         refactoring id is unknown, or the refactoring itself crashes.
     */
    public RefactoringResult apply(String source, RefactoringId id) {
        PatternRefactoring r = byId.get(id);
        if (r == null) {
            throw new RefactoringException("No refactoring registered for id " + id);
        }

        var result = parser.parse(source);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            throw new RefactoringException("Source failed to parse: " +
                result.getProblems().stream().map(Object::toString).toList());
        }
        CompilationUnit unit = result.getResult().get();
        LexicalPreservingPrinter.setup(unit);

        try {
            return r.apply(unit);
        } catch (RuntimeException e) {
            throw new RefactoringException(
                "Refactoring " + id.slug() + " crashed: " + e.getMessage(), e);
        }
    }

    public static final class RefactoringException extends RuntimeException {
        public RefactoringException(String msg) { super(msg); }
        public RefactoringException(String msg, Throwable cause) { super(msg, cause); }
    }
}
