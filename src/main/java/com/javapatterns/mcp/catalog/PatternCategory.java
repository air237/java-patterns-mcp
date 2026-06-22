package com.javapatterns.mcp.catalog;

/**
 * Top-level taxonomy of the 23 Gang of Four design patterns, as classified
 * in the original 1994 GoF book and adopted by
 * <a href="https://refactoring.guru/design-patterns/classification">refactoring.guru</a>.
 *
 * <ul>
 *   <li><b>Creational</b> — patterns that abstract or hide the instantiation
 *       process so the system is independent of how its objects are created,
 *       composed, and represented.</li>
 *   <li><b>Structural</b> — patterns that compose classes and objects into
 *       larger structures while keeping these structures flexible and
 *       efficient.</li>
 *   <li><b>Behavioral</b> — patterns that focus on algorithms and the
 *       assignment of responsibilities between objects.</li>
 * </ul>
 */
public enum PatternCategory {

    CREATIONAL("Creational", "creational-patterns"),
    STRUCTURAL("Structural", "structural-patterns"),
    BEHAVIORAL("Behavioral", "behavioral-patterns");

    private final String displayName;
    private final String slug;

    PatternCategory(String displayName, String slug) {
        this.displayName = displayName;
        this.slug = slug;
    }

    /** Human-readable category label, e.g. "Creational". */
    public String displayName() {
        return displayName;
    }

    /** URL slug used on refactoring.guru, e.g. "creational-patterns". */
    public String slug() {
        return slug;
    }
}
