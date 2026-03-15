package com.ecommerce.inventory.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.inventory.dto.InventoryResponse;
import com.ecommerce.inventory.dto.ReleaseInventoryRequest;
import com.ecommerce.inventory.dto.ReserveInventoryRequest;
import com.ecommerce.inventory.dto.UpdateInventoryRequest;
import com.ecommerce.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for inventory operations.
 * Requirements: 10.1, 10.2, 10.3
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * GET /api/inventory/product/{productId}
     * Requirements: 10.1
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<InventoryResponse>> getInventory(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getInventory(productId)));
    }

    /**
     * PUT /api/inventory/product/{productId}
     * Requirements: 10.1, 10.5
     */
    @PutMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<InventoryResponse>> updateInventory(
            @PathVariable Long productId,
            @Valid @RequestBody UpdateInventoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.updateInventory(productId, request)));
    }

    /**
     * POST /api/inventory/reserve
     * Requirements: 10.1, 10.2, 10.3
     */
    @PostMapping("/reserve")
    public ResponseEntity<ApiResponse<InventoryResponse>> reserve(
            @Valid @RequestBody ReserveInventoryRequest request) {
        InventoryResponse response = inventoryService.reserveStock(request.getProductId(), request.getQuantity());
        return ResponseEntity.ok(ApiResponse.success("Inventory reserved", response));
    }

    /**
     * POST /api/inventory/release
     * Requirements: 10.2
     */
    @PostMapping("/release")
    public ResponseEntity<ApiResponse<InventoryResponse>> release(
            @Valid @RequestBody ReleaseInventoryRequest request) {
        InventoryResponse response = inventoryService.releaseStock(request.getProductId(), request.getQuantity());
        return ResponseEntity.ok(ApiResponse.success("Inventory released", response));
    }
}
