package com.javapatterns.mcp.tools;

import com.javapatterns.mcp.McpJsonMappers;
import com.javapatterns.mcp.catalog.Pattern;
import com.javapatterns.mcp.generate.PatternGenerator;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratePatternToolTest {

    private final McpJsonMapper mapper = McpJsonMappers.defaultMapper();
    private final GeneratePatternTool tool =
        new GeneratePatternTool(PatternGenerator.getInstance(), mapper);

    @Test
    @DisplayName("tool metadata exposes the expected name and supported list")
    void toolMetadata() {
        var t = tool.specification().tool();
        assertThat(t.name()).isEqualTo("generate_pattern");
        assertThat(t.description()).containsIgnoringCase("Generate");
    }

    @Test
    @DisplayName("singleton: generated source declares the chosen type and package")
    void singletonContainsChosenIdentifiers() throws Exception {
        CallToolResult r = tool.handle(Map.of(
            "pattern", "singleton",
            "typeName", "Logger",
            "packageName", "com.acme.util"
        ));
        assertThat(r.isError()).isFalse();
        Map<String, Object> payload = parse(((TextContent) r.content().get(0)).text());
        assertThat(payload.get("pattern")).isEqualTo("Singleton");
        assertThat(payload.get("fileCount")).isEqualTo(1);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) payload.get("files");
        Map<String, Object> file = files.get(0);
        assertThat(file.get("fileName")).isEqualTo("Logger.java");
        String src = (String) file.get("source");
        assertThat(src)
            .contains("package com.acme.util;")
            .contains("public final class Logger")
            .contains("private static final Logger INSTANCE")
            .doesNotContain("${");
    }

    @Test
    @DisplayName("default package: PACKAGE_DECL is empty when packageName is omitted")
    void defaultPackageOmitsDeclaration() throws Exception {
        CallToolResult r = tool.handle(Map.of(
            "pattern", "singleton",
            "typeName", "Bus"
        ));
        assertThat(r.isError()).isFalse();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) parse(((TextContent) r.content().get(0)).text()).get("files");
        String src = (String) files.get(0).get("source");
        assertThat(src).doesNotContain("package ");
    }

    @Test
    @DisplayName("each supported pattern produces sources that compile standalone with a String type parameter")
    void allSupportedPatternsCompile(@TempDir Path classOutput) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();

        for (Pattern p : PatternGenerator.SUPPORTED) {
            // Use String for parameterised patterns (Strategy, Observer) so the
            // generated code stays self-contained — no user-defined domain
            // type needed to compile it.
            String typeName = (p == Pattern.STRATEGY || p == Pattern.OBSERVER) ? "String" : "Demo";
            // Builder + Singleton + FactoryMethod want a user-defined type
            // name (not "String"), which is fine — they don't reference it
            // as a generic parameter, only as a class name.
            CallToolResult r = tool.handle(Map.of(
                "pattern", p.slug(),
                "typeName", typeName,
                "packageName", "com.gen." + p.slug().replace("-", "")
            ));
            assertThat(r.isError())
                .as("generation must succeed for " + p)
                .isFalse();

            Map<String, Object> payload;
            try {
                payload = parse(((TextContent) r.content().get(0)).text());
            } catch (Exception e) {
                throw new AssertionError(e);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> files = (List<Map<String, Object>>) payload.get("files");
            compileFiles(compiler, p, files, classOutput);
        }
    }

    @Test
    @DisplayName("missing typeName is a tool error")
    void missingTypeNameIsError() {
        CallToolResult r = tool.handle(Map.of("pattern", "singleton"));
        assertThat(r.isError()).isTrue();
    }

    @Test
    @DisplayName("invalid typeName is a tool error")
    void invalidTypeNameIsError() {
        CallToolResult r = tool.handle(Map.of(
            "pattern", "singleton",
            "typeName", "1NotJavaIdent"
        ));
        assertThat(r.isError()).isTrue();
    }

    @Test
    @DisplayName("unsupported pattern returns a clear error directing the caller to pattern_examples")
    void unsupportedPatternIsError() {
        // Group-C pattern — intentionally not in PatternGenerator.SUPPORTED.
        CallToolResult r = tool.handle(Map.of(
            "pattern", "prototype",
            "typeName", "Shape"
        ));
        assertThat(r.isError()).isTrue();
        String text = ((TextContent) r.content().get(0)).text();
        assertThat(text).containsIgnoringCase("pattern_examples");
    }

    private void compileFiles(JavaCompiler compiler, Pattern pattern, List<Map<String, Object>> files, Path classOutput) {
        List<JavaFileObject> sources = new ArrayList<>(files.size());
        for (Map<String, Object> f : files) {
            String fileName = (String) f.get("fileName");
            String pkg = (String) f.get("packageName");
            String typeName = fileName.replace(".java", "");
            String fqn = (pkg == null || pkg.isEmpty()) ? typeName : pkg + "." + typeName;
            sources.add(new InMemorySource(fqn, (String) f.get("source")));
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, java.nio.charset.StandardCharsets.UTF_8)) {
            // Redirect generated .class files into the per-test @TempDir so the
            // project root never gets polluted (regression-fix for the stray
            // *.class files that used to land next to pom.xml).
            fm.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classOutput));

            JavaCompiler.CompilationTask task = compiler.getTask(
                null, fm, diagnostics, List.of("--release", "21"), null, sources);
            boolean ok = task.call();
            if (!ok) {
                StringBuilder report = new StringBuilder("Generated source did not compile for " + pattern + ":\n");
                for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                    report.append("  ").append(d.toString()).append('\n');
                }
                throw new AssertionError(report.toString());
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String json) throws Exception {
        return mapper.readValue(json, Map.class);
    }

    private static final class InMemorySource extends SimpleJavaFileObject {
        private final String source;
        InMemorySource(String fqn, String source) {
            super(URI.create("string:///" + fqn.replace('.', '/') + ".java"), Kind.SOURCE);
            this.source = source;
        }
        @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return source; }
    }
}
