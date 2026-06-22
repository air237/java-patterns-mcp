package com.javapatterns.examples.factory_method;

/** Concrete product — HTML rendering. */
public final class HtmlButton implements Button {
    @Override
    public String onClick(String action) {
        return "<button onclick=\"" + action + "\">OK</button>";
    }
}
