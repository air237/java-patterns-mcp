package com.javapatterns.examples.memento;

/**
 * Memento pattern — capture and externalize an object's internal state
 * without violating its encapsulation, so the object can be restored to
 * this state later.
 *
 * <p>Classic uses: editor undo/redo, game-save snapshots, transactional
 * rollback at object level.</p>
 *
 * <p>The {@code TextDocument} is the <i>originator</i>; the {@link
 * DocumentMemento} is the snapshot. The {@link Caretaker} stores snapshots
 * but never inspects them — only the originator can read its own
 * memento.</p>
 */
public final class TextDocument {

    private String text = "";

    public TextDocument write(String fragment) {
        this.text = this.text + fragment;
        return this;
    }

    public String text() { return text; }

    /** Save a snapshot of the current state. */
    public DocumentMemento save() {
        return new DocumentMemento(text);
    }

    /** Restore from a snapshot. Only the originator can interpret a memento. */
    public TextDocument restore(DocumentMemento m) {
        this.text = m.snapshot;
        return this;
    }
}
