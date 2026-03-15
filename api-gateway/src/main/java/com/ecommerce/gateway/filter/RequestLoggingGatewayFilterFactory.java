package com.ecommerce.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Logs every request: method, path, status, and duration.
 * Named "RequestLogging" in YAML (Spring strips the "GatewayFilterFactory" suffix).
 * Satisfies Requirement 13.7 (logging all requests for monitoring/audit).
 */
@Slf4j
@Component
public class RequestLoggingGatewayFilterFactory
        extends AbstractGatewayFilterFactory<RequestLoggingGatewayFilterFactory.Config> {

    public RequestLoggingGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            long startTime = Instant.now().toEpochMilli();

            String method = request.getMethod().name();
            String path = request.getURI().getPath();
            String clientIp = request.getRemoteAddress() != null
                    ? request.getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";

            log.info("Incoming request: {} {} from {}", method, path, clientIp);

            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                ServerHttpResponse response = exchange.getResponse();
                long duration = Instant.now().toEpochMilli() - startTime;
                int statusCode = response.getStatusCode() != null
                        ? response.getStatusCode().value()
                        : 0;
                log.info("Completed request: {} {} -> {} in {}ms", method, path, statusCode, duration);
            }));
        };
    }

    public static class Config {
        // No config needed
    }
}
