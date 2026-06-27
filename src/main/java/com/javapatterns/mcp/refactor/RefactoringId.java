package com.javapatterns.mcp.refactor;

import com.javapatterns.mcp.catalog.Pattern;

/**
 * Catalogue of the refactorings supported by this build. Each refactoring
 * is a small, idempotent, AST-level transformation tied to one design
 * pattern. The {@code slug} is the public identifier callers pass to the
 * MCP tool.
 */
public enum RefactoringId {

    SINGLETON_MAKE_CTOR_PRIVATE(
        Pattern.SINGLETON, "singleton-make-ctor-private",
        "Turn a public constructor of a Singleton-shaped class into a private constructor."),
    SINGLETON_ADD_HOLDER_IDIOM(
        Pattern.SINGLETON, "singleton-add-holder-idiom",
        "Replace an uncached getInstance() with the Bill-Pugh holder idiom."),
    SINGLETON_ADD_READ_RESOLVE(
        Pattern.SINGLETON, "singleton-add-read-resolve",
        "Add a private Object readResolve() to a Serializable Singleton."),
    BUILDER_MAKE_FIELDS_FINAL(
        Pattern.BUILDER, "builder-make-fields-final",
        "Mark every non-final instance field of the Builder's outer class as final."),
    OBSERVER_SNAPSHOT_ITERATION(
        Pattern.OBSERVER, "observer-snapshot-iteration",
        "Wrap the iterated collection inside a publish-like method with List.copyOf(...)."),
    ADAPTER_MAKE_ADAPTEE_FINAL(
        Pattern.ADAPTER, "adapter-make-adaptee-final",
        "Mark the adaptee field of an Adapter-shaped class as final."),
    TEMPLATE_METHOD_MAKE_FINAL(
        Pattern.TEMPLATE_METHOD, "template-method-make-final",
        "Mark the template method of a Template-Method-shaped abstract class as final."),
    FACTORY_METHOD_RESTRICT_CREATOR_CTOR(
        Pattern.FACTORY_METHOD, "factory-method-restrict-creator-ctor",
        "Demote public constructors of a concrete Creator (Factory-Method-shaped class) "
        + "to protected so callers cannot bypass the factory method."),
    STRATEGY_ADD_FUNCTIONAL_INTERFACE(
        Pattern.STRATEGY, "strategy-add-functional-interface",
        "Annotate a single-method Strategy interface with @FunctionalInterface "
        + "so the compiler protects the SAM contract."),
    DECORATOR_MAKE_WRAPPED_FINAL(
        Pattern.DECORATOR, "decorator-make-wrapped-final",
        "Mark the wrapped (delegate) field of a Decorator-shaped class as final."),
    STATE_MAKE_IMPLEMENTATIONS_FINAL(
        Pattern.STATE, "state-make-implementations-final",
        "Mark every concrete implementor of a State hierarchy as final."),
    COMMAND_MAKE_IMPLEMENTATIONS_FINAL(
        Pattern.COMMAND, "command-make-implementations-final",
        "Mark every concrete implementor of a Command contract as final.");

    private final Pattern pattern;
    private final String slug;
    private final String description;

    RefactoringId(Pattern pattern, String slug, String description) {
        this.pattern = pattern;
        this.slug = slug;
        this.description = description;
    }

    public Pattern pattern()      { return pattern; }
    public String slug()          { return slug; }
    public String description()   { return description; }

    /** Look up by slug (the public identifier) or by enum name. */
    public static RefactoringId fromKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Refactoring id must be non-blank");
        }
        String norm = key.trim();
        for (RefactoringId r : values()) {
            if (r.slug.equalsIgnoreCase(norm) || r.name().equalsIgnoreCase(norm)) return r;
        }
        throw new IllegalArgumentException("Unknown refactoring id: '" + key + "'");
    }
}
