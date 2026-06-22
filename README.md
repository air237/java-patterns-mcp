# java-patterns-mcp

> A **Model Context Protocol (MCP)** server that exposes the 23 Gang of Four
> design patterns to AI coding agents — **generation**, **canonical examples**,
> **AST-based detection**, **validation**, and **anti-pattern refactoring**
> for Java codebases.

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-blue)](https://openjdk.org/projects/jdk/21/)
[![MCP](https://img.shields.io/badge/MCP-2.0.0-purple)](https://modelcontextprotocol.io)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)

---

## Why

Generic LLMs can describe design patterns, but their generated Java code is
inconsistent — wrong thread-safety, subtle double-dispatch bugs, missing
equals/hashCode in value objects, broken Builder fluent chains. And no LLM
can deterministically scan a real codebase to say *"this class is a buggy
Singleton because the holder pattern is implemented incorrectly"*.

This MCP server fills that gap with **deterministic, AST-backed tooling**.

## Status

| Phase | Scope | State |
|---|---|---|
| 0 | Project skeleton (pom.xml, structure, licence) | ✅ done |
| 1 | MCP bootstrap + stdio transport + `ping` tool | ✅ done |
| 2 | Pattern catalog model (23 GoF + metadata) | ⏳ planned |
| 3 | `list_patterns` tool | ⏳ planned |
| 4 | `pattern_examples` tool (canonical, compilable) | ⏳ planned |
| 5 | `generate_pattern` tool (JTE templates) | ⏳ planned |
| 6-8 | All 23 GoF patterns implemented + tested | ⏳ planned |
| 9 | `detect_pattern` (JavaParser AST visitors) | ⏳ planned |
| 10 | `validate_pattern` (pattern-specific rules) | ⏳ planned |
| 11 | `refactor_to_pattern` (anti-pattern → pattern) | ⏳ planned |
| 12-13 | Packaging, OpenCode config example, CI | ⏳ planned |

## Tools (target API)

```
list_patterns         List all 23 GoF patterns, filterable by category.
pattern_examples      Return canonical, compilable example(s) for a pattern.
generate_pattern      Generate a customized pattern implementation
                      (package, type names, modern Java features).
detect_pattern        Scan Java source/dir for pattern instances with evidence.
validate_pattern      Verify a given implementation against pattern rules.
refactor_to_pattern   Transform anti-pattern code into a proper pattern.
```

## Build

Requires **JDK 21+** and **Maven 3.9+**.

```bash
mvn clean package
# produces: target/java-patterns-mcp-0.1.0-SNAPSHOT-all.jar
```

## Try it (Phase 1 — only `ping` is wired)

After `mvn package`, smoke-test directly with shell-piped JSON-RPC:

```bash
java -jar target/java-patterns-mcp-0.1.0-SNAPSHOT-all.jar <<'EOF'
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"smoke","version":"0.0.1"}}}
{"jsonrpc":"2.0","method":"notifications/initialized"}
{"jsonrpc":"2.0","id":2,"method":"tools/list"}
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"ping","arguments":{}}}
EOF
```

You should see three JSON-RPC responses on stdout:
1. Server info + protocol version.
2. The `ping` tool description.
3. `"java-patterns-mcp 0.1.0 — alive. Registered tools: [ping]"`.

## Wire into OpenCode

```jsonc
// ~/.config/opencode/opencode.json
{
  "mcp": {
    "java-patterns": {
      "type": "local",
      "command": [
        "java", "-jar",
        "/Users/<you>/git/com/java-patterns-mcp/target/java-patterns-mcp-0.1.0-SNAPSHOT-all.jar"
      ]
    }
  }
}
```

## Project layout

```
java-patterns-mcp/
├── pom.xml
├── README.md
├── LICENSE
└── src/
    ├── main/
    │   ├── java/com/javapatterns/mcp/
    │   │   ├── JavaPatternsMcpServer.java   ← main()
    │   │   ├── catalog/                     ← Pattern enum + metadata
    │   │   ├── tools/                       ← MCP tool handlers
    │   │   │   ├── ListPatternsTool.java
    │   │   │   ├── PatternExamplesTool.java
    │   │   │   ├── GeneratePatternTool.java
    │   │   │   ├── DetectPatternTool.java
    │   │   │   ├── ValidatePatternTool.java
    │   │   │   └── RefactorToPatternTool.java
    │   │   ├── generate/                    ← JTE template engine wiring
    │   │   ├── detect/                      ← JavaParser AST visitors
    │   │   ├── validate/                    ← pattern-specific rules
    │   │   └── refactor/                    ← anti-pattern transformations
    │   └── resources/
    │       ├── catalog/patterns.json        ← refactoring.guru-style metadata
    │       ├── examples/<pattern>/*.java    ← canonical examples
    │       ├── templates/<pattern>/*.jte    ← code generation templates
    │       └── logback.xml
    └── test/
        └── java/com/javapatterns/mcp/...
```

## License

[MIT](./LICENSE) © 2026 contributors. Pattern examples are adapted from
[refactoring.guru](https://refactoring.guru/design-patterns/java) and the
original *Design Patterns: Elements of Reusable Object-Oriented Software*
(Gamma, Helm, Johnson, Vlissides). All adapted code is original
re-implementation; reproduced verbatim third-party code is marked as such.
