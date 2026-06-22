package com.javapatterns.mcp.catalog;

import com.javapatterns.mcp.McpJsonMappers;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and caches the canonical Java examples for every pattern that has
 * any. Examples live under
 * {@code /resources/examples/<slug>/...} and are indexed by
 * {@code /resources/examples/examples-index.json}.
 *
 * <p>Index file shape:</p>
 * <pre>
 * {
 *   "examples": [
 *     {
 *       "pattern": "SINGLETON",
 *       "files": [
 *         {
 *           "fileName": "Singleton.java",
 *           "packageName": "com.javapatterns.examples.singleton",
 *           "path": "examples/singleton/Singleton.java",
 *           "note": "Thread-safe Bill-Pugh holder idiom"
 *         }
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 *
 * <p>Patterns without an example yet are simply absent from the index —
 * {@link #forPattern(Pattern)} returns an empty list in that case. Patterns
 * gain examples incrementally as the project covers more phases (Creational
 * first, then Structural, then Behavioral).</p>
 */
public final class PatternExamplesLoader {

    private static final String INDEX_PATH = "/examples/examples-index.json";

    private final Map<Pattern, List<PatternExample>> byPattern;

    /** Lazy thread-safe singleton via holder idiom. */
    private static final class Holder {
        private static final PatternExamplesLoader INSTANCE = load();
    }

    public static PatternExamplesLoader getInstance() {
        return Holder.INSTANCE;
    }

    private PatternExamplesLoader(Map<Pattern, List<PatternExample>> byPattern) {
        this.byPattern = Collections.unmodifiableMap(byPattern);
    }

    /** Returns the immutable example list for the given pattern, or an empty list. */
    public List<PatternExample> forPattern(Pattern pattern) {
        return byPattern.getOrDefault(pattern, List.of());
    }

    /** Total number of (pattern, file) tuples loaded. */
    public int totalExamples() {
        return byPattern.values().stream().mapToInt(List::size).sum();
    }

    /** Patterns that have at least one example. */
    public java.util.Set<Pattern> coveredPatterns() {
        return byPattern.keySet();
    }

    // ─────────────────────────────────────────────────────────────────

    private static PatternExamplesLoader load() {
        try (InputStream in = PatternExamplesLoader.class.getResourceAsStream(INDEX_PATH)) {
            if (in == null) {
                // No index file = no examples yet. That is a valid early state.
                return new PatternExamplesLoader(new EnumMap<>(Pattern.class));
            }
            byte[] indexBytes = in.readAllBytes();
            return parseAndResolve(indexBytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + INDEX_PATH, e);
        }
    }

    private static PatternExamplesLoader parseAndResolve(byte[] indexBytes) throws IOException {
        McpJsonMapper mapper = McpJsonMappers.defaultMapper();
        Index index = mapper.readValue(indexBytes, new TypeRef<Index>() { });

        Map<Pattern, List<PatternExample>> result = new EnumMap<>(Pattern.class);
        if (index == null || index.examples == null) {
            return new PatternExamplesLoader(result);
        }

        for (RawEntry entry : index.examples) {
            Pattern pattern = Pattern.valueOf(entry.pattern);
            List<PatternExample> files = new java.util.ArrayList<>();
            if (entry.files != null) {
                for (RawFile rf : entry.files) {
                    String source = readResource("/" + rf.path);
                    files.add(new PatternExample(pattern, rf.fileName, rf.packageName, source, rf.note));
                }
            }
            result.put(pattern, List.copyOf(files));
        }

        // Preserve enum declaration order in the iteration order.
        Map<Pattern, List<PatternExample>> ordered = new LinkedHashMap<>();
        for (Pattern p : Pattern.values()) {
            if (result.containsKey(p)) ordered.put(p, result.get(p));
        }
        return new PatternExamplesLoader(ordered);
    }

    private static String readResource(String resourcePath) throws IOException {
        try (InputStream in = PatternExamplesLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException(
                    "Example source listed in index but missing on classpath: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ─── JSON binding DTOs ─────────────────────────────────────────

    private static final class Index {
        public List<RawEntry> examples;
    }

    private static final class RawEntry {
        public String pattern;
        public List<RawFile> files;
    }

    private static final class RawFile {
        public String fileName;
        public String packageName;
        public String path;
        public String note;
    }
}
