package com.javapatterns.examples.observer;

/**
 * Observer pattern — define a one-to-many subscription so the subject can
 * notify many observers about events without knowing their concrete types.
 *
 * <p>Canonical uses: pub/sub, GUI event listeners, model-view updates,
 * any "when X happens, several Ys should react" scenario.</p>
 */
public interface Observer<T> {
    void onEvent(T event);
}
