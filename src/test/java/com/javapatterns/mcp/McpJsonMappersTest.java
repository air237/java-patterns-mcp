package com.javapatterns.mcp;

import io.modelcontextprotocol.json.McpJsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the JSON mapper service-loader plumbing works at test time —
 * i.e. that {@code mcp-json-jackson3} is on the classpath and its
 * {@code META-INF/services/io.modelcontextprotocol.json.McpJsonMapperSupplier}
 * is reachable.
 *
 * <p>This is the test-time analogue of the runtime check we depend on in
 * {@link JavaPatternsMcpServer#main}. The shaded fat-jar has its own
 * {@code ServicesResourceTransformer} smoke test in {@code mvn verify}.</p>
 */
class McpJsonMappersTest {

    @Test
    @DisplayName("defaultMapper() returns a mapper that can round-trip a value")
    void defaultMapperRoundTrip() throws Exception {
        McpJsonMapper mapper = McpJsonMappers.defaultMapper();
        assertThat(mapper).isNotNull();

        String json = mapper.writeValueAsString(java.util.Map.of("hello", "world"));
        assertThat(json).contains("\"hello\"").contains("\"world\"");

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> roundTrip = mapper.readValue(json, java.util.Map.class);
        assertThat(roundTrip).containsEntry("hello", "world");
    }
}
