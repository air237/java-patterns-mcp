package com.javapatterns.examples.bridge;

/** Implementor — declares the low-level rendering API. */
public interface Renderer {
    String renderCircle(double r);
    String renderSquare(double side);
}
