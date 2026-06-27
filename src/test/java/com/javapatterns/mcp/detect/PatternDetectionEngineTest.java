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
    @DisplayName("engine reports the 18 supported patterns")
    void supportedPatterns() {
        assertThat(engine.supportedPatterns()).containsExactlyInAnyOrder(
            Pattern.SINGLETON,
            Pattern.BUILDER,
            Pattern.FACTORY_METHOD,
            Pattern.STRATEGY,
            Pattern.OBSERVER,
            Pattern.COMPOSITE,
            Pattern.ADAPTER,
            Pattern.DECORATOR,
            Pattern.PROXY,
            Pattern.TEMPLATE_METHOD,
            Pattern.STATE,
            Pattern.COMMAND,
            Pattern.ABSTRACT_FACTORY,
            Pattern.BRIDGE,
            Pattern.FACADE,
            Pattern.VISITOR,
            Pattern.CHAIN_OF_RESPONSIBILITY,
            Pattern.MEDIATOR
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
    @DisplayName("Adapter is detected when a class implements a target + wraps an adaptee")
    void detectsAdapterSnippet() {
        String source = """
            package demo;
            interface MediaPlayer { void play(String file); }
            class AdvancedPlayer { void playMp4(String f) {} }
            final class MediaAdapter implements MediaPlayer {
                private final AdvancedPlayer adaptee = new AdvancedPlayer();
                public void play(String file) { adaptee.playMp4(file); }
            }
            """;
        List<DetectedPattern> hits = engine.detect(source);
        DetectedPattern hit = hits.stream()
            .filter(h -> h.pattern() == Pattern.ADAPTER)
            .findFirst().orElseThrow();
        assertThat(hit.className()).isEqualTo("MediaAdapter");
        assertThat(hit.confidence()).isEqualTo(1.0);
        assertThat(hit.evidence()).anySatisfy(e ->
            assertThat(e).contains("AdvancedPlayer"));
    }

    @Test
    @DisplayName("Decorator is detected when wrapper holds a field of the same interface")
    void detectsDecoratorSnippet() {
        String source = """
            package demo;
            interface Coffee { double cost(); }
            final class SimpleCoffee implements Coffee {
                public double cost() { return 2.0; }
            }
            class MilkDecorator implements Coffee {
                private final Coffee inner;
                MilkDecorator(Coffee inner) { this.inner = inner; }
                public double cost() { return inner.cost() + 0.5; }
            }
            """;
        List<DetectedPattern> hits = engine.detect(source);
        DetectedPattern hit = hits.stream()
            .filter(h -> h.pattern() == Pattern.DECORATOR)
            .findFirst().orElseThrow();
        assertThat(hit.className()).isEqualTo("MilkDecorator");
        assertThat(hit.confidence()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Proxy is detected when a hinted class name wraps the same interface")
    void detectsProxySnippet() {
        String source = """
            package demo;
            interface Image { void display(); }
            final class RealImage implements Image {
                public void display() {}
            }
            final class CachingImageProxy implements Image {
                private final Image real;
                CachingImageProxy(Image real) { this.real = real; }
                public void display() { real.display(); }
            }
            """;
        List<DetectedPattern> hits = engine.detect(source);
        DetectedPattern hit = hits.stream()
            .filter(h -> h.pattern() == Pattern.PROXY)
            .findFirst().orElseThrow();
        assertThat(hit.className()).isEqualTo("CachingImageProxy");
        assertThat(hit.confidence()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Template Method is detected on an abstract class whose final method calls abstract siblings")
    void detectsTemplateMethodSnippet() {
        String source = """
            package demo;
            abstract class Game {
                public final void play() {
                    initialize();
                    startPlay();
                    endPlay();
                }
                protected abstract void initialize();
                protected abstract void startPlay();
                protected abstract void endPlay();
            }
            """;
        List<DetectedPattern> hits = engine.detect(source);
        DetectedPattern hit = hits.stream()
            .filter(h -> h.pattern() == Pattern.TEMPLATE_METHOD)
            .findFirst().orElseThrow();
        assertThat(hit.className()).isEqualTo("Game");
        assertThat(hit.confidence()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("State is detected on a context + sealed-ish state hierarchy + 2 concrete states")
    void detectsStateSnippet() {
        String source = """
            package demo;
            interface TrafficLightState { TrafficLightState next(); }
            final class RedState implements TrafficLightState {
                public TrafficLightState next() { return new GreenState(); }
            }
            final class GreenState implements TrafficLightState {
                public TrafficLightState next() { return new RedState(); }
            }
            final class TrafficLight {
                private TrafficLightState state = new RedState();
                void tick() { state = state.next(); }
            }
            """;
        List<DetectedPattern> hits = engine.detect(source);
        DetectedPattern hit = hits.stream()
            .filter(h -> h.pattern() == Pattern.STATE)
            .findFirst().orElseThrow();
        assertThat(hit.className()).isEqualTo("TrafficLightState");
        assertThat(hit.confidence()).isEqualTo(1.0);
        assertThat(hit.evidence()).anySatisfy(e ->
            assertThat(e).contains("TrafficLight"));
    }

    @Test
    @DisplayName("Command is detected on an execute() interface with ≥ 2 concrete implementors")
    void detectsCommandSnippet() {
        String source = """
            package demo;
            interface Command { void execute(); }
            class Light { void on() {} void off() {} }
            final class TurnOn implements Command {
                private final Light light;
                TurnOn(Light l) { this.light = l; }
                public void execute() { light.on(); }
            }
            final class TurnOff implements Command {
                private final Light light;
                TurnOff(Light l) { this.light = l; }
                public void execute() { light.off(); }
            }
            """;
        List<DetectedPattern> hits = engine.detect(source);
        DetectedPattern hit = hits.stream()
            .filter(h -> h.pattern() == Pattern.COMMAND)
            .findFirst().orElseThrow();
        assertThat(hit.className()).isEqualTo("Command");
        assertThat(hit.confidence()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Abstract Factory is detected on a factory interface with ≥ 2 product methods + a concrete factory")
    void detectsAbstractFactorySnippet() {
        String source = """
            package demo;
            interface Button { String paint(); }
            interface Checkbox { String paint(); }
            interface GUIFactory {
                Button createButton();
                Checkbox createCheckbox();
            }
            final class MacFactory implements GUIFactory {
                public Button createButton()   { return () -> "mac btn"; }
                public Checkbox createCheckbox() { return () -> "mac chk"; }
            }
            """;
        List<DetectedPattern> hits = engine.detect(source);
        DetectedPattern hit = hits.stream()
            .filter(h -> h.pattern() == Pattern.ABSTRACT_FACTORY)
            .findFirst().orElseThrow();
        assertThat(hit.className()).isEqualTo("GUIFactory");
        assertThat(hit.confidence()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Bridge is detected on an abstraction holding an implementor field with concrete sub-/impl-classes")
    void detectsBridgeSnippet() {
        String source = """
            package demo;
            interface Renderer { String render(String s); }
            abstract class Shape {
                protected final Renderer renderer;
                protected Shape(Renderer r) { this.renderer = r; }
                public abstract String draw();
            }
            final class Circle extends Shape {
                Circle(Renderer r) { super(r); }
                public String draw() { return renderer.render("circle"); }
            }
            final class VectorRenderer implements Renderer {
                public String render(String s) { return "<v>" + s + "</v>"; }
            }
            """;
        List<DetectedPattern> hits = engine.detect(source);
        DetectedPattern hit = hits.stream()
            .filter(h -> h.pattern() == Pattern.BRIDGE)
            .findFirst().orElseThrow();
        assertThat(hit.className()).isEqualTo("Shape");
        assertThat(hit.confidence()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Facade is detected on a class with ≥ 2 subsystem fields coordinated in one public method")
    void detectsFacadeSnippet() {
        String source = """
            package demo;
            final class Inventory { boolean reserve(String s, int q) { return true; } }
            final class Payment   { String charge(String c, double a) { return "PMT"; } }
            final class Shipping  { String dispatch(String s, int q, String a) { return "TRK"; } }
            final class OrderFacade {
                private final Inventory inventory = new Inventory();
                private final Payment   payment   = new Payment();
                private final Shipping  shipping  = new Shipping();
                public String placeOrder(String sku, int q, String card, String addr) {
                    if (!inventory.reserve(sku, q)) return "FAIL";
                    String p = payment.charge(card, q * 9.99);
                    String t = shipping.dispatch(sku, q, addr);
                    return "OK " + p + " " + t;
                }
            }
            """;
        List<DetectedPattern> hits = engine.detect(source);
        DetectedPattern hit = hits.stream()
            .filter(h -> h.pattern() == Pattern.FACADE)
            .findFirst().orElseThrow();
        assertThat(hit.className()).isEqualTo("OrderFacade");
        assertThat(hit.confidence()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Visitor is detected on element + visitor abstractions with double-dispatch concrete elements")
    void detectsVisitorSnippet() {
        String source = """
            package demo;
            interface Shape { String accept(ShapeVisitor v); }
            interface ShapeVisitor {
                String visit(Circle c);
                String visit(Square s);
            }
            final class Circle implements Shape {
                public String accept(ShapeVisitor v) { return v.visit(this); }
            }
            final class Square implements Shape {
                public String accept(ShapeVisitor v) { return v.visit(this); }
            }
            """;
        List<DetectedPattern> hits = engine.detect(source);
        DetectedPattern hit = hits.stream()
            .filter(h -> h.pattern() == Pattern.VISITOR)
            .findFirst().orElseThrow();
        assertThat(hit.className()).isEqualTo("Shape");
        assertThat(hit.confidence()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Chain of Responsibility is detected on a handler with a self-ref next field and concrete subclasses")
    void detectsChainOfResponsibilitySnippet() {
        String source = """
            package demo;
            abstract class Handler {
                protected Handler next;
                public Handler setNext(Handler n) { this.next = n; return n; }
                public String handle(String r) {
                    return next != null ? next.handle(r) : "no-op";
                }
            }
            final class AuthHandler extends Handler {
                public String handle(String r) {
                    return r.startsWith("AUTH:") ? "[Auth]" : super.handle(r);
                }
            }
            """;
        List<DetectedPattern> hits = engine.detect(source);
        DetectedPattern hit = hits.stream()
            .filter(h -> h.pattern() == Pattern.CHAIN_OF_RESPONSIBILITY)
            .findFirst().orElseThrow();
        assertThat(hit.className()).isEqualTo("Handler");
        assertThat(hit.confidence()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Mediator is detected on a mediator with send + register methods and ≥ 1 colleague")
    void detectsMediatorSnippet() {
        String source = """
            package demo;
            interface ChatMediator {
                void send(String from, String msg);
                ChatMediator register(Colleague c);
            }
            abstract class Colleague {
                protected final ChatMediator mediator;
                protected final String name;
                Colleague(String n, ChatMediator m) { this.name = n; this.mediator = m; }
                public String name() { return name; }
                public abstract void receive(String from, String msg);
                public void send(String msg) { mediator.send(name, msg); }
            }
            """;
        List<DetectedPattern> hits = engine.detect(source);
        DetectedPattern hit = hits.stream()
            .filter(h -> h.pattern() == Pattern.MEDIATOR)
            .findFirst().orElseThrow();
        assertThat(hit.className()).isEqualTo("ChatMediator");
        assertThat(hit.confidence()).isEqualTo(1.0);
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

    @Test
    @DisplayName("detectAll runs every source in one pass and labels hits with their file")
    void detectAllRunsBatch() {
        java.util.Map<String, String> sources = new java.util.LinkedHashMap<>();
        sources.put("a/Logger.java", """
            package a;
            public final class Logger {
                private static final Logger INSTANCE = new Logger();
                private Logger() {}
                public static Logger getInstance() { return INSTANCE; }
            }
            """);
        sources.put("b/Pizza.java", """
            package b;
            public final class Pizza {
                private final String dough;
                private Pizza(Builder bld) { this.dough = bld.dough; }
                public static final class Builder {
                    private String dough;
                    public Builder dough(String d) { this.dough = d; return this; }
                    public Pizza build() { return new Pizza(this); }
                }
            }
            """);

        PatternDetectionEngine.BatchResult batch = engine.detectAll(sources);

        assertThat(batch.filesAnalyzed()).isEqualTo(2);
        assertThat(batch.errors()).isEmpty();

        // Every detection knows which file it came from.
        assertThat(batch.detections()).allSatisfy(fd ->
            assertThat(fd.file()).isIn("a/Logger.java", "b/Pizza.java"));

        // Both patterns must turn up in the combined output.
        java.util.Set<Pattern> seen = new java.util.HashSet<>();
        for (PatternDetectionEngine.FileDetection fd : batch.detections()) {
            seen.add(fd.detection().pattern());
        }
        assertThat(seen).contains(Pattern.SINGLETON, Pattern.BUILDER);
    }

    @Test
    @DisplayName("detectAll isolates parse errors per file instead of throwing")
    void detectAllIsolatesParseErrors() {
        java.util.Map<String, String> sources = new java.util.LinkedHashMap<>();
        sources.put("good.java", """
            package g;
            public final class S {
                private static final S I = new S();
                private S() {}
                public static S getInstance() { return I; }
            }
            """);
        sources.put("bad.java", "this is not java { { {");

        PatternDetectionEngine.BatchResult batch = engine.detectAll(sources);

        assertThat(batch.filesAnalyzed())
            .as("only the good file actually parsed")
            .isEqualTo(1);
        assertThat(batch.errors()).hasSize(1);
        assertThat(batch.errors().get(0).file()).isEqualTo("bad.java");
        assertThat(batch.detections()).isNotEmpty();
        assertThat(batch.detections().get(0).file()).isEqualTo("good.java");
    }
}
