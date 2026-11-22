package com.shop.ecommerce.payment.event;

import java.math.BigDecimal;

public record PaymentInitiatedEvent(
        String providerRef,
        Long orderId,
        BigDecimal amount,
        String currency,
        String paymentMethodId) {
}
