package com.javapatterns.examples.factory_method;

/** Concrete creator that produces native Windows buttons. */
public final class WindowsDialog extends Dialog {
    @Override
    protected Button createButton() {
        return new WindowsButton();
    }
}
