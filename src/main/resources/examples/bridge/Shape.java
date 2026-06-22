package com.javapatterns.examples.bridge;

/**
 * Bridge pattern — split a hierarchy into two independent dimensions:
 * an abstraction (this {@code Shape} family) and an implementor
 * ({@link Renderer}). They can be developed and extended separately, and
 * any abstraction can pair with any implementor.
 *
 * <pre>
 *   Shape (abstraction)     Renderer (implementor)
 *     │                       │
 *  ┌──┴──┐                ┌───┴───┐
 * Circle Square          Vector Raster
 * </pre>
 *
 * <p>Without Bridge you would have a 2×2 explosion of subclasses
 * (VectorCircle, RasterCircle, VectorSquare, RasterSquare). With Bridge,
 * you have N + M classes instead of N * M.</p>
 */
public abstract class Shape {

    /** The "implementor" — held by composition, not inheritance. */
    protected final Renderer renderer;

    protected Shape(Renderer renderer) {
        this.renderer = renderer;
    }

    public abstract String draw();
}
