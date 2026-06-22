package com.javapatterns.examples.factory_method;

/** Concrete product — Windows-style button (just text in this demo). */
public final class WindowsButton implements Button {
    @Override
    public String onClick(String action) {
        return "[WIN button: " + action + "]";
    }
}
