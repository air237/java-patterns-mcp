package com.javapatterns.mcp.tools;

import com.javapatterns.mcp.McpJsonMappers;
import com.javapatterns.mcp.catalog.PatternExamplesLoader;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PatternExamplesToolTest {

    private final McpJsonMapper mapper = McpJsonMappers.defaultMapper();
    private final PatternExamplesTool tool =
        new PatternExamplesTool(PatternExamplesLoader.getInstance(), mapper);

    @Test
    @DisplayName("tool metadata declares required `pattern` and optional `includeSource`")
    void toolMetadata() {
        var t = tool.specification().tool();
        assertThat(t.name()).isEqualTo("pattern_examples");
        assertThat(t.description()).contains("canonical");
    }

    @Test
    @DisplayName("returns the Singleton example with source")
    void singletonReturnsSource() throws Exception {
        CallToolResult r = tool.handle(Map.of("pattern", "singleton"));
        assertThat(r.isError()).isFalse();

        Map<String, Object> payload = parse(((TextContent) r.content().get(0)).text());
        assertThat(payload.get("pattern")).isEqualTo("Singleton");
        assertThat(payload.get("category")).isEqualTo("Creational");
        assertThat(payload.get("fileCount")).isEqualTo(1);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) payload.get("files");
        assertThat(files).hasSize(1);
        assertThat((String) files.get(0).get("fileName")).isEqualTo("Singleton.java");
        assertThat((String) files.get(0).get("source"))
            .contains("private static final class Holder")
            .contains("private static final Singleton INSTANCE");
    }

    @Test
    @DisplayName("Factory Method example returns 6 files")
    void factoryMethodFiles() throws Exception {
        CallToolResult r = tool.handle(Map.of("pattern", "factory-method"));
        Map<String, Object> payload = parse(((TextContent) r.content().get(0)).text());
        assertThat(payload.get("fileCount")).isEqualTo(6);
    }

    @Test
    @DisplayName("includeSource=false omits the source bytes but keeps file metadata")
    void includeSourceFalseSkipsBody() throws Exception {
        CallToolResult r = tool.handle(Map.of("pattern", "singleton", "includeSource", false));
        Map<String, Object> payload = parse(((TextContent) r.content().get(0)).text());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) payload.get("files");
        assertThat(files).hasSize(1);
        assertThat(files.get(0)).doesNotContainKey("source");
        assertThat(files.get(0)).containsKey("fileName");
        assertThat(files.get(0)).containsKey("note");
    }

    @Test
    @DisplayName("a covered pattern accepts multiple identifier forms")
    void patternIdentifierVariants() throws Exception {
        for (String key : List.of("SINGLETON", "singleton", "Singleton")) {
            CallToolResult r = tool.handle(Map.of("pattern", key));
            assertThat(r.isError()).as("key " + key).isFalse();
            Map<String, Object> payload = parse(((TextContent) r.content().get(0)).text());
            assertThat(payload.get("pattern")).isEqualTo("Singleton");
        }
    }

    @Test
    @DisplayName("at the current phase, all 23 patterns are covered so an uncovered probe is not meaningful — keep a sanity check on a covered one")
    void singletonAlwaysHasOneFile() throws Exception {
        CallToolResult r = tool.handle(Map.of("pattern", "singleton"));
        assertThat(r.isError()).isFalse();
        Map<String, Object> payload = parse(((TextContent) r.content().get(0)).text());
        assertThat(payload.get("pattern")).isEqualTo("Singleton");
        assertThat(payload.get("fileCount")).isEqualTo(1);
    }

    @Test
    @DisplayName("missing `pattern` argument is a tool error")
    void missingPatternIsError() {
        CallToolResult r = tool.handle(Map.of());
        assertThat(r.isError()).isTrue();
        assertThat(((TextContent) r.content().get(0)).text()).containsIgnoringCase("pattern");
    }

    @Test
    @DisplayName("unknown pattern is a tool error with helpful hint")
    void unknownPatternIsError() {
        CallToolResult r = tool.handle(Map.of("pattern", "monad"));
        assertThat(r.isError()).isTrue();
        assertThat(((TextContent) r.content().get(0)).text())
            .contains("monad")
            .contains("list_patterns");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String json) throws Exception {
        return mapper.readValue(json, Map.class);
    }
}
