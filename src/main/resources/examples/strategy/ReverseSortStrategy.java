package com.javapatterns.examples.strategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Concrete strategy — reverse-order sort. */
public final class ReverseSortStrategy<T extends Comparable<T>> implements SortStrategy<T> {

    @Override
    public List<T> sort(List<T> input) {
        List<T> copy = new ArrayList<>(input);
        copy.sort(Collections.reverseOrder());
        return copy;
    }
}
