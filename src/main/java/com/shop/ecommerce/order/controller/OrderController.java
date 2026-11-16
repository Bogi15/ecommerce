package com.shop.ecommerce.order.controller;

import com.shop.ecommerce.common.dto.PageResponse;
import com.shop.ecommerce.common.security.AuthenticatedUser;
import com.shop.ecommerce.order.dto.OrderResponse;
import com.shop.ecommerce.order.service.OrderService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 64) String idempotencyKey) {
        return orderService.createOrder(user.id(), idempotencyKey);
    }

    @GetMapping
    public PageResponse<OrderResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.listOrders(user.id(), pageable);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return orderService.getOrder(user.id(), id);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return orderService.cancelOrder(user.id(), id);
    }
}
