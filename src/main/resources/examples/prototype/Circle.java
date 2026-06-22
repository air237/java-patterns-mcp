package com.javapatterns.examples.prototype;

/** Concrete Shape — circle with a radius. */
public final class Circle extends Shape {

    private int radius;

    public Circle() { }

    /** Copy constructor — chains to {@link Shape#Shape(Shape)} then adds own state. */
    private Circle(Circle source) {
        super(source);
        this.radius = source.radius;
    }

    public int radius()              { return radius; }
    public Circle radius(int r)      { this.radius = r; return this; }

    @Override
    public Circle clone() {
        return new Circle(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Circle c)) return false;
        return super.equals(o) && radius == c.radius;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(super.hashCode(), radius);
    }
}
