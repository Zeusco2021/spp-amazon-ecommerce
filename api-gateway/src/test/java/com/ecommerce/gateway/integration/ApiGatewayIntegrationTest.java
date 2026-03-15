package com.ecommerce.gateway.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Integration tests for API Gateway routing, JWT authentication, and circuit breaker.
 * Validates Requirements 13 and 14.
 *
 * Uses the "test" profile which disables the global Redis-based RequestRateLimiter
 * and config server import.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApiGatewayIntegrationTest {

    private static final String JWT_SECRET = "myTestSecretKeyForJWTValidationTesting123456789";
    private static final SecretKey SECRET_KEY =
            Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));

    private static WireMockServer userServiceMock;
    private static WireMockServer productServiceMock;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void startWireMock() {
        userServiceMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        productServiceMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        userServiceMock.start();
        productServiceMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        userServiceMock.stop();
        productServiceMock.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> JWT_SECRET);
        registry.add("USER_SERVICE_HOST", () -> "localhost");
        registry.add("USER_SERVICE_PORT", () -> String.valueOf(userServiceMock.port()));
        registry.add("PRODUCT_SERVICE_HOST", () -> "localhost");
        registry.add("PRODUCT_SERVICE_PORT", () -> String.valueOf(productServiceMock.port()));
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private String validToken(Long userId, String role) {
        return Jwts.builder()
                .subject("user@example.com")
                .claim("userId", userId)
                .claim("email", "user@example.com")
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(SECRET_KEY)
                .compact();
    }

    private String expiredToken() {
        return Jwts.builder()
                .subject("user@example.com")
                .claim("userId", 1L)
                .claim("role", "CUSTOMER")
                .issuedAt(new Date(System.currentTimeMillis() - 10_000))
                .expiration(new Date(System.currentTimeMillis() - 5_000))
                .signWith(SECRET_KEY)
                .compact();
    }

    // ─── Routing tests ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Public POST /api/users/register routes without auth - Requirement 13.4")
    void publicRoute_register_noAuthRequired() {
        userServiceMock.stubFor(post(urlEqualTo("/api/users/register"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"email\":\"user@example.com\"}")));

        webTestClient.post()
                .uri("/api/users/register")
                .bodyValue("{\"email\":\"user@example.com\",\"password\":\"Pass123!\"}")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .exchange()
                .expectStatus().isCreated();
    }

    @Test
    @DisplayName("Public POST /api/users/login routes without auth - Requirement 13.4")
    void publicRoute_login_noAuthRequired() {
        userServiceMock.stubFor(post(urlEqualTo("/api/users/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token\":\"jwt-token\",\"userId\":1}")));

        webTestClient.post()
                .uri("/api/users/login")
                .bodyValue("{\"email\":\"user@example.com\",\"password\":\"Pass123!\"}")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("Public GET /api/products/** routes without auth - Requirement 13.4")
    void publicRoute_getProducts_noAuthRequired() {
        productServiceMock.stubFor(get(urlEqualTo("/api/products/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"name\":\"Test Product\"}")));

        webTestClient.get()
                .uri("/api/products/1")
                .exchange()
                .expectStatus().isOk();
    }

    // ─── JWT authentication tests ─────────────────────────────────────────────

    @Test
    @DisplayName("Protected route without token returns 401 - Requirement 13.3")
    void protectedRoute_noToken_returns401() {
        webTestClient.get()
                .uri("/api/users/1")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Protected route with expired token returns 401 - Requirement 13.3")
    void protectedRoute_expiredToken_returns401() {
        webTestClient.get()
                .uri("/api/users/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Protected route with invalid token returns 401 - Requirement 13.3")
    void protectedRoute_invalidToken_returns401() {
        webTestClient.get()
                .uri("/api/users/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.here")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Protected route with valid token routes to service - Requirements 13.1, 13.2")
    void protectedRoute_validToken_routesAndPropagatesUserId() {
        userServiceMock.stubFor(get(urlEqualTo("/api/users/1"))
                .withHeader("X-User-Id", equalTo("1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"email\":\"user@example.com\"}")));

        webTestClient.get()
                .uri("/api/users/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken(1L, "CUSTOMER"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("Missing Bearer prefix returns 401")
    void protectedRoute_missingBearerPrefix_returns401() {
        webTestClient.get()
                .uri("/api/users/1")
                .header(HttpHeaders.AUTHORIZATION, validToken(1L, "CUSTOMER"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ─── Circuit breaker / fallback tests ────────────────────────────────────

    @Test
    @DisplayName("Fallback endpoint returns 503 - Requirement 14.2")
    void fallbackEndpoint_returns503() {
        webTestClient.get()
                .uri("/fallback")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.error").isEqualTo("Service Unavailable");
    }

    @Test
    @DisplayName("Downstream 500 is handled gracefully - Requirement 13.8")
    void downstreamError_handledGracefully() {
        productServiceMock.stubFor(get(urlPathMatching("/api/products/error-test"))
                .willReturn(aResponse().withStatus(500)));

        // Gateway should either pass through the 500 or trigger fallback (503)
        webTestClient.get()
                .uri("/api/products/error-test")
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status)
                                .isIn(500, 503));
    }
}
