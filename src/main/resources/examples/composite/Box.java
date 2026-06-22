package com.javapatterns.examples.composite;

import java.util.ArrayList;
import java.util.List;

/**
 * Composite — a box that holds Components. Children may themselves be
 * boxes, giving arbitrary nesting. {@link #cost()} aggregates children
 * recursively.
 */
public final class Box implements Component {

    private final List<Component> children = new ArrayList<>();

    public Box add(Component c) {
        children.add(c);
        return this;
    }

    @Override
    public double cost() {
        double sum = 0.0;
        for (Component c : children) {
            sum += c.cost();
        }
        return sum;
    }

    public List<Component> children() {
        return List.copyOf(children);
    }
}
