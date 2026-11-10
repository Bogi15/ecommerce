package com.shop.ecommerce.common.exception;

import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;

public record ApiError(int status, String code, String message, Instant timestamp, List<String> details) {

    public static ApiError of(HttpStatus status, String code, String message) {
        return new ApiError(status.value(), code, message, Instant.now(), List.of());
    }

    public static ApiError of(HttpStatus status, String code, String message, List<String> details) {
        return new ApiError(status.value(), code, message, Instant.now(), details);
    }
}
