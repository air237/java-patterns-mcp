package com.javapatterns.examples.interpreter;

/**
 * Interpreter pattern — represents a grammar as a class hierarchy and
 * evaluates sentences by walking the AST.
 *
 * <p>This tiny example evaluates boolean expressions with {@code AND} and
 * {@code OR} over a {@link Context} of named flags. Useful for query
 * DSLs, business-rule engines, simple expression languages.</p>
 */
public interface Expression {
    boolean evaluate(Context ctx);
}
