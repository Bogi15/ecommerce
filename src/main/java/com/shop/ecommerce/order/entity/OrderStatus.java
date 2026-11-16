package com.shop.ecommerce.order.entity;

public enum OrderStatus {
    PENDING,
    STOCK_RESERVED,
    PAID,
    FULFILLED,
    CANCELLED,
    PAYMENT_FAILED;

    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PENDING -> next == STOCK_RESERVED || next == CANCELLED;
            case STOCK_RESERVED -> next == PAID || next == PAYMENT_FAILED || next == CANCELLED;
            case PAID -> next == FULFILLED;
            case FULFILLED, CANCELLED, PAYMENT_FAILED -> false;
        };
    }
}
