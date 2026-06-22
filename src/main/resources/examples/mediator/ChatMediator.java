package com.javapatterns.examples.mediator;

/**
 * Mediator pattern — centralize many-to-many communication into a single
 * object so the participants ({@link Colleague}s) stay decoupled from
 * each other.
 *
 * <p>Classic uses: chat rooms, traffic control, GUI form-field
 * coordination ("when X changes, validate Y and toggle Z").</p>
 */
public interface ChatMediator {
    void send(String from, String message);
    ChatMediator register(Colleague c);
}
