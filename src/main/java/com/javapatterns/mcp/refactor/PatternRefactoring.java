package com.javapatterns.mcp.refactor;

import com.github.javaparser.ast.CompilationUnit;

/**
 * A single AST-level refactoring. Implementations should:
 *
 * <ol>
 *   <li>Inspect the compilation unit;</li>
 *   <li>Apply the smallest possible mutation that fixes the issue
 *       (don't reformat unrelated code);</li>
 *   <li>Be idempotent — running the same refactoring twice in a row on
 *       the same source returns {@code changed == false} the second
 *       time;</li>
 *   <li>Print using {@code LexicalPreservingPrinter} so the rest of
 *       the source keeps its formatting and comments.</li>
 * </ol>
 */
public interface PatternRefactoring {

    /** Public identifier — appears as the {@code refactoring} input on the tool. */
    RefactoringId id();

    /**
     * Apply the refactoring to a parsed compilation unit and return the result.
     *
     * <p>The unit must already have been {@code LexicalPreservingPrinter.setup}-ed
     * by the caller (the engine does this), so concrete implementations can rely
     * on lexical preservation when adding / removing nodes.</p>
     */
    RefactoringResult apply(CompilationUnit unit);
}
