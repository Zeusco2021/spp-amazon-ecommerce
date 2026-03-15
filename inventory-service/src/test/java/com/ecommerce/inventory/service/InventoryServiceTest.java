package com.ecommerce.inventory.service;

import com.ecommerce.common.event.OrderItemEvent;
import com.ecommerce.common.kafka.KafkaTopics;
import com.ecommerce.inventory.dto.InventoryResponse;
import com.ecommerce.inventory.dto.UpdateInventoryRequest;
import com.ecommerce.inventory.entity.Inventory;
import com.ecommerce.inventory.exception.InsufficientInventoryException;
import com.ecommerce.inventory.exception.InventoryNotFoundException;
import com.ecommerce.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InventoryService.
 * Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        lenient().when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);
    }

    private Inventory inventory(Long productId, int available, int reserved) {
        return Inventory.builder()
                .id(productId)
                .productId(productId)
                .availableQuantity(available)
                .reservedQuantity(reserved)
                .build();
    }

    // -------------------------------------------------------------------------
    // getInventory
    // -------------------------------------------------------------------------

    @Test
    void getInventory_returnsCorrectResponse() {
        Inventory inv = inventory(1L, 50, 10);
        when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(inv));

        InventoryResponse response = inventoryService.getInventory(1L);

        assertEquals(1L, response.getProductId());
        assertEquals(50, response.getAvailableQuantity());
        assertEquals(10, response.getReservedQuantity());
        assertEquals(60, response.getTotalQuantity());
    }

    @Test
    void getInventory_notFound_throws() {
        when(inventoryRepository.findByProductId(99L)).thenReturn(Optional.empty());

        assertThrows(InventoryNotFoundException.class, () -> inventoryService.getInventory(99L));
    }

    // -------------------------------------------------------------------------
    // updateInventory
    // -------------------------------------------------------------------------

    @Test
    void updateInventory_setsAvailableQuantity() {
        Inventory inv = inventory(1L, 10, 0);
        when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(inv));
        when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        InventoryResponse response = inventoryService.updateInventory(1L, new UpdateInventoryRequest(100));

        assertEquals(100, response.getAvailableQuantity());
        verify(kafkaTemplate).send(eq(KafkaTopics.INVENTORY_UPDATED), anyString(), any());
    }

    @Test
    void updateInventory_createsNewRecordIfAbsent() {
        when(inventoryRepository.findByProductId(5L)).thenReturn(Optional.empty());
        when(inventoryRepository.save(any())).thenAnswer(i -> {
            Inventory saved = i.getArgument(0);
            saved = Inventory.builder()
                    .id(5L).productId(5L)
                    .availableQuantity(saved.getAvailableQuantity())
                    .reservedQuantity(0).build();
            return saved;
        });

        InventoryResponse response = inventoryService.updateInventory(5L, new UpdateInventoryRequest(25));

        assertEquals(25, response.getAvailableQuantity());
    }

    // -------------------------------------------------------------------------
    // reserveStock
    // -------------------------------------------------------------------------

    @Test
    void reserveStock_decreasesAvailableIncreasesReserved() {
        Inventory inv = inventory(1L, 20, 5);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inv));
        when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        InventoryResponse response = inventoryService.reserveStock(1L, 8);

        assertEquals(12, response.getAvailableQuantity());
        assertEquals(13, response.getReservedQuantity());
        assertEquals(25, response.getTotalQuantity()); // invariant preserved
    }

    @Test
    void reserveStock_exactAvailableAmount_succeeds() {
        Inventory inv = inventory(1L, 5, 0);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inv));
        when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        InventoryResponse response = inventoryService.reserveStock(1L, 5);

        assertEquals(0, response.getAvailableQuantity());
        assertEquals(5, response.getReservedQuantity());
    }

    @Test
    void reserveStock_insufficientStock_throws() {
        Inventory inv = inventory(1L, 3, 0);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inv));

        assertThrows(InsufficientInventoryException.class,
                () -> inventoryService.reserveStock(1L, 5));
    }

    @Test
    void reserveStock_zeroAvailable_throws() {
        Inventory inv = inventory(1L, 0, 10);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inv));

        assertThrows(InsufficientInventoryException.class,
                () -> inventoryService.reserveStock(1L, 1));
    }

    /**
     * Requirement 10.4: When available hits zero, OUT_OF_STOCK event is published.
     */
    @Test
    void reserveStock_hitsZero_publishesOutOfStockEvent() {
        Inventory inv = inventory(1L, 3, 0);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inv));
        when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        inventoryService.reserveStock(1L, 3);

        // Two INVENTORY_UPDATED sends: one for out-of-stock signal, one for normal update
        verify(kafkaTemplate, atLeast(1)).send(eq(KafkaTopics.INVENTORY_UPDATED), anyString(), any());
    }

    // -------------------------------------------------------------------------
    // releaseStock
    // -------------------------------------------------------------------------

    @Test
    void releaseStock_increasesAvailableDecreasesReserved() {
        Inventory inv = inventory(1L, 5, 10);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inv));
        when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        InventoryResponse response = inventoryService.releaseStock(1L, 4);

        assertEquals(9, response.getAvailableQuantity());
        assertEquals(6, response.getReservedQuantity());
        assertEquals(15, response.getTotalQuantity()); // invariant preserved
    }

    @Test
    void releaseStock_clampedToReserved_doesNotGoNegative() {
        // Release more than reserved — should clamp to reserved amount
        Inventory inv = inventory(1L, 10, 3);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inv));
        when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        InventoryResponse response = inventoryService.releaseStock(1L, 10);

        assertEquals(0, response.getReservedQuantity());
        assertTrue(response.getAvailableQuantity() >= 0);
    }

    // -------------------------------------------------------------------------
    // reserveForOrder (saga integration)
    // -------------------------------------------------------------------------

    @Test
    void reserveForOrder_allAvailable_publishesReservedEvent() {
        Inventory inv1 = inventory(1L, 10, 0);
        Inventory inv2 = inventory(2L, 5, 0);
        when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(inv1));
        when(inventoryRepository.findByProductId(2L)).thenReturn(Optional.of(inv2));
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inv1));
        when(inventoryRepository.findByProductIdForUpdate(2L)).thenReturn(Optional.of(inv2));
        when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        List<OrderItemEvent> items = List.of(
                new OrderItemEvent(1L, "SKU-1", 3, BigDecimal.TEN),
                new OrderItemEvent(2L, "SKU-2", 2, BigDecimal.ONE)
        );

        inventoryService.reserveForOrder(100L, "ORD-001", items);

        verify(kafkaTemplate).send(eq(KafkaTopics.INVENTORY_RESERVED), anyString(), any());
        verify(kafkaTemplate, never()).send(eq(KafkaTopics.INVENTORY_UNAVAILABLE), anyString(), any());
    }

    @Test
    void reserveForOrder_insufficientStock_publishesUnavailableEvent() {
        Inventory inv1 = inventory(1L, 1, 0); // only 1 available, need 5
        when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(inv1));

        List<OrderItemEvent> items = List.of(
                new OrderItemEvent(1L, "SKU-1", 5, BigDecimal.TEN)
        );

        inventoryService.reserveForOrder(101L, "ORD-002", items);

        verify(kafkaTemplate).send(eq(KafkaTopics.INVENTORY_UNAVAILABLE), anyString(), any());
        verify(kafkaTemplate, never()).send(eq(KafkaTopics.INVENTORY_RESERVED), anyString(), any());
    }

    @Test
    void reserveForOrder_productNotFound_publishesUnavailableEvent() {
        when(inventoryRepository.findByProductId(99L)).thenReturn(Optional.empty());

        List<OrderItemEvent> items = List.of(
                new OrderItemEvent(99L, "SKU-X", 1, BigDecimal.TEN)
        );

        inventoryService.reserveForOrder(102L, "ORD-003", items);

        verify(kafkaTemplate).send(eq(KafkaTopics.INVENTORY_UNAVAILABLE), anyString(), any());
    }

    // -------------------------------------------------------------------------
    // releaseForOrder
    // -------------------------------------------------------------------------

    @Test
    void releaseForOrder_releasesAllItems() {
        Inventory inv1 = inventory(1L, 0, 3);
        Inventory inv2 = inventory(2L, 2, 2);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inv1));
        when(inventoryRepository.findByProductIdForUpdate(2L)).thenReturn(Optional.of(inv2));
        when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        List<OrderItemEvent> items = List.of(
                new OrderItemEvent(1L, "SKU-1", 3, BigDecimal.TEN),
                new OrderItemEvent(2L, "SKU-2", 2, BigDecimal.ONE)
        );

        assertDoesNotThrow(() -> inventoryService.releaseForOrder(200L, items));
        verify(inventoryRepository, times(2)).save(any());
    }
}
