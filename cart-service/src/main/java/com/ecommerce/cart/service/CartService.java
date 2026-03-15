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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Cart service implementing dual-storage (Redis + MySQL) strategy.
 * Requirements: 6.1-6.8, 17.4
 */
@Service
@Transactional
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CartCacheService cartCacheService;
    private final ProductClient productClient;

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       CartCacheService cartCacheService,
                       ProductClient productClient) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.cartCacheService = cartCacheService;
        this.productClient = productClient;
    }

    /**
     * Get cart for a user. Tries Redis first, falls back to MySQL.
     * Creates an empty cart if none exists.
     * Requirements: 6.6, 6.7, 17.4
     */
    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        return cartCacheService.getOrLoad(userId, () -> {
            Cart cart = cartRepository.findByUserId(userId)
                    .orElseGet(() -> createEmptyCart(userId));
            return toCartResponse(cart);
        });
    }

    /**
     * Add an item to the cart. Increments quantity if product already exists.
     * Validates product existence and availability via Product Service.
     * Requirements: 6.1, 6.2
     */
    public CartResponse addItem(Long userId, AddCartItemRequest request) {
        // Validate product availability
        ProductClient.ProductInfo product = productClient.getProduct(request.getProductId());
        if (product == null || !product.isAvailable()) {
            throw new ProductNotAvailableException(request.getProductId());
        }

        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> createEmptyCart(userId));

        // Increment quantity if product already in cart (Req 6.2)
        Optional<CartItem> existing = cartItemRepository.findByCartIdAndProductId(cart.getId(), request.getProductId());
        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.setQuantity(item.getQuantity() + request.getQuantity());
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .productId(product.getId())
                    .productName(product.getName())
                    .productSku(product.getSku())
                    .imageUrl(product.getImageUrl())
                    .quantity(request.getQuantity())
                    .unitPrice(product.getEffectivePrice())
                    .build();
            cart.getItems().add(newItem);
        }

        cart.recalculateTotals();
        Cart saved = cartRepository.save(cart);
        CartResponse response = toCartResponse(saved);
        cartCacheService.cacheCart(response);
        return response;
    }

    /**
     * Update quantity of a specific cart item. Recalculates totals.
     * Requirements: 6.3
     */
    public CartResponse updateItem(Long userId, Long itemId, UpdateCartItemRequest request) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException(userId));

        CartItem item = cartItemRepository.findById(itemId)
                .filter(i -> i.getCart().getId().equals(cart.getId()))
                .orElseThrow(() -> new CartItemNotFoundException(itemId));

        item.setQuantity(request.getQuantity());
        cart.recalculateTotals();
        Cart saved = cartRepository.save(cart);
        CartResponse response = toCartResponse(saved);
        cartCacheService.cacheCart(response);
        return response;
    }

    /**
     * Remove an item from the cart. Recalculates totals.
     * Requirements: 6.4
     */
    public CartResponse removeItem(Long userId, Long itemId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException(userId));

        CartItem item = cartItemRepository.findById(itemId)
                .filter(i -> i.getCart().getId().equals(cart.getId()))
                .orElseThrow(() -> new CartItemNotFoundException(itemId));

        cart.getItems().remove(item);
        cartItemRepository.delete(item);
        cart.recalculateTotals();
        Cart saved = cartRepository.save(cart);
        CartResponse response = toCartResponse(saved);
        cartCacheService.cacheCart(response);
        return response;
    }

    /**
     * Clear all items from the cart.
     */
    public void clearCart(Long userId) {
        cartRepository.findByUserId(userId).ifPresent(cart -> {
            cart.getItems().clear();
            cart.setDiscount(BigDecimal.ZERO);
            cart.setCouponCode(null);
            cart.recalculateTotals();
            cartRepository.save(cart);
        });
        cartCacheService.invalidateCart(userId);
    }

    /**
     * Apply a coupon code to the cart.
     * Requirements: 6.8
     */
    public CartResponse applyCoupon(Long userId, ApplyCouponRequest request) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException(userId));

        BigDecimal discount = validateAndCalculateDiscount(request.getCouponCode(), cart.getSubtotal());
        cart.setCouponCode(request.getCouponCode());
        cart.setDiscount(discount);
        cart.recalculateTotals();

        Cart saved = cartRepository.save(cart);
        CartResponse response = toCartResponse(saved);
        cartCacheService.cacheCart(response);
        return response;
    }

    // --- Private helpers ---

    private Cart createEmptyCart(Long userId) {
        Cart cart = Cart.builder()
                .userId(userId)
                .subtotal(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .build();
        return cartRepository.save(cart);
    }

    /**
     * Validate coupon and return discount amount.
     * Simple fixed-discount coupons: SAVE10 = 10%, SAVE20 = 20%.
     * Requirements: 6.8
     */
    private BigDecimal validateAndCalculateDiscount(String couponCode, BigDecimal subtotal) {
        return switch (couponCode.toUpperCase()) {
            case "SAVE10" -> subtotal.multiply(BigDecimal.valueOf(0.10));
            case "SAVE20" -> subtotal.multiply(BigDecimal.valueOf(0.20));
            default -> throw new InvalidCouponException(couponCode);
        };
    }

    /**
     * Map Cart entity to CartResponse DTO.
     */
    CartResponse toCartResponse(Cart cart) {
        List<CartResponse.CartItemResponse> itemResponses = cart.getItems().stream()
                .map(item -> CartResponse.CartItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .productSku(item.getProductSku())
                        .imageUrl(item.getImageUrl())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .totalPrice(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .build())
                .toList();

        return CartResponse.builder()
                .id(cart.getId())
                .userId(cart.getUserId())
                .items(itemResponses)
                .subtotal(cart.getSubtotal())
                .discount(cart.getDiscount())
                .total(cart.getTotal())
                .couponCode(cart.getCouponCode())
                .updatedAt(cart.getUpdatedAt())
                .build();
    }
}
