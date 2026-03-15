package com.ecommerce.product.service;

import com.ecommerce.product.cache.ProductCacheService;
import com.ecommerce.product.dto.ProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.event.ProductEventPublisher;
import com.ecommerce.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProductService.
 * Requirements: 3, 17
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

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

    private ProductRequest validRequest;
    private Product savedProduct;

    @BeforeEach
    void setUp() {
        validRequest = new ProductRequest(
                "Test Product",
                "A description",
                "SKU-001",
                new BigDecimal("99.99"),
                null,
                10,
                1L,
                1L,
                "BrandX",
                null
        );

        savedProduct = Product.builder()
                .id(1L)
                .name("Test Product")
                .sku("SKU-001")
                .price(new BigDecimal("99.99"))
                .status(Product.Status.ACTIVE)
                .stockQuantity(10)
                .build();
    }

    // --- createProduct tests ---

    @Test
    void createProduct_success_returnsProductResponse() {
        // Requirement 3.1, 3.2
        when(productRepository.findBySku("SKU-001")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

        ProductResponse response = productService.createProduct(validRequest);

        assertThat(response.getName()).isEqualTo("Test Product");
        assertThat(response.getSku()).isEqualTo("SKU-001");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        verify(productEventPublisher).publishProductCreated(any(ProductResponse.class));
    }

    @Test
    void createProduct_duplicateSku_throwsException() {
        // Requirement 3.6
        Product existing = Product.builder().id(99L).sku("SKU-001").build();
        when(productRepository.findBySku("SKU-001")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> productService.createProduct(validRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("SKU already exists");

        verify(productRepository, never()).save(any());
    }

    @Test
    void createProduct_zeroPriceThrows() {
        // Requirement 3.4
        validRequest.setPrice(BigDecimal.ZERO);
        when(productRepository.findBySku(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.createProduct(validRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Price must be greater than zero");
    }

    @Test
    void createProduct_discountGreaterThanPriceThrows() {
        // Requirement 3.5
        validRequest.setPrice(new BigDecimal("100.00"));
        validRequest.setDiscountPrice(new BigDecimal("150.00"));
        when(productRepository.findBySku(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.createProduct(validRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Discount price must be less than price");
    }

    // --- getProduct tests ---

    @Test
    void getProduct_cacheHit_returnsCachedProduct() {
        // Requirement 17.1
        ProductResponse cached = ProductResponse.builder()
                .id(1L).name("Cached Product").sku("SKU-001").status("ACTIVE").build();

        when(productCacheService.getOrLoad(eq(1L), any())).thenReturn(cached);

        ProductResponse response = productService.getProduct(1L);

        assertThat(response.getName()).isEqualTo("Cached Product");
        verify(productRepository, never()).findById(anyLong());
    }

    // --- updateProduct tests ---

    @Test
    void updateProduct_success_invalidatesCache() {
        // Requirement 17.3
        Product existing = Product.builder()
                .id(1L).name("Old Name").sku("SKU-001")
                .price(new BigDecimal("50.00"))
                .status(Product.Status.ACTIVE)
                .imageUrls(new java.util.ArrayList<>())
                .build();

        Product updated = Product.builder()
                .id(1L).name("Test Product").sku("SKU-001")
                .price(new BigDecimal("99.99"))
                .status(Product.Status.ACTIVE)
                .imageUrls(new java.util.ArrayList<>())
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenReturn(updated);

        productService.updateProduct(1L, validRequest);

        verify(productCacheService).invalidateProduct(1L);
        verify(productCacheService).cacheProduct(any(ProductResponse.class));
    }

    // --- deleteProduct tests ---

    @Test
    void deleteProduct_success_invalidatesCache() {
        // Requirement 17.3
        when(productRepository.findById(1L)).thenReturn(Optional.of(savedProduct));

        productService.deleteProduct(1L);

        verify(productRepository).delete(savedProduct);
        verify(productCacheService).invalidateProduct(1L);
    }
}
