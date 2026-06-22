package com.javapatterns.mcp.validate;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Runs every registered {@link PatternValidator} on a parsed source.
 *
 * <p>Holder-idiom singleton; thread-safe; the underlying
 * {@link JavaParser} is configured once for Java 21.</p>
 */
public final class PatternValidationEngine {

    private static final class Holder {
        private static final PatternValidationEngine INSTANCE = new PatternValidationEngine();
    }

    public static PatternValidationEngine getInstance() {
        return Holder.INSTANCE;
    }

    private final JavaParser parser;
    private final List<PatternValidator> validators;

    private PatternValidationEngine() {
        ParserConfiguration cfg = new ParserConfiguration();
        cfg.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        this.parser = new JavaParser(cfg);
        this.validators = List.of(
            new SingletonValidator(),
            new BuilderValidator(),
            new ObserverValidator()
        );
    }

    /** Patterns this engine can validate (subset of all detected patterns). */
    public Set<Pattern> supportedPatterns() {
        EnumSet<Pattern> set = EnumSet.noneOf(Pattern.class);
        for (PatternValidator v : validators) set.add(v.pattern());
        return set;
    }

    /**
     * Validate every supported pattern in the source.
     *
     * @param source the Java source to scrutinise
     * @return all issues found, in pattern-then-line order
     * @throws ValidationException if the source does not parse
     */
    public List<ValidationIssue> validateAll(String source) {
        CompilationUnit unit = parse(source);
        List<ValidationIssue> all = new ArrayList<>();
        for (PatternValidator v : validators) {
            try {
                all.addAll(v.validate(unit));
            } catch (RuntimeException e) {
                throw new ValidationException(
                    "Validator " + v.getClass().getSimpleName() + " crashed: " + e.getMessage(), e);
            }
        }
        all.sort((a, b) -> {
            int byPattern = a.pattern().compareTo(b.pattern());
            if (byPattern != 0) return byPattern;
            int bySeverity = a.severity().compareTo(b.severity()); // ERROR < WARNING < INFO
            if (bySeverity != 0) return bySeverity;
            return Integer.compare(a.line(), b.line());
        });
        return all;
    }

    /**
     * Validate only a specific pattern. Useful when the caller already
     * knows what they're looking for and wants a focused report.
     */
    public List<ValidationIssue> validateOne(String source, Pattern pattern) {
        if (!supportedPatterns().contains(pattern)) {
            throw new IllegalArgumentException(
                "No validator wired for pattern " + pattern.name() +
                ". Supported: " + supportedPatterns());
        }
        CompilationUnit unit = parse(source);
        for (PatternValidator v : validators) {
            if (v.pattern() == pattern) {
                return v.validate(unit);
            }
        }
        return List.of(); // unreachable
    }

    private CompilationUnit parse(String source) {
        var result = parser.parse(source);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            throw new ValidationException("Source failed to parse: " +
                result.getProblems().stream().map(Object::toString).toList());
        }
        return result.getResult().get();
    }

    public static final class ValidationException extends RuntimeException {
        public ValidationException(String msg) { super(msg); }
        public ValidationException(String msg, Throwable cause) { super(msg, cause); }
    }
}
