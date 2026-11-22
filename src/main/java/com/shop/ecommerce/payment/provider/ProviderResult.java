package com.shop.ecommerce.payment.provider;

import com.shop.ecommerce.payment.entity.PaymentStatus;

public record ProviderResult(
        String providerRef,
        PaymentStatus outcome,
        String failureReason,
        Long amountMinor,
        String currency) {

    public static ProviderResult failed(String providerRef, String reason) {
        return new ProviderResult(providerRef, PaymentStatus.FAILED, reason, null, null);
    }
}
