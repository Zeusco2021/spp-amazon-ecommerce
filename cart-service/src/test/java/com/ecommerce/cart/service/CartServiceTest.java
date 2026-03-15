package com.ecommerce.cart.service;

import com.ecommerce.cart.cache.CartCacheService;
import com.ecommerce.cart.client.ProductClient;
import com.ecommerce.cart.dto.AddCartItemRequest;
import com.ecommerce.cart.dto.ApplyCouponRequest;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.dto.UpdateCartItemRequest;
import com.ecommerce.cart.entity.Cart;
import com.ecommerce.cart.entity.CartItem;
import com.ecommerce.cart.exception.CartItemNotFoundException;
import com.ecommerce.cart.exception.CartNotFoundException;
import com.ecommerce.cart.exception.InvalidCouponException;
import com.ecommerce.cart.exception.ProductNotAvailableException;
import com.ecommerce.cart.repository.CartItemRepository;
import com.ecommerce.cart.repository.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CartService.
 * Requirements: 6.1-6.8, 17.4
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private CartCacheService cartCacheService;
    @Mock private ProductClient productClient;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, cartItemRepository, cartCacheService, productClient);
    }

    // --- Helpers ---

    private Cart emptyCart(Long userId) {
        Cart cart = Cart.builder()
                .id(1L)
                .userId(userId)
                .items(new ArrayList<>())
                .subtotal(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .build();
        return cart;
    }

    private ProductClient.ProductInfo availableProduct(Long id, BigDecimal price) {
        ProductClient.ProductInfo p = new ProductClient.ProductInfo();
        p.setId(id);
        p.setName("Product " + id);
        p.setSku("SKU-" + id);
        p.setPrice(price);
        p.setStatus("ACTIVE");
        p.setStockQuantity(10);
        return p;
    }

    // =========================================================
    // Task 11.2 - Add items / increment quantity
    // =========================================================

    @Test
    void addItem_newProduct_addsItemToCart() {
        // Given
        Long userId = 1L;
        Cart cart = emptyCart(userId);
        ProductClient.ProductInfo product = availableProduct(10L, new BigDecimal("29.99"));

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(productClient.getProduct(10L)).thenReturn(product);
        when(cartItemRepository.findByCartIdAndProductId(1L, 10L)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        CartResponse response = cartService.addItem(userId, new AddCartItemRequest(10L, 2));

        // Then
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(response.getSubtotal()).isEqualByComparingTo(new BigDecimal("59.98"));
        verify(cartCacheService).cacheCart(any());
    }

    @Test
    void addItem_existingProduct_incrementsQuantity() {
        // Given - Req 6.2
        Long userId = 1L;
        Cart cart = emptyCart(userId);
        ProductClient.ProductInfo product = availableProduct(10L, new BigDecimal("10.00"));

        CartItem existingItem = CartItem.builder()
                .id(5L).cart(cart).productId(10L).productName("Product 10")
                .quantity(3).unitPrice(new BigDecimal("10.00")).build();
        cart.getItems().add(existingItem);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(productClient.getProduct(10L)).thenReturn(product);
        when(cartItemRepository.findByCartIdAndProductId(1L, 10L)).thenReturn(Optional.of(existingItem));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        CartResponse response = cartService.addItem(userId, new AddCartItemRequest(10L, 2));

        // Then - quantity should be 3 + 2 = 5
        assertThat(existingItem.getQuantity()).isEqualTo(5);
        assertThat(response.getSubtotal()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void addItem_productNotAvailable_throwsException() {
        // Given - Req 6.1
        when(productClient.getProduct(99L)).thenReturn(null);

        // When / Then
        assertThatThrownBy(() -> cartService.addItem(1L, new AddCartItemRequest(99L, 1)))
                .isInstanceOf(ProductNotAvailableException.class);
    }

    @Test
    void addItem_productOutOfStock_throwsException() {
        // Given
        ProductClient.ProductInfo outOfStock = availableProduct(10L, new BigDecimal("10.00"));
        outOfStock.setStockQuantity(0);
        when(productClient.getProduct(10L)).thenReturn(outOfStock);

        // When / Then
        assertThatThrownBy(() -> cartService.addItem(1L, new AddCartItemRequest(10L, 1)))
                .isInstanceOf(ProductNotAvailableException.class);
    }

    // =========================================================
    // Task 11.3 - Update and remove items
    // =========================================================

    @Test
    void updateItem_validItem_updatesQuantityAndRecalculates() {
        // Given - Req 6.3
        Long userId = 1L;
        Cart cart = emptyCart(userId);
        CartItem item = CartItem.builder()
                .id(5L).cart(cart).productId(10L).productName("P")
                .quantity(2).unitPrice(new BigDecimal("15.00")).build();
        cart.getItems().add(item);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findById(5L)).thenReturn(Optional.of(item));
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        CartResponse response = cartService.updateItem(userId, 5L, new UpdateCartItemRequest(4));

        // Then
        assertThat(item.getQuantity()).isEqualTo(4);
        assertThat(response.getSubtotal()).isEqualByComparingTo(new BigDecimal("60.00"));
    }

    @Test
    void removeItem_validItem_removesAndRecalculates() {
        // Given - Req 6.4
        Long userId = 1L;
        Cart cart = emptyCart(userId);
        CartItem item = CartItem.builder()
                .id(5L).cart(cart).productId(10L).productName("P")
                .quantity(2).unitPrice(new BigDecimal("20.00")).build();
        cart.getItems().add(item);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findById(5L)).thenReturn(Optional.of(item));
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        CartResponse response = cartService.removeItem(userId, 5L);

        // Then
        assertThat(response.getItems()).isEmpty();
        assertThat(response.getSubtotal()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(cartItemRepository).delete(item);
    }

    @Test
    void removeItem_itemNotInCart_throwsException() {
        // Given
        Long userId = 1L;
        Cart cart = emptyCart(userId);
        CartItem itemFromOtherCart = CartItem.builder()
                .id(99L).cart(emptyCart(2L)).productId(10L).quantity(1)
                .unitPrice(BigDecimal.ONE).build();

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findById(99L)).thenReturn(Optional.of(itemFromOtherCart));

        // When / Then
        assertThatThrownBy(() -> cartService.removeItem(userId, 99L))
                .isInstanceOf(CartItemNotFoundException.class);
    }

    @Test
    void updateItem_cartNotFound_throwsException() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateItem(1L, 5L, new UpdateCartItemRequest(2)))
                .isInstanceOf(CartNotFoundException.class);
    }

    // =========================================================
    // Task 11.4 - Total calculation
    // =========================================================

    @Test
    void recalculateTotals_multipleItems_correctSubtotal() {
        // Given - Req 6.5
        Cart cart = emptyCart(1L);
        cart.getItems().add(CartItem.builder().id(1L).cart(cart).productId(1L)
                .productName("A").quantity(2).unitPrice(new BigDecimal("10.00")).build());
        cart.getItems().add(CartItem.builder().id(2L).cart(cart).productId(2L)
                .productName("B").quantity(3).unitPrice(new BigDecimal("5.00")).build());

        // When
        cart.recalculateTotals();

        // Then: subtotal = 2*10 + 3*5 = 35
        assertThat(cart.getSubtotal()).isEqualByComparingTo(new BigDecimal("35.00"));
        assertThat(cart.getTotal()).isEqualByComparingTo(new BigDecimal("35.00"));
    }

    @Test
    void recalculateTotals_withDiscount_totalNeverNegative() {
        // Given
        Cart cart = emptyCart(1L);
        cart.getItems().add(CartItem.builder().id(1L).cart(cart).productId(1L)
                .productName("A").quantity(1).unitPrice(new BigDecimal("5.00")).build());
        cart.setDiscount(new BigDecimal("100.00")); // discount > subtotal

        // When
        cart.recalculateTotals();

        // Then: total should be 0, not negative
        assertThat(cart.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // =========================================================
    // Task 11.5 - Dual persistence (Redis + MySQL)
    // =========================================================

    @Test
    void getCart_cacheHit_doesNotQueryMySQL() {
        // Given - Req 6.6, 17.4
        Long userId = 1L;
        CartResponse cached = CartResponse.builder().id(1L).userId(userId).build();
        when(cartCacheService.getOrLoad(eq(userId), any())).thenReturn(cached);

        // When
        CartResponse result = cartService.getCart(userId);

        // Then
        assertThat(result).isEqualTo(cached);
        verifyNoInteractions(cartRepository);
    }

    @Test
    void addItem_afterSave_cachesCartInRedis() {
        // Given - Req 6.6
        Long userId = 1L;
        Cart cart = emptyCart(userId);
        ProductClient.ProductInfo product = availableProduct(10L, new BigDecimal("10.00"));

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(productClient.getProduct(10L)).thenReturn(product);
        when(cartItemRepository.findByCartIdAndProductId(1L, 10L)).thenReturn(Optional.empty());
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        cartService.addItem(userId, new AddCartItemRequest(10L, 1));

        // Then - cart is cached in Redis after every mutation
        verify(cartCacheService).cacheCart(any(CartResponse.class));
    }

    @Test
    void clearCart_invalidatesRedisCache() {
        // Given - Req 6.6
        Long userId = 1L;
        Cart cart = emptyCart(userId);
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        cartService.clearCart(userId);

        // Then
        verify(cartCacheService).invalidateCart(userId);
    }

    // =========================================================
    // Task 11.6 - Coupon system
    // =========================================================

    @Test
    void applyCoupon_validSave10_applies10PercentDiscount() {
        // Given - Req 6.8
        Long userId = 1L;
        Cart cart = emptyCart(userId);
        cart.getItems().add(CartItem.builder().id(1L).cart(cart).productId(1L)
                .productName("A").quantity(1).unitPrice(new BigDecimal("100.00")).build());
        cart.recalculateTotals();

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        CartResponse response = cartService.applyCoupon(userId, new ApplyCouponRequest("SAVE10"));

        // Then
        assertThat(response.getDiscount()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(response.getTotal()).isEqualByComparingTo(new BigDecimal("90.00"));
        assertThat(response.getCouponCode()).isEqualTo("SAVE10");
    }

    @Test
    void applyCoupon_validSave20_applies20PercentDiscount() {
        // Given
        Long userId = 1L;
        Cart cart = emptyCart(userId);
        cart.getItems().add(CartItem.builder().id(1L).cart(cart).productId(1L)
                .productName("A").quantity(2).unitPrice(new BigDecimal("50.00")).build());
        cart.recalculateTotals();

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        CartResponse response = cartService.applyCoupon(userId, new ApplyCouponRequest("SAVE20"));

        // Then
        assertThat(response.getDiscount()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(response.getTotal()).isEqualByComparingTo(new BigDecimal("80.00"));
    }

    @Test
    void applyCoupon_invalidCode_throwsException() {
        // Given
        Long userId = 1L;
        Cart cart = emptyCart(userId);
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));

        // When / Then
        assertThatThrownBy(() -> cartService.applyCoupon(userId, new ApplyCouponRequest("INVALID")))
                .isInstanceOf(InvalidCouponException.class);
    }

    @Test
    void applyCoupon_cartNotFound_throwsException() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.applyCoupon(1L, new ApplyCouponRequest("SAVE10")))
                .isInstanceOf(CartNotFoundException.class);
    }
}
