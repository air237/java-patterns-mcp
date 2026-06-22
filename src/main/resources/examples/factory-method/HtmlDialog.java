package com.javapatterns.examples.factory_method;

/** Concrete creator that produces HTML buttons. */
public final class HtmlDialog extends Dialog {
    @Override
    protected Button createButton() {
        return new HtmlButton();
    }
}
