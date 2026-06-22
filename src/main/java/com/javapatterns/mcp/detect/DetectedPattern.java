package com.javapatterns.mcp.detect;

import com.javapatterns.mcp.catalog.Pattern;

import java.util.List;
import java.util.Objects;

/**
 * The result of detecting a single pattern instance in a Java source file.
 *
 * @param pattern       which GoF pattern was recognised
 * @param className     the fully-qualified or simple class name where the
 *                      instance lives (the "anchor" class of the pattern —
 *                      e.g. for Singleton, the class with the private ctor;
 *                      for Builder, the outer immutable type)
 * @param startLine     first line of the anchor class (1-based, inclusive)
 * @param confidence    0.0 (very weak signal) to 1.0 (textbook match);
 *                      each detector documents how it sums up sub-signals
 * @param evidence      human-readable list of the signals that fired
 *                      (e.g. "private constructor", "static INSTANCE field",
 *                       "getInstance() returns the holder")
 */
public record DetectedPattern(
    Pattern pattern,
    String className,
    int startLine,
    double confidence,
    List<String> evidence
) {
    public DetectedPattern {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(className, "className");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be in [0,1], got " + confidence);
        }
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
