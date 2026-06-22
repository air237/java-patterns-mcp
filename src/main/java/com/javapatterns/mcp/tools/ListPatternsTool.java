package com.javapatterns.mcp.tools;

import com.javapatterns.mcp.catalog.Pattern;
import com.javapatterns.mcp.catalog.PatternCategory;
import com.javapatterns.mcp.catalog.PatternMetadata;
import com.javapatterns.mcp.catalog.PatternRegistry;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Tool {@code list_patterns}: returns the catalog of the 23 Gang of Four design
 * patterns, optionally filtered by category.
 *
 * <p>Input schema:</p>
 * <pre>
 * {
 *   "category": "Creational" | "Structural" | "Behavioral"   // optional
 * }
 * </pre>
 *
 * <p>Output (single text content block, JSON-encoded):</p>
 * <pre>
 * {
 *   "count": 5,
 *   "patterns": [
 *     {
 *       "name": "Singleton",
 *       "category": "Creational",
 *       "slug": "singleton",
 *       "aliases": ["Holder", "Bill Pugh Singleton"],
 *       "intent": "Lets you ensure ...",
 *       "referenceUrl": "https://refactoring.guru/design-patterns/singleton"
 *     },
 *     ...
 *   ]
 * }
 * </pre>
 *
 * <p>Returning the payload as a JSON string inside a single {@code TextContent}
 * (rather than as structured tool output) is intentional: it keeps the tool
 * output compact, machine-parseable, <i>and</i> immediately readable in any
 * MCP client UI without extra rendering.</p>
 */
public final class ListPatternsTool {

    public static final String NAME = "list_patterns";

    private final PatternRegistry registry;
    private final io.modelcontextprotocol.json.McpJsonMapper jsonMapper;

    public ListPatternsTool(PatternRegistry registry,
                            io.modelcontextprotocol.json.McpJsonMapper jsonMapper) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
    }

    public McpServerFeatures.SyncToolSpecification specification() {
        Tool tool = Tool.builder(NAME)
            .description(
                "List the 23 Gang of Four design patterns with their intent, " +
                "category, aliases and reference URL. Optional argument " +
                "`category` filters the result: Creational, Structural, or Behavioral.")
            .inputSchema(JsonSchema.builder()
                .type("object")
                .properties(Map.of(
                    "category", Map.of(
                        "type", "string",
                        "description", "Filter by category. Allowed values: Creational, Structural, Behavioral. " +
                                       "Case-insensitive. Omit to return all 23 patterns.",
                        "enum", List.of("Creational", "Structural", "Behavioral",
                                        "creational", "structural", "behavioral")
                    )
                ))
                .build())
            .build();

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler((exchange, request) -> handle(request == null ? Map.of() : request.arguments()))
            .build();
    }

    // ─── impl ───────────────────────────────────────────────────────

    CallToolResult handle(Map<String, Object> args) {
        try {
            PatternCategory filter = resolveCategory(args);
            List<PatternMetadata> entries = (filter == null)
                ? registry.all()
                : registry.byCategory(filter);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("count", entries.size());
            payload.put("patterns", entries.stream().map(this::toJsonModel).toList());

            String json = jsonMapper.writeValueAsString(payload);
            return CallToolResult.builder()
                .content(List.of(TextContent.builder(json).build()))
                .isError(false)
                .build();
        } catch (IllegalArgumentException e) {
            return CallToolResult.builder()
                .content(List.of(TextContent.builder("Invalid argument: " + e.getMessage()).build()))
                .isError(true)
                .build();
        } catch (Exception e) {
            return CallToolResult.builder()
                .content(List.of(TextContent.builder("Internal error: " + e.getMessage()).build()))
                .isError(true)
                .build();
        }
    }

    private static PatternCategory resolveCategory(Map<String, Object> args) {
        if (args == null) return null;
        Object raw = args.get("category");
        if (raw == null) return null;
        String value = raw.toString().trim();
        if (value.isEmpty()) return null;
        for (PatternCategory c : PatternCategory.values()) {
            if (c.name().equalsIgnoreCase(value)
                || c.displayName().equalsIgnoreCase(value)) {
                return c;
            }
        }
        throw new IllegalArgumentException(
            "Unknown category '" + value + "'. Allowed: Creational, Structural, Behavioral.");
    }

    private Map<String, Object> toJsonModel(PatternMetadata md) {
        Pattern p = md.pattern();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.displayName());
        m.put("category", p.category().displayName());
        m.put("slug", p.slug());
        m.put("aliases", md.aliases());
        m.put("intent", md.intent());
        m.put("referenceUrl", p.referenceUrl());
        return m;
    }

    // expose for tests
    static Locale locale() { return Locale.ROOT; }
}
