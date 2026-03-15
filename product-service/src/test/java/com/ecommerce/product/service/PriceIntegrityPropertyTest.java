package com.ecommerce.product.service;

import com.ecommerce.product.cache.ProductCacheService;
import com.ecommerce.product.dto.ProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.event.ProductEventPublisher;
import com.ecommerce.product.repository.ProductRepository;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-Based Tests for Price Integrity
 *
 * Propiedad 3: Integridad de Precios
 * Validates: Requirements 3.4, 3.5
 *
 * Formal property:
 * ∀ producto P: precio(P) > 0
 * ∀ producto P con descuento: precioDescuento(P) < precio(P)
 */
@ExtendWith(MockitoExtension.class)
class PriceIntegrityPropertyTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductCacheService productCacheService;

    @Mock
    private ProductEventPublisher productEventPublisher;

    @Mock
    private com.ecommerce.product.audit.AuditService auditService;

    @InjectMocks
    private ProductService productService;

    @BeforeEach
    void setUp() {
        lenient().doNothing().when(productEventPublisher).publishProductCreated(any(ProductResponse.class));
        lenient().doNothing().when(productCacheService).cacheProduct(any(ProductResponse.class));
    }

    // -------------------------------------------------------------------------
    // a) @RepeatedTest(10) — positive price always accepted
    // -------------------------------------------------------------------------

    /**
     * **Validates: Requirements 3.4**
     *
     * Property: For any positive price, createProduct() succeeds and the
     * returned product has a price > 0.
     * Repeated 10 times with random positive prices.
     */
    @RepeatedTest(10)
    void priceIntegrity_priceAlwaysPositive() {
        // Random price between 0.01 and 9999.99
        double raw = 0.01 + Math.random() * 9999.98;
        BigDecimal price = BigDecimal.valueOf(Math.round(raw * 100) / 100.0);

        String sku = "SKU-" + UUID.randomUUID();
        ProductRequest request = buildRequest(sku, price, null);

        Product saved = buildSavedProduct(1L, price);
        when(productRepository.findBySku(anyString())).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenReturn(saved);

        ProductResponse response = productService.createProduct(request);

        assertNotNull(response);
        assertTrue(response.getPrice().compareTo(BigDecimal.ZERO) > 0,
                "Returned price must be > 0, was: " + response.getPrice());
    }

    // -------------------------------------------------------------------------
    // b) @RepeatedTest(10) — discount always less than price
    // -------------------------------------------------------------------------

    /**
     * **Validates: Requirements 3.5**
     *
     * Property: When discountPrice < price, createProduct() always succeeds.
     * Repeated 10 times with random valid price pairs.
     */
    @RepeatedTest(10)
    void priceIntegrity_discountAlwaysLessThanPrice() {
        // price in [10.00, 9999.99], discountPrice in [0.01, price - 0.01]
        double rawPrice = 10.00 + Math.random() * 9989.99;
        BigDecimal price = BigDecimal.valueOf(Math.round(rawPrice * 100) / 100.0);
        double rawDiscount = 0.01 + Math.random() * (price.doubleValue() - 0.02);
        BigDecimal discountPrice = BigDecimal.valueOf(Math.round(rawDiscount * 100) / 100.0);

        // Ensure discountPrice < price (guard against rounding edge cases)
        if (discountPrice.compareTo(price) >= 0) {
            discountPrice = price.subtract(BigDecimal.valueOf(0.01));
        }

        String sku = "SKU-" + UUID.randomUUID();
        ProductRequest request = buildRequest(sku, price, discountPrice);

        Product saved = buildSavedProduct(1L, price);
        when(productRepository.findBySku(anyString())).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenReturn(saved);

        ProductResponse response = productService.createProduct(request);

        assertNotNull(response, "createProduct must succeed when discountPrice < price");
    }

    // -------------------------------------------------------------------------
    // c) @Test — zero price throws
    // -------------------------------------------------------------------------

    /**
     * **Validates: Requirements 3.4**
     *
     * Property: A price of zero must always be rejected.
     */
    @Test
    void priceIntegrity_zeroPriceThrows() {
        ProductRequest request = buildRequest("SKU-ZERO", BigDecimal.ZERO, null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> productService.createProduct(request));
        assertEquals("Price must be greater than zero", ex.getMessage());
    }

    // -------------------------------------------------------------------------
    // d) @Test — negative price throws
    // -------------------------------------------------------------------------

    /**
     * **Validates: Requirements 3.4**
     *
     * Property: A negative price must always be rejected.
     */
    @Test
    void priceIntegrity_negativePriceThrows() {
        ProductRequest request = buildRequest("SKU-NEG", BigDecimal.valueOf(-1), null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> productService.createProduct(request));
        assertEquals("Price must be greater than zero", ex.getMessage());
    }

    // -------------------------------------------------------------------------
    // e) @Test — discount equal to price throws
    // -------------------------------------------------------------------------

    /**
     * **Validates: Requirements 3.5**
     *
     * Property: A discountPrice equal to price must always be rejected.
     */
    @Test
    void priceIntegrity_discountEqualToPriceThrows() {
        BigDecimal price = BigDecimal.valueOf(100);
        BigDecimal discountPrice = BigDecimal.valueOf(100);
        ProductRequest request = buildRequest("SKU-EQ", price, discountPrice);

        when(productRepository.findBySku(anyString())).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> productService.createProduct(request));
        assertEquals("Discount price must be less than price", ex.getMessage());
    }

    // -------------------------------------------------------------------------
    // f) @Test — discount greater than price throws
    // -------------------------------------------------------------------------

    /**
     * **Validates: Requirements 3.5**
     *
     * Property: A discountPrice greater than price must always be rejected.
     */
    @Test
    void priceIntegrity_discountGreaterThanPriceThrows() {
        BigDecimal price = BigDecimal.valueOf(100);
        BigDecimal discountPrice = BigDecimal.valueOf(150);
        ProductRequest request = buildRequest("SKU-GT", price, discountPrice);

        when(productRepository.findBySku(anyString())).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> productService.createProduct(request));
        assertEquals("Discount price must be less than price", ex.getMessage());
    }

    // -------------------------------------------------------------------------
    // g) @Property(tries=20) with jqwik — valid prices always accepted
    // -------------------------------------------------------------------------

    /**
     * **Validates: Requirements 3.4, 3.5**
     *
     * jqwik property: For any price in [0.01, 9999.99] and discountPrice in
     * [0.01, 9998.99] where discountPrice < price, createProduct() always succeeds.
     *
     * Note: jqwik does not process @ExtendWith(MockitoExtension.class), so
     * mocks are created manually per try to ensure isolation.
     */
    @Property(tries = 20)
    void priceIntegrity_jqwik_validPricesAlwaysAccepted(
            @ForAll("validPrices") BigDecimal price,
            @ForAll("validDiscountPrices") BigDecimal discountPrice) {

        // Filter: only test when discountPrice < price
        Assume.that(discountPrice.compareTo(price) < 0);

        // Create fresh mocks for each jqwik try (jqwik ignores @Mock fields)
        ProductRepository mockRepo = mock(ProductRepository.class);
        ProductCacheService mockCache = mock(ProductCacheService.class);
        ProductEventPublisher mockPublisher = mock(ProductEventPublisher.class);
        ProductService svc = new ProductService(mockRepo, mockCache, mockPublisher,
                mock(com.ecommerce.product.audit.AuditService.class));

        String sku = "SKU-" + UUID.randomUUID();
        ProductRequest request = buildRequest(sku, price, discountPrice);

        Product saved = buildSavedProduct(1L, price);
        when(mockRepo.findBySku(anyString())).thenReturn(Optional.empty());
        when(mockRepo.save(any(Product.class))).thenReturn(saved);

        ProductResponse response = svc.createProduct(request);

        assertNotNull(response, "createProduct must succeed for valid price pair");
        assertTrue(response.getPrice().compareTo(BigDecimal.ZERO) > 0,
                "Returned price must be > 0");
    }

    // -------------------------------------------------------------------------
    // Arbitraries / generators
    // -------------------------------------------------------------------------

    @Provide
    Arbitrary<BigDecimal> validPrices() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(0.01), BigDecimal.valueOf(9999.99))
                .ofScale(2);
    }

    @Provide
    Arbitrary<BigDecimal> validDiscountPrices() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(0.01), BigDecimal.valueOf(9998.99))
                .ofScale(2);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ProductRequest buildRequest(String sku, BigDecimal price, BigDecimal discountPrice) {
        ProductRequest request = new ProductRequest();
        request.setName("Test Product");
        request.setDescription("Test description");
        request.setSku(sku);
        request.setPrice(price);
        request.setDiscountPrice(discountPrice);
        request.setStockQuantity(10);
        request.setCategoryId(1L);
        request.setSellerId(1L);
        request.setBrand("TestBrand");
        return request;
    }

    private Product buildSavedProduct(Long id, BigDecimal price) {
        return Product.builder()
                .id(id)
                .name("Test Product")
                .description("Test description")
                .sku("SKU-" + id)
                .price(price)
                .stockQuantity(10)
                .categoryId(1L)
                .sellerId(1L)
                .brand("TestBrand")
                .status(Product.Status.ACTIVE)
                .build();
    }
}
