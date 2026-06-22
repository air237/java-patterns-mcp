package com.javapatterns.examples.mediator;

import java.util.ArrayList;
import java.util.List;

/** Concrete mediator — a simple chat room that fans out messages. */
public final class ChatRoom implements ChatMediator {

    private final List<Colleague> members = new ArrayList<>();
    private final List<String> log = new ArrayList<>();

    @Override
    public ChatMediator register(Colleague c) {
        members.add(c);
        return this;
    }

    @Override
    public void send(String from, String message) {
        log.add(from + ": " + message);
        for (Colleague c : members) {
            if (!c.name().equals(from)) {
                c.receive(from, message);
            }
        }
    }

    public List<String> log() {
        return List.copyOf(log);
    }
}
