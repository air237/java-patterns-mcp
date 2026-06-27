package com.javapatterns.mcp.generate;

import com.javapatterns.mcp.catalog.Pattern;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generates fully parameterised Java sources for a design pattern.
 *
 * <p>Each supported pattern has a template index file at
 * {@code /templates/<slug>/template-index.json} listing the template files
 * and their resolved file names. Each template file lives next to the
 * index and uses {@code ${PLACEHOLDER}} markers (no escaping, no nesting —
 * intentional: keeps generation deterministic and easy to debug).</p>
 *
 * <p>The set of placeholders varies per pattern. The common ones are:</p>
 * <ul>
 *   <li>{@code ${PACKAGE_NAME}} — the target package (no trailing dot).</li>
 *   <li>{@code ${PACKAGE_DECL}} — either {@code "package x.y;"} or empty if
 *       the user passes the empty / default package.</li>
 *   <li>{@code ${TYPE_NAME}} — the user's chosen "main" type (e.g.
 *       {@code "Logger"} for Singleton, {@code "Pizza"} for Builder).</li>
 * </ul>
 *
 * <p>Patterns without a template return a structured "not generated yet"
 * response from the calling tool — the registry below is the single source of
 * truth for which patterns have generators wired.</p>
 */
public final class PatternGenerator {

    /** Patterns whose templates are bundled in this build. */
    public static final java.util.Set<Pattern> SUPPORTED = java.util.EnumSet.of(
        Pattern.SINGLETON,
        Pattern.FACTORY_METHOD,
        Pattern.BUILDER,
        Pattern.STRATEGY,
        Pattern.OBSERVER,
        Pattern.DECORATOR,
        Pattern.STATE,
        Pattern.COMMAND,
        Pattern.ADAPTER
    );

    /** Singleton instance — templates are loaded lazily on first use. */
    private static final class Holder {
        private static final PatternGenerator INSTANCE = new PatternGenerator();
    }

    public static PatternGenerator getInstance() {
        return Holder.INSTANCE;
    }

    private PatternGenerator() { /* nothing to init — templates are loaded on demand */ }

    /**
     * Generates the file set for the given pattern.
     *
     * @param pattern    one of {@link #SUPPORTED}; other patterns throw {@link UnsupportedOperationException}.
     * @param params     placeholder values. Must contain {@code TYPE_NAME} and {@code PACKAGE_NAME}.
     *                   {@code PACKAGE_DECL} is auto-derived from {@code PACKAGE_NAME}.
     * @return ordered list of generated files, ready to write to disk.
     */
    public List<GeneratedFile> generate(Pattern pattern, Map<String, String> params) {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(params, "params");
        if (!SUPPORTED.contains(pattern)) {
            throw new UnsupportedOperationException(
                "No template wired yet for pattern " + pattern.name() +
                ". Use the pattern_examples tool for canonical reference sources, " +
                "or contribute a template under /templates/" + pattern.slug() + "/.");
        }

        // Normalise + augment params.
        Map<String, String> merged = new LinkedHashMap<>(params);
        merged.computeIfAbsent("TYPE_NAME", k -> "MyType");
        String pkg = merged.getOrDefault("PACKAGE_NAME", "").trim();
        merged.put("PACKAGE_NAME", pkg);
        merged.put("PACKAGE_DECL", pkg.isEmpty() ? "" : "package " + pkg + ";");

        TemplateIndex index = loadIndex(pattern);
        List<GeneratedFile> out = new java.util.ArrayList<>(index.files.size());
        for (TemplateFile tf : index.files) {
            String body = readTemplate(pattern, tf.template);
            String rendered = substitute(body, merged);
            String fileName = substitute(tf.fileName, merged);
            out.add(new GeneratedFile(fileName, pkg, rendered));
        }
        return out;
    }

    /** Plain {@code ${KEY}} substitution. Unknown keys are left as-is. */
    static String substitute(String template, Map<String, String> params) {
        // Walk the string once. We avoid regex to make behaviour
        // (and performance) trivially predictable.
        StringBuilder out = new StringBuilder(template.length());
        int i = 0;
        while (i < template.length()) {
            int dollar = template.indexOf("${", i);
            if (dollar < 0) {
                out.append(template, i, template.length());
                break;
            }
            int end = template.indexOf('}', dollar + 2);
            if (end < 0) { // no closing — emit the rest verbatim
                out.append(template, i, template.length());
                break;
            }
            out.append(template, i, dollar);
            String key = template.substring(dollar + 2, end);
            String value = params.get(key);
            if (value != null) {
                out.append(value);
            } else {
                out.append(template, dollar, end + 1); // keep placeholder untouched
            }
            i = end + 1;
        }
        return out.toString();
    }

    // ─── resource loading ──────────────────────────────────────────

    private TemplateIndex loadIndex(Pattern pattern) {
        String indexPath = "/templates/" + pattern.slug() + "/template-index.json";
        try (InputStream in = PatternGenerator.class.getResourceAsStream(indexPath)) {
            if (in == null) {
                throw new IllegalStateException(
                    "Template index missing on classpath: " + indexPath);
            }
            byte[] bytes = in.readAllBytes();
            return parseIndex(bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + indexPath, e);
        }
    }

    private String readTemplate(Pattern pattern, String templateName) {
        String path = "/templates/" + pattern.slug() + "/" + templateName;
        try (InputStream in = PatternGenerator.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Template file missing: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read template " + path, e);
        }
    }

    private static TemplateIndex parseIndex(byte[] bytes) throws IOException {
        io.modelcontextprotocol.json.McpJsonMapper mapper =
            com.javapatterns.mcp.McpJsonMappers.defaultMapper();
        return mapper.readValue(bytes, new io.modelcontextprotocol.json.TypeRef<TemplateIndex>() { });
    }

    // ─── JSON DTOs ─────────────────────────────────────────────────

    static final class TemplateIndex {
        public List<TemplateFile> files;
    }

    static final class TemplateFile {
        /** File name written to disk; may itself contain placeholders. */
        public String fileName;
        /** Template resource name under {@code /templates/<slug>/}. */
        public String template;
    }

    /** Result of a {@link #generate(Pattern, Map)} call — one generated file. */
    public record GeneratedFile(String fileName, String packageName, String source) { }
}
