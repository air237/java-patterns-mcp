package com.javapatterns.mcp.tools;

import com.javapatterns.mcp.catalog.Pattern;
import com.javapatterns.mcp.detect.DetectedPattern;
import com.javapatterns.mcp.detect.PatternDetectionEngine;
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
 * Tool {@code detect_pattern}: parses a Java source file and reports which
 * GoF design patterns its classes / interfaces participate in.
 *
 * <p>Input schema:</p>
 * <pre>
 * {
 *   "source": "package x.y;\nclass Foo { ... }"   // required
 * }
 * </pre>
 *
 * <p>Output (single text content block, JSON-encoded):</p>
 * <pre>
 * {
 *   "supportedPatterns": [ "Singleton", "Builder", ... ],
 *   "detected": [
 *     { "pattern": "Singleton",
 *       "className": "Logger",
 *       "startLine": 12,
 *       "confidence": 0.75,
 *       "evidence": ["private ctor", "static INSTANCE field of own type", ...] }
 *   ]
 * }
 * </pre>
 *
 * <p>Patterns without a detector yet are listed neither as supported nor
 * detected; check {@code supportedPatterns} to see what the current build
 * recognises.</p>
 */
public final class DetectPatternTool {

    public static final String NAME = "detect_pattern";

    private final PatternDetectionEngine engine;
    private final McpJsonMapper jsonMapper;

    public DetectPatternTool(PatternDetectionEngine engine, McpJsonMapper jsonMapper) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
    }

    public McpServerFeatures.SyncToolSpecification specification() {
        Tool tool = Tool.builder(NAME)
            .description(
                "Parse a Java source and report which GoF design patterns its classes / " +
                "interfaces participate in. Returns per-instance confidence (0–1) and " +
                "the structural signals that fired. Supported in this build: " +
                supportedSlugList() + ".")
            .inputSchema(JsonSchema.builder()
                .type("object")
                .properties(Map.of(
                    "source", Map.of(
                        "type", "string",
                        "description",
                        "The full Java source text to analyse. A single compilation unit; pass " +
                        "multiple files by calling this tool once per file."
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
            List<DetectedPattern> hits;
            try {
                hits = engine.detect(source);
            } catch (PatternDetectionEngine.DetectionException e) {
                return errorResult("Failed to parse source: " + e.getMessage());
            }

            List<String> supported = engine.supportedPatterns().stream()
                .map(Pattern::displayName)
                .sorted()
                .toList();

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("supportedPatterns", supported);
            payload.put("detectionCount", hits.size());
            payload.put("detected", hits.stream().map(this::toJsonModel).toList());

            String json = jsonMapper.writeValueAsString(payload);
            return CallToolResult.builder()
                .content(List.of(TextContent.builder(json).build()))
                .isError(false)
                .build();
        } catch (Exception e) {
            return errorResult("Internal error: " + e.getMessage());
        }
    }

    private Map<String, Object> toJsonModel(DetectedPattern d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pattern", d.pattern().displayName());
        m.put("category", d.pattern().category().displayName());
        m.put("className", d.className());
        m.put("startLine", d.startLine());
        m.put("confidence", round(d.confidence(), 3));
        m.put("evidence", d.evidence());
        return m;
    }

    private static double round(double v, int places) {
        double factor = Math.pow(10, places);
        return Math.round(v * factor) / factor;
    }

    private static String supportedSlugList() {
        return PatternDetectionEngine.getInstance().supportedPatterns().stream()
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
