package com.shop.ecommerce.payment.entity;

public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED;

    public boolean canTransitionTo(PaymentStatus next) {
        return switch (this) {
            case PENDING -> next == SUCCEEDED || next == FAILED;
            case SUCCEEDED, FAILED -> false;
        };
    }
}
