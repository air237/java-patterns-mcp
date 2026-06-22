package com.javapatterns.examples.iterator;

/**
 * Iterator pattern — walk over the elements of an aggregate without
 * exposing its internals.
 *
 * <p>Java's {@code java.util.Iterator} is the canonical example; here we
 * mirror it on a small custom aggregate to make the structural points
 * explicit.</p>
 */
public interface CustomIterator<T> {
    boolean hasNext();
    T next();
}
