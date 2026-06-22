package com.javapatterns.examples.iterator;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Concrete aggregate — a doubly-linked list of names. Exposes its
 * iterator without revealing its underlying storage.
 */
public final class NameRoster implements Aggregate<String> {

    private final List<String> names = new ArrayList<>();

    public NameRoster add(String name) { names.add(name); return this; }

    @Override
    public CustomIterator<String> iterator() {
        return new NameIterator();
    }

    /** Inner class — has private access to the backing list. */
    private final class NameIterator implements CustomIterator<String> {
        private int cursor = 0;

        @Override
        public boolean hasNext() { return cursor < names.size(); }

        @Override
        public String next() {
            if (!hasNext()) throw new NoSuchElementException();
            return names.get(cursor++);
        }
    }
}
