package com.shop.ecommerce.order.controller;

import com.shop.ecommerce.common.dto.PageResponse;
import com.shop.ecommerce.common.security.AuthenticatedUser;
import com.shop.ecommerce.order.dto.OrderResponse;
import com.shop.ecommerce.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Orders")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(
            summary = "Place an order from the cart",
            description = "Repeating the request with the same Idempotency-Key returns the same order.")
    @ApiResponse(responseCode = "400", description = "Missing or invalid Idempotency-Key header",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Cart empty, product unavailable or mixed currency")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "Client-generated unique key, max 64 characters")
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 64) String idempotencyKey) {
        return orderService.createOrder(user.id(), idempotencyKey);
    }

    @Operation(summary = "List the user's orders, newest first")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Unknown sort property")
    @GetMapping
    public PageResponse<OrderResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.listOrders(user.id(), pageable);
    }

    @Operation(summary = "Get one of the user's orders")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Order not found")
    @GetMapping("/{id}")
    public OrderResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return orderService.getOrder(user.id(), id);
    }

    @Operation(summary = "Cancel an order")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Order not found")
    @ApiResponse(responseCode = "422", description = "Order cannot be cancelled in its status")
    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return orderService.cancelOrder(user.id(), id);
    }
}
