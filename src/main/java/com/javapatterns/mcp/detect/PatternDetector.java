package com.javapatterns.mcp.detect;

import com.github.javaparser.ast.CompilationUnit;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.List;

/**
 * Strategy for recognising one design pattern in a parsed
 * {@link CompilationUnit}.
 *
 * <p>Detectors are stateless and side-effect free: they look at the AST
 * and emit zero or more {@link DetectedPattern} entries. Confidence
 * scoring is per-detector; see each implementation for the rule set.</p>
 */
public interface PatternDetector {

    /** Which pattern this detector recognises. */
    Pattern pattern();

    /**
     * @param unit a parsed Java source file. Must not be {@code null}.
     * @return all instances of {@link #pattern()} found in the unit, in
     *         declaration order.
     */
    List<DetectedPattern> detect(CompilationUnit unit);
}
