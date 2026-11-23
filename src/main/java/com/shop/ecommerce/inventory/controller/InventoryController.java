package com.shop.ecommerce.inventory.controller;

import com.shop.ecommerce.inventory.dto.StockResponse;
import com.shop.ecommerce.inventory.dto.StockUpdateRequest;
import com.shop.ecommerce.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Inventory")
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private static final String ADMIN_ONLY = "Requires the ADMIN role.";

    private final InventoryService inventoryService;

    @Operation(summary = "Get stock for a product", description = ADMIN_ONLY)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "Not an admin")
    @ApiResponse(responseCode = "404", description = "Stock not found")
    @GetMapping("/{productId}")
    public StockResponse getStock(@PathVariable Long productId) {
        return inventoryService.getStock(productId);
    }

    @Operation(summary = "Set available stock for a product", description = ADMIN_ONLY)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Invalid input")
    @ApiResponse(responseCode = "403", description = "Not an admin")
    @ApiResponse(responseCode = "404", description = "Stock not found")
    @ApiResponse(responseCode = "409", description = "Concurrent update, retry")
    @PutMapping("/{productId}")
    public StockResponse setStock(@PathVariable Long productId, @Valid @RequestBody StockUpdateRequest request) {
        return inventoryService.setStock(productId, request.quantityAvailable());
    }
}
