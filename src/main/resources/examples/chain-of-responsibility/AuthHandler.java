package com.javapatterns.examples.chain_of_responsibility;

/** Concrete handler — accepts requests starting with "AUTH:". */
public final class AuthHandler extends Handler {

    @Override
    public String handle(String request) {
        if (request.startsWith("AUTH:")) {
            return "[AuthHandler] " + request.substring(5);
        }
        return super.handle(request);
    }
}
