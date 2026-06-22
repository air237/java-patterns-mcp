package com.javapatterns.examples.strategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Concrete strategy — quick-sort wrapper that delegates to JDK sort. */
public final class QuickSortStrategy<T extends Comparable<T>> implements SortStrategy<T> {

    @Override
    public List<T> sort(List<T> input) {
        List<T> copy = new ArrayList<>(input);
        Collections.sort(copy);
        return copy;
    }
}
