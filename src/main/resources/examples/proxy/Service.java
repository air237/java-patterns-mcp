package com.javapatterns.examples.proxy;

/**
 * Proxy pattern — provide a substitute that controls access to the real
 * object. Variants include virtual proxy (lazy loading), protection proxy
 * (authorization), remote proxy (RMI/RPC), and caching proxy.
 *
 * <p>This {@link Service} interface is implemented by both the real and
 * the proxy class so clients can swap them transparently.</p>
 */
public interface Service {
    String request(String key);
}
