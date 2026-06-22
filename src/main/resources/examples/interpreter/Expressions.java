package com.javapatterns.examples.interpreter;

/**
 * AST nodes for our tiny boolean grammar:
 *   {@link Variable}    — terminal, looks up a named flag in the context
 *   {@link And}, {@link Or} — non-terminals, evaluate two sub-expressions
 *
 * <p>All three are bundled in this file for brevity; in a real grammar
 * each would be its own top-level class.</p>
 */
public final class Expressions {

    private Expressions() { /* utility */ }

    public static final class Variable implements Expression {
        private final String name;
        public Variable(String name) { this.name = name; }

        @Override
        public boolean evaluate(Context ctx) {
            return ctx.get(name);
        }
    }

    public static final class And implements Expression {
        private final Expression left;
        private final Expression right;
        public And(Expression left, Expression right) { this.left = left; this.right = right; }

        @Override
        public boolean evaluate(Context ctx) {
            return left.evaluate(ctx) && right.evaluate(ctx);
        }
    }

    public static final class Or implements Expression {
        private final Expression left;
        private final Expression right;
        public Or(Expression left, Expression right) { this.left = left; this.right = right; }

        @Override
        public boolean evaluate(Context ctx) {
            return left.evaluate(ctx) || right.evaluate(ctx);
        }
    }
}
