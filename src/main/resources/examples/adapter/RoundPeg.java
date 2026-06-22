package com.javapatterns.examples.adapter;

/**
 * Adapter pattern — let two incompatible interfaces collaborate.
 *
 * <p>Imagine the client expects a {@link RoundPeg}-shaped API, but you
 * already have a {@link SquarePeg} class from a third-party library. The
 * {@link SquarePegAdapter} wraps the SquarePeg and presents a
 * RoundPeg interface, computing the equivalent radius on the fly.</p>
 *
 * <p>Adapter is purely a structural pattern — no behavior is added,
 * only the surface is reshaped.</p>
 */
public class RoundPeg {

    private final double radius;

    public RoundPeg(double radius) {
        this.radius = radius;
    }

    public double getRadius() {
        return radius;
    }
}
