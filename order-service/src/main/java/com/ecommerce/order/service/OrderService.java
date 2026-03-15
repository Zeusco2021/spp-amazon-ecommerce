package com.ecommerce.order.service;

import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.common.event.OrderCreatedEvent;
import com.ecommerce.common.event.OrderItemEvent;
import com.ecommerce.common.event.OrderShippedEvent;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.kafka.KafkaTopics;
import com.ecommerce.order.audit.AuditService;
import com.ecommerce.order.dto.*;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.entity.ShippingAddress;
import com.ecommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Core order service implementing Saga pattern for distributed transactions.
 * Requirements: 7.1-7.16, 8.1-8.7
 */
@Service
@Transactional
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuditService auditService;

    public OrderService(OrderRepository orderRepository, KafkaTemplate<String, Object> kafkaTemplate,
                        AuditService auditService) {
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.auditService = auditService;
    }

    // -------------------------------------------------------------------------
    // Task 14.4 — Create order (Saga start)
    // -------------------------------------------------------------------------

    /**
     * Creates a new order in PENDING state and publishes OrderCreatedEvent to start the Saga.
     * Requirements: 7.1, 7.2, 7.3, 7.4, 7.14, 7.15, 7.16
     */
    public OrderResponse createOrder(CreateOrderRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException("EMPTY_CART", "Order must contain at least one item");
        }

        String orderNumber = generateOrderNumber();

        // Build order items — prices captured at time of purchase (Req 7.16)
        List<OrderItem> items = request.getItems().stream()
                .map(itemReq -> OrderItem.builder()
                        .productId(itemReq.getProductId())
                        .productName(itemReq.getProductName())
                        .productSku(itemReq.getProductSku())
                        .quantity(itemReq.getQuantity())
                        .unitPrice(itemReq.getUnitPrice())
                        .totalPrice(itemReq.getUnitPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity())))
                        .build())
                .collect(Collectors.toList());

        BigDecimal subtotal = items.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal tax = calculateTax(subtotal, request.getShippingAddress());
        BigDecimal shippingCost = calculateShippingCost(items);
        BigDecimal discount = request.getDiscount() != null ? request.getDiscount() : BigDecimal.ZERO;
        BigDecimal total = subtotal.add(tax).add(shippingCost).subtract(discount);

        Order order = Order.builder()
                .orderNumber(orderNumber)
                .userId(request.getUserId())
                .subtotal(subtotal)
                .tax(tax)
                .shippingCost(shippingCost)
                .discount(discount)
                .total(total)
                .status(OrderStatus.PENDING)
                .shippingAddress(request.getShippingAddress())
                .build();

        items.forEach(item -> item.setOrder(order));
        order.getItems().addAll(items);

        Order saved = orderRepository.save(order);

        // Publish OrderCreatedEvent to start the Saga (Req 7.4)
        List<OrderItemEvent> itemEvents = saved.getItems().stream()
                .map(item -> new OrderItemEvent(
                        item.getProductId(),
                        item.getProductSku(),
                        item.getQuantity(),
                        item.getUnitPrice()))
                .collect(Collectors.toList());

        OrderCreatedEvent event = new OrderCreatedEvent(
                saved.getId(),
                saved.getOrderNumber(),
                saved.getUserId(),
                itemEvents,
                saved.getTotal(),
                saved.getCreatedAt() != null ? saved.getCreatedAt() : LocalDateTime.now());

        kafkaTemplate.send(KafkaTopics.ORDER_CREATED, String.valueOf(saved.getId()), event);
        logger.info("Order created: orderId={}, orderNumber={}", saved.getId(), saved.getOrderNumber());

        // Req 16.3: log order creation event asynchronously
        auditService.logOrderCreated(
                String.valueOf(saved.getUserId()),
                String.valueOf(saved.getId()),
                saved.getTotal(),
                null);

        return toResponse(saved);
    }

    // -------------------------------------------------------------------------
    // Task 14.6 — Status management
    // -------------------------------------------------------------------------

    /**
     * Updates order status with valid transition enforcement.
     * Requirements: 8.1, 8.2, 8.3, 8.4
     */
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = findOrderById(orderId);
        validateTransition(order.getStatus(), newStatus);

        order.setStatus(newStatus);

        switch (newStatus) {
            case CONFIRMED -> order.setConfirmedAt(LocalDateTime.now());
            case SHIPPED -> {
                order.setShippedAt(LocalDateTime.now());
                order.setTrackingNumber("TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            }
            case DELIVERED -> order.setDeliveredAt(LocalDateTime.now());
            default -> { /* no timestamp for other transitions */ }
        }

        Order saved = orderRepository.save(order);

        // Publish status-change events for notification service (Req 12.2, 12.3)
        if (newStatus == OrderStatus.SHIPPED) {
            OrderShippedEvent shippedEvent = new OrderShippedEvent(
                    saved.getId(),
                    saved.getOrderNumber(),
                    saved.getUserId(),
                    saved.getTotal(),
                    saved.getTrackingNumber(),
                    saved.getShippedAt());
            kafkaTemplate.send(KafkaTopics.ORDER_SHIPPED, String.valueOf(saved.getId()), shippedEvent);
        } else if (newStatus == OrderStatus.DELIVERED) {
            publishOrderEvent(KafkaTopics.ORDER_DELIVERED, saved);
        }

        logger.info("Order {} status updated to {}", orderId, newStatus);
        return toResponse(saved);
    }

    // -------------------------------------------------------------------------
    // Task 14.7 — Cancellation
    // -------------------------------------------------------------------------

    /**
     * Cancels an order if it is in PENDING or CONFIRMED state.
     * Publishes OrderCreatedEvent to ORDER_CANCELLED so inventory can release stock.
     * Requirements: 8.5, 8.6
     */
    public OrderResponse cancelOrder(Long orderId) {
        Order order = findOrderById(orderId);

        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new BusinessException("CANNOT_CANCEL_ORDER",
                    "Cannot cancel order in status: " + order.getStatus());
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException("ORDER_ALREADY_CANCELLED", "Order is already cancelled");
        }

        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BusinessException("CANNOT_CANCEL_ORDER",
                    "Cannot cancel order in status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);

        publishCancelledEvent(saved);
        logger.info("Order {} cancelled", orderId);

        return toResponse(saved);
    }

    // -------------------------------------------------------------------------
    // Task 14.8 — Queries
    // -------------------------------------------------------------------------

    /**
     * Returns an order by ID.
     * Requirements: 15.7
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId) {
        return toResponse(findOrderById(orderId));
    }

    /**
     * Returns paginated order history for a user.
     * Requirements: 15.7
     */
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getUserOrders(Long userId, int page, int size) {
        Page<Order> orderPage = orderRepository.findByUserId(userId, PageRequest.of(page, size));
        List<OrderResponse> content = orderPage.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return PageResponse.of(content, page, size, orderPage.getTotalElements());
    }

    // -------------------------------------------------------------------------
    // Task 14.9 — Tracking
    // -------------------------------------------------------------------------

    /**
     * Returns tracking information for an order.
     * Requirements: 8.7
     */
    @Transactional(readOnly = true)
    public TrackingResponse getOrderTracking(Long orderId) {
        Order order = findOrderById(orderId);
        return TrackingResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .trackingNumber(order.getTrackingNumber())
                .estimatedDelivery(null)
                .build();
    }

    // -------------------------------------------------------------------------
    // Saga state updates (called by SagaEventConsumer)
    // -------------------------------------------------------------------------

    /**
     * Called when InventoryReservedEvent is received — move to PROCESSING.
     * Requirements: 7.5, 7.6
     */
    public void onInventoryReserved(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            logger.warn("onInventoryReserved: order not found for orderId={}", orderId);
            return;
        }
        order.setStatus(OrderStatus.PROCESSING);
        orderRepository.save(order);
        logger.info("Order {} moved to PROCESSING after inventory reserved", orderId);
    }

    /**
     * Called when InventoryUnavailableEvent is received — cancel order.
     * Requirements: 7.7, 7.12
     */
    public void onInventoryUnavailable(Long orderId, String orderNumber) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            logger.warn("onInventoryUnavailable: order not found for orderId={}", orderId);
            return;
        }
        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);
        publishCancelledEvent(saved);
        logger.info("Order {} cancelled due to inventory unavailable", orderId);
    }

    /**
     * Called when PaymentSuccessEvent is received — confirm order.
     * Requirements: 7.9, 7.11
     */
    public void onPaymentSuccess(Long orderId, Long paymentId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            logger.warn("onPaymentSuccess: order not found for orderId={}", orderId);
            return;
        }
        order.setStatus(OrderStatus.CONFIRMED);
        order.setConfirmedAt(LocalDateTime.now());
        order.setPaymentId(paymentId);
        Order saved = orderRepository.save(order);

        publishOrderEvent(KafkaTopics.ORDER_CONFIRMED, saved);
        logger.info("Order {} confirmed after payment success", orderId);
    }

    /**
     * Called when PaymentFailedEvent is received — cancel order.
     * Requirements: 7.10, 7.12, 7.13
     */
    public void onPaymentFailed(Long orderId, String orderNumber) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            logger.warn("onPaymentFailed: order not found for orderId={}", orderId);
            return;
        }
        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);
        publishCancelledEvent(saved);
        logger.info("Order {} cancelled due to payment failure", orderId);
    }

    // -------------------------------------------------------------------------
    // Task 14.2 — Order number generation
    // -------------------------------------------------------------------------

    /**
     * Generates a unique order number in format ORD-YYYYMMDD-XXXXXX.
     * Retries up to 10 times if collision detected.
     * Requirements: 7.2
     */
    private String generateOrderNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        for (int i = 0; i < 10; i++) {
            String candidate = "ORD-" + datePart + "-" +
                    String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
            if (!orderRepository.existsByOrderNumber(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException("ORDER_NUMBER_GENERATION_FAILED",
                "Could not generate unique order number");
    }

    // -------------------------------------------------------------------------
    // Task 14.3 — Total calculation
    // -------------------------------------------------------------------------

    /**
     * Calculates tax based on shipping address country.
     * US: 8%, GB: 20%, others: 0%.
     * Requirements: 7.15
     */
    private BigDecimal calculateTax(BigDecimal subtotal, ShippingAddress address) {
        if (address == null || address.getCountry() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal rate = switch (address.getCountry()) {
            case "US" -> new BigDecimal("0.08");
            case "GB" -> new BigDecimal("0.20");
            default -> BigDecimal.ZERO;
        };
        return subtotal.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates shipping cost based on total item quantity.
     * ≤3 items: $5.99, ≤10 items: $9.99, >10 items: $14.99.
     * Requirements: 7.14
     */
    private BigDecimal calculateShippingCost(List<OrderItem> items) {
        int totalQty = items.stream().mapToInt(OrderItem::getQuantity).sum();
        if (totalQty <= 3) return new BigDecimal("5.99");
        if (totalQty <= 10) return new BigDecimal("9.99");
        return new BigDecimal("14.99");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Order findOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }

    private void validateTransition(OrderStatus current, OrderStatus next) {
        boolean valid = switch (current) {
            case PENDING -> next == OrderStatus.CONFIRMED;
            case CONFIRMED -> next == OrderStatus.PROCESSING;
            case PROCESSING -> next == OrderStatus.SHIPPED;
            case SHIPPED -> next == OrderStatus.DELIVERED;
            default -> false;
        };
        if (!valid) {
            throw new BusinessException("INVALID_STATUS_TRANSITION",
                    "Cannot transition from " + current + " to " + next);
        }
    }

    private void publishCancelledEvent(Order order) {
        List<OrderItemEvent> itemEvents = order.getItems().stream()
                .map(item -> new OrderItemEvent(
                        item.getProductId(),
                        item.getProductSku(),
                        item.getQuantity(),
                        item.getUnitPrice()))
                .collect(Collectors.toList());

        OrderCreatedEvent cancelEvent = new OrderCreatedEvent(
                order.getId(),
                order.getOrderNumber(),
                order.getUserId(),
                itemEvents,
                order.getTotal(),
                order.getCreatedAt() != null ? order.getCreatedAt() : LocalDateTime.now());

        kafkaTemplate.send(KafkaTopics.ORDER_CANCELLED, String.valueOf(order.getId()), cancelEvent);
    }

    private void publishOrderEvent(String topic, Order order) {
        List<OrderItemEvent> itemEvents = order.getItems().stream()
                .map(item -> new OrderItemEvent(
                        item.getProductId(),
                        item.getProductSku(),
                        item.getQuantity(),
                        item.getUnitPrice()))
                .collect(Collectors.toList());

        OrderCreatedEvent event = new OrderCreatedEvent(
                order.getId(),
                order.getOrderNumber(),
                order.getUserId(),
                itemEvents,
                order.getTotal(),
                order.getCreatedAt() != null ? order.getCreatedAt() : LocalDateTime.now());

        kafkaTemplate.send(topic, String.valueOf(order.getId()), event);
    }

    public OrderResponse toResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .productSku(item.getProductSku())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .totalPrice(item.getTotalPrice())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .items(itemResponses)
                .subtotal(order.getSubtotal())
                .tax(order.getTax())
                .shippingCost(order.getShippingCost())
                .discount(order.getDiscount())
                .total(order.getTotal())
                .status(order.getStatus())
                .shippingAddress(order.getShippingAddress())
                .paymentId(order.getPaymentId())
                .trackingNumber(order.getTrackingNumber())
                .createdAt(order.getCreatedAt())
                .confirmedAt(order.getConfirmedAt())
                .shippedAt(order.getShippedAt())
                .deliveredAt(order.getDeliveredAt())
                .build();
    }
}
