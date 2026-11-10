package com.shop.ecommerce.catalog.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull Long categoryId) {
}
