package com.javapatterns.mcp.tools;

import com.javapatterns.mcp.catalog.Pattern;
import com.javapatterns.mcp.catalog.PatternExample;
import com.javapatterns.mcp.catalog.PatternExamplesLoader;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Tool {@code pattern_examples}: returns canonical Java example sources for a
 * given design pattern.
 *
 * <p>Input schema:</p>
 * <pre>
 * {
 *   "pattern":      "Singleton" | "singleton" | "SINGLETON" | …   (required)
 *   "includeSource": true | false                                (optional, default true)
 * }
 * </pre>
 *
 * <p>Output (single text content block, JSON-encoded):</p>
 * <pre>
 * {
 *   "pattern": "Singleton",
 *   "category": "Creational",
 *   "fileCount": 1,
 *   "files": [
 *     {
 *       "fileName": "Singleton.java",
 *       "packageName": "com.javapatterns.examples.singleton",
 *       "note": "Thread-safe Bill-Pugh holder idiom",
 *       "source": "package com.javapatterns.examples.singleton;\n..."
 *     }
 *   ]
 * }
 * </pre>
 *
 * <p>If {@code includeSource=false}, the {@code source} field is omitted —
 * useful when the agent only wants to discover what files exist for a pattern
 * before fetching their bytes.</p>
 *
 * <p>Patterns that do not yet have an example return a {@code fileCount: 0}
 * payload with an empty {@code files} array, not an error. Use
 * {@code list_patterns} to see all 23, then call this tool to discover which
 * have examples wired up.</p>
 */
public final class PatternExamplesTool {

    public static final String NAME = "pattern_examples";

    private final PatternExamplesLoader loader;
    private final McpJsonMapper jsonMapper;

    public PatternExamplesTool(PatternExamplesLoader loader, McpJsonMapper jsonMapper) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
    }

    public McpServerFeatures.SyncToolSpecification specification() {
        Tool tool = Tool.builder(NAME)
            .description(
                "Return canonical, compilable Java example source(s) for a given " +
                "Gang of Four design pattern. Accepts enum names, slugs, or display names " +
                "(case-insensitive). When `includeSource` is false the file metadata is " +
                "returned without the source bytes — useful for discovery.")
            .inputSchema(JsonSchema.builder()
                .type("object")
                .properties(Map.of(
                    "pattern", Map.of(
                        "type", "string",
                        "description",
                        "Pattern identifier. Examples: 'singleton', 'Singleton', 'SINGLETON', " +
                        "'chain-of-responsibility', 'Chain of Responsibility'."
                    ),
                    "includeSource", Map.of(
                        "type", "boolean",
                        "description", "Include the file source bytes in the response. Defaults to true.",
                        "default", true
                    )
                ))
                .required(List.of("pattern"))
                .build())
            .build();

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler((exchange, request) -> handle(request == null ? Map.of() : request.arguments()))
            .build();
    }

    CallToolResult handle(Map<String, Object> args) {
        try {
            String patternKey = stringArg(args, "pattern");
            if (patternKey == null) {
                return errorResult("Missing required argument: 'pattern'.");
            }
            boolean includeSource = boolArg(args, "includeSource", true);

            Pattern pattern;
            try {
                pattern = Pattern.fromKey(patternKey);
            } catch (IllegalArgumentException e) {
                return errorResult("Unknown pattern '" + patternKey + "'. " +
                    "Use the `list_patterns` tool to see all supported pattern names.");
            }

            List<PatternExample> examples = loader.forPattern(pattern);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("pattern", pattern.displayName());
            payload.put("category", pattern.category().displayName());
            payload.put("fileCount", examples.size());
            payload.put("files", examples.stream()
                .map(ex -> toJsonModel(ex, includeSource))
                .toList());

            String json = jsonMapper.writeValueAsString(payload);
            return CallToolResult.builder()
                .content(List.of(TextContent.builder(json).build()))
                .isError(false)
                .build();
        } catch (Exception e) {
            return errorResult("Internal error: " + e.getMessage());
        }
    }

    private static Map<String, Object> toJsonModel(PatternExample ex, boolean includeSource) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fileName", ex.fileName());
        m.put("packageName", ex.packageName());
        m.put("note", ex.note());
        if (includeSource) {
            m.put("source", ex.source());
        }
        return m;
    }

    private static String stringArg(Map<String, Object> args, String name) {
        if (args == null) return null;
        Object v = args.get(name);
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static boolean boolArg(Map<String, Object> args, String name, boolean defaultValue) {
        if (args == null) return defaultValue;
        Object v = args.get(name);
        if (v == null) return defaultValue;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString());
    }

    private static CallToolResult errorResult(String message) {
        return CallToolResult.builder()
            .content(List.of(TextContent.builder(message).build()))
            .isError(true)
            .build();
    }
}
