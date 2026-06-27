package com.javapatterns.mcp.tools;

import com.javapatterns.mcp.McpJsonMappers;
import com.javapatterns.mcp.detect.PatternDetectionEngine;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the batch-mode upgrade of {@code detect_pattern}: a single tool
 * invocation now accepts {@code source}, {@code paths}, or {@code directory}
 * and returns one consolidated payload with per-file labels.
 */
class DetectPatternToolTest {

    private static final String SINGLETON_SRC = """
        package demo;
        public final class Logger {
            private static final Logger INSTANCE = new Logger();
            private Logger() {}
            public static Logger getInstance() { return INSTANCE; }
        }
        """;

    private static final String BUILDER_SRC = """
        package demo;
        public final class Pizza {
            private final String dough;
            private Pizza(Builder b) { this.dough = b.dough; }
            public static final class Builder {
                private String dough;
                public Builder dough(String d) { this.dough = d; return this; }
                public Pizza build() { return new Pizza(this); }
            }
        }
        """;

    private static final String BROKEN_SRC = "this is not valid Java %%%";

    private final McpJsonMapper mapper = McpJsonMappers.defaultMapper();
    private final DetectPatternTool tool =
        new DetectPatternTool(PatternDetectionEngine.getInstance(), mapper);

    @Test
    @DisplayName("tool metadata advertises source / paths / directory")
    void toolMetadata() {
        Tool t = tool.specification().tool();
        assertThat(t.name()).isEqualTo("detect_pattern");
        assertThat(t.description())
            .contains("source")
            .contains("paths")
            .contains("directory");
        assertThat(t.inputSchema()).isNotNull();
    }

    @Test
    @DisplayName("single 'source' input still works (backward compatibility)")
    void singleSourceMode() throws Exception {
        CallToolResult result = tool.handle(Map.of("source", SINGLETON_SRC));
        assertThat(result.isError()).isFalse();
        Map<String, Object> payload = parsePayload(result);

        assertThat(asInt(payload, "filesAnalyzed")).isEqualTo(1);
        assertThat(asInt(payload, "detectionCount")).isGreaterThan(0);

        List<Map<String, Object>> hits = asList(payload, "detected");
        Map<String, Object> firstHit = hits.get(0);
        assertThat(firstHit.get("file")).isEqualTo("<source>");
        assertThat(firstHit.get("pattern")).isEqualTo("Singleton");
        assertThat(asList(payload, "errors")).isEmpty();
    }

    @Test
    @DisplayName("'paths' input analyses multiple files in one call")
    void multiplePathsMode(@TempDir Path tmp) throws Exception {
        Path singleton = Files.writeString(tmp.resolve("Logger.java"), SINGLETON_SRC);
        Path builder   = Files.writeString(tmp.resolve("Pizza.java"),  BUILDER_SRC);

        CallToolResult result = tool.handle(Map.of(
            "paths", List.of(singleton.toString(), builder.toString())));

        assertThat(result.isError()).isFalse();
        Map<String, Object> payload = parsePayload(result);

        assertThat(asInt(payload, "filesAnalyzed")).isEqualTo(2);
        assertThat(asList(payload, "errors")).isEmpty();

        List<Map<String, Object>> hits = asList(payload, "detected");
        // Every detection now carries the originating file path.
        for (Map<String, Object> hit : hits) {
            String file = (String) hit.get("file");
            assertThat(file).endsWith(".java");
            assertThat(file).isIn(singleton.toString(), builder.toString());
        }

        // Both patterns must be found somewhere in the batch.
        List<String> patterns = hits.stream()
            .map(h -> (String) h.get("pattern"))
            .toList();
        assertThat(patterns).contains("Singleton", "Builder");
    }

    @Test
    @DisplayName("'directory' input walks recursively and uses relative file labels")
    void directoryMode(@TempDir Path tmp) throws Exception {
        Path sub = Files.createDirectories(tmp.resolve("sub"));
        Files.writeString(tmp.resolve("Logger.java"), SINGLETON_SRC);
        Files.writeString(sub.resolve("Pizza.java"),  BUILDER_SRC);
        // A non-java file is ignored.
        Files.writeString(tmp.resolve("notes.md"), "ignored");

        CallToolResult result = tool.handle(Map.of("directory", tmp.toString()));

        assertThat(result.isError()).isFalse();
        Map<String, Object> payload = parsePayload(result);
        assertThat(asInt(payload, "filesAnalyzed")).isEqualTo(2);

        // Labels are relative to the supplied directory.
        List<String> files = asList(payload, "detected").stream()
            .map(h -> (String) h.get("file"))
            .toList();
        assertThat(files).allSatisfy(f -> assertThat(f).doesNotContain(tmp.toString()));
        assertThat(files).anyMatch("Logger.java"::equals);
        assertThat(files).anyMatch(f -> f.endsWith("Pizza.java")); // sub/Pizza.java
    }

    @Test
    @DisplayName("parse failure in ONE file does not abort the batch")
    void perFileParseErrorIsolated(@TempDir Path tmp) throws Exception {
        Path good   = Files.writeString(tmp.resolve("Good.java"),   SINGLETON_SRC);
        Path broken = Files.writeString(tmp.resolve("Broken.java"), BROKEN_SRC);

        CallToolResult result = tool.handle(Map.of(
            "paths", List.of(good.toString(), broken.toString())));

        assertThat(result.isError()).isFalse();
        Map<String, Object> payload = parsePayload(result);

        assertThat(asInt(payload, "filesAnalyzed"))
            .as("only the good file is counted as analysed")
            .isEqualTo(1);
        assertThat(asInt(payload, "detectionCount")).isGreaterThan(0);

        List<Map<String, Object>> errors = asList(payload, "errors");
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).get("file")).isEqualTo(broken.toString());
        assertThat((String) errors.get(0).get("message")).isNotEmpty();
    }

    @Test
    @DisplayName("non-existent directory yields a clean error entry (not an exception)")
    void nonExistentDirectory() throws Exception {
        CallToolResult result = tool.handle(Map.of(
            "directory", "/this/path/definitely/does/not/exist/abc123xyz"));

        assertThat(result.isError()).isFalse();
        Map<String, Object> payload = parsePayload(result);
        assertThat(asInt(payload, "filesAnalyzed")).isZero();
        assertThat(asList(payload, "errors")).hasSize(1);
    }

    @Test
    @DisplayName("missing input is reported as a user-facing error")
    void missingInputIsError() {
        CallToolResult result = tool.handle(Map.of());
        assertThat(result.isError()).isTrue();
        String message = ((TextContent) result.content().get(0)).text();
        assertThat(message).contains("source").contains("paths").contains("directory");
    }

    @Test
    @DisplayName("specifying multiple inputs at once is rejected")
    void multipleInputsRejected() {
        CallToolResult result = tool.handle(Map.of(
            "source", SINGLETON_SRC,
            "directory", "/tmp"));
        assertThat(result.isError()).isTrue();
        String message = ((TextContent) result.content().get(0)).text();
        assertThat(message).containsIgnoringCase("not several at once");
    }

    // ------------------------------------------------------------------ utils

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(CallToolResult result) throws Exception {
        TextContent tc = (TextContent) result.content().get(0);
        return mapper.readValue(tc.text(), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asList(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        if (v == null) return List.of();
        return (List<Map<String, Object>>) v;
    }

    private static int asInt(Map<String, Object> payload, String key) {
        return ((Number) payload.get(key)).intValue();
    }
}
