package com.javapatterns.mcp.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class PatternRegistryTest {

    @Test
    @DisplayName("registry loads metadata for all 23 patterns")
    void registryIsComplete() {
        PatternRegistry registry = PatternRegistry.getInstance();
        assertThat(registry.size()).isEqualTo(23);
        assertThat(registry.all()).hasSize(23);
    }

    @ParameterizedTest(name = "{0} has intent + problem populated")
    @EnumSource(Pattern.class)
    void everyPatternHasIntentAndProblem(Pattern p) {
        PatternMetadata md = PatternRegistry.getInstance().get(p);
        assertThat(md).isNotNull();
        assertThat(md.intent())
            .as("intent for " + p)
            .isNotBlank()
            .hasSizeGreaterThan(20);
        assertThat(md.problem())
            .as("problem for " + p)
            .isNotBlank()
            .hasSizeGreaterThan(20);
        assertThat(md.aliases()).isNotNull(); // may be empty but never null
    }

    @Test
    @DisplayName("byCategory returns the right number of patterns per category")
    void byCategoryCounts() {
        PatternRegistry r = PatternRegistry.getInstance();
        assertThat(r.byCategory(PatternCategory.CREATIONAL)).hasSize(5);
        assertThat(r.byCategory(PatternCategory.STRUCTURAL)).hasSize(7);
        assertThat(r.byCategory(PatternCategory.BEHAVIORAL)).hasSize(11);
    }

    @Test
    @DisplayName("singleton metadata mentions a global access point")
    void spotCheckSingletonIntent() {
        PatternMetadata md = PatternRegistry.getInstance().get(Pattern.SINGLETON);
        assertThat(md.intent())
            .containsIgnoringCase("one instance")
            .containsIgnoringCase("global access");
        assertThat(md.aliases()).contains("Holder");
    }

    @Test
    @DisplayName("observer aliases include Publish-Subscribe")
    void spotCheckObserverAliases() {
        PatternMetadata md = PatternRegistry.getInstance().get(Pattern.OBSERVER);
        assertThat(md.aliases()).contains("Publish-Subscribe");
    }
}
