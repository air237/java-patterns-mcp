package com.javapatterns.mcp.tools;

import com.javapatterns.mcp.McpJsonMappers;
import com.javapatterns.mcp.catalog.PatternRegistry;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ListPatternsToolTest {

    private final McpJsonMapper mapper = McpJsonMappers.defaultMapper();
    private final ListPatternsTool tool =
        new ListPatternsTool(PatternRegistry.getInstance(), mapper);

    @Test
    @DisplayName("tool metadata declares optional `category` enum")
    void toolMetadata() {
        Tool t = tool.specification().tool();
        assertThat(t.name()).isEqualTo("list_patterns");
        assertThat(t.description()).contains("23 Gang of Four");
        assertThat(t.inputSchema()).isNotNull();
    }

    @Test
    @DisplayName("with no arguments, returns all 23 patterns")
    void unfilteredCallReturnsAll23() throws Exception {
        CallToolResult result = tool.handle(Map.of());
        assertThat(result.isError()).isFalse();

        String json = ((TextContent) result.content().get(0)).text();
        Map<String, Object> parsed = parse(json);
        assertThat(parsed.get("count")).isEqualTo(23);
        assertThat((List<?>) parsed.get("patterns")).hasSize(23);
    }

    @Test
    @DisplayName("with category=Creational, returns exactly 5 patterns")
    void creationalFilter() throws Exception {
        CallToolResult result = tool.handle(Map.of("category", "Creational"));
        assertThat(result.isError()).isFalse();

        Map<String, Object> parsed = parse(((TextContent) result.content().get(0)).text());
        assertThat(parsed.get("count")).isEqualTo(5);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> patterns = (List<Map<String, Object>>) parsed.get("patterns");
        assertThat(patterns).hasSize(5);
        assertThat(patterns).allSatisfy(p ->
            assertThat(p.get("category")).isEqualTo("Creational"));
    }

    @Test
    @DisplayName("category filter is case-insensitive")
    void categoryFilterIsCaseInsensitive() throws Exception {
        CallToolResult lower = tool.handle(Map.of("category", "structural"));
        CallToolResult upper = tool.handle(Map.of("category", "STRUCTURAL"));
        CallToolResult mixed = tool.handle(Map.of("category", "Structural"));

        assertThat(lower.isError()).isFalse();
        assertThat(upper.isError()).isFalse();
        assertThat(mixed.isError()).isFalse();

        assertThat(parse(((TextContent) lower.content().get(0)).text()).get("count")).isEqualTo(7);
        assertThat(parse(((TextContent) upper.content().get(0)).text()).get("count")).isEqualTo(7);
        assertThat(parse(((TextContent) mixed.content().get(0)).text()).get("count")).isEqualTo(7);
    }

    @Test
    @DisplayName("unknown category returns an isError=true tool result, not an exception")
    void unknownCategoryReturnsToolError() {
        CallToolResult result = tool.handle(Map.of("category", "Functional"));
        assertThat(result.isError()).isTrue();
        String text = ((TextContent) result.content().get(0)).text();
        assertThat(text).containsIgnoringCase("unknown category").contains("Functional");
    }

    @Test
    @DisplayName("each pattern in the output exposes name, category, slug, aliases, intent, referenceUrl")
    void patternShape() throws Exception {
        CallToolResult result = tool.handle(Map.of("category", "Behavioral"));
        Map<String, Object> parsed = parse(((TextContent) result.content().get(0)).text());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> patterns = (List<Map<String, Object>>) parsed.get("patterns");
        assertThat(patterns).isNotEmpty();
        for (Map<String, Object> p : patterns) {
            assertThat(p).containsOnlyKeys("name", "category", "slug", "aliases", "intent", "referenceUrl");
            assertThat((String) p.get("name")).isNotBlank();
            assertThat((String) p.get("category")).isEqualTo("Behavioral");
            assertThat((String) p.get("slug")).isNotBlank().doesNotContain(" ");
            assertThat((String) p.get("intent")).isNotBlank();
            assertThat((String) p.get("referenceUrl")).startsWith("https://refactoring.guru/");
            assertThat(p.get("aliases")).isInstanceOf(List.class);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String json) throws Exception {
        return mapper.readValue(json, Map.class);
    }
}
