package com.ohgiraffers.handlermethod.cost;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AiCostConfiguredPricingTest {
    @ParameterizedTest
    @EnumSource(value = AiCostControlService.CostStage.class,
            names = {"ANSWER", "INPUT_GUARDRAIL", "OUTPUT_GUARDRAIL"})
    void configuredStandardPricesCharge1000InputAnd200OutputTokensAt000027Usd(
            AiCostControlService.CostStage stage) throws Exception {
        // Resolve the shipped defaults without reading process credentials or contacting any provider.
        var sources = new MutablePropertySources();
        sources.addFirst(new PropertiesPropertySource("shipped-defaults",
                PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"))));
        var config = new PropertySourcesPropertyResolver(sources);
        var registry = new SimpleMeterRegistry();
        var cost = new AiCostControlService(decimal(config, "llm.cost.monthly-budget-usd"),
                decimal(config, "llm.cost.daily-budget-usd"), decimal(config, "llm.cost.session-budget-usd"),
                decimal(config, "llm.cost.input-usd-per-1k-tokens"), decimal(config, "llm.cost.output-usd-per-1k-tokens"),
                config.getRequiredProperty("llm.max-output-tokens", Integer.class),
                config.getRequiredProperty("llm.cost.max-input-pages", Integer.class),
                config.getRequiredProperty("llm.cost.estimated-chars-per-page", Integer.class), registry);
        String prefix = stage == AiCostControlService.CostStage.ANSWER ? "llm.cost." : "guardrails.cost.";
        var decision = cost.reserveStageWithBlock(stage, 1000, 200, "gpt-4o-mini", "pricing-test",
                decimal(config, prefix + "input-usd-per-1k-tokens"),
                decimal(config, prefix + "output-usd-per-1k-tokens"));
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reservation().estimatedUsd()).isEqualByComparingTo("0.00027");
        assertThat(cost.recordUsage(decision.reservation(), 1000, 200, true))
                .isEqualTo(AiCostControlService.CostSource.REPORTED);
        assertThat(registry.get("ai.cost.stage.actual.usd").tag("stage", stage.tag()).gauge().value())
                .isEqualTo(0.00027);
        assertThat(registry.get("ai.cost.reserved.usd").gauge().value()).isZero();
        assertThat(registry.get("ai.cost.session.budget.usd").gauge().value()).isEqualTo(0.12);
        assertThat(registry.get("ai.cost.daily.budget.usd").gauge().value()).isEqualTo(0.50);
    }

    private BigDecimal decimal(PropertySourcesPropertyResolver config, String key) {
        return new BigDecimal(config.getRequiredProperty(key));
    }
}
