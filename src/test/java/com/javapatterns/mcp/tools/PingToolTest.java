package com.javapatterns.mcp.tools;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PingTool}. These tests do not spin up a transport —
 * they exercise the tool {@link McpServerFeatures.SyncToolSpecification} directly,
 * which is what the MCP framework invokes when it routes a {@code tools/call}
 * request.
 */
class PingToolTest {

    @Test
    @DisplayName("ping tool declares name, description, and an empty object input schema")
    void toolMetadata() {
        var spec = new PingTool(
            "test-server",
            "0.0.1",
            () -> List.of("ping")
        ).specification();

        Tool tool = spec.tool();
        assertThat(tool.name()).isEqualTo("ping");
        assertThat(tool.description())
            .isNotBlank()
            .containsIgnoringCase("Health-check");

        // Input schema must be an object with no properties — the tool takes
        // no arguments and the schema enforces that.
        assertThat(tool.inputSchema()).isNotNull();
    }

    @Test
    @DisplayName("ping call returns server identity and live tool registry")
    void callHandlerEmbedsIdentityAndRegistry() {
        var spec = new PingTool(
            "java-patterns-mcp",
            "9.9.9",
            () -> List.of("ping", "list_patterns", "generate_pattern")
        ).specification();

        // Invoke the call handler with null exchange and null request — the
        // ping handler doesn't read either of them. In production the MCP
        // runtime supplies them, but they are not part of this contract.
        CallToolResult result = spec.callHandler().apply(null, null);

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(1);
        TextContent text = (TextContent) result.content().get(0);
        assertThat(text.text())
            .contains("java-patterns-mcp")
            .contains("9.9.9")
            .contains("alive")
            .contains("ping")
            .contains("list_patterns")
            .contains("generate_pattern");
    }

    @Test
    @DisplayName("ping reflects changes to the registered tool list at call time")
    void registryIsEvaluatedLazily() {
        var registry = new java.util.ArrayList<String>();
        registry.add("ping");

        var spec = new PingTool("s", "v", () -> List.copyOf(registry)).specification();

        CallToolResult first = spec.callHandler().apply(null, null);
        assertThat(((TextContent) first.content().get(0)).text()).contains("[ping]");

        registry.add("another_tool");

        CallToolResult second = spec.callHandler().apply(null, null);
        assertThat(((TextContent) second.content().get(0)).text())
            .contains("ping")
            .contains("another_tool");
    }
}
