package com.javapatterns.examples.adapter;

/** Client whose API only knows about {@link RoundPeg}. */
public final class RoundHole {

    private final double radius;

    public RoundHole(double radius) {
        this.radius = radius;
    }

    public double getRadius() {
        return radius;
    }

    public boolean fits(RoundPeg peg) {
        return peg.getRadius() <= radius;
    }
}
