package com.javapatterns.mcp.catalog;

/**
 * Immutable metadata for a single design pattern, loaded from the
 * {@code /catalog/patterns.json} resource at startup.
 *
 * <p>Fields:</p>
 * <ul>
 *   <li>{@code pattern} — the enum constant.</li>
 *   <li>{@code intent} — one-sentence purpose statement from the GoF book /
 *       refactoring.guru.</li>
 *   <li>{@code problem} — short description of the design problem this pattern
 *       solves (1–2 sentences, plain English).</li>
 *   <li>{@code aliases} — alternative names used in industry, e.g. Singleton ↔
 *       "Holder pattern", Observer ↔ "Publish-Subscribe".</li>
 * </ul>
 *
 * <p>{@link Pattern#displayName()}, {@link Pattern#category()},
 * {@link Pattern#slug()}, and {@link Pattern#referenceUrl()} are intentionally
 * not duplicated here — they live on the enum itself and stay the single
 * source of truth.</p>
 */
public record PatternMetadata(
    Pattern pattern,
    String intent,
    String problem,
    java.util.List<String> aliases
) {
    public PatternMetadata {
        java.util.Objects.requireNonNull(pattern, "pattern");
        java.util.Objects.requireNonNull(intent, "intent");
        java.util.Objects.requireNonNull(problem, "problem");
        aliases = aliases == null ? java.util.List.of() : java.util.List.copyOf(aliases);
    }
}
