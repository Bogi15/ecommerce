package com.shop.ecommerce.inventory.dto;

import com.shop.ecommerce.inventory.entity.StockItem;

public record StockResponse(Long productId, int quantityAvailable, int quantityReserved) {

    public static StockResponse from(StockItem stock) {
        return new StockResponse(stock.getProductId(), stock.getQuantityAvailable(), stock.getQuantityReserved());
    }

    public static StockResponse empty(Long productId) {
        return new StockResponse(productId, 0, 0);
    }
}
