package com.javapatterns.examples.flyweight;

import java.util.HashMap;
import java.util.Map;

/** Flyweight factory — returns a shared {@link TreeType} per (name, texture, color) key. */
public final class TreeTypeFactory {

    private static final Map<String, TreeType> CACHE = new HashMap<>();

    private TreeTypeFactory() { /* utility */ }

    public static TreeType get(String name, String texture, String color) {
        String key = name + "|" + texture + "|" + color;
        return CACHE.computeIfAbsent(key, k -> new TreeType(name, texture, color));
    }

    public static int distinctTypesCreated() {
        return CACHE.size();
    }
}
