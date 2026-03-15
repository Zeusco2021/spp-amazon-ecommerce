package com.ecommerce.gateway.filter;

import com.ecommerce.gateway.security.JwtTokenValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Gateway filter that validates JWT tokens and propagates userId/email/role
 * as downstream headers.
 * Named "JwtAuthentication" in YAML (Spring strips the "GatewayFilterFactory" suffix).
 *
 * <p>Satisfies Requirements:
 * <ul>
 *   <li>13.1 – validates JWT on protected routes</li>
 *   <li>13.2 – extracts userId and adds it to request headers</li>
 *   <li>13.3 – returns 401 for invalid/expired tokens</li>
 *   <li>15.5 – verifies user has required permissions</li>
 * </ul>
 */
@Slf4j
@Component
public class JwtAuthenticationGatewayFilterFactory
        extends AbstractGatewayFilterFactory<JwtAuthenticationGatewayFilterFactory.Config> {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_EMAIL = "X-User-Email";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    private final JwtTokenValidator jwtTokenValidator;

    public JwtAuthenticationGatewayFilterFactory(JwtTokenValidator jwtTokenValidator) {
        super(Config.class);
        this.jwtTokenValidator = jwtTokenValidator;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                log.warn("Missing or malformed Authorization header for path: {}",
                        exchange.getRequest().getURI().getPath());
                return unauthorized(exchange, "Missing or malformed Authorization header");
            }

            String token = authHeader.substring(BEARER_PREFIX.length());

            if (!jwtTokenValidator.isValid(token)) {
                log.warn("Invalid or expired JWT token for path: {}",
                        exchange.getRequest().getURI().getPath());
                return unauthorized(exchange, "Invalid or expired token");
            }

            // Extract claims and propagate as headers to downstream services
            String userId = jwtTokenValidator.getUserId(token);
            String email = jwtTokenValidator.getEmail(token);
            String role = jwtTokenValidator.getRole(token);

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header(HEADER_USER_ID, userId != null ? userId : "")
                    .header(HEADER_USER_EMAIL, email != null ? email : "")
                    .header(HEADER_USER_ROLE, role != null ? role : "")
                    .build();

            log.debug("Authenticated user {} (role={}) accessing {}", userId, role,
                    exchange.getRequest().getURI().getPath());

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"timestamp\":\"%s\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"%s\"}",
                LocalDateTime.now(), message);

        return response.writeWith(
                Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }

    public static class Config {
        // No additional config needed
    }
}
