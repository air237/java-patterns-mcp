package com.javapatterns.examples.proxy;

import java.util.HashMap;
import java.util.Map;

/**
 * Caching proxy. Implements the same {@link Service} interface but holds
 * an internal cache; only forwards to the wrapped service on a miss.
 */
public final class CachingServiceProxy implements Service {

    private final Service delegate;
    private final Map<String, String> cache = new HashMap<>();

    public CachingServiceProxy(Service delegate) {
        this.delegate = delegate;
    }

    @Override
    public String request(String key) {
        String hit = cache.get(key);
        if (hit != null) return "[cache] " + hit;

        String fresh = delegate.request(key);
        cache.put(key, fresh);
        return fresh;
    }

    public int cachedKeys() {
        return cache.size();
    }
}
