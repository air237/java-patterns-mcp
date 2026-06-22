package com.javapatterns.mcp.validate;

import com.javapatterns.mcp.catalog.Pattern;
import com.javapatterns.mcp.catalog.PatternExamplesLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PatternValidationEngineTest {

    private final PatternValidationEngine engine = PatternValidationEngine.getInstance();

    @Test
    @DisplayName("engine reports the 5 wired validators")
    void supportedPatterns() {
        assertThat(engine.supportedPatterns()).containsExactlyInAnyOrder(
            Pattern.SINGLETON, Pattern.BUILDER, Pattern.OBSERVER,
            Pattern.STRATEGY, Pattern.FACTORY_METHOD);
    }

    // ─── Singleton ─────────────────────────────────────────────────

    @Test
    @DisplayName("the bundled Singleton example yields no ERROR or WARNING")
    void bundledSingletonHasNoSeriousIssues() {
        String source = PatternExamplesLoader.getInstance()
            .forPattern(Pattern.SINGLETON).get(0).source();
        List<ValidationIssue> issues = engine.validateAll(source);
        assertThat(issues)
            .as("bundled Singleton example: " + issues)
            .filteredOn(i -> i.severity() != Severity.INFO)
            .isEmpty();
    }

    @Test
    @DisplayName("Singleton with a public ctor is flagged ERROR")
    void publicCtorIsError() {
        String source = """
            public class Bad {
                public Bad() {}
                public static Bad getInstance() { return new Bad(); }
            }
            """;
        List<ValidationIssue> issues = engine.validateOne(source, Pattern.SINGLETON);
        assertThat(issues).anySatisfy(i -> {
            assertThat(i.severity()).isEqualTo(Severity.ERROR);
            assertThat(i.issue()).containsIgnoringCase("public constructor");
        });
    }

    @Test
    @DisplayName("Singleton's getInstance() that just `new`s without caching is ERROR")
    void uncachedGetInstanceIsError() {
        String source = """
            public class Bad {
                private Bad() {}
                public static Bad getInstance() { return new Bad(); }
            }
            """;
        List<ValidationIssue> issues = engine.validateOne(source, Pattern.SINGLETON);
        assertThat(issues).anySatisfy(i -> {
            assertThat(i.severity()).isEqualTo(Severity.ERROR);
            assertThat(i.issue()).containsIgnoringCase("static instance field");
        });
    }

    @Test
    @DisplayName("Serializable Singleton without readResolve is WARNING")
    void serializableWithoutReadResolveIsWarning() {
        String source = """
            import java.io.Serializable;
            public class BadSer implements Serializable {
                private static final BadSer INSTANCE = new BadSer();
                private BadSer() {}
                public static BadSer getInstance() { return INSTANCE; }
            }
            """;
        List<ValidationIssue> issues = engine.validateOne(source, Pattern.SINGLETON);
        assertThat(issues).anySatisfy(i -> {
            assertThat(i.severity()).isEqualTo(Severity.WARNING);
            assertThat(i.issue()).containsIgnoringCase("readResolve");
        });
    }

    // ─── Builder ───────────────────────────────────────────────────

    @Test
    @DisplayName("the bundled Builder example yields no ERROR or WARNING")
    void bundledBuilderHasNoSeriousIssues() {
        String source = PatternExamplesLoader.getInstance()
            .forPattern(Pattern.BUILDER).get(0).source();
        List<ValidationIssue> issues = engine.validateAll(source);
        assertThat(issues)
            .as("bundled Builder example: " + issues)
            .filteredOn(i -> i.severity() != Severity.INFO)
            .isEmpty();
    }

    @Test
    @DisplayName("Builder with a non-final outer field is ERROR")
    void nonFinalFieldIsError() {
        String source = """
            public final class Pizza {
                private String name; // not final
                private Pizza(Builder b) { this.name = b.name; }
                public static final class Builder {
                    private String name;
                    public Builder name(String n) { this.name = n; return this; }
                    public Pizza build() { return new Pizza(this); }
                }
            }
            """;
        List<ValidationIssue> issues = engine.validateOne(source, Pattern.BUILDER);
        assertThat(issues).anySatisfy(i -> {
            assertThat(i.severity()).isEqualTo(Severity.ERROR);
            assertThat(i.issue()).containsIgnoringCase("not final");
        });
    }

    @Test
    @DisplayName("Builder fluent setter that does not return this is WARNING")
    void fluentSetterMissingReturnThisIsWarning() {
        String source = """
            public final class Pizza {
                private final String name;
                private Pizza(Builder b) { this.name = b.name; }
                public static final class Builder {
                    private String name;
                    public Builder name(String n) { this.name = n; return null; } // bug
                    public Pizza build() { return new Pizza(this); }
                }
            }
            """;
        List<ValidationIssue> issues = engine.validateOne(source, Pattern.BUILDER);
        assertThat(issues).anySatisfy(i -> {
            assertThat(i.severity()).isEqualTo(Severity.WARNING);
            assertThat(i.issue()).containsIgnoringCase("return this");
        });
    }

    // ─── Observer ──────────────────────────────────────────────────

    @Test
    @DisplayName("the bundled Observer example yields no ERROR or WARNING")
    void bundledObserverHasNoSeriousIssues() {
        // Find the EventBus file specifically.
        var examples = PatternExamplesLoader.getInstance().forPattern(Pattern.OBSERVER);
        String source = examples.stream()
            .filter(f -> f.fileName().equals("EventBus.java"))
            .findFirst().orElseThrow().source();
        List<ValidationIssue> issues = engine.validateAll(source);
        assertThat(issues)
            .as("bundled Observer EventBus: " + issues)
            .filteredOn(i -> i.severity() != Severity.INFO)
            .isEmpty();
    }

    @Test
    @DisplayName("Observer without unsubscribe is ERROR")
    void noUnsubscribeIsError() {
        String source = """
            import java.util.ArrayList;
            import java.util.List;
            public class LeakyBus {
                private final List<Runnable> listeners = new ArrayList<>();
                public void subscribe(Runnable r) { listeners.add(r); }
                public void publish() {
                    for (Runnable r : List.copyOf(listeners)) r.run();
                }
            }
            """;
        List<ValidationIssue> issues = engine.validateOne(source, Pattern.OBSERVER);
        assertThat(issues).anySatisfy(i -> {
            assertThat(i.severity()).isEqualTo(Severity.ERROR);
            assertThat(i.issue()).containsIgnoringCase("unsubscribe");
        });
    }

    @Test
    @DisplayName("Observer that iterates the live list is WARNING")
    void liveIterationIsWarning() {
        String source = """
            import java.util.ArrayList;
            import java.util.List;
            public class BrittleBus {
                private final List<Runnable> listeners = new ArrayList<>();
                public void subscribe(Runnable r) { listeners.add(r); }
                public void unsubscribe(Runnable r) { listeners.remove(r); }
                public void publish() {
                    for (Runnable r : listeners) r.run();
                }
            }
            """;
        List<ValidationIssue> issues = engine.validateOne(source, Pattern.OBSERVER);
        assertThat(issues).anySatisfy(i -> {
            assertThat(i.severity()).isEqualTo(Severity.WARNING);
            assertThat(i.issue()).containsIgnoringCase("ConcurrentModificationException");
            assertThat(i.suggestion()).containsIgnoringCase("snapshot");
        });
    }

    // ─── Strategy ──────────────────────────────────────────────────

    @Test
    @DisplayName("Strategy contract that is a concrete class is ERROR")
    void concreteStrategyContractIsError() {
        String source = """
            public class SortStrategy {
                public java.util.List<Integer> sort(java.util.List<Integer> in) { return in; }
            }
            """;
        List<ValidationIssue> issues = engine.validateOne(source, Pattern.STRATEGY);
        assertThat(issues).anySatisfy(i -> {
            assertThat(i.severity()).isEqualTo(Severity.ERROR);
            assertThat(i.issue()).containsIgnoringCase("concrete class");
        });
    }

    @Test
    @DisplayName("Strategy with only 1 implementation is WARNING")
    void singleImplementationStrategyIsWarning() {
        String source = """
            interface SortStrategy { java.util.List<Integer> sort(java.util.List<Integer> in); }
            final class AscSort implements SortStrategy {
                public java.util.List<Integer> sort(java.util.List<Integer> in) { return in; }
            }
            """;
        List<ValidationIssue> issues = engine.validateOne(source, Pattern.STRATEGY);
        assertThat(issues).anySatisfy(i -> {
            assertThat(i.severity()).isEqualTo(Severity.WARNING);
            assertThat(i.issue()).containsIgnoringCase("1 implementation");
        });
    }

    @Test
    @DisplayName("Strategy with multiple abstract methods is WARNING")
    void multiMethodStrategyIsWarning() {
        String source = """
            interface SortStrategy {
                java.util.List<Integer> sort(java.util.List<Integer> in);
                java.util.List<Integer> partialSort(java.util.List<Integer> in, int n);
            }
            final class AscSort implements SortStrategy {
                public java.util.List<Integer> sort(java.util.List<Integer> in) { return in; }
                public java.util.List<Integer> partialSort(java.util.List<Integer> in, int n) { return in; }
            }
            final class DescSort implements SortStrategy {
                public java.util.List<Integer> sort(java.util.List<Integer> in) { return in; }
                public java.util.List<Integer> partialSort(java.util.List<Integer> in, int n) { return in; }
            }
            """;
        List<ValidationIssue> issues = engine.validateOne(source, Pattern.STRATEGY);
        assertThat(issues).anySatisfy(i -> {
            assertThat(i.severity()).isEqualTo(Severity.WARNING);
            assertThat(i.issue()).containsIgnoringCase("abstract methods");
        });
    }

    // ─── Factory Method ────────────────────────────────────────────

    @Test
    @DisplayName("Factory Method on a non-abstract Creator with public ctor is ERROR")
    void publicCreatorCtorIsError() {
        String source = """
            interface Button { void render(); }
            final class WindowsButton implements Button { public void render() {} }
            public class Dialog {
                public Dialog() {}
                public Button createButton() { return new WindowsButton(); }
            }
            """;
        List<ValidationIssue> issues = engine.validateOne(source, Pattern.FACTORY_METHOD);
        assertThat(issues).anySatisfy(i -> {
            assertThat(i.severity()).isEqualTo(Severity.ERROR);
            assertThat(i.issue()).containsIgnoringCase("public constructor");
        });
    }

    @Test
    @DisplayName("Factory Method returning a concrete product type is WARNING")
    void concreteReturnTypeIsWarning() {
        String source = """
            class WindowsButton { public void render() {} }
            public abstract class Dialog {
                protected Dialog() {}
                public WindowsButton createButton() { return new WindowsButton(); }
            }
            """;
        List<ValidationIssue> issues = engine.validateOne(source, Pattern.FACTORY_METHOD);
        assertThat(issues).anySatisfy(i -> {
            assertThat(i.severity()).isEqualTo(Severity.WARNING);
            assertThat(i.issue()).containsIgnoringCase("concrete type");
        });
    }

    @Test
    @DisplayName("Factory Method that inline-news multiple products (simple-factory smell) is WARNING")
    void inlineSwitchIsWarning() {
        String source = """
            interface Button { void render(); }
            final class WindowsButton implements Button { public void render() {} }
            final class LinuxButton implements Button { public void render() {} }
            public abstract class Dialog {
                protected Dialog() {}
                public Button createButton(String os) {
                    if (os.equals("win")) return new WindowsButton();
                    return new LinuxButton();
                }
            }
            """;
        List<ValidationIssue> issues = engine.validateOne(source, Pattern.FACTORY_METHOD);
        assertThat(issues).anySatisfy(i -> {
            assertThat(i.severity()).isEqualTo(Severity.WARNING);
            assertThat(i.issue()).containsIgnoringCase("simple factory");
        });
    }

    // ─── general ───────────────────────────────────────────────────

    @Test
    @DisplayName("a plain POJO yields zero issues")
    void plainPojoYieldsNoIssues() {
        String source = """
            public class Plain {
                private final String name;
                public Plain(String n) { this.name = n; }
                public String getName() { return name; }
            }
            """;
        assertThat(engine.validateAll(source)).isEmpty();
    }

    @Test
    @DisplayName("unparseable input throws ValidationException")
    void unparseableInputThrows() {
        try {
            engine.validateAll("this is not java { {");
            org.junit.jupiter.api.Assertions.fail("Expected ValidationException");
        } catch (PatternValidationEngine.ValidationException e) {
            assertThat(e.getMessage()).containsIgnoringCase("parse");
        }
    }
}
