package com.ecommerce.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j circuit breaker and time limiter configuration.
 *
 * <p>Satisfies Requirements:
 * <ul>
 *   <li>13.8 – circuit breaker for microservice failures</li>
 *   <li>14.1 – activates circuit breaker after 5s timeout</li>
 *   <li>14.2 – returns 503 fallback when circuit is open</li>
 *   <li>14.3 – auto-closes circuit after 30s recovery window</li>
 * </ul>
 */
@Configuration
public class ResilienceConfig {

    /**
     * Default circuit breaker: opens after 50% failure rate over 10 calls,
     * waits 30s before attempting half-open, times out at 5s per call.
     */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(5))
                        .build())
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .build())
                .build());
    }
}
