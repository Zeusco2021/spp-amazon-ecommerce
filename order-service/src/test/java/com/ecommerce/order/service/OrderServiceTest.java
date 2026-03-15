package com.ecommerce.order.service;

import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.kafka.KafkaTopics;
import com.ecommerce.order.dto.*;
import com.ecommerce.order.entity.*;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderService.
 * Requirements: 7.1-7.16, 8.1-8.7
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private com.ecommerce.order.audit.AuditService auditService;

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        lenient().when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);
    }

    // -------------------------------------------------------------------------
    // createOrder
    // -------------------------------------------------------------------------

    /**
     * Requirements: 7.1, 7.2, 7.3, 7.4
     */
    @Test
    void createOrder_success_publishesOrderCreatedEvent() {
        when(orderRepository.existsByOrderNumber(anyString())).thenReturn(false);
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.getItems().forEach(item -> item.setOrder(o));
            return o;
        });

        CreateOrderRequest request = buildCreateOrderRequest();
        OrderResponse response = orderService.createOrder(request);

        assertNotNull(response);
        assertEquals(OrderStatus.PENDING, response.getStatus());
        assertNotNull(response.getOrderNumber());
        assertTrue(response.getOrderNumber().matches("ORD-\\d{8}-\\d{6}"));

        verify(kafkaTemplate).send(eq(KafkaTopics.ORDER_CREATED), anyString(), any());
    }

    /**
     * Requirements: 7.1
     */
    @Test
    void createOrder_emptyItems_throwsBusinessException() {
        CreateOrderRequest request = new CreateOrderRequest(
                1L, List.of(), buildShippingAddress(), null, BigDecimal.ZERO);

        assertThrows(BusinessException.class, () -> orderService.createOrder(request));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    /**
     * Requirements: 7.14, 7.15 — total invariant
     */
    @Test
    void createOrder_totalInvariantHolds() {
        when(orderRepository.existsByOrderNumber(anyString())).thenReturn(false);
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.getItems().forEach(item -> item.setOrder(o));
            return o;
        });

        CreateOrderRequest request = buildCreateOrderRequest(); // US address, 2 items at $25 each
        OrderResponse response = orderService.createOrder(request);

        BigDecimal computedTotal = response.getSubtotal()
                .add(response.getTax())
                .add(response.getShippingCost())
                .subtract(response.getDiscount());

        assertEquals(0, response.getTotal().compareTo(computedTotal),
                "total must equal subtotal + tax + shippingCost - discount");
    }

    // -------------------------------------------------------------------------
    // cancelOrder
    // -------------------------------------------------------------------------

    /**
     * Requirements: 8.5
     */
    @Test
    void cancelOrder_pendingStatus_succeeds() {
        Order order = buildOrder(1L, OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.cancelOrder(1L);

        assertEquals(OrderStatus.CANCELLED, response.getStatus());
        verify(kafkaTemplate).send(eq(KafkaTopics.ORDER_CANCELLED), anyString(), any());
    }

    /**
     * Requirements: 8.5
     */
    @Test
    void cancelOrder_confirmedStatus_succeeds() {
        Order order = buildOrder(1L, OrderStatus.CONFIRMED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.cancelOrder(1L);

        assertEquals(OrderStatus.CANCELLED, response.getStatus());
    }

    /**
     * Requirements: 8.6
     */
    @Test
    void cancelOrder_shippedStatus_throwsBusinessException() {
        Order order = buildOrder(1L, OrderStatus.SHIPPED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(BusinessException.class, () -> orderService.cancelOrder(1L));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    /**
     * Requirements: 8.6
     */
    @Test
    void cancelOrder_deliveredStatus_throwsBusinessException() {
        Order order = buildOrder(1L, OrderStatus.DELIVERED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(BusinessException.class, () -> orderService.cancelOrder(1L));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    // -------------------------------------------------------------------------
    // updateOrderStatus
    // -------------------------------------------------------------------------

    /**
     * Requirements: 8.1, 8.4
     */
    @Test
    void updateOrderStatus_validTransition_pendingToConfirmed() {
        Order order = buildOrder(1L, OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.updateOrderStatus(1L, OrderStatus.CONFIRMED);

        assertEquals(OrderStatus.CONFIRMED, response.getStatus());
        assertNotNull(response.getConfirmedAt());
    }

    /**
     * Requirements: 8.4
     */
    @Test
    void updateOrderStatus_invalidTransition_throwsBusinessException() {
        Order order = buildOrder(1L, OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(BusinessException.class,
                () -> orderService.updateOrderStatus(1L, OrderStatus.DELIVERED));
    }

    /**
     * Requirements: 8.2
     */
    @Test
    void updateOrderStatus_toShipped_setsTrackingNumberAndTimestamp() {
        Order order = buildOrder(1L, OrderStatus.PROCESSING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.updateOrderStatus(1L, OrderStatus.SHIPPED);

        assertEquals(OrderStatus.SHIPPED, response.getStatus());
        assertNotNull(response.getTrackingNumber());
        assertTrue(response.getTrackingNumber().startsWith("TRK-"));
        assertNotNull(response.getShippedAt());
    }

    /**
     * Requirements: 8.3
     */
    @Test
    void updateOrderStatus_toDelivered_setsDeliveredAt() {
        Order order = buildOrder(1L, OrderStatus.SHIPPED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.updateOrderStatus(1L, OrderStatus.DELIVERED);

        assertEquals(OrderStatus.DELIVERED, response.getStatus());
        assertNotNull(response.getDeliveredAt());
    }

    // -------------------------------------------------------------------------
    // getOrderById
    // -------------------------------------------------------------------------

    /**
     * Requirements: 15.7
     */
    @Test
    void getOrderById_notFound_throwsResourceNotFoundException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.getOrderById(99L));
    }

    // -------------------------------------------------------------------------
    // getUserOrders
    // -------------------------------------------------------------------------

    /**
     * Requirements: 15.7
     */
    @Test
    void getUserOrders_returnsPaginatedResults() {
        Order order1 = buildOrder(1L, OrderStatus.CONFIRMED);
        Order order2 = buildOrder(2L, OrderStatus.PENDING);

        when(orderRepository.findByUserId(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(order1, order2), PageRequest.of(0, 10), 2));

        PageResponse<OrderResponse> response = orderService.getUserOrders(1L, 0, 10);

        assertEquals(2, response.content().size());
        assertEquals(2, response.totalElements());
        assertEquals(0, response.page());
    }

    // -------------------------------------------------------------------------
    // getOrderTracking
    // -------------------------------------------------------------------------

    /**
     * Requirements: 8.7
     */
    @Test
    void getOrderTracking_returnsTrackingInfo() {
        Order order = buildOrder(1L, OrderStatus.SHIPPED);
        order.setTrackingNumber("TRK-ABCD1234");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        TrackingResponse response = orderService.getOrderTracking(1L);

        assertEquals(1L, response.getOrderId());
        assertEquals(OrderStatus.SHIPPED, response.getStatus());
        assertEquals("TRK-ABCD1234", response.getTrackingNumber());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private CreateOrderRequest buildCreateOrderRequest() {
        CreateOrderItemRequest item = new CreateOrderItemRequest(
                1L, "SKU-001", "Test Product", 2, new BigDecimal("25.00"));
        return new CreateOrderRequest(
                1L, List.of(item), buildShippingAddress(), null, BigDecimal.ZERO);
    }

    private ShippingAddress buildShippingAddress() {
        return ShippingAddress.builder()
                .fullName("Test User")
                .addressLine1("123 Main St")
                .city("Anytown")
                .state("CA")
                .postalCode("12345")
                .country("US")
                .phoneNumber("+1-555-0100")
                .build();
    }

    private Order buildOrder(Long id, OrderStatus status) {
        List<OrderItem> items = new ArrayList<>();
        OrderItem item = OrderItem.builder()
                .id(1L)
                .productId(100L)
                .productName("Test Product")
                .productSku("SKU-001")
                .quantity(2)
                .unitPrice(new BigDecimal("25.00"))
                .totalPrice(new BigDecimal("50.00"))
                .build();
        items.add(item);

        Order order = Order.builder()
                .id(id)
                .orderNumber("ORD-20240101-" + String.format("%06d", id))
                .userId(1L)
                .items(items)
                .subtotal(new BigDecimal("50.00"))
                .tax(new BigDecimal("4.00"))
                .shippingCost(new BigDecimal("5.99"))
                .discount(BigDecimal.ZERO)
                .total(new BigDecimal("59.99"))
                .status(status)
                .shippingAddress(buildShippingAddress())
                .createdAt(LocalDateTime.now())
                .build();

        items.forEach(i -> i.setOrder(order));
        return order;
    }
}
