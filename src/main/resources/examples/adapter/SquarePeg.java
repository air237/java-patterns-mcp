package com.javapatterns.examples.adapter;

/** Legacy class — the API the client cannot use directly. */
public final class SquarePeg {

    private final double width;

    public SquarePeg(double width) {
        this.width = width;
    }

    public double getWidth() {
        return width;
    }
}
