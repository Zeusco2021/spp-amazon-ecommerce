package com.ecommerce.cart.controller;

import com.ecommerce.cart.dto.AddCartItemRequest;
import com.ecommerce.cart.dto.ApplyCouponRequest;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.dto.UpdateCartItemRequest;
import com.ecommerce.cart.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for cart operations.
 * Requirements: 6.1-6.8
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    /** GET /api/cart/{userId} - Get cart for a user. */
    @GetMapping("/{userId}")
    public ResponseEntity<CartResponse> getCart(@PathVariable Long userId) {
        return ResponseEntity.ok(cartService.getCart(userId));
    }

    /**
     * POST /api/cart/{userId}/items - Add item to cart.
     * Increments quantity if product already exists.
     * Requirements: 6.1, 6.2
     */
    @PostMapping("/{userId}/items")
    public ResponseEntity<CartResponse> addItem(
            @PathVariable Long userId,
            @Valid @RequestBody AddCartItemRequest request) {
        return ResponseEntity.ok(cartService.addItem(userId, request));
    }

    /**
     * PUT /api/cart/{userId}/items/{itemId} - Update item quantity.
     * Requirements: 6.3
     */
    @PutMapping("/{userId}/items/{itemId}")
    public ResponseEntity<CartResponse> updateItem(
            @PathVariable Long userId,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return ResponseEntity.ok(cartService.updateItem(userId, itemId, request));
    }

    /**
     * DELETE /api/cart/{userId}/items/{itemId} - Remove item from cart.
     * Requirements: 6.4
     */
    @DeleteMapping("/{userId}/items/{itemId}")
    public ResponseEntity<CartResponse> removeItem(
            @PathVariable Long userId,
            @PathVariable Long itemId) {
        return ResponseEntity.ok(cartService.removeItem(userId, itemId));
    }

    /** DELETE /api/cart/{userId} - Clear entire cart. */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> clearCart(@PathVariable Long userId) {
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/cart/{userId}/apply-coupon - Apply discount coupon.
     * Requirements: 6.8
     */
    @PostMapping("/{userId}/apply-coupon")
    public ResponseEntity<CartResponse> applyCoupon(
            @PathVariable Long userId,
            @Valid @RequestBody ApplyCouponRequest request) {
        return ResponseEntity.ok(cartService.applyCoupon(userId, request));
    }
}
