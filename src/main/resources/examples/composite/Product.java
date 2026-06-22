package com.javapatterns.examples.composite;

/** Leaf — a real product with its own price. */
public final class Product implements Component {

    private final String name;
    private final double price;

    public Product(String name, double price) {
        this.name = name;
        this.price = price;
    }

    public String name() { return name; }

    @Override
    public double cost() {
        return price;
    }
}
