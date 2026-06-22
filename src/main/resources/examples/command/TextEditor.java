package com.javapatterns.examples.command;

/**
 * Receiver — the object that knows how to perform the work. The Command
 * holds a reference and delegates to it.
 */
public final class TextEditor {

    private final StringBuilder buffer = new StringBuilder();

    public void append(String text) { buffer.append(text); }

    public void removeLast(int len) {
        int from = Math.max(0, buffer.length() - len);
        buffer.delete(from, buffer.length());
    }

    public String content() { return buffer.toString(); }
}
