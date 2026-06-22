package com.javapatterns.mcp;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.McpJsonMapperSupplier;

import java.util.ServiceLoader;

/**
 * Resolves the default {@link McpJsonMapper} via the JDK {@link ServiceLoader}
 * mechanism.
 *
 * <p>The MCP Java SDK ships its JSON implementation as a separate artifact
 * ({@code mcp-json-jackson3} by default in v2.0.0), registered via a
 * {@code META-INF/services/io.modelcontextprotocol.json.McpJsonMapperSupplier}
 * service file. We don't hard-link to {@code JacksonMcpJsonMapperSupplier}
 * directly so that swapping in {@code mcp-json-jackson2} would Just Work without
 * code changes.</p>
 *
 * <p><b>Heads-up for shaded jars:</b> the Maven Shade plugin must merge
 * {@code META-INF/services/*} files, which it does via the
 * {@code ServicesResourceTransformer} (already configured in {@code pom.xml}).
 * Without that transformer this lookup would fail at runtime inside the
 * fat-jar even though it works in {@code mvn compile}.</p>
 */
public final class McpJsonMappers {

    private McpJsonMappers() { /* utility */ }

    /**
     * @return the JSON mapper supplied by the first {@link McpJsonMapperSupplier}
     *         found on the classpath.
     * @throws IllegalStateException if no supplier is registered (i.e. neither
     *         {@code mcp-json-jackson3} nor {@code mcp-json-jackson2} is on the
     *         classpath, or the service file was lost during shading).
     */
    public static McpJsonMapper defaultMapper() {
        return ServiceLoader.load(McpJsonMapperSupplier.class)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No McpJsonMapperSupplier found on the classpath. Make sure " +
                "mcp-json-jackson3 (or mcp-json-jackson2) is a runtime dependency, " +
                "and that ServicesResourceTransformer is configured in maven-shade-plugin."))
            .get();
    }
}
