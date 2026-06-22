package com.javapatterns.examples.decorator;

/**
 * Base decorator. Holds the wrapped {@link Notifier} and forwards by
 * default. Subclasses add their own behaviour before/after the delegate.
 */
public abstract class NotifierDecorator implements Notifier {

    protected final Notifier wrapped;

    protected NotifierDecorator(Notifier wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public String send(String message) {
        return wrapped.send(message);
    }
}
