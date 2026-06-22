package com.javapatterns.examples.visitor;

/** Visitor interface — one visit* method per concrete Shape. */
public interface ShapeVisitor {
    String visit(Shapes.Circle circle);
    String visit(Shapes.Square square);
}
