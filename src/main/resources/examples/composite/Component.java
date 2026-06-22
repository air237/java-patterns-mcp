package com.javapatterns.examples.composite;

/**
 * Composite pattern — let clients treat leaf objects and whole trees the
 * same way.
 *
 * <p>This {@link Component} interface declares {@link #cost()}. Both
 * {@link Product} (leaf) and {@link Box} (composite of components,
 * possibly nested) implement it. The client recurses without ever asking
 * "is this a leaf or a box?".</p>
 */
public interface Component {
    double cost();
}
