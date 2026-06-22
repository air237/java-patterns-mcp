package com.javapatterns.examples.visitor;

/**
 * Visitor pattern — separate algorithms from the object structure they
 * operate on. Each {@link Shape} has an {@code accept(Visitor)} that
 * double-dispatches to the right visitor method.
 *
 * <p>Visitor is the textbook answer when you have a stable type
 * hierarchy and operations vary much more often than the structure.</p>
 */
public interface Shape {
    String accept(ShapeVisitor visitor);
}
