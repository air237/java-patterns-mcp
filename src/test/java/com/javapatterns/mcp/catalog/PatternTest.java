package com.javapatterns.mcp.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatternTest {

    @Test
    @DisplayName("exactly 23 GoF design patterns are defined")
    void exactly23Patterns() {
        assertThat(Pattern.values()).hasSize(23);
    }

    @Test
    @DisplayName("category counts match the GoF taxonomy: 5 / 7 / 11")
    void categoryCounts() {
        long creational = java.util.Arrays.stream(Pattern.values())
            .filter(p -> p.category() == PatternCategory.CREATIONAL).count();
        long structural = java.util.Arrays.stream(Pattern.values())
            .filter(p -> p.category() == PatternCategory.STRUCTURAL).count();
        long behavioral = java.util.Arrays.stream(Pattern.values())
            .filter(p -> p.category() == PatternCategory.BEHAVIORAL).count();

        assertThat(creational).isEqualTo(5);
        assertThat(structural).isEqualTo(7);
        assertThat(behavioral).isEqualTo(11);
    }

    @ParameterizedTest(name = "{0} has non-blank displayName, slug and reference URL")
    @EnumSource(Pattern.class)
    void everyPatternHasMetadata(Pattern p) {
        assertThat(p.displayName()).isNotBlank();
        assertThat(p.slug()).isNotBlank().doesNotContain(" ", "_");
        assertThat(p.referenceUrl())
            .startsWith("https://refactoring.guru/design-patterns/")
            .endsWith(p.slug());
    }

    @Test
    @DisplayName("slugs are unique across the enum")
    void slugsAreUnique() {
        long distinct = java.util.Arrays.stream(Pattern.values())
            .map(Pattern::slug)
            .distinct()
            .count();
        assertThat(distinct).isEqualTo(Pattern.values().length);
    }

    @Test
    @DisplayName("fromKey resolves enum name, slug, and display name (case-insensitive)")
    void fromKeyResolvesAllVariants() {
        assertThat(Pattern.fromKey("singleton")).isEqualTo(Pattern.SINGLETON);
        assertThat(Pattern.fromKey("SINGLETON")).isEqualTo(Pattern.SINGLETON);
        assertThat(Pattern.fromKey("Singleton")).isEqualTo(Pattern.SINGLETON);

        assertThat(Pattern.fromKey("chain-of-responsibility")).isEqualTo(Pattern.CHAIN_OF_RESPONSIBILITY);
        assertThat(Pattern.fromKey("CHAIN_OF_RESPONSIBILITY")).isEqualTo(Pattern.CHAIN_OF_RESPONSIBILITY);
        assertThat(Pattern.fromKey("Chain of Responsibility")).isEqualTo(Pattern.CHAIN_OF_RESPONSIBILITY);
        assertThat(Pattern.fromKey("chain of responsibility")).isEqualTo(Pattern.CHAIN_OF_RESPONSIBILITY);

        assertThat(Pattern.fromKey("template-method")).isEqualTo(Pattern.TEMPLATE_METHOD);
    }

    @Test
    @DisplayName("fromKey rejects blank and unknown identifiers")
    void fromKeyRejectsBad() {
        assertThatThrownBy(() -> Pattern.fromKey(null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Pattern.fromKey(""))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Pattern.fromKey("monad"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("monad");
    }
}
