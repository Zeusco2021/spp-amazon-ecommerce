package com.ecommerce.inventory.service;

import com.ecommerce.inventory.dto.InventoryResponse;
import com.ecommerce.inventory.dto.UpdateInventoryRequest;
import com.ecommerce.inventory.entity.Inventory;
import com.ecommerce.inventory.exception.InsufficientInventoryException;
import com.ecommerce.inventory.repository.InventoryRepository;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-Based Tests for Inventory Consistency
 *
 * Propiedad 1: Consistencia de Inventario
 * Validates: Requirement 10.6
 *
 * Formal invariant:
 * ∀ inventory I: I.total = I.available + I.reserved
 * ∀ inventory I: I.available >= 0
 * ∀ inventory I: I.reserved >= 0
 *
 * After any reserve(q): available' = available - q, reserved' = reserved + q
 * After any release(q): available' = available + q, reserved' = reserved - q
 * In both cases: total remains constant.
 */
@ExtendWith(MockitoExtension.class)
class InventoryConsistencyPropertyTest {

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

    // -------------------------------------------------------------------------
    // a) @RepeatedTest(10) — invariant holds after reserve
    // -------------------------------------------------------------------------

    /**
     * Property: After reserving q units, total = available + reserved is preserved.
     * Requirement 10.6
     */
    @RepeatedTest(10)
    void consistency_totalPreservedAfterReserve() {
        int total = 10 + (int) (Math.random() * 90);   // 10..99
        int reserve = 1 + (int) (Math.random() * (total - 1)); // 1..total-1

        Inventory inv = buildInventory(1L, total, 0);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inv));
        when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        InventoryResponse response = inventoryService.reserveStock(1L, reserve);

        assertEquals(total, response.getTotalQuantity(),
                "Total must remain constant after reserve");
        assertEquals(total - reserve, response.getAvailableQuantity());
        assertEquals(reserve, response.getReservedQuantity());
        assertTrue(response.getAvailableQuantity() >= 0, "Available must never be negative");
    }

    // -------------------------------------------------------------------------
    // b) @RepeatedTest(10) — invariant holds after release
    // -------------------------------------------------------------------------

    /**
     * Property: After releasing q units, total = available + reserved is preserved.
     * Requirement 10.6
     */
    @RepeatedTest(10)
    void consistency_totalPreservedAfterRelease() {
        int reserved = 5 + (int) (Math.random() * 45);  // 5..49
        int available = (int) (Math.random() * 50);      // 0..49
        int release = 1 + (int) (Math.random() * reserved); // 1..reserved

        Inventory inv = buildInventory(2L, available, reserved);
        when(inventoryRepository.findByProductIdForUpdate(2L)).thenReturn(Optional.of(inv));
        when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        InventoryResponse response = inventoryService.releaseStock(2L, release);

        int expectedTotal = available + reserved;
        assertEquals(expectedTotal, response.getTotalQuantity(),
                "Total must remain constant after release");
        assertTrue(response.getAvailableQuantity() >= 0, "Available must never be negative");
        assertTrue(response.getReservedQuantity() >= 0, "Reserved must never be negative");
    }

    // -------------------------------------------------------------------------
    // c) @Test — reserve more than available throws
    // -------------------------------------------------------------------------

    /**
     * Property: Reserving more than available must always be rejected.
     * Requirement 10.3
     */
    @Test
    void consistency_reserveMoreThanAvailableThrows() {
        Inventory inv = buildInventory(3L, 5, 0);
        when(inventoryRepository.findByProductIdForUpdate(3L)).thenReturn(Optional.of(inv));

        assertThrows(InsufficientInventoryException.class,
                () -> inventoryService.reserveStock(3L, 10));
    }

    // -------------------------------------------------------------------------
    // d) @Test — available never goes negative
    // -------------------------------------------------------------------------

    /**
     * Property: Available quantity must never become negative.
     * Requirement 10.3
     */
    @Test
    void consistency_availableNeverNegative() {
        Inventory inv = buildInventory(4L, 3, 0);
        when(inventoryRepository.findByProductIdForUpdate(4L)).thenReturn(Optional.of(inv));

        assertThrows(InsufficientInventoryException.class,
                () -> inventoryService.reserveStock(4L, 4));
    }

    // -------------------------------------------------------------------------
    // e) @Test — reserve then release restores original state
    // -------------------------------------------------------------------------

    /**
     * Property: reserve(q) followed by release(q) restores the original state.
     * Requirement 10.6
     */
    @Test
    void consistency_reserveThenReleaseRestoresState() {
        int initial = 20;
        int quantity = 7;

        Inventory inv = buildInventory(5L, initial, 0);
        when(inventoryRepository.findByProductIdForUpdate(5L)).thenReturn(Optional.of(inv));
        when(inventoryRepository.save(any())).thenAnswer(i -> {
            Inventory saved = i.getArgument(0);
            // Simulate persistence by updating the same object
            inv.setAvailableQuantity(saved.getAvailableQuantity());
            inv.setReservedQuantity(saved.getReservedQuantity());
            return saved;
        });

        inventoryService.reserveStock(5L, quantity);
        assertEquals(initial - quantity, inv.getAvailableQuantity());
        assertEquals(quantity, inv.getReservedQuantity());

        inventoryService.releaseStock(5L, quantity);
        assertEquals(initial, inv.getAvailableQuantity());
        assertEquals(0, inv.getReservedQuantity());
    }

    // -------------------------------------------------------------------------
    // f) @Property(tries=20) with jqwik — invariant holds for any valid reserve
    // -------------------------------------------------------------------------

    /**
     * jqwik property: For any available in [1,100] and reserve in [1,available],
     * the invariant total = available + reserved always holds after reserve.
     * Requirement 10.6
     */
    @Property(tries = 20)
    void consistency_jqwik_invariantHoldsAfterReserve(
            @ForAll("availableQuantities") Integer available,
            @ForAll("reserveQuantities") Integer reserve) {

        Assume.that(reserve <= available);

        InventoryRepository mockRepo = mock(InventoryRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> mockKafka = mock(KafkaTemplate.class);
        InventoryService svc = new InventoryService(mockRepo, mockKafka);

        Inventory inv = buildInventory(99L, available, 0);
        when(mockRepo.findByProductIdForUpdate(99L)).thenReturn(Optional.of(inv));
        when(mockRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(mockKafka.send(anyString(), anyString(), any())).thenReturn(null);

        InventoryResponse response = svc.reserveStock(99L, reserve);

        assertEquals(available, response.getTotalQuantity(),
                "Invariant: total must equal available + reserved");
        assertTrue(response.getAvailableQuantity() >= 0,
                "Available must never be negative");
        assertEquals(available - reserve, response.getAvailableQuantity());
        assertEquals(reserve, response.getReservedQuantity());
    }

    // -------------------------------------------------------------------------
    // Arbitraries
    // -------------------------------------------------------------------------

    @Provide
    Arbitrary<Integer> availableQuantities() {
        return Arbitraries.integers().between(1, 100);
    }

    @Provide
    Arbitrary<Integer> reserveQuantities() {
        return Arbitraries.integers().between(1, 100);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Inventory buildInventory(Long productId, int available, int reserved) {
        return Inventory.builder()
                .id(productId)
                .productId(productId)
                .availableQuantity(available)
                .reservedQuantity(reserved)
                .build();
    }
}
