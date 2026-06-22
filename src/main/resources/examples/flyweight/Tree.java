package com.javapatterns.examples.flyweight;

/**
 * The context object that holds <i>extrinsic</i> state (x, y) plus a reference
 * to the shared <i>intrinsic</i> state ({@link TreeType}). One million Trees
 * cost ~1 million × (8 bytes int + 8 bytes int + 4-8 bytes reference), not
 * one million full {@link TreeType} copies.
 */
public final class Tree {

    private final int x;
    private final int y;
    private final TreeType type;

    public Tree(int x, int y, TreeType type) {
        this.x = x;
        this.y = y;
        this.type = type;
    }

    public String draw() {
        return type.draw(x, y);
    }
}
