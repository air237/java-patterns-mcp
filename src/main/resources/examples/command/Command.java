package com.javapatterns.examples.command;

/**
 * Command pattern — turn a request into a stand-alone object carrying all
 * information needed to perform it (and, optionally, to undo it).
 *
 * <p>Enables queueing, scheduling, logging, undo/redo, and full
 * decoupling of GUI/trigger from action.</p>
 */
public interface Command {
    String execute();
    String undo();
}
