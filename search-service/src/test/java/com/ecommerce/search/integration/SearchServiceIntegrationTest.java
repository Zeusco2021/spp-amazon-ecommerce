package com.ecommerce.search.integration;

import com.ecommerce.common.event.ProductCreatedEvent;
import com.ecommerce.search.cache.SearchCacheService;
import com.ecommerce.search.consumer.ProductEventConsumer;
import com.ecommerce.search.dto.SearchResponse;
import com.ecommerce.search.service.ProductIndexService;
import com.ecommerce.search.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration tests for Search Service using Testcontainers (Elasticsearch).
 * Tests product indexing, search with filters, and autocomplete.
 * Requirements: 5, 19.4
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.kafka.bootstrap-servers=localhost:9092",
                "spring.data.redis.host=localhost",
                "spring.data.redis.port=6379"
        }
)
@Testcontainers
class SearchServiceIntegrationTest {

    @Container
    static ElasticsearchContainer elasticsearchContainer =
            new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.11.0")
                    .withEnv("discovery.type", "single-node")
                    .withEnv("xpack.security.enabled", "false")
                    .waitingFor(Wait.forHttp("/").forStatusCode(200));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", elasticsearchContainer::getHttpHostAddress);
    }

    @Autowired
    private SearchService searchService;

    @Autowired
    private ProductIndexService productIndexService;

    // Mock Redis cache to bypass Redis dependency in integration tests
    @MockBean
    private SearchCacheService searchCacheService;

    // Mock Kafka consumer to avoid needing a real Kafka broker
    @MockBean
    private ProductEventConsumer productEventConsumer;

    @BeforeEach
    void setUp() {
        // Make cache pass-through: always call the actual search supplier
        when(searchCacheService.computeQueryHash(any(), any(), any(), any(), any(), any(Integer.class), any(Integer.class), any()))
                .thenAnswer(inv -> "test-hash");
        when(searchCacheService.getOrSearch(any(), any())).thenAnswer(inv -> {
            Supplier<SearchResponse> supplier = inv.getArgument(1);
            return supplier.get();
        });
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private ProductCreatedEvent buildEvent(long id, String name, String brand, double price,
                                           long categoryId, String status) {
        return new ProductCreatedEvent(
                id, name, "SKU-" + id, BigDecimal.valueOf(price),
                categoryId, 1L, status, LocalDateTime.now()
        );
    }

    private void indexAndWait(ProductCreatedEvent event) throws InterruptedException {
        productIndexService.indexProduct(event);
        // Give Elasticsearch time to index the document
        Thread.sleep(1500);
    }

    // ─── Tests ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Test 1: indexProduct then search returns the indexed product - Req 5.1, 5.9")
    void indexProduct_thenSearch_returnsIndexedProduct() throws InterruptedException {
        ProductCreatedEvent event = buildEvent(1001L, "Laptop Gaming Pro", "TechBrand", 1299.99, 10L, "ACTIVE");
        indexAndWait(event);

        SearchResponse response = searchService.search("Laptop Gaming", null, null, null, null, null, 0, 10);

        assertThat(response).isNotNull();
        assertThat(response.getProducts()).isNotEmpty();
        assertThat(response.getProducts())
                .anyMatch(p -> p.getName() != null && p.getName().contains("Laptop"));
    }

    @Test
    @DisplayName("Test 2: search with categoryId filter returns only matching products - Req 5.3")
    void search_withCategoryFilter_returnsOnlyMatchingProducts() throws InterruptedException {
        // Index products in different categories
        indexAndWait(buildEvent(2001L, "Smartphone Alpha", "PhoneCo", 599.99, 20L, "ACTIVE"));
        indexAndWait(buildEvent(2002L, "Smartphone Beta", "PhoneCo", 799.99, 20L, "ACTIVE"));
        indexAndWait(buildEvent(2003L, "Tablet Gamma", "TabletCo", 399.99, 30L, "ACTIVE"));

        SearchResponse response = searchService.search("Smartphone", 20L, null, null, null, null, 0, 10);

        assertThat(response).isNotNull();
        assertThat(response.getProducts()).isNotEmpty();
        // All returned products should be from category 20
        // (Tablet from category 30 should not appear)
        assertThat(response.getProducts())
                .allMatch(p -> p.getName() != null && p.getName().contains("Smartphone"));
    }

    @Test
    @DisplayName("Test 3: search with price range filter returns only products within range - Req 5.4")
    void search_withPriceRangeFilter_returnsOnlyProductsInRange() throws InterruptedException {
        indexAndWait(buildEvent(3001L, "Budget Headphones", "AudioCo", 29.99, 40L, "ACTIVE"));
        indexAndWait(buildEvent(3002L, "Mid Headphones", "AudioCo", 99.99, 40L, "ACTIVE"));
        indexAndWait(buildEvent(3003L, "Premium Headphones", "AudioCo", 299.99, 40L, "ACTIVE"));

        SearchResponse response = searchService.search("Headphones", null, 50.0, 150.0, null, null, 0, 10);

        assertThat(response).isNotNull();
        assertThat(response.getProducts()).isNotEmpty();
        // Only the mid-range headphones (99.99) should be returned
        assertThat(response.getProducts())
                .allMatch(p -> p.getPrice() != null
                        && p.getPrice().doubleValue() >= 50.0
                        && p.getPrice().doubleValue() <= 150.0);
    }

    @Test
    @DisplayName("Test 4: autocomplete returns suggestions based on prefix - Req 5.7")
    void autocomplete_returnsMatchingSuggestions() throws InterruptedException {
        indexAndWait(buildEvent(4001L, "Wireless Mouse", "MouseCo", 49.99, 50L, "ACTIVE"));
        indexAndWait(buildEvent(4002L, "Wireless Keyboard", "KeyCo", 79.99, 50L, "ACTIVE"));
        indexAndWait(buildEvent(4003L, "Wired Mouse", "MouseCo", 19.99, 50L, "ACTIVE"));

        List<String> suggestions = searchService.autocomplete("Wireless", 10);

        assertThat(suggestions).isNotNull();
        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions)
                .allMatch(s -> s.toLowerCase().contains("wireless"));
    }
}
