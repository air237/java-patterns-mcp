package com.javapatterns.mcp.validate;

import com.javapatterns.mcp.catalog.Pattern;

import java.util.Objects;

/**
 * A single finding from validating a pattern instance.
 *
 * @param pattern      which pattern was being validated
 * @param className    the anchor class the issue is attached to
 * @param line         line number (1-based; -1 if unknown)
 * @param severity     see {@link Severity}
 * @param issue        short, one-sentence description of the problem
 * @param suggestion   actionable fix; never null but may be empty
 */
public record ValidationIssue(
    Pattern pattern,
    String className,
    int line,
    Severity severity,
    String issue,
    String suggestion
) {
    public ValidationIssue {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(issue, "issue");
        suggestion = suggestion == null ? "" : suggestion;
        if (issue.isBlank()) throw new IllegalArgumentException("issue must be non-blank");
    }
}
