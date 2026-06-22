package com.javapatterns.examples.decorator;

/**
 * Decorator pattern — attach new responsibilities to objects at runtime by
 * wrapping them in objects of the same type.
 *
 * <p>{@link Notifier} is the component interface. {@link EmailNotifier} is
 * the concrete component. {@link SmsDecorator} and {@link SlackDecorator}
 * are decorators that wrap any {@link Notifier} and add another channel.
 * They can be stacked arbitrarily.</p>
 */
public interface Notifier {
    String send(String message);
}
