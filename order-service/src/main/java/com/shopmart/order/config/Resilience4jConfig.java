package com.shopmart.order.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class Resilience4jConfig {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @PostConstruct
    public void registerEventListeners() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("inventoryCB");

        cb.getEventPublisher()
                .onStateTransition(event -> {
                    log.warn("================================================================================");
                    log.warn("[CIRCUIT BREAKER STATE TRANSITION] CircuitBreaker '{}' transitioned from {} to {}",
                            event.getCircuitBreakerName(),
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState());
                    log.warn("[STATE MEANING]");
                    switch (event.getStateTransition().getToState()) {
                        case CLOSED -> log.warn("-> CLOSED: Service is healthy. All requests pass through normally.");
                        case OPEN -> log.warn("-> OPEN: Failure rate exceeded threshold! Requests short-circuited directly to fallback.");
                        case HALF_OPEN -> log.warn("-> HALF-OPEN: Testing trial requests to verify if downstream service has recovered.");
                        default -> {}
                    }
                    log.warn("================================================================================");
                })
                .onError(event -> log.error("[CIRCUIT BREAKER ERROR] Call failed: {}", event.getThrowable().getMessage()))
                .onSuccess(event -> log.info("[CIRCUIT BREAKER SUCCESS] Call succeeded, elapsed: {} ms", event.getElapsedDuration().toMillis()));
    }
}
