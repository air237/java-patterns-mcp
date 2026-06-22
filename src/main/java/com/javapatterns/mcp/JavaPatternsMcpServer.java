package com.javapatterns.mcp;

import com.javapatterns.mcp.catalog.PatternRegistry;
import com.javapatterns.mcp.tools.ListPatternsTool;
import com.javapatterns.mcp.tools.PingTool;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Entry point for the {@code java-patterns-mcp} server.
 *
 * <p>Bootstraps an MCP synchronous server over stdio:</p>
 * <ol>
 *   <li>Creates a {@link StdioServerTransportProvider} that reads JSON-RPC
 *       frames from {@code stdin} and writes responses to {@code stdout}.</li>
 *   <li>Builds an {@link McpSyncServer} with the default Jackson 3 JSON mapper.</li>
 *   <li>Registers the initial tool set (currently just {@code ping} — Phase 1
 *       scope; pattern tools land in Phase 3+).</li>
 *   <li>Blocks the main thread until the client closes the transport
 *       (i.e. stdin reaches EOF).</li>
 * </ol>
 *
 * <p><b>Important:</b> stdio MCP uses {@code stdout} exclusively for JSON-RPC
 * frames. All logging is routed to {@code stderr} via {@code logback.xml} —
 * never write to {@code System.out} from this server.</p>
 */
public final class JavaPatternsMcpServer {

    private static final Logger log = LoggerFactory.getLogger(JavaPatternsMcpServer.class);

    static final String SERVER_NAME = "java-patterns-mcp";
    static final String SERVER_VERSION = "0.1.0";

    private JavaPatternsMcpServer() {
        // entry-point class
    }

    public static void main(String[] args) throws InterruptedException {
        log.info("{} {} starting (Phase 3: stdio + ping + list_patterns)", SERVER_NAME, SERVER_VERSION);

        McpJsonMapper jsonMapper = McpJsonMappers.defaultMapper();
        McpServerTransportProvider transport = new StdioServerTransportProvider(jsonMapper);

        McpSyncServer server = buildServer(transport, jsonMapper);
        log.info("MCP server ready, waiting for client over stdio.");

        // The stdio transport itself runs on background schedulers; the main thread
        // must stay alive until the JVM shutdown hook (triggered by the client
        // closing stdin) tears the process down.
        CountDownLatch shutdown = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received, closing MCP server gracefully.");
            try {
                server.closeGracefully();
            } catch (Exception e) {
                log.warn("Error during server graceful shutdown", e);
            } finally {
                shutdown.countDown();
            }
        }, "mcp-shutdown"));
        shutdown.await();
        log.info("Bye.");
    }

    /**
     * Builds the {@link McpSyncServer} with the given transport and registers
     * the currently wired tool set. Package-private so the integration test can
     * build a server backed by an alternative transport without going through stdio.
     */
    static McpSyncServer buildServer(McpServerTransportProvider transport, McpJsonMapper jsonMapper) {
        List<String> registeredTools = List.of(PingTool.NAME, ListPatternsTool.NAME);

        McpServerFeatures.SyncToolSpecification ping =
            new PingTool(SERVER_NAME, SERVER_VERSION, () -> registeredTools).specification();

        McpServerFeatures.SyncToolSpecification listPatterns =
            new ListPatternsTool(PatternRegistry.getInstance(), jsonMapper).specification();

        return McpServer.sync(transport)
            .serverInfo(SERVER_NAME, SERVER_VERSION)
            .tools(ping, listPatterns)
            .build();
    }
}
