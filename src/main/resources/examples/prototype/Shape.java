package com.javapatterns.examples.prototype;

/**
 * Prototype pattern — copy existing objects without depending on their
 * concrete classes.
 *
 * <p>{@link Shape} declares a {@link #clone()} contract; every subclass is
 * responsible for producing a copy of itself. The client code uses only the
 * abstract type and never names a concrete class to clone.</p>
 *
 * <p>Note the equals/hashCode pair — Prototype clones must be {@code equal}
 * to their source. Most bugs in this pattern come from incomplete copies
 * (shallow vs deep) or forgotten equals/hashCode overrides.</p>
 */
public abstract class Shape {

    protected int x;
    protected int y;
    protected String color;

    protected Shape() { }

    /** Copy constructor — used by subclasses to clone shared state. */
    protected Shape(Shape source) {
        this.x = source.x;
        this.y = source.y;
        this.color = source.color;
    }

    public abstract Shape clone();

    public int x()         { return x; }
    public int y()         { return y; }
    public String color()  { return color; }

    public Shape moveTo(int x, int y) { this.x = x; this.y = y; return this; }
    public Shape paint(String c)      { this.color = c; return this; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Shape s)) return false;
        return x == s.x && y == s.y && java.util.Objects.equals(color, s.color);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(x, y, color);
    }
}
