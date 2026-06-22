package com.javapatterns.examples.bridge;

public final class VectorRenderer implements Renderer {
    @Override
    public String renderCircle(double r) {
        return "<svg><circle r=\"" + r + "\"/></svg>";
    }

    @Override
    public String renderSquare(double side) {
        return "<svg><rect width=\"" + side + "\" height=\"" + side + "\"/></svg>";
    }
}
