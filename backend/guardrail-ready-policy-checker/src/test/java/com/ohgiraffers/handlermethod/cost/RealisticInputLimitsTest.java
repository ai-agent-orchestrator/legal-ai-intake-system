package com.ohgiraffers.handlermethod.cost;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class RealisticInputLimitsTest {
    @Test void shippedCostGateMatchesTheStoryLimitAndReservesTheConfiguredCompletionLimit() throws Exception {
        var sources = new MutablePropertySources();
        sources.addFirst(new PropertiesPropertySource("defaults", PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"))));
        var config = new PropertySourcesPropertyResolver(sources);
        var registry = new SimpleMeterRegistry();
        var cost = new AiCostControlService(new BigDecimal("8"), BigDecimal.ONE, new BigDecimal("0.12"),
                new BigDecimal("0.002"), new BigDecimal("0.010"),
                config.getRequiredProperty("llm.max-output-tokens", Integer.class),
                config.getRequiredProperty("llm.cost.max-input-pages", Integer.class),
                config.getRequiredProperty("llm.cost.estimated-chars-per-page", Integer.class), registry);
        String story = "민사" + "가".repeat(98);
        assertThat(cost.checkInputPageLimit(story)).isEmpty();
        assertThat(cost.checkInputPageLimit(story + "가")).hasValueSatisfying(block -> {
            assertThat(block.detailCode()).isEqualTo("REQUEST_TOO_LONG");
            assertThat(block.safeResponse()).contains("100자");
        });
        assertThat(cost.maxOutputTokens()).isEqualTo(2048);
        assertThat(config.getRequiredProperty("guardrails.cost.estimated-output-tokens", Integer.class)).isEqualTo(512);
        var reservation = cost.reserveWithBlock("instructions", story, "gpt-6.1-sol", "limits").reservation();
        // Korean input must not be budgeted using the old English-oriented characters/4 shortcut.
        assertThat(reservation.estimatedUsd()).isGreaterThanOrEqualTo(new BigDecimal("0.02108"));
        cost.recordUsage(reservation, 1500, 300, true);
        assertThat(registry.get("ai.cost.reserved.usd").gauge().value()).isZero();
        assertThat(registry.get("ai.cost.actual.usd").gauge().value()).isEqualTo(0.006);
    }
    @Test void framingAndWhitespaceAreIncludedInInputReservation() {
        assertThat(AiCostControlService.estimateChatInputTokens("system", "민사"))
                .isGreaterThan(AiCostControlService.estimateTokens("system") + AiCostControlService.estimateTokens("민사"));
        assertThat(AiCostControlService.estimateTokens(" ")).isPositive();
        assertThat(AiCostControlService.estimateTokens("민사")).isGreaterThanOrEqualTo(2);
    }
}
