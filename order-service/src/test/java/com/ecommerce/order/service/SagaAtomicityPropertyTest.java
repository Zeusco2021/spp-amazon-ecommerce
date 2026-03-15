package com.ecommerce.order.service;

import com.ecommerce.order.dto.CreateOrderItemRequest;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.entity.*;
import com.ecommerce.order.repository.OrderRepository;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Propiedad 2: Atomicidad de Pedidos (Saga)
 * Validates: Requirements 7.5-7.13
 *
 * Formal properties:
 * 1. After successful saga: order.status == CONFIRMED, paymentId != null, confirmedAt != null
 * 2. After inventory failure: order.status == CANCELLED
 * 3. After payment failure: order.status == CANCELLED
 * 4. Order total invariant: total = subtotal + tax + shippingCost - discount (always)
 * 5. Order number format: always matches "ORD-\\d{8}-\\d{6}"
 */
@ExtendWith(MockitoExtension.class)
class SagaAtomicityPropertyTest {

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
    // Property 1: After successful saga → CONFIRMED, paymentId != null, confirmedAt != null
    // Requirements: 7.9, 7.11
    // -------------------------------------------------------------------------

    /**
     * After PaymentSuccessEvent: order must be CONFIRMED with paymentId and confirmedAt set.
     */
    @RepeatedTest(10)
    void saga_afterPaymentSuccess_orderIsConfirmedWithPaymentId() {
        Long orderId = ThreadLocalRandom.current().nextLong(1, 10000);
        Long paymentId = ThreadLocalRandom.current().nextLong(1, 10000);

        Order order = buildOrder(orderId, OrderStatus.PROCESSING);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.onPaymentSuccess(orderId, paymentId);

        assertEquals(OrderStatus.CONFIRMED, order.getStatus(),
                "After payment success, order must be CONFIRMED");
        assertNotNull(order.getPaymentId(),
                "After payment success, paymentId must be set");
        assertNotNull(order.getConfirmedAt(),
                "After payment success, confirmedAt must be set");
        assertEquals(paymentId, order.getPaymentId());
    }

    // -------------------------------------------------------------------------
    // Property 2: After inventory failure → CANCELLED
    // Requirements: 7.7, 7.12
    // -------------------------------------------------------------------------

    /**
     * After InventoryUnavailableEvent: order must be CANCELLED.
     */
    @RepeatedTest(10)
    void saga_afterInventoryUnavailable_orderIsCancelled() {
        Long orderId = ThreadLocalRandom.current().nextLong(1, 10000);

        Order order = buildOrder(orderId, OrderStatus.PENDING);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.onInventoryUnavailable(orderId, "ORD-20240101-" + String.format("%06d", orderId % 1_000_000));

        assertEquals(OrderStatus.CANCELLED, order.getStatus(),
                "After inventory unavailable, order must be CANCELLED");
    }

    // -------------------------------------------------------------------------
    // Property 3: After payment failure → CANCELLED
    // Requirements: 7.10, 7.12, 7.13
    // -------------------------------------------------------------------------

    /**
     * After PaymentFailedEvent: order must be CANCELLED.
     */
    @RepeatedTest(10)
    void saga_afterPaymentFailed_orderIsCancelled() {
        Long orderId = ThreadLocalRandom.current().nextLong(1, 10000);

        Order order = buildOrder(orderId, OrderStatus.PROCESSING);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.onPaymentFailed(orderId, "ORD-20240101-" + String.format("%06d", orderId % 1_000_000));

        assertEquals(OrderStatus.CANCELLED, order.getStatus(),
                "After payment failure, order must be CANCELLED");
    }

    // -------------------------------------------------------------------------
    // Property 4: Total invariant — total = subtotal + tax + shippingCost - discount
    // @Property(tries=20) with jqwik
    // Requirements: 7.14, 7.15
    // -------------------------------------------------------------------------

    /**
     * For any valid order creation, total = subtotal + tax + shippingCost - discount.
     */
    @Property(tries = 20)
    void saga_totalInvariant_totalEqualsSubtotalPlusTaxPlusShippingMinusDiscount(
            @ForAll("validPrices") BigDecimal unitPrice,
            @ForAll("validQuantities") Integer quantity,
            @ForAll("validCountries") String country) {

        OrderRepository mockRepo = mock(OrderRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> mockKafka = mock(KafkaTemplate.class);
        OrderService svc = new OrderService(mockRepo, mockKafka, mock(com.ecommerce.order.audit.AuditService.class));

        when(mockRepo.existsByOrderNumber(anyString())).thenReturn(false);
        when(mockRepo.save(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.getItems().forEach(item -> item.setOrder(o));
            return o;
        });
        when(mockKafka.send(anyString(), anyString(), any())).thenReturn(null);

        ShippingAddress address = ShippingAddress.builder()
                .country(country)
                .fullName("Test User")
                .addressLine1("123 Main St")
                .city("Anytown")
                .state("CA")
                .postalCode("12345")
                .build();

        CreateOrderItemRequest itemReq = new CreateOrderItemRequest(
                1L, "SKU-001", "Test Product", quantity, unitPrice);

        CreateOrderRequest request = new CreateOrderRequest(
                1L, List.of(itemReq), address, null, BigDecimal.ZERO);

        OrderResponse response = svc.createOrder(request);

        BigDecimal expectedSubtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal computedTotal = response.getSubtotal()
                .add(response.getTax())
                .add(response.getShippingCost())
                .subtract(response.getDiscount());

        assertEquals(0, response.getTotal().compareTo(computedTotal),
                "Invariant: total must equal subtotal + tax + shippingCost - discount");
        assertEquals(0, response.getSubtotal().compareTo(expectedSubtotal),
                "Subtotal must equal sum of item totals");
        assertTrue(response.getTotal().compareTo(BigDecimal.ZERO) > 0,
                "Total must be positive");
    }

    // -------------------------------------------------------------------------
    // Property 5: Order number format — always matches "ORD-\d{8}-\d{6}"
    // @Property(tries=20) with jqwik
    // Requirements: 7.2
    // -------------------------------------------------------------------------

    /**
     * Generated order numbers always match the format ORD-YYYYMMDD-XXXXXX.
     */
    @Property(tries = 20)
    void saga_orderNumberFormat_alwaysMatchesPattern(
            @ForAll("validPrices") BigDecimal unitPrice,
            @ForAll("validQuantities") Integer quantity) {

        OrderRepository mockRepo = mock(OrderRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> mockKafka = mock(KafkaTemplate.class);
        OrderService svc = new OrderService(mockRepo, mockKafka, mock(com.ecommerce.order.audit.AuditService.class));

        when(mockRepo.existsByOrderNumber(anyString())).thenReturn(false);
        when(mockRepo.save(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.getItems().forEach(item -> item.setOrder(o));
            return o;
        });
        when(mockKafka.send(anyString(), anyString(), any())).thenReturn(null);

        ShippingAddress address = ShippingAddress.builder()
                .country("US")
                .fullName("Test User")
                .addressLine1("123 Main St")
                .city("Anytown")
                .state("CA")
                .postalCode("12345")
                .build();

        CreateOrderItemRequest itemReq = new CreateOrderItemRequest(
                1L, "SKU-001", "Test Product", quantity, unitPrice);

        CreateOrderRequest request = new CreateOrderRequest(
                1L, List.of(itemReq), address, null, BigDecimal.ZERO);

        OrderResponse response = svc.createOrder(request);

        assertNotNull(response.getOrderNumber(), "Order number must not be null");
        assertTrue(response.getOrderNumber().matches("ORD-\\d{8}-\\d{6}"),
                "Order number must match format ORD-YYYYMMDD-XXXXXX, got: " + response.getOrderNumber());
    }

    // -------------------------------------------------------------------------
    // Arbitraries
    // -------------------------------------------------------------------------

    @Provide
    Arbitrary<BigDecimal> validPrices() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.01"), new BigDecimal("999.99"))
                .ofScale(2);
    }

    @Provide
    Arbitrary<Integer> validQuantities() {
        return Arbitraries.integers().between(1, 20);
    }

    @Provide
    Arbitrary<String> validCountries() {
        return Arbitraries.of("US", "GB", "CA", "MX", "DE", "FR");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Order buildOrder(Long id, OrderStatus status) {
        List<OrderItem> items = new ArrayList<>();
        OrderItem item = OrderItem.builder()
                .id(1L)
                .productId(100L)
                .productName("Test Product")
                .productSku("SKU-001")
                .quantity(2)
                .unitPrice(new BigDecimal("10.00"))
                .totalPrice(new BigDecimal("20.00"))
                .build();
        items.add(item);

        Order order = Order.builder()
                .id(id)
                .orderNumber("ORD-20240101-" + String.format("%06d", id % 1_000_000))
                .userId(1L)
                .items(items)
                .subtotal(new BigDecimal("20.00"))
                .tax(new BigDecimal("1.60"))
                .shippingCost(new BigDecimal("5.99"))
                .discount(BigDecimal.ZERO)
                .total(new BigDecimal("27.59"))
                .status(status)
                .shippingAddress(ShippingAddress.builder()
                        .country("US")
                        .fullName("Test User")
                        .addressLine1("123 Main St")
                        .city("Anytown")
                        .state("CA")
                        .postalCode("12345")
                        .build())
                .createdAt(LocalDateTime.now())
                .build();

        items.forEach(i -> i.setOrder(order));
        return order;
    }
}
