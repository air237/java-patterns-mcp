package com.javapatterns.mcp.validate;

/**
 * Severity of a pattern validation issue.
 *
 * <ul>
 *   <li>{@link #ERROR} — implementation bug, not just style. Examples:
 *       Singleton with a non-thread-safe lazy init, Builder that lets
 *       the target object's mutable fields leak.</li>
 *   <li>{@link #WARNING} — likely anti-pattern. Examples: Singleton that
 *       extends another class (breaks subclass-as-singleton invariants),
 *       Observer that iterates the live subscriber list (re-entrant
 *       subscribe → ConcurrentModificationException).</li>
 *   <li>{@link #INFO} — convention or stylistic suggestion. Examples:
 *       missing javadoc on getInstance(), Builder field not marked
 *       {@code final}.</li>
 * </ul>
 */
public enum Severity { ERROR, WARNING, INFO }
