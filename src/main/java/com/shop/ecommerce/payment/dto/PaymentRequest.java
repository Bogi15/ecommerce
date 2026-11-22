package com.shop.ecommerce.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PaymentRequest(
        @NotBlank @Size(max = 255) @Pattern(regexp = "pm_\\w+") String paymentMethodId) {
}
