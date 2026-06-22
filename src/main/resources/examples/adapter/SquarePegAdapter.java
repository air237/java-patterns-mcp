package com.javapatterns.examples.adapter;

/**
 * The Adapter — exposes a {@link RoundPeg} surface while delegating to a
 * wrapped {@link SquarePeg}.
 *
 * <p>The {@code getRadius()} computation is the canonical "diagonal/2" trick:
 * the radius of the smallest circle that fits a square of width w is
 * {@code w * sqrt(2) / 2}.</p>
 */
public final class SquarePegAdapter extends RoundPeg {

    private final SquarePeg square;

    public SquarePegAdapter(SquarePeg square) {
        super(0); // unused: we override getRadius() below
        this.square = square;
    }

    @Override
    public double getRadius() {
        return square.getWidth() * Math.sqrt(2) / 2;
    }
}
