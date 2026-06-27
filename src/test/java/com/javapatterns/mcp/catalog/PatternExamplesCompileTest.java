package com.javapatterns.mcp.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the canonical example sources bundled with this project actually
 * compile with the in-process {@link JavaCompiler}.
 *
 * <p>This is critical: it is easy to commit a pattern example that <i>reads</i>
 * fine but does not type-check. Running every example through {@code javac}
 * on every build catches that early.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PatternExamplesCompileTest {

    private final PatternExamplesLoader loader = PatternExamplesLoader.getInstance();

    @Test
    @DisplayName("at least the 5 Creational patterns have examples")
    void creationalPatternsAreCovered() {
        assertThat(loader.forPattern(Pattern.SINGLETON)).isNotEmpty();
        assertThat(loader.forPattern(Pattern.FACTORY_METHOD)).isNotEmpty();
        assertThat(loader.forPattern(Pattern.ABSTRACT_FACTORY)).isNotEmpty();
        assertThat(loader.forPattern(Pattern.BUILDER)).isNotEmpty();
        assertThat(loader.forPattern(Pattern.PROTOTYPE)).isNotEmpty();
    }

    @Test
    @DisplayName("all 7 Structural patterns have examples")
    void structuralPatternsAreCovered() {
        assertThat(loader.forPattern(Pattern.ADAPTER)).isNotEmpty();
        assertThat(loader.forPattern(Pattern.BRIDGE)).isNotEmpty();
        assertThat(loader.forPattern(Pattern.COMPOSITE)).isNotEmpty();
        assertThat(loader.forPattern(Pattern.DECORATOR)).isNotEmpty();
        assertThat(loader.forPattern(Pattern.FACADE)).isNotEmpty();
        assertThat(loader.forPattern(Pattern.FLYWEIGHT)).isNotEmpty();
        assertThat(loader.forPattern(Pattern.PROXY)).isNotEmpty();
    }

    @Test
    @DisplayName("all 11 Behavioral patterns have examples")
    void behavioralPatternsAreCovered() {
        assertThat(loader.forPattern(Pattern.CHAIN_OF_RESPONSIBILITY)).isNotEmpty();
        assertThat(loader.forPattern(Pattern.COMMAND)).isNotEmpty();
        assertThat(loader.forPattern(Pattern.INTERPRETER)).isNotEmpty();
        assertThat(loader.forPattern(Pattern.ITERATOR)).isNotEmpty();
        assertThat(loader.forPattern(Pattern.MEDIATOR)).isNotEmpty();
        assertThat(loader.forPattern(Pattern.MEMENTO)).isNotEmpty();
        assertThat(loader.forPattern(Pattern.OBSERVER)).isNotEmpty();
        assertThat(loader.forPattern(Pattern.STATE)).isNotEmpty();
        assertThat(loader.forPattern(Pattern.STRATEGY)).isNotEmpty();
        assertThat(loader.forPattern(Pattern.TEMPLATE_METHOD)).isNotEmpty();
        assertThat(loader.forPattern(Pattern.VISITOR)).isNotEmpty();
    }

    @Test
    @DisplayName("all 23 GoF patterns have at least one example")
    void allPatternsAreCovered() {
        assertThat(loader.coveredPatterns()).hasSize(23);
    }

    @Test
    @DisplayName("every example file declares the package the index claims it does")
    void packageDeclarationsMatchIndex() {
        for (Pattern p : loader.coveredPatterns()) {
            for (PatternExample ex : loader.forPattern(p)) {
                String firstLine = ex.source().lines().findFirst().orElse("");
                assertThat(firstLine.trim())
                    .as("first line of " + p + "/" + ex.fileName())
                    .isEqualTo("package " + ex.packageName() + ";");
            }
        }
    }

    @Test
    @DisplayName("every example pattern's sources compile together with javac in-memory")
    void allExamplesCompile(@TempDir Path classOutput) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("JDK JavaCompiler available (run tests with JDK, not JRE)").isNotNull();

        for (Pattern p : loader.coveredPatterns()) {
            compilePatternExamples(compiler, p, classOutput);
        }
    }

    private void compilePatternExamples(JavaCompiler compiler, Pattern pattern, Path classOutput) {
        List<PatternExample> files = loader.forPattern(pattern);
        if (files.isEmpty()) return;

        List<JavaFileObject> sources = new ArrayList<>(files.size());
        for (PatternExample ex : files) {
            String typeName = ex.packageName() + "." + ex.fileName().replace(".java", "");
            sources.add(new InMemorySource(typeName, ex.source()));
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
                StringBuilder report = new StringBuilder("Compilation failure for pattern " + pattern + ":\n");
                for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                    report.append("  ").append(d.toString()).append('\n');
                }
                throw new AssertionError(report.toString());
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** In-memory JavaFileObject backed by an example's source string. */
    private static final class InMemorySource extends SimpleJavaFileObject {
        private final String source;

        InMemorySource(String fullyQualifiedName, String source) {
            super(URI.create("string:///" + fullyQualifiedName.replace('.', '/') + ".java"),
                  Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
