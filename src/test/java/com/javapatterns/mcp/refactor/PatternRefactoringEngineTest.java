package com.javapatterns.mcp.refactor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatternRefactoringEngineTest {

    private final PatternRefactoringEngine engine = PatternRefactoringEngine.getInstance();

    @Test
    @DisplayName("engine reports all 5 wired refactorings")
    void supported() {
        assertThat(engine.supported()).containsExactlyInAnyOrder(
            RefactoringId.SINGLETON_MAKE_CTOR_PRIVATE,
            RefactoringId.SINGLETON_ADD_HOLDER_IDIOM,
            RefactoringId.SINGLETON_ADD_READ_RESOLVE,
            RefactoringId.BUILDER_MAKE_FIELDS_FINAL,
            RefactoringId.OBSERVER_SNAPSHOT_ITERATION
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
