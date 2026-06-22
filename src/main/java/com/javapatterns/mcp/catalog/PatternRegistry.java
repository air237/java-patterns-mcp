package com.javapatterns.mcp.catalog;

import com.javapatterns.mcp.McpJsonMappers;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and caches the canonical metadata for every {@link Pattern} from
 * the bundled {@code /catalog/patterns.json} resource.
 *
 * <p>The registry is a process-singleton, loaded eagerly on first access via
 * the holder idiom (thread-safe, lazy, no synchronization on the hot path).
 * It validates at load time that <i>every</i> enum constant has a
 * {@link PatternMetadata} entry — a missing entry is a programming error and
 * throws {@link IllegalStateException}.</p>
 *
 * <p>This class is intentionally read-only. Modifying patterns means editing
 * the JSON resource and the {@link Pattern} enum together, not mutating
 * runtime state.</p>
 */
public final class PatternRegistry {

    private static final String RESOURCE_PATH = "/catalog/patterns.json";

    private final Map<Pattern, PatternMetadata> byEnum;

    /** Holder idiom for lazy thread-safe singleton initialization. */
    private static final class Holder {
        private static final PatternRegistry INSTANCE = load();
    }

    public static PatternRegistry getInstance() {
        return Holder.INSTANCE;
    }

    private PatternRegistry(Map<Pattern, PatternMetadata> byEnum) {
        this.byEnum = Collections.unmodifiableMap(byEnum);
    }

    /** Returns metadata for the given pattern. Never null. */
    public PatternMetadata get(Pattern pattern) {
        PatternMetadata md = byEnum.get(pattern);
        if (md == null) {
            // Defensive: load() guarantees completeness, but if someone adds a
            // new enum value without updating the JSON we want a clear error.
            throw new IllegalStateException(
                "No metadata loaded for pattern " + pattern.name() +
                " — add it to " + RESOURCE_PATH);
        }
        return md;
    }

    /** Returns metadata for all 23 patterns, in declaration order. */
    public List<PatternMetadata> all() {
        return java.util.Arrays.stream(Pattern.values())
            .map(byEnum::get)
            .toList();
    }

    /** Returns metadata for patterns in a given category. */
    public List<PatternMetadata> byCategory(PatternCategory category) {
        java.util.Objects.requireNonNull(category, "category");
        return all().stream()
            .filter(md -> md.pattern().category() == category)
            .toList();
    }

    /** Number of patterns in the registry — must be 23. */
    public int size() {
        return byEnum.size();
    }

    // ───── load ─────────────────────────────────────────────────────

    private static PatternRegistry load() {
        try (InputStream in = PatternRegistry.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException(
                    "Catalog resource not found on classpath: " + RESOURCE_PATH);
            }
            byte[] bytes = in.readAllBytes();
            return parseAndValidate(bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + RESOURCE_PATH, e);
        }
    }

    private static PatternRegistry parseAndValidate(byte[] json) throws IOException {
        McpJsonMapper mapper = McpJsonMappers.defaultMapper();
        Catalog catalog = mapper.readValue(json, new TypeRef<Catalog>() { });

        if (catalog == null || catalog.patterns == null || catalog.patterns.isEmpty()) {
            throw new IllegalStateException("Catalog JSON is empty or malformed.");
        }

        Map<Pattern, PatternMetadata> map = new EnumMap<>(Pattern.class);
        for (RawEntry entry : catalog.patterns) {
            Pattern pattern = Pattern.valueOf(entry.pattern);
            if (map.containsKey(pattern)) {
                throw new IllegalStateException("Duplicate entry for pattern: " + pattern);
            }
            map.put(pattern, new PatternMetadata(
                pattern,
                entry.intent,
                entry.problem,
                entry.aliases == null ? List.of() : entry.aliases
            ));
        }

        // Completeness check: every enum constant must be present.
        for (Pattern p : Pattern.values()) {
            if (!map.containsKey(p)) {
                throw new IllegalStateException(
                    "Catalog is missing entry for pattern: " + p.name() +
                    ". Update " + RESOURCE_PATH);
            }
        }
        return new PatternRegistry(map);
    }

    // ───── JSON binding DTOs (not exposed) ──────────────────────────

    /** Top-level JSON shape: {@code {"patterns": [ ... ]}}. */
    private static final class Catalog {
        public List<RawEntry> patterns;
    }

    private static final class RawEntry {
        public String pattern;
        public String intent;
        public String problem;
        public List<String> aliases;
    }
}
