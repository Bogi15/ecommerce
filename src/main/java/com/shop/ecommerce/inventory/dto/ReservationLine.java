package com.shop.ecommerce.inventory.dto;

public record ReservationLine(
        Long productId,
        int quantity) {
}
