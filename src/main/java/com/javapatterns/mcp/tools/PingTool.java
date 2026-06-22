package com.javapatterns.mcp.tools;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.util.List;
import java.util.Map;

/**
 * Health-check tool: clients can call {@code ping} to verify the server is alive
 * and report its identity, version, and the list of currently registered tools.
 *
 * <p>Useful for:</p>
 * <ul>
 *   <li>Smoke-testing the MCP stdio handshake from a real client (OpenCode,
 *       Claude Desktop, Cursor, etc.).</li>
 *   <li>Asserting in integration tests that the server starts and responds.</li>
 *   <li>Debugging tool-registration drift — the response lists every tool
 *       currently exposed by the server.</li>
 * </ul>
 *
 * <p>Input: no arguments.</p>
 * <p>Output: a single TextContent block with a short status string.</p>
 */
public final class PingTool {

    /** Tool name as exposed over MCP. */
    public static final String NAME = "ping";

    private final String serverName;
    private final String serverVersion;
    private final java.util.function.Supplier<List<String>> registeredToolNames;

    public PingTool(String serverName,
                    String serverVersion,
                    java.util.function.Supplier<List<String>> registeredToolNames) {
        this.serverName = serverName;
        this.serverVersion = serverVersion;
        this.registeredToolNames = registeredToolNames;
    }

    public McpServerFeatures.SyncToolSpecification specification() {
        Tool tool = Tool.builder(NAME)
            .description(
                "Health-check tool. Returns server name, version, and the list of " +
                "currently registered MCP tools. Takes no arguments.")
            .inputSchema(JsonSchema.builder()
                .type("object")
                .properties(Map.of())
                .build())
            .build();

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler((exchange, request) -> {
                List<String> tools = registeredToolNames.get();
                String response = "%s %s — alive. Registered tools: %s"
                    .formatted(serverName, serverVersion, tools);
                return CallToolResult.builder()
                    .content(List.of(TextContent.builder(response).build()))
                    .isError(false)
                    .build();
            })
            .build();
    }
}
