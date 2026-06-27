package com.booktown.global.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class BooktownMetrics {

    private final MeterRegistry registry;

    public BooktownMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordAuthFailure(String reason) {
        Counter.builder("booktown_auth_failures_total")
                .description("Authentication failures by reason")
                .tag("reason", sanitizeTag(reason))
                .register(registry)
                .increment();
    }

    public void recordAiCall(String provider, String model, String operation, String status, long elapsedNanos) {
        Timer.builder("booktown_ai_call_duration_seconds")
                .description("AI call latency")
                .tag("provider", sanitizeTag(provider))
                .tag("model", sanitizeTag(model))
                .tag("operation", sanitizeTag(operation))
                .tag("status", sanitizeTag(status))
                .register(registry)
                .record(elapsedNanos, TimeUnit.NANOSECONDS);

        Counter.builder("booktown_ai_calls_total")
                .description("AI call count by status")
                .tag("provider", sanitizeTag(provider))
                .tag("model", sanitizeTag(model))
                .tag("operation", sanitizeTag(operation))
                .tag("status", sanitizeTag(status))
                .register(registry)
                .increment();
    }

    public void recordAiTokenUsage(String provider, String model, String operation, long tokens) {
        DistributionSummary.builder("booktown_ai_tokens")
                .description("AI token usage")
                .tag("provider", sanitizeTag(provider))
                .tag("model", sanitizeTag(model))
                .tag("operation", sanitizeTag(operation))
                .register(registry)
                .record(Math.max(tokens, 0));
    }

    public void recordAiCostUsd(String provider, String model, String operation, double costUsd) {
        DistributionSummary.builder("booktown_ai_cost_usd")
                .description("AI estimated cost in USD")
                .tag("provider", sanitizeTag(provider))
                .tag("model", sanitizeTag(model))
                .tag("operation", sanitizeTag(operation))
                .register(registry)
                .record(Math.max(costUsd, 0));
    }

    private String sanitizeTag(String value) {
        return (value == null || value.isBlank()) ? "unknown" : value.toLowerCase();
    }
}
