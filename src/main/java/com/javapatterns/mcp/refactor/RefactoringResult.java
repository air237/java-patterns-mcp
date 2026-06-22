package com.javapatterns.mcp.refactor;

import java.util.List;
import java.util.Objects;

/**
 * Outcome of running one refactoring on a source.
 *
 * @param refactoringId which refactoring was applied
 * @param newSource     the rewritten source (verbatim from
 *                      {@code LexicalPreservingPrinter.print()})
 * @param changed       {@code true} iff anything was actually rewritten
 *                      — useful for idempotency checks ("apply this
 *                      twice and the second pass returns false")
 * @param changes       human-readable list of what changed (one entry
 *                      per modified site, with class name + line)
 */
public record RefactoringResult(
    RefactoringId refactoringId,
    String newSource,
    boolean changed,
    List<String> changes
) {
    public RefactoringResult {
        Objects.requireNonNull(refactoringId, "refactoringId");
        Objects.requireNonNull(newSource, "newSource");
        changes = changes == null ? List.of() : List.copyOf(changes);
    }
}
