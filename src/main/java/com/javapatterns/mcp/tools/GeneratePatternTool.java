package com.javapatterns.mcp.tools;

import com.javapatterns.mcp.catalog.Pattern;
import com.javapatterns.mcp.generate.PatternGenerator;
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
 * Tool {@code generate_pattern}: produces a tailored Java implementation of
 * a chosen pattern with the user's package name and type name(s).
 *
 * <p>Input schema:</p>
 * <pre>
 * {
 *   "pattern":     "singleton" | "builder" | "strategy" | "observer" | "factory-method",
 *   "packageName": "com.acme.something"  (optional, empty = default package),
 *   "typeName":    "Logger"              (required — the main domain type),
 *   "extra":       { ... }               (reserved for future pattern-specific params)
 * }
 * </pre>
 *
 * <p>Output (single text content block, JSON-encoded):</p>
 * <pre>
 * {
 *   "pattern":    "Singleton",
 *   "category":   "Creational",
 *   "fileCount":  1,
 *   "files": [
 *     { "fileName": "Logger.java",
 *       "packageName": "com.acme.something",
 *       "source": "package com.acme.something;\n..." }
 *   ]
 * }
 * </pre>
 *
 * <p>Patterns without a template return an error result with a hint to
 * call {@code pattern_examples} instead.</p>
 */
public final class GeneratePatternTool {

    public static final String NAME = "generate_pattern";

    private final McpJsonMapper jsonMapper;
    private final PatternGenerator generator;

    public GeneratePatternTool(PatternGenerator generator, McpJsonMapper jsonMapper) {
        this.generator = Objects.requireNonNull(generator, "generator");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
    }

    public McpServerFeatures.SyncToolSpecification specification() {
        Tool tool = Tool.builder(NAME)
            .description(
                "Generate a customised Java implementation of a design pattern, " +
                "with the user's package name and main type name. Returns the " +
                "rendered source code, ready to compile. Supported patterns in " +
                "this build: " + supportedSlugList() + ". Other patterns return " +
                "a tool error directing the caller to `pattern_examples`.")
            .inputSchema(JsonSchema.builder()
                .type("object")
                .properties(Map.of(
                    "pattern", Map.of(
                        "type", "string",
                        "description",
                        "Pattern identifier. One of: singleton, builder, strategy, observer, factory-method. " +
                        "Enum names, slugs and display names are all accepted (case-insensitive)."
                    ),
                    "typeName", Map.of(
                        "type", "string",
                        "description",
                        "The main type name to use in the generated code " +
                        "(e.g. 'Logger' for Singleton, 'Pizza' for Builder)."
                    ),
                    "packageName", Map.of(
                        "type", "string",
                        "description",
                        "Java package for the generated sources. Empty means default package."
                    )
                ))
                .required(List.of("pattern", "typeName"))
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
            String typeName = stringArg(args, "typeName");
            String packageName = stringArg(args, "packageName");

            if (patternKey == null) return errorResult("Missing required argument: 'pattern'.");
            if (typeName == null)   return errorResult("Missing required argument: 'typeName'.");
            if (!isJavaIdentifier(typeName)) {
                return errorResult("'typeName' must be a valid Java identifier (got: " + typeName + ").");
            }
            if (packageName != null && !packageName.isEmpty() && !isJavaPackageName(packageName)) {
                return errorResult("'packageName' must be a valid Java package (got: " + packageName + ").");
            }

            Pattern pattern;
            try {
                pattern = Pattern.fromKey(patternKey);
            } catch (IllegalArgumentException e) {
                return errorResult("Unknown pattern '" + patternKey + "'.");
            }
            if (!PatternGenerator.SUPPORTED.contains(pattern)) {
                return errorResult(
                    "No generator template wired yet for " + pattern.displayName() +
                    ". Supported in this build: " + supportedSlugList() +
                    ". For a canonical reference implementation use the `pattern_examples` tool.");
            }

            Map<String, String> params = new LinkedHashMap<>();
            params.put("TYPE_NAME", typeName);
            params.put("PACKAGE_NAME", packageName == null ? "" : packageName);

            List<PatternGenerator.GeneratedFile> generated = generator.generate(pattern, params);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("pattern", pattern.displayName());
            payload.put("category", pattern.category().displayName());
            payload.put("fileCount", generated.size());
            payload.put("files", generated.stream().map(g -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("fileName", g.fileName());
                m.put("packageName", g.packageName());
                m.put("source", g.source());
                return m;
            }).toList());

            String json = jsonMapper.writeValueAsString(payload);
            return CallToolResult.builder()
                .content(List.of(TextContent.builder(json).build()))
                .isError(false)
                .build();
        } catch (Exception e) {
            return errorResult("Internal error: " + e.getMessage());
        }
    }

    private static String supportedSlugList() {
        return PatternGenerator.SUPPORTED.stream()
            .map(Pattern::slug)
            .sorted()
            .reduce((a, b) -> a + ", " + b)
            .orElse("(none)");
    }

    private static String stringArg(Map<String, Object> args, String name) {
        if (args == null) return null;
        Object v = args.get(name);
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /** Single-identifier check — letters, digits and underscores, must not start with a digit. */
    static boolean isJavaIdentifier(String s) {
        if (s == null || s.isEmpty()) return false;
        if (!Character.isJavaIdentifierStart(s.charAt(0))) return false;
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) return false;
        }
        return true;
    }

    /** Dotted package name — every segment is a valid Java identifier. */
    static boolean isJavaPackageName(String s) {
        if (s == null || s.isEmpty()) return false;
        for (String segment : s.split("\\.")) {
            if (!isJavaIdentifier(segment)) return false;
        }
        return true;
    }

    private static CallToolResult errorResult(String message) {
        return CallToolResult.builder()
            .content(List.of(TextContent.builder(message).build()))
            .isError(true)
            .build();
    }
}
