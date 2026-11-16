package com.shop.ecommerce.cart.controller;

import com.shop.ecommerce.cart.dto.AddCartItemRequest;
import com.shop.ecommerce.cart.dto.CartResponse;
import com.shop.ecommerce.cart.dto.UpdateCartItemRequest;
import com.shop.ecommerce.cart.service.CartService;
import com.shop.ecommerce.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public CartResponse getCart(@AuthenticationPrincipal AuthenticatedUser user) {
        return cartService.getCart(user.id());
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartResponse addItem(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(user.id(), request);
    }

    @PutMapping("/items/{itemId}")
    public CartResponse updateItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItem(user.id(), itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    public CartResponse removeItem(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long itemId) {
        return cartService.removeItem(user.id(), itemId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal AuthenticatedUser user) {
        cartService.clear(user.id());
    }
}
