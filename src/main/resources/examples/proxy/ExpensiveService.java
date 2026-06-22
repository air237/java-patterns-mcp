package com.javapatterns.examples.proxy;

/**
 * Expensive concrete service. Pretend each call takes 200 ms or hits a
 * remote API. We want to avoid that when possible.
 */
public final class ExpensiveService implements Service {

    private int callCount = 0;

    @Override
    public String request(String key) {
        callCount++;
        return "expensive(" + key + ")=" + key.hashCode();
    }

    public int callCount() { return callCount; }
}
