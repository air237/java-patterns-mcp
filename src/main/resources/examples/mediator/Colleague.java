package com.javapatterns.examples.mediator;

/**
 * Colleague — knows about the mediator, not about other colleagues. All
 * communication goes through the mediator.
 */
public abstract class Colleague {

    protected final String name;
    protected final ChatMediator mediator;

    protected Colleague(String name, ChatMediator mediator) {
        this.name = name;
        this.mediator = mediator;
    }

    public String name() { return name; }

    public abstract void receive(String from, String message);

    public void send(String message) {
        mediator.send(name, message);
    }
}
