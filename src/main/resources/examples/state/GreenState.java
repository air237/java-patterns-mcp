package com.javapatterns.examples.state;

public final class GreenState implements LightState {
    @Override public String label() { return "GREEN"; }
    @Override public LightState next() { return new YellowState(); }
}
