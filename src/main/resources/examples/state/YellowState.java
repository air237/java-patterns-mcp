package com.javapatterns.examples.state;

public final class YellowState implements LightState {
    @Override public String label() { return "YELLOW"; }
    @Override public LightState next() { return new RedState(); }
}
