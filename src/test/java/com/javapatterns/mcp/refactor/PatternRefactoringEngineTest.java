package com.javapatterns.mcp.refactor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatternRefactoringEngineTest {

    private final PatternRefactoringEngine engine = PatternRefactoringEngine.getInstance();

    @Test
    @DisplayName("engine reports all 12 wired refactorings")
    void supported() {
        assertThat(engine.supported()).containsExactlyInAnyOrder(
            RefactoringId.SINGLETON_MAKE_CTOR_PRIVATE,
            RefactoringId.SINGLETON_ADD_HOLDER_IDIOM,
            RefactoringId.SINGLETON_ADD_READ_RESOLVE,
            RefactoringId.BUILDER_MAKE_FIELDS_FINAL,
            RefactoringId.OBSERVER_SNAPSHOT_ITERATION,
            RefactoringId.ADAPTER_MAKE_ADAPTEE_FINAL,
            RefactoringId.TEMPLATE_METHOD_MAKE_FINAL,
            RefactoringId.FACTORY_METHOD_RESTRICT_CREATOR_CTOR,
            RefactoringId.STRATEGY_ADD_FUNCTIONAL_INTERFACE,
            RefactoringId.DECORATOR_MAKE_WRAPPED_FINAL,
            RefactoringId.STATE_MAKE_IMPLEMENTATIONS_FINAL,
            RefactoringId.COMMAND_MAKE_IMPLEMENTATIONS_FINAL
        );
    }

    // ─── singleton-make-ctor-private ───────────────────────────────

    @Test
    @DisplayName("public ctor on a class with getInstance() becomes private")
    void singletonMakeCtorPrivate_rewrites() {
        String src = """
            public class Cache {
                public Cache() {}
                public static Cache getInstance() { return new Cache(); }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.SINGLETON_MAKE_CTOR_PRIVATE);
        assertThat(r.changed()).isTrue();
        assertThat(r.changes()).hasSize(1);
        assertThat(r.newSource())
            .contains("private Cache()")
            .doesNotContain("public Cache()");
    }

    @Test
    @DisplayName("singleton-make-ctor-private is idempotent")
    void singletonMakeCtorPrivate_idempotent() {
        String src = """
            public class Cache {
                private Cache() {}
                public static Cache getInstance() { return new Cache(); }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.SINGLETON_MAKE_CTOR_PRIVATE);
        assertThat(r.changed()).isFalse();
        assertThat(r.changes()).isEmpty();
    }

    @Test
    @DisplayName("singleton-make-ctor-private does not touch a regular class")
    void singletonMakeCtorPrivate_leavesPojosAlone() {
        String src = """
            public class JustABean {
                public JustABean() {}
                public String hello() { return "hi"; }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.SINGLETON_MAKE_CTOR_PRIVATE);
        assertThat(r.changed()).isFalse();
        assertThat(r.newSource()).contains("public JustABean()");
    }

    // ─── singleton-add-holder-idiom ────────────────────────────────

    @Test
    @DisplayName("getInstance() that `new`s every call is rewritten with a Holder class")
    void singletonAddHolderIdiom_rewrites() {
        String src = """
            public class Cache {
                private Cache() {}
                public static Cache getInstance() { return new Cache(); }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.SINGLETON_ADD_HOLDER_IDIOM);
        assertThat(r.changed()).isTrue();
        assertThat(r.newSource())
            .contains("private static final class Holder")
            .contains("Holder.INSTANCE")
            .contains("private static final Cache INSTANCE");
    }

    @Test
    @DisplayName("singleton-add-holder-idiom is idempotent if a Holder already exists")
    void singletonAddHolderIdiom_idempotent() {
        // Pass 1 — add the Holder.
        String src1 = """
            public class Cache {
                private Cache() {}
                public static Cache getInstance() { return new Cache(); }
            }
            """;
        String after1 = engine.apply(src1, RefactoringId.SINGLETON_ADD_HOLDER_IDIOM).newSource();
        // Pass 2 — should be a no-op.
        RefactoringResult r2 = engine.apply(after1, RefactoringId.SINGLETON_ADD_HOLDER_IDIOM);
        assertThat(r2.changed()).isFalse();
    }

    // ─── singleton-add-read-resolve ────────────────────────────────

    @Test
    @DisplayName("Serializable Singleton without readResolve() gets one added")
    void singletonAddReadResolve_rewrites() {
        String src = """
            import java.io.Serializable;
            public class Cfg implements Serializable {
                private static final Cfg INSTANCE = new Cfg();
                private Cfg() {}
                public static Cfg getInstance() { return INSTANCE; }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.SINGLETON_ADD_READ_RESOLVE);
        assertThat(r.changed()).isTrue();
        assertThat(r.newSource())
            .contains("private Object readResolve()")
            .contains("return getInstance()");
    }

    @Test
    @DisplayName("singleton-add-read-resolve is a no-op if already present")
    void singletonAddReadResolve_idempotent() {
        String src = """
            import java.io.Serializable;
            public class Cfg implements Serializable {
                private static final Cfg INSTANCE = new Cfg();
                private Cfg() {}
                public static Cfg getInstance() { return INSTANCE; }
                private Object readResolve() { return getInstance(); }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.SINGLETON_ADD_READ_RESOLVE);
        assertThat(r.changed()).isFalse();
    }

    // ─── builder-make-fields-final ─────────────────────────────────

    @Test
    @DisplayName("Builder's outer non-final fields become final")
    void builderMakeFieldsFinal_rewrites() {
        String src = """
            public final class Pizza {
                private String name;
                private int    qty;
                private Pizza(Builder b) { this.name = b.name; this.qty = b.qty; }
                public static final class Builder {
                    private String name;
                    private int    qty = 1;
                    public Pizza build() { return new Pizza(this); }
                }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.BUILDER_MAKE_FIELDS_FINAL);
        assertThat(r.changed()).isTrue();
        // The two outer instance fields are now final.
        assertThat(r.newSource())
            .contains("private final String name")
            .contains("private final int    qty");
    }

    @Test
    @DisplayName("builder-make-fields-final is idempotent")
    void builderMakeFieldsFinal_idempotent() {
        String src = """
            public final class Pizza {
                private final String name;
                private Pizza(Builder b) { this.name = b.name; }
                public static final class Builder {
                    private String name;
                    public Pizza build() { return new Pizza(this); }
                }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.BUILDER_MAKE_FIELDS_FINAL);
        assertThat(r.changed()).isFalse();
    }

    // ─── observer-snapshot-iteration ───────────────────────────────

    @Test
    @DisplayName("publish() iterating the live list gets wrapped with List.copyOf(...)")
    void observerSnapshotIteration_rewrites() {
        String src = """
            import java.util.ArrayList;
            import java.util.List;
            public class Bus {
                private final List<Runnable> listeners = new ArrayList<>();
                public void subscribe(Runnable r) { listeners.add(r); }
                public void unsubscribe(Runnable r) { listeners.remove(r); }
                public void publish() {
                    for (Runnable r : listeners) r.run();
                }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.OBSERVER_SNAPSHOT_ITERATION);
        assertThat(r.changed()).isTrue();
        assertThat(r.newSource())
            .contains("java.util.List.copyOf(listeners)");
    }

    @Test
    @DisplayName("observer-snapshot-iteration is idempotent")
    void observerSnapshotIteration_idempotent() {
        String src = """
            import java.util.ArrayList;
            import java.util.List;
            public class Bus {
                private final List<Runnable> listeners = new ArrayList<>();
                public void subscribe(Runnable r) { listeners.add(r); }
                public void publish() {
                    for (Runnable r : List.copyOf(listeners)) r.run();
                }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.OBSERVER_SNAPSHOT_ITERATION);
        assertThat(r.changed()).isFalse();
    }

    // ─── Adapter: make adaptee final ───────────────────────────────

    @Test
    @DisplayName("adapter-make-adaptee-final promotes a non-final adaptee field")
    void adapterMakeAdapteeFinal_promotes() {
        String src = """
            interface Target { String request(); }
            class Legacy { String specificRequest() { return "x"; } }
            public class BadAdapter implements Target {
                private Legacy adaptee;
                public BadAdapter(Legacy a) { this.adaptee = a; }
                public String request() { return adaptee.specificRequest(); }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.ADAPTER_MAKE_ADAPTEE_FINAL);
        assertThat(r.changed()).isTrue();
        assertThat(r.newSource()).contains("private final Legacy adaptee");
        assertThat(r.changes()).anyMatch(s -> s.contains("adaptee") && s.contains("final"));
    }

    @Test
    @DisplayName("adapter-make-adaptee-final is idempotent on already-final adaptee")
    void adapterMakeAdapteeFinal_idempotent() {
        String src = """
            interface Target { String request(); }
            class Legacy { String specificRequest() { return "x"; } }
            public class CleanAdapter implements Target {
                private final Legacy adaptee;
                public CleanAdapter(Legacy a) { this.adaptee = a; }
                public String request() { return adaptee.specificRequest(); }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.ADAPTER_MAKE_ADAPTEE_FINAL);
        assertThat(r.changed()).isFalse();
    }

    @Test
    @DisplayName("adapter-make-adaptee-final ignores plain classes (no target type)")
    void adapterMakeAdapteeFinal_ignoresPlainClasses() {
        String src = """
            public class PlainPojo {
                private String name;
                public PlainPojo(String n) { this.name = n; }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.ADAPTER_MAKE_ADAPTEE_FINAL);
        assertThat(r.changed()).isFalse();
    }

    // ─── Template Method: make template final ──────────────────────

    @Test
    @DisplayName("template-method-make-final promotes a non-final template method")
    void templateMethodMakeFinal_promotes() {
        String src = """
            public abstract class BadPipeline {
                public String run(String in) {
                    return prefix() + in + suffix();
                }
                protected abstract String prefix();
                protected abstract String suffix();
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.TEMPLATE_METHOD_MAKE_FINAL);
        assertThat(r.changed()).isTrue();
        assertThat(r.newSource()).contains("public final String run");
        assertThat(r.changes()).anyMatch(s -> s.contains("run") && s.contains("final"));
    }

    @Test
    @DisplayName("template-method-make-final is idempotent on already-final template")
    void templateMethodMakeFinal_idempotent() {
        String src = """
            public abstract class GoodPipeline {
                public final String run(String in) {
                    return prefix() + in + suffix();
                }
                protected abstract String prefix();
                protected abstract String suffix();
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.TEMPLATE_METHOD_MAKE_FINAL);
        assertThat(r.changed()).isFalse();
    }

    @Test
    @DisplayName("template-method-make-final ignores plain abstract classes without abstract hooks")
    void templateMethodMakeFinal_ignoresPlainAbstract() {
        String src = """
            public abstract class JustAbstract {
                public String run(String in) {
                    return helper(in);
                }
                private String helper(String s) { return s; }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.TEMPLATE_METHOD_MAKE_FINAL);
        assertThat(r.changed()).isFalse();
    }

    // ─── Factory Method: restrict creator ctor ─────────────────────

    @Test
    @DisplayName("factory-method-restrict-creator-ctor demotes a public Creator ctor to protected")
    void factoryMethodRestrictCreatorCtor_demotes() {
        String src = """
            interface Button { String click(); }
            public class HtmlDialog {
                public HtmlDialog() {}
                protected Button createButton() { return () -> "html"; }
                public String render() { return createButton().click(); }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.FACTORY_METHOD_RESTRICT_CREATOR_CTOR);
        assertThat(r.changed()).isTrue();
        assertThat(r.newSource()).contains("protected HtmlDialog()");
        assertThat(r.newSource()).doesNotContain("public HtmlDialog()");
        assertThat(r.changes()).anyMatch(s -> s.contains("HtmlDialog") && s.contains("protected"));
    }

    @Test
    @DisplayName("factory-method-restrict-creator-ctor is idempotent on already-protected ctor")
    void factoryMethodRestrictCreatorCtor_idempotent() {
        String src = """
            interface Button { String click(); }
            public class HtmlDialog {
                protected HtmlDialog() {}
                protected Button createButton() { return () -> "html"; }
                public String render() { return createButton().click(); }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.FACTORY_METHOD_RESTRICT_CREATOR_CTOR);
        assertThat(r.changed()).isFalse();
    }

    @Test
    @DisplayName("factory-method-restrict-creator-ctor ignores plain classes without a factory method")
    void factoryMethodRestrictCreatorCtor_ignoresPlainClasses() {
        String src = """
            public class PlainPojo {
                public PlainPojo() {}
                public String hello() { return "hi"; }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.FACTORY_METHOD_RESTRICT_CREATOR_CTOR);
        assertThat(r.changed()).isFalse();
    }

    @Test
    @DisplayName("factory-method-restrict-creator-ctor skips Builder-named classes")
    void factoryMethodRestrictCreatorCtor_skipsBuilders() {
        String src = """
            public class CarBuilder {
                public CarBuilder() {}
                public String build() { return "car"; }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.FACTORY_METHOD_RESTRICT_CREATOR_CTOR);
        assertThat(r.changed()).isFalse();
    }

    // ─── Strategy: add @FunctionalInterface ────────────────────────

    @Test
    @DisplayName("strategy-add-functional-interface annotates a single-method Strategy interface")
    void strategyAddFunctionalInterface_annotates() {
        String src = """
            public interface SortStrategy {
                java.util.List<Integer> sort(java.util.List<Integer> in);
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.STRATEGY_ADD_FUNCTIONAL_INTERFACE);
        assertThat(r.changed()).isTrue();
        assertThat(r.newSource()).contains("@FunctionalInterface");
        assertThat(r.changes()).anyMatch(s -> s.contains("SortStrategy") && s.contains("FunctionalInterface"));
    }

    @Test
    @DisplayName("strategy-add-functional-interface is idempotent on already-annotated interface")
    void strategyAddFunctionalInterface_idempotent() {
        String src = """
            @FunctionalInterface
            public interface SortStrategy {
                java.util.List<Integer> sort(java.util.List<Integer> in);
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.STRATEGY_ADD_FUNCTIONAL_INTERFACE);
        assertThat(r.changed()).isFalse();
    }

    @Test
    @DisplayName("strategy-add-functional-interface ignores multi-method strategies")
    void strategyAddFunctionalInterface_ignoresMultiMethod() {
        String src = """
            public interface FatStrategy {
                String first();
                String second();
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.STRATEGY_ADD_FUNCTIONAL_INTERFACE);
        assertThat(r.changed()).isFalse();
        assertThat(r.newSource()).doesNotContain("@FunctionalInterface");
    }

    @Test
    @DisplayName("strategy-add-functional-interface ignores non-Strategy interfaces")
    void strategyAddFunctionalInterface_ignoresNonStrategy() {
        String src = """
            public interface Comparator<T> {
                int compare(T a, T b);
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.STRATEGY_ADD_FUNCTIONAL_INTERFACE);
        assertThat(r.changed()).isFalse();
    }

    @Test
    @DisplayName("strategy-add-functional-interface ignores concrete classes (even named *Strategy)")
    void strategyAddFunctionalInterface_ignoresConcreteClasses() {
        String src = """
            public class ConcreteStrategy {
                public String run() { return "x"; }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.STRATEGY_ADD_FUNCTIONAL_INTERFACE);
        assertThat(r.changed()).isFalse();
    }

    @Test
    @DisplayName("strategy-add-functional-interface tolerates default/static methods (still a SAM)")
    void strategyAddFunctionalInterface_tolerantOfDefaultsAndStatics() {
        String src = """
            public interface SortStrategy {
                java.util.List<Integer> sort(java.util.List<Integer> in);
                default boolean stable() { return false; }
                static SortStrategy noop() { return in -> in; }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.STRATEGY_ADD_FUNCTIONAL_INTERFACE);
        assertThat(r.changed()).isTrue();
        assertThat(r.newSource()).contains("@FunctionalInterface");
    }

    // ─── Decorator: make wrapped final ─────────────────────────────

    @Test
    @DisplayName("decorator-make-wrapped-final promotes a non-final wrapped field")
    void decoratorMakeWrappedFinal_promotes() {
        String src = """
            interface Notifier { String send(String m); }
            public class SmsDecorator implements Notifier {
                private Notifier wrapped;
                public SmsDecorator(Notifier w) { this.wrapped = w; }
                public String send(String m) { return wrapped.send(m) + " + sms"; }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.DECORATOR_MAKE_WRAPPED_FINAL);
        assertThat(r.changed()).isTrue();
        assertThat(r.newSource()).contains("private final Notifier wrapped");
        assertThat(r.changes()).anyMatch(s -> s.contains("wrapped") && s.contains("final"));
    }

    @Test
    @DisplayName("decorator-make-wrapped-final is idempotent on already-final wrapped")
    void decoratorMakeWrappedFinal_idempotent() {
        String src = """
            interface Notifier { String send(String m); }
            public class SmsDecorator implements Notifier {
                private final Notifier wrapped;
                public SmsDecorator(Notifier w) { this.wrapped = w; }
                public String send(String m) { return wrapped.send(m); }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.DECORATOR_MAKE_WRAPPED_FINAL);
        assertThat(r.changed()).isFalse();
    }

    @Test
    @DisplayName("decorator-make-wrapped-final ignores plain classes (no decorator shape)")
    void decoratorMakeWrappedFinal_ignoresPlainClasses() {
        String src = """
            public class PlainPojo {
                private String name;
                public PlainPojo(String n) { this.name = n; }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.DECORATOR_MAKE_WRAPPED_FINAL);
        assertThat(r.changed()).isFalse();
    }

    // ─── State: make implementations final ─────────────────────────

    @Test
    @DisplayName("state-make-implementations-final marks every concrete state final")
    void stateMakeImplementationsFinal_promotes() {
        String src = """
            public interface LightState { String label(); }
            public class RedState implements LightState {
                public String label() { return "red"; }
            }
            public class GreenState implements LightState {
                public String label() { return "green"; }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.STATE_MAKE_IMPLEMENTATIONS_FINAL);
        assertThat(r.changed()).isTrue();
        assertThat(r.newSource()).contains("public final class RedState");
        assertThat(r.newSource()).contains("public final class GreenState");
        assertThat(r.changes()).hasSize(2);
    }

    @Test
    @DisplayName("state-make-implementations-final is idempotent on already-final states")
    void stateMakeImplementationsFinal_idempotent() {
        String src = """
            public interface LightState { String label(); }
            public final class RedState implements LightState {
                public String label() { return "red"; }
            }
            public final class GreenState implements LightState {
                public String label() { return "green"; }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.STATE_MAKE_IMPLEMENTATIONS_FINAL);
        assertThat(r.changed()).isFalse();
    }

    @Test
    @DisplayName("state-make-implementations-final ignores files without a State hierarchy")
    void stateMakeImplementationsFinal_ignoresNonState() {
        String src = """
            public class NotAState {
                public String value() { return "x"; }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.STATE_MAKE_IMPLEMENTATIONS_FINAL);
        assertThat(r.changed()).isFalse();
    }

    // ─── Command: make implementations final ───────────────────────

    @Test
    @DisplayName("command-make-implementations-final marks every concrete command final")
    void commandMakeImplementationsFinal_promotes() {
        String src = """
            public interface Command {
                String execute();
            }
            public class PrintCommand implements Command {
                private final String t;
                public PrintCommand(String t) { this.t = t; }
                public String execute() { return "print:" + t; }
            }
            public class LogCommand implements Command {
                private final String t;
                public LogCommand(String t) { this.t = t; }
                public String execute() { return "log:" + t; }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.COMMAND_MAKE_IMPLEMENTATIONS_FINAL);
        assertThat(r.changed()).isTrue();
        assertThat(r.newSource()).contains("public final class PrintCommand");
        assertThat(r.newSource()).contains("public final class LogCommand");
        assertThat(r.changes()).hasSize(2);
    }

    @Test
    @DisplayName("command-make-implementations-final is idempotent on already-final commands")
    void commandMakeImplementationsFinal_idempotent() {
        String src = """
            public interface Command {
                String execute();
            }
            public final class PrintCommand implements Command {
                private final String t;
                public PrintCommand(String t) { this.t = t; }
                public String execute() { return "print:" + t; }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.COMMAND_MAKE_IMPLEMENTATIONS_FINAL);
        assertThat(r.changed()).isFalse();
    }

    @Test
    @DisplayName("command-make-implementations-final ignores files without a Command contract")
    void commandMakeImplementationsFinal_ignoresNonCommand() {
        String src = """
            public class Runner {
                public String go() { return "x"; }
            }
            """;
        RefactoringResult r = engine.apply(src, RefactoringId.COMMAND_MAKE_IMPLEMENTATIONS_FINAL);
        assertThat(r.changed()).isFalse();
    }

    // ─── error paths ───────────────────────────────────────────────

    @Test
    @DisplayName("unparseable input throws RefactoringException")
    void unparseableInputThrows() {
        try {
            engine.apply("this is not java { {", RefactoringId.SINGLETON_MAKE_CTOR_PRIVATE);
            org.junit.jupiter.api.Assertions.fail("Expected RefactoringException");
        } catch (PatternRefactoringEngine.RefactoringException e) {
            assertThat(e.getMessage()).containsIgnoringCase("parse");
        }
    }
}
