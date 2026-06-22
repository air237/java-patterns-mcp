package com.javapatterns.examples.state;

public final class RedState implements LightState {
    @Override public String label() { return "RED"; }
    @Override public LightState next() { return new GreenState(); }
}
