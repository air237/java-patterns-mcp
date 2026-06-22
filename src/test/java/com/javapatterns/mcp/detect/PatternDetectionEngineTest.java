package com.javapatterns.mcp.detect;

import com.javapatterns.mcp.catalog.Pattern;
import com.javapatterns.mcp.catalog.PatternExamplesLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises every detector against the bundled canonical example
 * sources. If a detector cannot find its own pattern in the textbook
 * implementation, it has a bug.
 */
class PatternDetectionEngineTest {

    private final PatternDetectionEngine engine = PatternDetectionEngine.getInstance();
    private final PatternExamplesLoader examples = PatternExamplesLoader.getInstance();

    @Test
    @DisplayName("engine reports the expected supported patterns")
    void supportedPatterns() {
        assertThat(engine.supportedPatterns()).containsExactlyInAnyOrder(
            Pattern.SINGLETON,
            Pattern.BUILDER,
            Pattern.FACTORY_METHOD,
            Pattern.STRATEGY,
            Pattern.OBSERVER,
            Pattern.COMPOSITE
        );
    }

    @Test
    @DisplayName("Singleton example is detected with high confidence (≥ 0.75)")
    void detectsSingletonExample() {
        String source = examples.forPattern(Pattern.SINGLETON).get(0).source();
        List<DetectedPattern> hits = engine.detect(source);
        DetectedPattern hit = hits.stream()
            .filter(h -> h.pattern() == Pattern.SINGLETON)
            .findFirst()
            .orElseThrow();
        assertThat(hit.className()).isEqualTo("Singleton");
        assertThat(hit.confidence()).isGreaterThanOrEqualTo(0.75);
        assertThat(hit.evidence())
            .anySatisfy(e -> assertThat(e).contains("private constructor"))
            .anySatisfy(e -> assertThat(e).contains("Bill-Pugh"));
    }

    @Test
    @DisplayName("Builder example (Car) is detected with high confidence")
    void detectsBuilderExample() {
        String source = examples.forPattern(Pattern.BUILDER).get(0).source();
        List<DetectedPattern> hits = engine.detect(source);
        DetectedPattern hit = hits.stream()
            .filter(h -> h.pattern() == Pattern.BUILDER)
            .findFirst()
            .orElseThrow();
        assertThat(hit.className()).isEqualTo("Car");
        assertThat(hit.confidence()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Factory Method example (Dialog) is detected")
    void detectsFactoryMethodExample() {
        // The example splits across 6 files; we pick the abstract Dialog.
        String source = examples.forPattern(Pattern.FACTORY_METHOD).stream()
            .filter(f -> f.fileName().equals("Dialog.java"))
            .findFirst().orElseThrow().source();
        List<DetectedPattern> hits = engine.detect(source);
        assertThat(hits)
            .anySatisfy(h -> {
                assertThat(h.pattern()).isEqualTo(Pattern.FACTORY_METHOD);
                assertThat(h.className()).isEqualTo("Dialog");
                assertThat(h.confidence()).isGreaterThanOrEqualTo(0.5);
            });
    }

    @Test
    @DisplayName("Strategy is detected on a multi-file synthetic snippet")
    void detectsStrategyInSyntheticSnippet() {
        // Single compilation unit with 3 impls of the same one-method iface.
        String source = """
            package demo;
            interface SortStrategy { java.util.List<Integer> sort(java.util.List<Integer> in); }
            final class AscSort implements SortStrategy {
                public java.util.List<Integer> sort(java.util.List<Integer> in) { return in; }
            }
            final class DescSort implements SortStrategy {
                public java.util.List<Integer> sort(java.util.List<Integer> in) { return in; }
            }
            final class RandomSort implements SortStrategy {
                public java.util.List<Integer> sort(java.util.List<Integer> in) { return in; }
            }
            """;
        List<DetectedPattern> hits = engine.detect(source);
        DetectedPattern hit = hits.stream()
            .filter(h -> h.pattern() == Pattern.STRATEGY)
            .findFirst()
            .orElseThrow();
        assertThat(hit.className()).isEqualTo("SortStrategy");
        assertThat(hit.confidence()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Observer is detected on the EventBus example")
    void detectsObserverOnEventBus() {
        String source = examples.forPattern(Pattern.OBSERVER).stream()
            .filter(f -> f.fileName().equals("EventBus.java"))
            .findFirst().orElseThrow().source();
        List<DetectedPattern> hits = engine.detect(source);
        DetectedPattern hit = hits.stream()
            .filter(h -> h.pattern() == Pattern.OBSERVER)
            .findFirst()
            .orElseThrow();
        assertThat(hit.className()).isEqualTo("EventBus");
        assertThat(hit.confidence()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Composite is detected on a snippet with leaf + composite")
    void detectsCompositeSnippet() {
        String source = """
            package demo;
            import java.util.ArrayList;
            import java.util.List;
            interface Component { double cost(); }
            final class Leaf implements Component {
                public double cost() { return 1.0; }
            }
            final class Box implements Component {
                private final List<Component> children = new ArrayList<>();
                public double cost() {
                    double s = 0;
                    for (Component c : children) s += c.cost();
                    return s;
                }
            }
            """;
        List<DetectedPattern> hits = engine.detect(source);
        assertThat(hits)
            .anySatisfy(h -> {
                assertThat(h.pattern()).isEqualTo(Pattern.COMPOSITE);
                assertThat(h.className()).isEqualTo("Component");
                assertThat(h.confidence()).isEqualTo(1.0);
            });
    }

    @Test
    @DisplayName("a plain POJO produces no detections (no false positives)")
    void plainPojoYieldsNoHits() {
        String source = """
            package demo;
            public class PlainBean {
                private String name;
                public String getName() { return name; }
                public void setName(String n) { this.name = n; }
            }
            """;
        assertThat(engine.detect(source)).isEmpty();
    }

    @Test
    @DisplayName("unparseable input is reported as DetectionException")
    void unparseableInputThrows() {
        String source = "this is not java at all { { {";
        try {
            engine.detect(source);
            org.junit.jupiter.api.Assertions.fail("Expected DetectionException");
        } catch (PatternDetectionEngine.DetectionException e) {
            assertThat(e.getMessage()).containsIgnoringCase("parse");
        }
    }
}
