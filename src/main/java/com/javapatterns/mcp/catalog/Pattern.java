package com.javapatterns.mcp.catalog;

import java.util.Locale;

import static com.javapatterns.mcp.catalog.PatternCategory.BEHAVIORAL;
import static com.javapatterns.mcp.catalog.PatternCategory.CREATIONAL;
import static com.javapatterns.mcp.catalog.PatternCategory.STRUCTURAL;

/**
 * Enumeration of the 23 Gang of Four design patterns, with category and the
 * {@code refactoring.guru} URL slug for each.
 *
 * <p>This enum is intentionally pure metadata — no behaviour, no examples,
 * no implementation. The richer data (intent, problem hint, applicability)
 * lives in {@code /resources/catalog/patterns.json} and is loaded lazily by
 * {@link PatternRegistry}.</p>
 *
 * <p>The pattern ordering follows refactoring.guru:
 * Creational (5), Structural (7), Behavioral (11). Total: 23.</p>
 */
public enum Pattern {

    // ───── Creational ────────────────────────────────────────────────
    ABSTRACT_FACTORY    (CREATIONAL, "Abstract Factory",        "abstract-factory"),
    BUILDER             (CREATIONAL, "Builder",                 "builder"),
    FACTORY_METHOD      (CREATIONAL, "Factory Method",          "factory-method"),
    PROTOTYPE           (CREATIONAL, "Prototype",               "prototype"),
    SINGLETON           (CREATIONAL, "Singleton",               "singleton"),

    // ───── Structural ───────────────────────────────────────────────
    ADAPTER             (STRUCTURAL, "Adapter",                 "adapter"),
    BRIDGE              (STRUCTURAL, "Bridge",                  "bridge"),
    COMPOSITE           (STRUCTURAL, "Composite",               "composite"),
    DECORATOR           (STRUCTURAL, "Decorator",               "decorator"),
    FACADE              (STRUCTURAL, "Facade",                  "facade"),
    FLYWEIGHT           (STRUCTURAL, "Flyweight",               "flyweight"),
    PROXY               (STRUCTURAL, "Proxy",                   "proxy"),

    // ───── Behavioral ───────────────────────────────────────────────
    CHAIN_OF_RESPONSIBILITY (BEHAVIORAL, "Chain of Responsibility", "chain-of-responsibility"),
    COMMAND             (BEHAVIORAL, "Command",                 "command"),
    INTERPRETER         (BEHAVIORAL, "Interpreter",             "interpreter"),
    ITERATOR            (BEHAVIORAL, "Iterator",                "iterator"),
    MEDIATOR            (BEHAVIORAL, "Mediator",                "mediator"),
    MEMENTO             (BEHAVIORAL, "Memento",                 "memento"),
    OBSERVER            (BEHAVIORAL, "Observer",                "observer"),
    STATE               (BEHAVIORAL, "State",                   "state"),
    STRATEGY            (BEHAVIORAL, "Strategy",                "strategy"),
    TEMPLATE_METHOD     (BEHAVIORAL, "Template Method",         "template-method"),
    VISITOR             (BEHAVIORAL, "Visitor",                 "visitor");

    /** refactoring.guru base URL — the canonical reference for every pattern. */
    public static final String REFACTORING_GURU_BASE = "https://refactoring.guru/design-patterns/";

    private final PatternCategory category;
    private final String displayName;
    private final String slug;

    Pattern(PatternCategory category, String displayName, String slug) {
        this.category = category;
        this.displayName = displayName;
        this.slug = slug;
    }

    public PatternCategory category() { return category; }

    /** Human-readable name, e.g. {@code "Chain of Responsibility"}. */
    public String displayName() { return displayName; }

    /** URL slug used on refactoring.guru, e.g. {@code "chain-of-responsibility"}. */
    public String slug() { return slug; }

    /** Full URL of the main article on refactoring.guru. */
    public String referenceUrl() {
        return REFACTORING_GURU_BASE + slug;
    }

    /**
     * Resolve a Pattern from a free-form identifier. Accepts the enum name,
     * the slug, or the display name (case-insensitive). Useful for tool input
     * normalization.
     *
     * @param key identifier such as {@code "singleton"}, {@code "Singleton"},
     *            {@code "SINGLETON"}, {@code "Chain of Responsibility"},
     *            {@code "chain-of-responsibility"}, or {@code "CHAIN_OF_RESPONSIBILITY"}.
     * @return the matching pattern.
     * @throws IllegalArgumentException if no pattern matches.
     */
    public static Pattern fromKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Pattern key must be non-blank");
        }
        String normalized = key.trim();
        // 1) enum name match (case-insensitive)
        for (Pattern p : values()) {
            if (p.name().equalsIgnoreCase(normalized)) return p;
        }
        // 2) slug match
        String slugCandidate = normalized.toLowerCase(Locale.ROOT).replace(' ', '-').replace('_', '-');
        for (Pattern p : values()) {
            if (p.slug.equalsIgnoreCase(slugCandidate)) return p;
        }
        // 3) display name match (case-insensitive)
        for (Pattern p : values()) {
            if (p.displayName.equalsIgnoreCase(normalized)) return p;
        }
        throw new IllegalArgumentException("Unknown design pattern: '" + key + "'");
    }
}
