package com.javapatterns.mcp.tools;

import com.javapatterns.mcp.catalog.Pattern;
import com.javapatterns.mcp.detect.DetectedPattern;
import com.javapatterns.mcp.detect.PatternDetectionEngine;
import com.javapatterns.mcp.detect.PatternDetectionEngine.BatchResult;
import com.javapatterns.mcp.detect.PatternDetectionEngine.FileDetection;
import com.javapatterns.mcp.detect.PatternDetectionEngine.FileError;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tool {@code detect_pattern}: parses Java source(s) and reports which
 * GoF design patterns the contained classes / interfaces participate in.
 *
 * <p>The tool now accepts <b>any one</b> of the following inputs, so
 * callers do not have to fan out one MCP round-trip per file:</p>
 *
 * <pre>
 * { "source":    "package x.y; class Foo { ... }" }      // single inline source
 * { "paths":     ["src/A.java", "src/B.java"]   }        // list of files on disk
 * { "directory": "src/main/java"                }        // recursive *.java scan
 * </pre>
 *
 * <p>Output payload (single text content block, JSON-encoded):</p>
 * <pre>
 * {
 *   "supportedPatterns": [ "Singleton", "Builder", ... ],
 *   "filesAnalyzed": 3,
 *   "detectionCount": 5,
 *   "detected": [
 *     { "file": "src/Foo.java",
 *       "pattern": "Singleton",
 *       "category": "Creational",
 *       "className": "Logger",
 *       "startLine": 12,
 *       "confidence": 0.75,
 *       "evidence": ["private ctor", "static INSTANCE", ...] }
 *   ],
 *   "errors": [
 *     { "file": "src/Broken.java", "message": "parse error: …" }
 *   ]
 * }
 * </pre>
 *
 * <p>For backward compatibility, the legacy {@code "source"} single-string
 * input is still accepted and behaves exactly as before — {@code file} is
 * still set ({@code "&lt;source&gt;"}) so the schema is uniform.</p>
 *
 * <p>Patterns without a detector yet are listed neither as supported nor
 * detected; check {@code supportedPatterns} to see what the current build
 * recognises.</p>
 */
public final class DetectPatternTool {

    public static final String NAME = "detect_pattern";

    /** Safety cap on number of files processed per call. */
    static final int DEFAULT_MAX_FILES = 1000;
    /** Safety cap on a single file's size in bytes (skipped with an error if larger). */
    static final long DEFAULT_MAX_FILE_BYTES = 1_048_576L; // 1 MB

    private final PatternDetectionEngine engine;
    private final McpJsonMapper jsonMapper;

    public DetectPatternTool(PatternDetectionEngine engine, McpJsonMapper jsonMapper) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
    }

    public McpServerFeatures.SyncToolSpecification specification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("source", Map.of(
            "type", "string",
            "description",
            "A single Java compilation unit as inline text. Use for ad-hoc / one-off analysis."
        ));
        properties.put("paths", Map.of(
            "type", "array",
            "items", Map.of("type", "string"),
            "description",
            "List of absolute or relative paths to .java files. The server reads them itself, "
            + "so the caller does NOT need to slurp the source in. Recommended when several "
            + "specific files must be analysed in one call."
        ));
        properties.put("directory", Map.of(
            "type", "string",
            "description",
            "Path to a directory; the server walks it recursively and analyses every *.java file. "
            + "Recommended for package- or project-wide scans. Capped at " + DEFAULT_MAX_FILES + " files."
        ));

        Tool tool = Tool.builder(NAME)
            .description(
                "Parse Java source(s) and report which GoF design patterns the contained classes / "
                + "interfaces participate in. Returns per-instance confidence (0–1) and the structural "
                + "signals that fired, with the originating file for every hit. Supports three input "
                + "modes — pass exactly one of: 'source' (single inline string), 'paths' (list of .java "
                + "files on disk), or 'directory' (recursive scan). Per-file parse failures are reported "
                + "in 'errors' and do NOT abort the batch. Supported patterns in this build: "
                + supportedSlugList() + ".")
            .inputSchema(JsonSchema.builder()
                .type("object")
                .properties(properties)
                .build())
            .build();

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler((exchange, request) -> handle(request == null ? Map.of() : request.arguments()))
            .build();
    }

    CallToolResult handle(Map<String, Object> args) {
        try {
            Input input;
            try {
                input = parseInput(args);
            } catch (IllegalArgumentException e) {
                return errorResult(e.getMessage());
            }

            // 1. Materialise label -> source content map according to the chosen mode.
            Map<String, String> sources = new LinkedHashMap<>();
            List<FileError> ioErrors = new ArrayList<>();
            switch (input.mode) {
                case SOURCE -> sources.put("<source>", input.source);
                case PATHS -> readPaths(input.paths, sources, ioErrors);
                case DIRECTORY -> readDirectory(input.directory, sources, ioErrors);
            }

            // 2. Run the detection engine on whatever we managed to read.
            BatchResult batch = sources.isEmpty()
                ? BatchResult.empty()
                : engine.detectAll(sources);

            // 3. Combine engine errors with up-front I/O errors.
            List<FileError> allErrors = new ArrayList<>(ioErrors);
            allErrors.addAll(batch.errors());

            // 4. Serialise.
            List<String> supported = engine.supportedPatterns().stream()
                .map(Pattern::displayName)
                .sorted()
                .toList();

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("supportedPatterns", supported);
            payload.put("filesAnalyzed", batch.filesAnalyzed());
            payload.put("detectionCount", batch.detections().size());
            payload.put("detected", batch.detections().stream()
                .map(this::toJsonModel)
                .toList());
            payload.put("errors", allErrors.stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("file", e.file());
                    m.put("message", e.message());
                    return m;
                })
                .toList());

            String json = jsonMapper.writeValueAsString(payload);
            return CallToolResult.builder()
                .content(List.of(TextContent.builder(json).build()))
                .isError(false)
                .build();
        } catch (Exception e) {
            return errorResult("Internal error: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- input

    private enum Mode { SOURCE, PATHS, DIRECTORY }

    private record Input(Mode mode, String source, List<String> paths, String directory) { }

    private static Input parseInput(Map<String, Object> args) {
        if (args == null) args = Map.of();
        String source = stringArg(args, "source");
        List<String> paths = stringListArg(args, "paths");
        String directory = stringArg(args, "directory");

        int provided = 0;
        if (source != null) provided++;
        if (paths != null && !paths.isEmpty()) provided++;
        if (directory != null) provided++;

        if (provided == 0) {
            throw new IllegalArgumentException(
                "Provide exactly one of: 'source' (inline string), 'paths' (string array), or 'directory' (string).");
        }
        if (provided > 1) {
            throw new IllegalArgumentException(
                "Provide exactly one of 'source', 'paths', or 'directory' — not several at once.");
        }

        if (source != null) return new Input(Mode.SOURCE, source, List.of(), null);
        if (paths != null && !paths.isEmpty()) return new Input(Mode.PATHS, null, paths, null);
        return new Input(Mode.DIRECTORY, null, List.of(), directory);
    }

    // ---------------------------------------------------------------- I/O

    private static void readPaths(List<String> paths,
                                  Map<String, String> out,
                                  List<FileError> errors) {
        int count = 0;
        for (String p : paths) {
            if (count >= DEFAULT_MAX_FILES) {
                errors.add(new FileError(p,
                    "skipped: file count cap of " + DEFAULT_MAX_FILES + " reached"));
                continue;
            }
            tryRead(Path.of(p), p, out, errors);
            count++;
        }
    }

    private static void readDirectory(String directory,
                                      Map<String, String> out,
                                      List<FileError> errors) {
        Path root = Path.of(directory);
        if (!Files.isDirectory(root)) {
            errors.add(new FileError(directory, "not a directory or does not exist"));
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> javaFiles = walk
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".java"))
                .sorted()
                .toList();

            int count = 0;
            for (Path p : javaFiles) {
                String label = root.relativize(p).toString();
                if (count >= DEFAULT_MAX_FILES) {
                    errors.add(new FileError(label,
                        "skipped: file count cap of " + DEFAULT_MAX_FILES + " reached"));
                    continue;
                }
                tryRead(p, label, out, errors);
                count++;
            }
        } catch (IOException e) {
            errors.add(new FileError(directory, "directory walk failed: " + e.getMessage()));
        }
    }

    private static void tryRead(Path file,
                                String label,
                                Map<String, String> out,
                                List<FileError> errors) {
        try {
            if (!Files.isRegularFile(file)) {
                errors.add(new FileError(label, "not a regular file"));
                return;
            }
            long size = Files.size(file);
            if (size > DEFAULT_MAX_FILE_BYTES) {
                errors.add(new FileError(label,
                    "skipped: file is " + size + " bytes, cap is " + DEFAULT_MAX_FILE_BYTES));
                return;
            }
            out.put(label, Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            errors.add(new FileError(label, "read failed: " + e.getMessage()));
        }
    }

    // ---------------------------------------------------------------- output

    private Map<String, Object> toJsonModel(FileDetection fd) {
        DetectedPattern d = fd.detection();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("file", fd.file());
        m.put("pattern", d.pattern().displayName());
        m.put("category", d.pattern().category().displayName());
        m.put("className", d.className());
        m.put("startLine", d.startLine());
        m.put("confidence", round(d.confidence(), 3));
        m.put("evidence", d.evidence());
        return m;
    }

    private static double round(double v, int places) {
        double factor = Math.pow(10, places);
        return Math.round(v * factor) / factor;
    }

    private static String supportedSlugList() {
        return PatternDetectionEngine.getInstance().supportedPatterns().stream()
            .map(Pattern::slug)
            .sorted()
            .collect(Collectors.joining(", "));
    }

    private static String stringArg(Map<String, Object> args, String name) {
        if (args == null) return null;
        Object v = args.get(name);
        if (v == null) return null;
        String s = v.toString();
        return s.isEmpty() ? null : s;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringListArg(Map<String, Object> args, String name) {
        if (args == null) return null;
        Object v = args.get(name);
        if (v == null) return null;
        if (v instanceof Collection<?> c) {
            List<String> out = new ArrayList<>(c.size());
            for (Object o : c) {
                if (o == null) continue;
                String s = o.toString();
                if (!s.isEmpty()) out.add(s);
            }
            return out;
        }
        // Tolerate a single string passed where a list was expected.
        if (v instanceof String s && !s.isEmpty()) {
            return List.of(s);
        }
        return null;
    }

    private static CallToolResult errorResult(String message) {
        return CallToolResult.builder()
            .content(List.of(TextContent.builder(message).build()))
            .isError(true)
            .build();
    }
}
