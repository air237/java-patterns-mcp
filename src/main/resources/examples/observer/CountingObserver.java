package com.javapatterns.examples.observer;

import java.util.ArrayList;
import java.util.List;

/** A concrete observer that records every event it sees, for tests/demos. */
public final class CountingObserver<T> implements Observer<T> {

    private final List<T> seen = new ArrayList<>();

    @Override
    public void onEvent(T event) {
        seen.add(event);
    }

    public List<T> received() {
        return List.copyOf(seen);
    }

    public int count() {
        return seen.size();
    }
}
