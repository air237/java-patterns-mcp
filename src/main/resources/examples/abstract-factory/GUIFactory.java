package com.javapatterns.examples.abstract_factory;

/**
 * Abstract Factory pattern — refactoring.guru-style GUI factory.
 *
 * <p>{@code GUIFactory} declares a family of related products
 * ({@link Button}, {@link Checkbox}). Concrete factories produce a
 * matching set (Windows-Win, Mac-Mac). Client code uses only the
 * interfaces and never names a concrete class.</p>
 */
public interface GUIFactory {
    Button createButton();
    Checkbox createCheckbox();
}
