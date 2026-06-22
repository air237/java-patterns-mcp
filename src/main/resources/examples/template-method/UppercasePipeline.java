package com.javapatterns.examples.template_method;

/** Concrete subclass — implements only the variable steps. */
public final class UppercasePipeline extends DataPipeline {

    @Override
    protected String parse(String input) {
        return input.trim();
    }

    @Override
    protected String transform(String parsed) {
        return parsed.toUpperCase(java.util.Locale.ROOT);
    }
}
