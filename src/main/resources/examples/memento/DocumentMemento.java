package com.javapatterns.examples.memento;

/**
 * Memento — an opaque snapshot. Its field is package-private so only
 * {@link TextDocument} (same package, the originator) can read it. The
 * Caretaker holds it as a black box.
 */
public final class DocumentMemento {

    /** Package-private on purpose: only TextDocument is allowed to peek. */
    final String snapshot;

    DocumentMemento(String snapshot) {
        this.snapshot = snapshot;
    }
}
