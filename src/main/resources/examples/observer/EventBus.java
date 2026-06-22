package com.javapatterns.examples.observer;

import java.util.ArrayList;
import java.util.List;

/**
 * Subject — keeps a registry of observers and broadcasts events to them.
 * Iteration is over a copy of the list so that observers can subscribe or
 * unsubscribe during dispatch without throwing ConcurrentModificationException.
 */
public final class EventBus<T> {

    private final List<Observer<T>> observers = new ArrayList<>();

    public void subscribe(Observer<T> o) {
        observers.add(o);
    }

    public void unsubscribe(Observer<T> o) {
        observers.remove(o);
    }

    public void publish(T event) {
        for (Observer<T> o : List.copyOf(observers)) {
            o.onEvent(event);
        }
    }

    public int observerCount() {
        return observers.size();
    }
}
