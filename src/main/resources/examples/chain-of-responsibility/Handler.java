package com.javapatterns.examples.chain_of_responsibility;

/**
 * Chain of Responsibility — pass a request along a linear chain of handlers.
 * Each handler decides whether to process or forward to the next.
 *
 * <p>Classic uses: servlet filters, validation pipelines, authorization
 * layers, GUI event bubbling.</p>
 */
public abstract class Handler {

    protected Handler next;

    /** Fluent setter — returns the next handler so callers can chain inline. */
    public Handler setNext(Handler next) {
        this.next = next;
        return next;
    }

    /** Default implementation: forward to the next handler, or stop. */
    public String handle(String request) {
        if (next != null) {
            return next.handle(request);
        }
        return "[no handler accepted: " + request + "]";
    }
}
