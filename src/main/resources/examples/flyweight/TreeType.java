package com.javapatterns.examples.flyweight;

/**
 * Flyweight pattern — share fine-grained immutable state across many
 * logical instances to keep memory bounded.
 *
 * <p>Imagine rendering 100 000 trees in a forest game. Without flyweight
 * you would have 100 000 {@code Tree} objects each carrying its sprite,
 * texture and species name. With flyweight, the per-species data lives in
 * a single shared {@link TreeType} and each tree only stores its
 * extrinsic state (x, y).</p>
 */
public final class TreeType {

    private final String name;
    private final String texture;
    private final String color;

    /** Package-private — only {@link TreeTypeFactory} creates these. */
    TreeType(String name, String texture, String color) {
        this.name = name;
        this.texture = texture;
        this.color = color;
    }

    public String draw(int x, int y) {
        return name + "@(" + x + "," + y + ") " +
            "[" + color + ", " + texture + "]";
    }

    public String name() { return name; }
}
