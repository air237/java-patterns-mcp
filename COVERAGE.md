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
| Factory Method | ✅ | ✅ | ✅ | ✅ | ✅ (1) |
| Observer | ✅ | ✅ | ✅ | ✅ | ✅ (1) |
| Strategy | ✅ | ✅ | ✅ | ✅ | ✅ (1) |
| Decorator | ✅ | ✅ | ✅ | ✅ | ✅ (1) |
| State | ✅ | ✅ | ✅ | ✅ | ✅ (1) |
| Command | ✅ | ✅ | ✅ | ✅ | ✅ (1) |
| Adapter | ✅ | ✅ | ✅ | ✅ | ✅ (1) |
| Composite | ✅ | ⛔ | ✅ | ⛔ | ⛔ |
| Proxy | ✅ | ⛔ | ✅ | ⛔ | ⛔ |
| Template Method | ✅ | ✅ | ✅ | ✅ | ✅ (1) |
| **Group A subtotals** | **12/12** | **10/12** | **12/12** | **10/12** | **10 patterns / 12 refactorings** |

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
| `validate_pattern` | 10 | 23 | 43% | `PatternValidationEngine` validators list |
| `refactor_to_pattern` | 12 refactorings on 10 patterns | – | – | `RefactoringId` enum |

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
| `factory-method-restrict-creator-ctor` | Factory Method | Demote public constructors of a concrete Creator (Factory-Method-shaped class) to `protected` so callers cannot bypass the factory method. |
| `strategy-add-functional-interface` | Strategy | Annotate a single-method `*Strategy` interface with `@FunctionalInterface` so the compiler protects the SAM contract. |
| `decorator-make-wrapped-final` | Decorator | Mark the wrapped (delegate) field of a Decorator-shaped class as `final`. |
| `state-make-implementations-final` | State | Mark every concrete implementor of a State hierarchy as `final`. |
| `command-make-implementations-final` | Command | Mark every concrete implementor of a Command contract as `final`. |

---

## Roadmap: what's next for Group A

The 12 Group-A patterns are the priority target. Concrete gaps:

### 2 missing `generate` templates
Composite · Proxy

### 2 missing `validate` validators
Composite · Proxy

### 2 patterns without any `refactor` recipe
Composite · Proxy
(10 patterns now have ≥1 refactoring: Singleton, Builder, Observer, Adapter, Template Method, Factory Method, Strategy, Decorator, State, Command. Composite and Proxy are the last two Group-A patterns missing all three tools — generate template + validator + refactoring.)

### Per-pattern benchmark

| Pattern | When | New LOC | New files | New tests | Notes |
|---|---|---:|---:|---:|---|
| Adapter | June 2026 | ~480 | 6 | 8 | First full Group-A rollout: object-adapter shape with composition, null-check guard, class-adapter detection. |
| Template Method | June 2026 | ~410 | 5 | 9 | Educational follow-up: shows what Template Method IS (inheritance + abstract hooks) and what it is NOT (lambda-strategy). Caught the EJ-19 "constructor calls overridable method" anti-pattern. |
| Factory Method + Strategy | June 2026 | ~320 | 2 (refactorings) | 10 | Refactor-only round: closed the last two "validator without refactor" gaps with two atomic recipes (`factory-method-restrict-creator-ctor`, `strategy-add-functional-interface`). |
| Decorator + State + Command | June 2026 | ~1140 | 6 (3 validators + 3 refactorings) | 22 | Validate + refactor double round for the three patterns that had detect + generate already. Validators each fire only on detector-shaped classes (zero false positives on the bundled canonical examples). Refactorings are all atomic "promote to final" modifier flips. |

Group-A patterns with full 4-tool coverage as of this commit: 10
out of 12 — only Composite and Proxy remain, each needing all
three of generate + validate + refactor.

Extrapolated to the remaining 2 Group-A patterns: roughly
800-1000 LOC of new code, ~3-4 hours of work, to bring Group A
to 12/12 full coverage. The total Group-A rollout so far has
cost ~2350 LOC across 5 milestones.

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
