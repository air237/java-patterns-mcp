package com.javapatterns.mcp.detect;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Wires the registered {@link PatternDetector}s together. One detector per
 * pattern; patterns without a detector are simply absent from the results
 * (callers can query {@link #supportedPatterns()} to find out).
 *
 * <p>The engine is stateless and safe to reuse across calls; the underlying
 * {@link JavaParser} is configured once with Java 21 source level.</p>
 */
public final class PatternDetectionEngine {

    private static final class Holder {
        private static final PatternDetectionEngine INSTANCE = new PatternDetectionEngine();
    }

    public static PatternDetectionEngine getInstance() {
        return Holder.INSTANCE;
    }

    private final JavaParser parser;
    private final List<PatternDetector> detectors;

    private PatternDetectionEngine() {
        ParserConfiguration cfg = new ParserConfiguration();
        cfg.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        this.parser = new JavaParser(cfg);
        this.detectors = List.of(
            new SingletonDetector(),
            new BuilderDetector(),
            new FactoryMethodDetector(),
            new StrategyDetector(),
            new ObserverDetector(),
            new CompositeDetector(),
            new AdapterDetector(),
            new DecoratorDetector(),
            new ProxyDetector(),
            new TemplateMethodDetector(),
            new StateDetector(),
            new CommandDetector()
        );
    }

    /** Set of patterns the engine has a detector for. */
    public Set<Pattern> supportedPatterns() {
        EnumSet<Pattern> set = EnumSet.noneOf(Pattern.class);
        for (PatternDetector d : detectors) set.add(d.pattern());
        return set;
    }

    /**
     * Parse and detect patterns in a single Java source file.
     *
     * @param source the file content as a string
     * @return all detections found, sorted by (pattern declaration order, line).
     * @throws DetectionException if the source does not parse.
     */
    public List<DetectedPattern> detect(String source) {
        var result = parser.parse(source);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            throw new DetectionException("Source failed to parse: " +
                result.getProblems().stream().map(Object::toString).toList());
        }
        CompilationUnit unit = result.getResult().get();

        List<DetectedPattern> all = new ArrayList<>();
        for (PatternDetector d : detectors) {
            try {
                all.addAll(d.detect(unit));
            } catch (RuntimeException e) {
                // A buggy detector must not bring down the whole analysis.
                // Bubble up only the offending detector's identity.
                throw new DetectionException(
                    "Detector " + d.getClass().getSimpleName() + " crashed: " + e.getMessage(), e);
            }
        }
        all.sort((a, b) -> {
            int byPattern = a.pattern().compareTo(b.pattern());
            return byPattern != 0 ? byPattern : Integer.compare(a.startLine(), b.startLine());
        });
        return all;
    }

    /** Failure during analysis — usually unparseable input. */
    public static final class DetectionException extends RuntimeException {
        public DetectionException(String msg) { super(msg); }
        public DetectionException(String msg, Throwable cause) { super(msg, cause); }
    }
}
