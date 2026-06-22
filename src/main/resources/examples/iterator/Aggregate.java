package com.javapatterns.examples.iterator;

/** Aggregate interface — produces an iterator over T. */
public interface Aggregate<T> {
    CustomIterator<T> iterator();
}
