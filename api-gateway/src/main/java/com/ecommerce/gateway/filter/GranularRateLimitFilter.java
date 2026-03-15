package com.ecommerce.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Granular per-endpoint rate limiting stored in Redis.
 * Complements the global RequestRateLimiter filter.
 * Only active when a ReactiveStringRedisTemplate bean is available.
 *
 * <p>Satisfies Requirements:
 * <ul>
 *   <li>13.5 – rate limiting of 100 req/s per user</li>
 *   <li>13.6 – returns 429 Too Many Requests when limit exceeded</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnBean(ReactiveStringRedisTemplate.class)
public class GranularRateLimitFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;

    /** Endpoint-specific limits: limit, windowSeconds, blockSeconds */
    private static final Map<String, RateLimitConfig> ENDPOINT_LIMITS = Map.of(
            "POST:/api/users/login",               new RateLimitConfig(5,   60,  900),
            "POST:/api/users/register",            new RateLimitConfig(3,  3600,   0),
            "POST:/api/orders",                    new RateLimitConfig(20,   60,   0),
            "POST:/api/payments/process",          new RateLimitConfig(20,   60,   0)
    );

    public GranularRateLimitFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public int getOrder() {
        // Run before routing filters
        return -50;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getPath();
        String endpointKey = method + ":" + path;

        RateLimitConfig config = ENDPOINT_LIMITS.get(endpointKey);
        if (config == null) {
            return chain.filter(exchange);
        }

        String clientIp = getClientIp(exchange);
        String redisKey = "rate_limit:" + endpointKey + ":" + clientIp;
        String blockedKey = "blocked:" + redisKey;

        return redisTemplate.hasKey(blockedKey)
                .flatMap(blocked -> {
                    if (Boolean.TRUE.equals(blocked)) {
                        return tooManyRequests(exchange, config);
                    }
                    return redisTemplate.opsForValue().increment(redisKey)
                            .flatMap(count -> {
                                Mono<Boolean> expireMono = count == 1
                                        ? redisTemplate.expire(redisKey, Duration.ofSeconds(config.windowSeconds()))
                                        : Mono.just(false);

                                return expireMono.flatMap(ignored -> {
                                    ServerHttpResponse response = exchange.getResponse();
                                    response.getHeaders().add("X-RateLimit-Limit",
                                            String.valueOf(config.limit()));
                                    response.getHeaders().add("X-RateLimit-Remaining",
                                            String.valueOf(Math.max(0, config.limit() - count)));
                                    response.getHeaders().add("X-RateLimit-Reset",
                                            String.valueOf(config.windowSeconds()));

                                    if (count > config.limit()) {
                                        Mono<Void> blockMono = config.blockSeconds() > 0
                                                ? redisTemplate.opsForValue()
                                                        .set(blockedKey, "1",
                                                                Duration.ofSeconds(config.blockSeconds()))
                                                        .then()
                                                : Mono.empty();
                                        return blockMono.then(tooManyRequests(exchange, config));
                                    }
                                    return chain.filter(exchange);
                                });
                            });
                });
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange, RateLimitConfig config) {
        log.warn("Rate limit exceeded for {} {}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getURI().getPath());

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().add("Retry-After", String.valueOf(config.windowSeconds()));

        String body = String.format(
                "{\"timestamp\":\"%s\",\"status\":429,\"error\":\"Too Many Requests\"," +
                "\"message\":\"Rate limit exceeded. Please retry after %d seconds.\"}",
                LocalDateTime.now(), config.windowSeconds());

        return response.writeWith(
                Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }

    private String getClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    public record RateLimitConfig(int limit, int windowSeconds, int blockSeconds) {}
}
