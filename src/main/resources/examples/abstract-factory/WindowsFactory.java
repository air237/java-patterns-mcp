package com.javapatterns.examples.abstract_factory;

/** Concrete factory producing Windows-style products. */
public final class WindowsFactory implements GUIFactory {

    @Override
    public Button createButton() {
        return () -> "[Windows Button]";
    }

    @Override
    public Checkbox createCheckbox() {
        return () -> "[Windows Checkbox]";
    }
}
