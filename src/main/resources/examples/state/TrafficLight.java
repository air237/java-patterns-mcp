package com.javapatterns.examples.state;

/**
 * State pattern — let an object alter its behaviour when its internal
 * state changes, as if the object had changed its class.
 *
 * <p>{@code TrafficLight} is the context. {@link LightState} is the state
 * interface. {@link RedState}, {@link GreenState}, {@link YellowState}
 * are concrete states. Each state knows what comes next.</p>
 */
public final class TrafficLight {

    private LightState state = new RedState();

    public String tick() {
        String label = state.label();
        state = state.next();
        return label;
    }

    public LightState currentState() {
        return state;
    }
}
