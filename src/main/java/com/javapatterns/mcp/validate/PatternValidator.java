package com.javapatterns.mcp.validate;

import com.github.javaparser.ast.CompilationUnit;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.List;

/**
 * Validates one pattern's implementation inside a parsed {@link CompilationUnit}.
 *
 * <p>A validator runs only when a detector has already identified the
 * pattern in the source (or the user has explicitly asked for it). It
 * returns zero or more {@link ValidationIssue}s — an empty list means
 * "implementation looks correct against the rules this validator
 * encodes".</p>
 *
 * <p>Validators are stateless and side-effect free.</p>
 */
public interface PatternValidator {

    /** Which pattern this validator scrutinises. */
    Pattern pattern();

    /**
     * @param unit the parsed source. Never null.
     * @return all issues found, in declaration order.
     */
    List<ValidationIssue> validate(CompilationUnit unit);
}
