package com.javapatterns.examples.factory_method;

/**
 * Factory Method pattern — refactoring.guru-style Dialog example.
 *
 * <p>The abstract {@code Dialog} class defines the algorithm
 * ({@link #render()}) and delegates the choice of concrete {@code Button} to
 * the subclass via the {@link #createButton()} factory method. Adding a new
 * platform means subclassing — no existing code changes.</p>
 *
 * <pre>
 *   Dialog ─┐
 *           │ uses
 *           ▼
 *         Button         (product interface)
 *           ▲
 *           │
 *     ┌─────┴─────┐
 *     │           │
 *  HtmlButton  WindowsButton
 * </pre>
 */
public abstract class Dialog {

    /**
     * The template method. Defines the high-level flow; subclasses only
     * decide which concrete {@link Button} to produce.
     */
    public String render() {
        Button button = createButton();
        return "[Dialog] " + button.onClick("submit");
    }

    /**
     * The factory method itself. Each subclass returns its own concrete product.
     */
    protected abstract Button createButton();
}
