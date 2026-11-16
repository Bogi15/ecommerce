package com.shop.ecommerce.cart.dto;

import com.shop.ecommerce.cart.entity.Cart;
import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        Long id,
        List<CartItemResponse> items,
        BigDecimal totalAmount,
        String currency) {

    public static CartResponse from(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream().map(CartItemResponse::from).toList();
        BigDecimal total = items.stream().map(CartItemResponse::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        String currency = cart.getItems().isEmpty() ? null : cart.getItems().getFirst().getCurrency();

        return new CartResponse(cart.getId(), items, total, currency);
    }

    public static CartResponse empty() {
        return new CartResponse(null, List.of(), BigDecimal.ZERO, null);
    }
}
