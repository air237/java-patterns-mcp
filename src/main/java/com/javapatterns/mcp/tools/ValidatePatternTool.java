package com.javapatterns.mcp.tools;

import com.javapatterns.mcp.catalog.Pattern;
import com.javapatterns.mcp.validate.PatternValidationEngine;
import com.javapatterns.mcp.validate.ValidationIssue;
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
 * Tool {@code validate_pattern}: parses a Java source and checks each
 * detected pattern instance against pattern-specific correctness rules.
 *
 * <p>Input schema:</p>
 * <pre>
 * {
 *   "source":  "package ...; class Foo { ... }",   // required
 *   "pattern": "singleton" | "builder" | "observer" | null
 *              // optional — if omitted, validates every supported pattern
 * }
 * </pre>
 *
 * <p>Output (single text content block, JSON-encoded):</p>
 * <pre>
 * {
 *   "supportedPatterns": ["Builder", "Observer", "Singleton"],
 *   "issueCount": 2,
 *   "errors": 1, "warnings": 1, "infos": 0,
 *   "issues": [
 *     { "pattern": "Singleton", "className": "Cache", "line": 5,
 *       "severity": "ERROR",
 *       "issue": "...", "suggestion": "..." }
 *   ]
 * }
 * </pre>
 *
 * <p>An empty {@code issues} array means "the implementation matches
 * the rules this validator encodes". A non-empty list does not mean
 * the code is broken — only that the validator's heuristic fired.
 * Read each issue's severity + suggestion before changing anything.</p>
 */
public final class ValidatePatternTool {

    public static final String NAME = "validate_pattern";

    private final PatternValidationEngine engine;
    private final McpJsonMapper jsonMapper;

    public ValidatePatternTool(PatternValidationEngine engine, McpJsonMapper jsonMapper) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
    }

    public McpServerFeatures.SyncToolSpecification specification() {
        Tool tool = Tool.builder(NAME)
            .description(
                "Validate the implementation quality of design pattern instances in a Java " +
                "source. Returns issues grouped by ERROR / WARNING / INFO, each with a " +
                "concrete suggestion. Pass `pattern` to focus on one; omit to validate " +
                "every supported pattern in the source. Supported in this build: " +
                supportedSlugList() + ".")
            .inputSchema(JsonSchema.builder()
                .type("object")
                .properties(Map.of(
                    "source", Map.of(
                        "type", "string",
                        "description", "The full Java source text to validate."
                    ),
                    "pattern", Map.of(
                        "type", "string",
                        "description",
                        "Optional pattern identifier. If omitted, every supported pattern is " +
                        "validated. Accepts enum names, slugs and display names."
                    )
                ))
                .required(List.of("source"))
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
            if (source == null) {
                return errorResult("Missing required argument: 'source'.");
            }
            String patternKey = stringArg(args, "pattern");

            List<ValidationIssue> issues;
            try {
                if (patternKey == null) {
                    issues = engine.validateAll(source);
                } else {
                    Pattern pattern;
                    try {
                        pattern = Pattern.fromKey(patternKey);
                    } catch (IllegalArgumentException e) {
                        return errorResult("Unknown pattern '" + patternKey + "'.");
                    }
                    if (!engine.supportedPatterns().contains(pattern)) {
                        return errorResult(
                            "No validator for " + pattern.displayName() +
                            ". Supported: " + supportedSlugList() + ".");
                    }
                    issues = engine.validateOne(source, pattern);
                }
            } catch (PatternValidationEngine.ValidationException e) {
                return errorResult("Failed to parse source: " + e.getMessage());
            }

            long errors   = issues.stream().filter(i -> i.severity().name().equals("ERROR")).count();
            long warnings = issues.stream().filter(i -> i.severity().name().equals("WARNING")).count();
            long infos    = issues.stream().filter(i -> i.severity().name().equals("INFO")).count();

            List<String> supported = engine.supportedPatterns().stream()
                .map(Pattern::displayName)
                .sorted()
                .toList();

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("supportedPatterns", supported);
            payload.put("issueCount", issues.size());
            payload.put("errors", errors);
            payload.put("warnings", warnings);
            payload.put("infos", infos);
            payload.put("issues", issues.stream().map(this::toJsonModel).toList());

            String json = jsonMapper.writeValueAsString(payload);
            return CallToolResult.builder()
                .content(List.of(TextContent.builder(json).build()))
                .isError(false)
                .build();
        } catch (Exception e) {
            return errorResult("Internal error: " + e.getMessage());
        }
    }

    private Map<String, Object> toJsonModel(ValidationIssue i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pattern", i.pattern().displayName());
        m.put("className", i.className());
        m.put("line", i.line());
        m.put("severity", i.severity().name());
        m.put("issue", i.issue());
        m.put("suggestion", i.suggestion());
        return m;
    }

    private static String supportedSlugList() {
        return PatternValidationEngine.getInstance().supportedPatterns().stream()
            .map(Pattern::slug)
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
