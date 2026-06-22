package com.javapatterns.examples.memento;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Caretaker — stores mementos but never opens them. Provides a simple
 * undo stack on top of the originator.
 */
public final class History {

    private final Deque<DocumentMemento> stack = new ArrayDeque<>();

    public void push(DocumentMemento m) { stack.push(m); }

    public DocumentMemento pop() { return stack.pop(); }

    public int size() { return stack.size(); }
}
