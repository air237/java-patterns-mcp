package com.javapatterns.examples.template_method;

/**
 * Template Method pattern — define the skeleton of an algorithm in the
 * superclass and let subclasses override specific steps without changing
 * the overall structure.
 *
 * <p>{@link #process(String)} is the template: it is {@code final} so
 * subclasses cannot rearrange the steps. The individual steps —
 * {@link #parse(String)}, {@link #transform(String)},
 * {@link #emit(String)} — are open for override.</p>
 */
public abstract class DataPipeline {

    /** The template method — locked algorithm structure. */
    public final String process(String input) {
        String parsed = parse(input);
        String transformed = transform(parsed);
        return emit(transformed);
    }

    protected abstract String parse(String input);
    protected abstract String transform(String parsed);

    /** Hook with a sensible default — subclasses may override. */
    protected String emit(String transformed) {
        return "[" + transformed + "]";
    }
}
