package com.ecommerce.order.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.order.dto.*;
import com.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for order operations.
 * Requirements: 7.1-7.16, 8.1-8.7, 15.7
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * POST /api/orders — Create a new order (starts Saga).
     * Requirements: 7.1, 7.2, 7.3, 7.4
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order created", response));
    }

    /**
     * GET /api/orders/{id} — Get order by ID.
     * Requirements: 15.7
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long requestingUserId) {
        OrderResponse response = orderService.getOrderById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/orders/user/{userId} — Get paginated order history for a user.
     * Requirements: 15.7
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getUserOrders(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResponse<OrderResponse> response = orderService.getUserOrders(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * PUT /api/orders/{id}/status — Update order status.
     * Requirements: 8.1, 8.2, 8.3, 8.4
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        OrderResponse response = orderService.updateOrderStatus(id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success("Order status updated", response));
    }

    /**
     * PUT /api/orders/{id}/cancel — Cancel an order.
     * Requirements: 8.5, 8.6
     */
    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(@PathVariable Long id) {
        OrderResponse response = orderService.cancelOrder(id);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled", response));
    }

    /**
     * GET /api/orders/{id}/tracking — Get tracking information.
     * Requirements: 8.7
     */
    @GetMapping("/{id}/tracking")
    public ResponseEntity<ApiResponse<TrackingResponse>> trackOrder(@PathVariable Long id) {
        TrackingResponse response = orderService.getOrderTracking(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
