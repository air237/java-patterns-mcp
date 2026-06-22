package com.javapatterns.examples.chain_of_responsibility;

/** Concrete handler — accepts requests starting with "LOG:". */
public final class LoggingHandler extends Handler {

    @Override
    public String handle(String request) {
        if (request.startsWith("LOG:")) {
            return "[LoggingHandler] " + request.substring(4);
        }
        return super.handle(request);
    }
}
