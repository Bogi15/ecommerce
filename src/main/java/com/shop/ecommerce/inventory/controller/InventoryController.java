package com.shop.ecommerce.inventory.controller;

import com.shop.ecommerce.inventory.dto.StockResponse;
import com.shop.ecommerce.inventory.dto.StockUpdateRequest;
import com.shop.ecommerce.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    public StockResponse getStock(@PathVariable Long productId) {
        return inventoryService.getStock(productId);
    }

    @PutMapping("/{productId}")
    public StockResponse setStock(@PathVariable Long productId, @Valid @RequestBody StockUpdateRequest request) {
        return inventoryService.setStock(productId, request.quantityAvailable());
    }
}
