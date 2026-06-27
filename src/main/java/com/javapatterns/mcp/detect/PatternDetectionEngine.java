package com.javapatterns.mcp.detect;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Wires the registered {@link PatternDetector}s together. One detector per
 * pattern; patterns without a detector are simply absent from the results
 * (callers can query {@link #supportedPatterns()} to find out).
 *
 * <p>The engine is stateless and safe to reuse across calls; the underlying
 * {@link JavaParser} is configured once with Java 21 source level.</p>
 *
 * <p>Two analysis modes are offered:</p>
 * <ul>
 *   <li>{@link #detect(String)} — single compilation unit, throws on parse
 *       failure. Use this for ad-hoc / single-file analysis.</li>
 *   <li>{@link #detectAll(Map)} — many compilation units in one call, per-file
 *       errors are collected (never thrown) so a single bad file does not
 *       sink a batch. Use this for directory- / project-wide scans.</li>
 * </ul>
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
            new CommandDetector(),
            new AbstractFactoryDetector(),
            new BridgeDetector(),
            new FacadeDetector(),
            new VisitorDetector(),
            new ChainOfResponsibilityDetector(),
            new MediatorDetector()
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

        List<DetectedPattern> all = runDetectors(unit, "<source>");
        all.sort((a, b) -> {
            int byPattern = a.pattern().compareTo(b.pattern());
            return byPattern != 0 ? byPattern : Integer.compare(a.startLine(), b.startLine());
        });
        return all;
    }

    /**
     * Parse and detect patterns across many Java sources in one call. A file
     * that fails to parse contributes a {@link FileError} to the result but
     * does NOT abort the batch. Detections are sorted by file label, then
     * pattern, then start line.
     *
     * @param sourcesByLabel ordered map of {@code label → java-source}. The
     *                       label is whatever the caller wants to use to
     *                       identify the file in the output (usually a path).
     *                       Must not be {@code null}; may be empty (returns
     *                       an empty result).
     * @return aggregated result containing every detection and every parse
     *         error encountered.
     */
    public BatchResult detectAll(Map<String, String> sourcesByLabel) {
        Objects.requireNonNull(sourcesByLabel, "sourcesByLabel");

        List<FileDetection> detections = new ArrayList<>();
        List<FileError> errors = new ArrayList<>();
        int analyzed = 0;

        for (Map.Entry<String, String> e : sourcesByLabel.entrySet()) {
            String label = e.getKey();
            String source = e.getValue();
            if (source == null) {
                errors.add(new FileError(label, "source content is null"));
                continue;
            }
            var result = parser.parse(source);
            if (!result.isSuccessful() || result.getResult().isEmpty()) {
                String msg = result.getProblems().stream()
                    .map(Object::toString)
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("unknown parse error");
                errors.add(new FileError(label, msg));
                continue;
            }
            analyzed++;
            try {
                for (DetectedPattern d : runDetectors(result.getResult().get(), label)) {
                    detections.add(new FileDetection(label, d));
                }
            } catch (DetectionException ex) {
                // A buggy detector inside ONE file should not kill the batch.
                errors.add(new FileError(label, ex.getMessage()));
            }
        }

        detections.sort((a, b) -> {
            int byFile = a.file().compareTo(b.file());
            if (byFile != 0) return byFile;
            int byPattern = a.detection().pattern().compareTo(b.detection().pattern());
            if (byPattern != 0) return byPattern;
            return Integer.compare(a.detection().startLine(), b.detection().startLine());
        });

        return new BatchResult(
            List.copyOf(detections),
            List.copyOf(errors),
            analyzed);
    }

    private List<DetectedPattern> runDetectors(CompilationUnit unit, String label) {
        List<DetectedPattern> hits = new ArrayList<>();
        for (PatternDetector d : detectors) {
            try {
                hits.addAll(d.detect(unit));
            } catch (RuntimeException e) {
                throw new DetectionException(
                    "Detector " + d.getClass().getSimpleName()
                    + " crashed on " + label + ": " + e.getMessage(), e);
            }
        }
        return hits;
    }

    /** A single detection together with the file label it came from. */
    public record FileDetection(String file, DetectedPattern detection) {
        public FileDetection {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(detection, "detection");
        }
    }

    /** A per-file failure (parse error or detector crash). */
    public record FileError(String file, String message) {
        public FileError {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(message, "message");
        }
    }

    /**
     * Aggregated outcome of {@link #detectAll(Map)}.
     *
     * @param detections    all detections across every successfully parsed file
     * @param errors        per-file failures (each entry is one file)
     * @param filesAnalyzed number of files that parsed successfully and were
     *                      handed to the detectors
     */
    public record BatchResult(
        List<FileDetection> detections,
        List<FileError> errors,
        int filesAnalyzed
    ) {
        public BatchResult {
            detections = detections == null ? List.of() : List.copyOf(detections);
            errors = errors == null ? List.of() : List.copyOf(errors);
        }

        /** Convenience: empty result. */
        public static BatchResult empty() {
            return new BatchResult(List.of(), List.of(), 0);
        }
    }

    /** Failure during analysis — usually unparseable input. */
    public static final class DetectionException extends RuntimeException {
        public DetectionException(String msg) { super(msg); }
        public DetectionException(String msg, Throwable cause) { super(msg, cause); }
    }
}
