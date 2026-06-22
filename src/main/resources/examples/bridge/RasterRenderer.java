package com.javapatterns.examples.bridge;

public final class RasterRenderer implements Renderer {
    @Override
    public String renderCircle(double r) {
        return "[raster: circle radius=" + r + " px]";
    }

    @Override
    public String renderSquare(double side) {
        return "[raster: square side=" + side + " px]";
    }
}
