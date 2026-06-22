package com.javapatterns.examples.visitor;

/**
 * Concrete shapes and a sample visitor (an SVG renderer) bundled in one
 * file for brevity. In a real project each class lives on its own.
 */
public final class Shapes {

    private Shapes() { /* utility */ }

    public static final class Circle implements Shape {
        public final double radius;
        public Circle(double radius) { this.radius = radius; }

        @Override
        public String accept(ShapeVisitor v) {
            return v.visit(this);
        }
    }

    public static final class Square implements Shape {
        public final double side;
        public Square(double side) { this.side = side; }

        @Override
        public String accept(ShapeVisitor v) {
            return v.visit(this);
        }
    }

    /** Sample visitor — renders shapes as SVG. */
    public static final class SvgRenderer implements ShapeVisitor {
        @Override
        public String visit(Shapes.Circle c) {
            return "<circle r=\"" + c.radius + "\"/>";
        }

        @Override
        public String visit(Shapes.Square s) {
            return "<rect width=\"" + s.side + "\" height=\"" + s.side + "\"/>";
        }
    }
}
