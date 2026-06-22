package com.javapatterns.examples.decorator;

/** Concrete component — the base behavior. */
public final class EmailNotifier implements Notifier {

    private final String recipient;

    public EmailNotifier(String recipient) {
        this.recipient = recipient;
    }

    @Override
    public String send(String message) {
        return "[email -> " + recipient + "]: " + message;
    }
}
