package com.javapatterns.examples.abstract_factory;

/** Concrete factory producing macOS-style products. */
public final class MacOSFactory implements GUIFactory {

    @Override
    public Button createButton() {
        return () -> "[macOS Button]";
    }

    @Override
    public Checkbox createCheckbox() {
        return () -> "[macOS Checkbox]";
    }
}
