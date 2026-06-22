package com.javapatterns.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the {@code java-patterns-mcp} server.
 *
 * <p>This is a placeholder for Phase 0 (project skeleton). The actual MCP
 * server bootstrap — stdio transport, tool registration, lifecycle — is
 * implemented in Phase 1.</p>
 *
 * <p>Once Phase 1 lands, this class will:</p>
 * <ol>
 *   <li>Create a {@code StdioServerTransportProvider}.</li>
 *   <li>Build an {@code McpSyncServer} via {@code McpServer.sync(...)}.</li>
 *   <li>Register tools: {@code list_patterns}, {@code pattern_examples},
 *       {@code generate_pattern}, {@code detect_pattern},
 *       {@code validate_pattern}, {@code refactor_to_pattern}.</li>
 *   <li>Block on stdin until the client closes the transport.</li>
 * </ol>
 */
public final class JavaPatternsMcpServer {

    private static final Logger log = LoggerFactory.getLogger(JavaPatternsMcpServer.class);

    private JavaPatternsMcpServer() {
        // utility / entry-point class
    }

    public static void main(String[] args) {
        log.info("java-patterns-mcp starting (Phase 0 skeleton — no tools wired yet)");
        log.warn("MCP server bootstrap arrives in Phase 1.");
        // Intentionally exit immediately for now; Phase 1 will block on stdio.
    }
}
