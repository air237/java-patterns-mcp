package com.javapatterns.mcp.tools;

import com.javapatterns.mcp.refactor.PatternRefactoringEngine;
import com.javapatterns.mcp.refactor.RefactoringId;
import com.javapatterns.mcp.refactor.RefactoringResult;
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
import java.util.stream.Collectors;

/**
 * Tool {@code refactor_to_pattern}: applies an AST-level refactoring
 * to a Java source and returns the rewritten code together with a
 * change log.
 *
 * <p>Each refactoring is idempotent — applying the same refactoring
 * twice on the same source produces {@code changed=false} the second
 * time.</p>
 *
 * <p>Input schema:</p>
 * <pre>
 * {
 *   "source":      "package ...; class Foo { ... }",   // required
 *   "refactoring": "singleton-make-ctor-private"       // required slug
 * }
 * </pre>
 *
 * <p>Output (single text content block, JSON-encoded):</p>
 * <pre>
 * {
 *   "refactoring":    "singleton-make-ctor-private",
 *   "pattern":        "Singleton",
 *   "changed":        true,
 *   "changeCount":    1,
 *   "changes":        ["Cache: constructor at line 3 made private"],
 *   "newSource":      "..."
 * }
 * </pre>
 */
public final class RefactorPatternTool {

    public static final String NAME = "refactor_to_pattern";

    private final PatternRefactoringEngine engine;
    private final McpJsonMapper jsonMapper;

    public RefactorPatternTool(PatternRefactoringEngine engine, McpJsonMapper jsonMapper) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
    }

    public McpServerFeatures.SyncToolSpecification specification() {
        Tool tool = Tool.builder(NAME)
            .description(
                "Apply a small, idempotent AST refactoring to a Java source. Returns " +
                "the rewritten code plus a change log. Use validate_pattern first to " +
                "discover which refactoring to apply. Supported in this build: " +
                supportedSlugList() + ".")
            .inputSchema(JsonSchema.builder()
                .type("object")
                .properties(Map.of(
                    "source", Map.of(
                        "type", "string",
                        "description", "The Java source to rewrite."
                    ),
                    "refactoring", Map.of(
                        "type", "string",
                        "description",
                        "Refactoring identifier. One of: " + supportedSlugList() + "."
                    )
                ))
                .required(List.of("source", "refactoring"))
                .build())
            .build();

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler((exchange, request) -> handle(request == null ? Map.of() : request.arguments()))
            .build();
    }

    CallToolResult handle(Map<String, Object> args) {
        try {
            String source = stringArg(args, "source");
            String refKey = stringArg(args, "refactoring");
            if (source == null) return errorResult("Missing required argument: 'source'.");
            if (refKey == null) return errorResult("Missing required argument: 'refactoring'.");

            RefactoringId id;
            try {
                id = RefactoringId.fromKey(refKey);
            } catch (IllegalArgumentException e) {
                return errorResult("Unknown refactoring '" + refKey + "'. " +
                    "Supported: " + supportedSlugList() + ".");
            }

            RefactoringResult result;
            try {
                result = engine.apply(source, id);
            } catch (PatternRefactoringEngine.RefactoringException e) {
                return errorResult(e.getMessage());
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("refactoring", id.slug());
            payload.put("pattern", id.pattern().displayName());
            payload.put("changed", result.changed());
            payload.put("changeCount", result.changes().size());
            payload.put("changes", result.changes());
            payload.put("newSource", result.newSource());

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
        return PatternRefactoringEngine.getInstance().supported().stream()
            .map(RefactoringId::slug)
            .sorted()
            .collect(Collectors.joining(", "));
    }

    private static String stringArg(Map<String, Object> args, String name) {
        if (args == null) return null;
        Object v = args.get(name);
        if (v == null) return null;
        String s = v.toString();
        return s.isEmpty() ? null : s;
    }

    private static CallToolResult errorResult(String message) {
        return CallToolResult.builder()
            .content(List.of(TextContent.builder(message).build()))
            .isError(true)
            .build();
    }
}
