package com.shop.ecommerce.catalog.dto;

import com.shop.ecommerce.catalog.entity.Product;
import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        String currency,
        boolean active,
        Long categoryId) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCurrency(),
                product.isActive(),
                product.getCategory().getId());
    }
}
