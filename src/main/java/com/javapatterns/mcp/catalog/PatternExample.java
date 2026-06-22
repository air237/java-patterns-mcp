package com.javapatterns.mcp.catalog;

import java.util.Objects;

/**
 * A single, fully runnable Java source file that demonstrates one design
 * pattern.
 *
 * <p>Examples are loaded eagerly into memory from the classpath at startup —
 * they are <i>not</i> generated on the fly. Each example is verified to
 * compile by the build (the example sources sit in
 * {@code src/main/resources/examples/<slug>/...} and a parallel JUnit suite
 * compiles + exercises them).</p>
 *
 * @param pattern  the pattern this example illustrates
 * @param fileName the Java file name (e.g. {@code "Singleton.java"})
 * @param packageName the package the source declares
 *                    (e.g. {@code "com.javapatterns.examples.singleton"})
 * @param source   the full source code, ready to write to disk and javac
 * @param note     a one-line caption explaining what variant this is
 *                 (e.g. "Thread-safe Bill-Pugh holder idiom")
 */
public record PatternExample(
    Pattern pattern,
    String fileName,
    String packageName,
    String source,
    String note
) {
    public PatternExample {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(packageName, "packageName");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(note, "note");
        if (fileName.isBlank()) throw new IllegalArgumentException("fileName must be non-blank");
        if (source.isBlank()) throw new IllegalArgumentException("source must be non-blank");
    }
}
