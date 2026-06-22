package com.javapatterns.examples.interpreter;

import java.util.HashMap;
import java.util.Map;

/** Shared evaluation context — variable bindings for the AST. */
public final class Context {

    private final Map<String, Boolean> flags = new HashMap<>();

    public Context set(String name, boolean value) {
        flags.put(name, value);
        return this;
    }

    public boolean get(String name) {
        Boolean v = flags.get(name);
        if (v == null) throw new IllegalStateException("Undefined variable: " + name);
        return v;
    }
}
