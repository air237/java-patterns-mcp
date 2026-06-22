package com.javapatterns.examples.bridge;

public final class Square extends Shape {
    private final double side;

    public Square(Renderer renderer, double side) {
        super(renderer);
        this.side = side;
    }

    @Override
    public String draw() {
        return renderer.renderSquare(side);
    }
}
