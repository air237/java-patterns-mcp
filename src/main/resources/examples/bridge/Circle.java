package com.javapatterns.examples.bridge;

public final class Circle extends Shape {
    private final double radius;

    public Circle(Renderer renderer, double radius) {
        super(renderer);
        this.radius = radius;
    }

    @Override
    public String draw() {
        return renderer.renderCircle(radius);
    }
}
