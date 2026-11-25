package com.shop.ecommerce.cart.controller;

import com.shop.ecommerce.cart.dto.AddCartItemRequest;
import com.shop.ecommerce.cart.dto.CartResponse;
import com.shop.ecommerce.cart.dto.UpdateCartItemRequest;
import com.shop.ecommerce.cart.service.CartService;
import com.shop.ecommerce.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Cart")
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @Operation(summary = "Get the current user's cart")
    @GetMapping
    public CartResponse getCart(@AuthenticationPrincipal AuthenticatedUser user) {
        return cartService.getCart(user.id());
    }

    @Operation(summary = "Add a product to the cart")
    @ApiResponse(responseCode = "400", description = "Invalid input")
    @ApiResponse(responseCode = "404", description = "Product not found")
    @ApiResponse(responseCode = "422", description = "Quantity limit or mixed currency")
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartResponse addItem(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(user.id(), request);
    }

    @Operation(summary = "Change the quantity of a cart item")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Invalid input")
    @ApiResponse(responseCode = "404", description = "Cart or item not found")
    @PutMapping("/items/{itemId}")
    public CartResponse updateItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItem(user.id(), itemId, request);
    }

    @Operation(summary = "Remove an item from the cart")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Cart or item not found")
    @DeleteMapping("/items/{itemId}")
    public CartResponse removeItem(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long itemId) {
        return cartService.removeItem(user.id(), itemId);
    }

    @Operation(summary = "Empty the cart")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal AuthenticatedUser user) {
        cartService.clear(user.id());
    }
}
