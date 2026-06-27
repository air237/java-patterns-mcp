# Tool ↔ Pattern Coverage

Track which of the five functional MCP tools (`pattern_examples`,
`generate_pattern`, `detect_pattern`, `validate_pattern`,
`refactor_to_pattern`) supports which of the 23 Gang of Four design
patterns.

> **How to read this file:** the matrix below is the canonical
> "what does this MCP actually do for pattern X" reference. If you
> add a new detector / validator / refactoring / template, update the
> corresponding cell in the same commit so this file stays a single
> source of truth.

The `ping` and `list_patterns` tools are not in the matrix — they are
administrative and pattern-agnostic by design (`list_patterns`
always returns the full 23-pattern catalogue).

---

## Strategic grouping

Not every pattern deserves every tool. Patterns fall into three
prioritisation buckets:

| Group | Patterns | Strategy |
|---|---|---|
| **A — full 4-tool coverage worthwhile** | Singleton, Builder, Factory Method, Observer, Strategy, Decorator, State, Command, Adapter, Composite, Proxy, Template Method | High-frequency patterns with well-known anti-pattern variants. Worth implementing `generate` + `detect` + `validate` + `refactor`. |
| **B — `generate` + `detect` only** | Abstract Factory, Bridge, Facade, Visitor, Chain of Responsibility, Mediator | Common enough to recognise and scaffold; "wrong implementation" is loosely defined → `validate` is low-ROI, `refactor` is rarely the target. |
| **C — `pattern_examples` (maybe `detect`)** | Prototype, Flyweight, Interpreter, Iterator, Memento | Rare in modern Java, or superseded by JDK idioms (`java.util.Iterator`, records, JVM string interning). The canonical example is the main contribution. |

---

## Coverage matrix

Legend: ✅ supported · ⛔ not implemented · ⚪ intentionally out of scope
(see "Strategic grouping" above).

### A — full coverage target

| Pattern | `pattern_examples` | `generate` | `detect` | `validate` | `refactor` |
|---|:---:|:---:|:---:|:---:|:---:|
| Singleton | ✅ | ✅ | ✅ | ✅ | ✅ (3) |
| Builder | ✅ | ✅ | ✅ | ✅ | ✅ (1) |
| Factory Method | ✅ | ✅ | ✅ | ✅ | ⛔ |
| Observer | ✅ | ✅ | ✅ | ✅ | ✅ (1) |
| Strategy | ✅ | ✅ | ✅ | ✅ | ⛔ |
| Decorator | ✅ | ✅ | ✅ | ⛔ | ⛔ |
| State | ✅ | ✅ | ✅ | ⛔ | ⛔ |
| Command | ✅ | ✅ | ✅ | ⛔ | ⛔ |
| Adapter | ✅ | ✅ | ✅ | ✅ | ✅ (1) |
| Composite | ✅ | ⛔ | ✅ | ⛔ | ⛔ |
| Proxy | ✅ | ⛔ | ✅ | ⛔ | ⛔ |
| Template Method | ✅ | ✅ | ✅ | ✅ | ✅ (1) |
| **Group A subtotals** | **12/12** | **10/12** | **12/12** | **6/12** | **4 patterns / 7 refactorings** |

### B — `generate` + `detect` target

| Pattern | `pattern_examples` | `generate` | `detect` | `validate` | `refactor` |
|---|:---:|:---:|:---:|:---:|:---:|
| Abstract Factory | ✅ | ⛔ | ⛔ | ⚪ | ⚪ |
| Bridge | ✅ | ⛔ | ⛔ | ⚪ | ⚪ |
| Facade | ✅ | ⛔ | ⛔ | ⚪ | ⚪ |
| Visitor | ✅ | ⛔ | ⛔ | ⚪ | ⚪ |
| Chain of Responsibility | ✅ | ⛔ | ⛔ | ⚪ | ⚪ |
| Mediator | ✅ | ⛔ | ⛔ | ⚪ | ⚪ |
| **Group B subtotals** | **6/6** | **0/6** | **0/6** | – | – |

### C — examples-only

| Pattern | `pattern_examples` | `generate` | `detect` | `validate` | `refactor` |
|---|:---:|:---:|:---:|:---:|:---:|
| Prototype | ✅ | ⛔ | ⚪ | ⚪ | ⚪ |
| Flyweight | ✅ | ⛔ | ⚪ | ⚪ | ⚪ |
| Interpreter | ✅ | ⛔ | ⚪ | ⚪ | ⚪ |
| Iterator | ✅ | ⛔ | ⚪ | ⚪ | ⚪ |
| Memento | ✅ | ⛔ | ⚪ | ⚪ | ⚪ |
| **Group C subtotals** | **5/5** | **0/5** | – | – | – |

---

## Totals

| Tool | Implemented | Out of 23 | % | Source of truth |
|---|---:|---:|---:|---|
| `pattern_examples` | 23 | 23 | 100% | `src/main/resources/examples/<slug>/` directories |
| `generate_pattern` | 10 | 23 | 43% | `PatternGenerator.SUPPORTED` |
| `detect_pattern` | 12 | 23 | 52% | `PatternDetectionEngine` detectors list |
| `validate_pattern` | 7 | 23 | 30% | `PatternValidationEngine` validators list |
| `refactor_to_pattern` | 7 refactorings on 5 patterns | – | – | `RefactoringId` enum |

---

## Refactorings inventory

The `refactor_to_pattern` tool exposes individual transformations, not
"pattern conversions". Each entry below is the public slug callers
pass to the MCP tool.

| Slug | Pattern | What it does |
|---|---|---|
| `singleton-make-ctor-private` | Singleton | Turn a public constructor into a private one. |
| `singleton-add-holder-idiom` | Singleton | Replace an uncached `getInstance()` with the Bill-Pugh holder idiom. |
| `singleton-add-read-resolve` | Singleton | Add `private Object readResolve()` to a `Serializable` Singleton. |
| `builder-make-fields-final` | Builder | Mark every non-final field of the Builder's outer class as `final`. |
| `observer-snapshot-iteration` | Observer | Wrap the iterated collection of a publish-like method with `List.copyOf(...)`. |
| `adapter-make-adaptee-final` | Adapter | Mark the adaptee field of an Adapter-shaped class as `final`. |
| `template-method-make-final` | Template Method | Mark the template method of an abstract class as `final` so subclasses cannot bypass the locked algorithm skeleton. |

---

## Roadmap: what's next for Group A

The 12 Group-A patterns are the priority target. Concrete gaps:

### 3 missing `generate` templates
Composite · Proxy
(Adapter completed in commit `ca5441d`; Template Method completed in commit `<TBD>`.)

### 6 missing `validate` validators
Decorator · State · Command · Composite · Proxy
(Adapter completed in commit `ca5441d`; Template Method completed in commit `<TBD>`.)

### 8 patterns without any `refactor` recipe
Factory Method · Strategy · Decorator · State · Command · Composite · Proxy
(5 patterns now have ≥1 refactoring: Singleton, Builder, Observer, Adapter, Template Method.)

### Per-pattern benchmark

| Pattern | When | New LOC | New files | New tests | Notes |
|---|---|---:|---:|---:|---|
| Adapter | June 2026 | ~480 | 6 | 8 | First full Group-A rollout: object-adapter shape with composition, null-check guard, class-adapter detection. |
| Template Method | June 2026 | ~410 | 5 | 9 | Educational follow-up: shows what Template Method IS (inheritance + abstract hooks) and what it is NOT (lambda-strategy). Caught the EJ-19 "constructor calls overridable method" anti-pattern. |

Average per pattern so far: ~445 LOC, ~5.5 new files, ~8.5 new tests.

Extrapolated to the remaining 8 Group-A patterns (those that still
need at least one of generate / validate / refactor): roughly
3500-4000 LOC of new code, ~12-15 hours of work, to bring Group A
to full coverage.

---

## Updating this file

When you ship a new component, update:

1. The corresponding cell in the **Coverage matrix** above.
2. The relevant **Totals** row.
3. If you added a refactoring, the **Refactorings inventory** table.
4. Cross-check against the source of truth listed in the **Totals**
   table (e.g. `PatternGenerator.SUPPORTED`, `PatternDetectionEngine`
   detector list, etc.). The matrix MUST match the code.

A `mvn test` run is the simplest check that the registrations actually
agree with what the engines expose at runtime — every engine has a
"reports the N wired …" sanity test.
