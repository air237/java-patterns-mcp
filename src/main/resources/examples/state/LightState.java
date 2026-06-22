package com.javapatterns.examples.state;

/**
 * State interface and the 3 concrete states. Bundled in a single file
 * for brevity; each could be a top-level class.
 */
public sealed interface LightState
    permits RedState, GreenState, YellowState {

    String label();
    LightState next();
}
