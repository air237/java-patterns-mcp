package com.javapatterns.examples.singleton;

/**
 * Thread-safe Singleton — Bill Pugh "initialization-on-demand holder" idiom.
 *
 * <p>This is the recommended modern Java Singleton. It is:</p>
 * <ul>
 *   <li><b>Lazy</b>: the holder class — and therefore {@code INSTANCE} —
 *       is loaded only when {@link #getInstance()} is first called.</li>
 *   <li><b>Thread-safe</b>: class initialization is guaranteed exactly once
 *       by the JVM under the JLS §12.4 happens-before rules. No locks needed
 *       at call time.</li>
 *   <li><b>Serialization-safe</b>: not Serializable, so there is no
 *       readResolve hole.</li>
 * </ul>
 *
 * <p>For pure constants or eagerly-initialized values, consider
 * {@code public enum Singleton { INSTANCE; ... }} instead — it gets you
 * Singleton semantics plus serialization-safety in one line.</p>
 */
public final class Singleton {

    private final long createdAtNanos;

    private Singleton() {
        // Reject the reflective end-run: throw if somebody managed to call
        // the constructor twice (e.g. via setAccessible(true)).
        if (Holder.INSTANCE != null) {
            throw new IllegalStateException("Singleton already constructed.");
        }
        this.createdAtNanos = System.nanoTime();
    }

    public static Singleton getInstance() {
        return Holder.INSTANCE;
    }

    public long createdAtNanos() {
        return createdAtNanos;
    }

    /**
     * Inner holder class — loaded by the JVM only on the first call to
     * {@link Singleton#getInstance()}. Class initialization is single-threaded
     * by spec, so this is both lazy and thread-safe with no explicit locking.
     */
    private static final class Holder {
        private static final Singleton INSTANCE = new Singleton();

        private Holder() { /* no instances */ }
    }
}
