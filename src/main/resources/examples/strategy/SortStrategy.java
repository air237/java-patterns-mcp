package com.javapatterns.examples.strategy;

/**
 * Strategy pattern — define a family of algorithms and let the client
 * pick one at runtime.
 *
 * <p>Modern Java often expresses Strategy as a {@code Function<A,B>}
 * lambda; the classic interface-based form remains useful when the
 * strategy carries state or several methods.</p>
 */
public interface SortStrategy<T extends Comparable<T>> {
    java.util.List<T> sort(java.util.List<T> input);
}
