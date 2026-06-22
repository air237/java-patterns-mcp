package com.javapatterns.examples.decorator;

public final class SlackDecorator extends NotifierDecorator {

    private final String channel;

    public SlackDecorator(Notifier wrapped, String channel) {
        super(wrapped);
        this.channel = channel;
    }

    @Override
    public String send(String message) {
        return super.send(message) + " | [slack -> " + channel + "]: " + message;
    }
}
