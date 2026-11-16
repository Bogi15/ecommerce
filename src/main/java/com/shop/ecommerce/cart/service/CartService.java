package com.shop.ecommerce.cart.service;

import com.shop.ecommerce.cart.dto.AddCartItemRequest;
import com.shop.ecommerce.cart.dto.CartResponse;
import com.shop.ecommerce.cart.dto.UpdateCartItemRequest;
import com.shop.ecommerce.cart.entity.Cart;
import com.shop.ecommerce.cart.entity.CartItem;
import com.shop.ecommerce.cart.repository.CartRepository;
import com.shop.ecommerce.catalog.dto.ProductResponse;
import com.shop.ecommerce.catalog.service.ProductService;
import com.shop.ecommerce.common.exception.BusinessRuleException;
import com.shop.ecommerce.common.exception.NotFoundException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartService {

    private static final int MAX_QUANTITY_PER_ITEM = 1000;

    private final CartRepository cartRepository;
    private final ProductService productService;

    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        return cartRepository.findByUserId(userId).map(CartResponse::from).orElseGet(CartResponse::empty);
    }

    @Transactional
    public CartResponse addItem(Long userId, AddCartItemRequest request) {
        ProductResponse product = productService.getById(request.productId());
        Cart cart = cartRepository.findByUserId(userId).orElseGet(() -> newCart(userId));
        requireSingleCurrency(cart, product);

        CartItem item = cart.getItems().stream()
                .filter(existing -> existing.getProductId().equals(product.id()))
                .findFirst()
                .orElseGet(() -> {
                    CartItem created = new CartItem();
                    created.setProductId(product.id());
                    cart.addItem(created);
                    return created;
                });

        int newQuantity = item.getQuantity() + request.quantity();

        if (newQuantity > MAX_QUANTITY_PER_ITEM) {
            throw new BusinessRuleException("At most " + MAX_QUANTITY_PER_ITEM + " units per item");
        }

        item.setQuantity(newQuantity);
        item.setProductName(product.name());
        item.setUnitPrice(product.price());
        item.setCurrency(product.currency());
        cart.setUpdatedAt(Instant.now());

        return CartResponse.from(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse updateItem(Long userId, Long itemId, UpdateCartItemRequest request) {
        Cart cart = findCart(userId);
        findItem(cart, itemId).setQuantity(request.quantity());
        cart.setUpdatedAt(Instant.now());

        return CartResponse.from(cart);
    }

    @Transactional
    public CartResponse removeItem(Long userId, Long itemId) {
        Cart cart = findCart(userId);
        cart.getItems().remove(findItem(cart, itemId));
        cart.setUpdatedAt(Instant.now());

        return CartResponse.from(cart);
    }

    @Transactional
    public void clear(Long userId) {
        cartRepository.findByUserId(userId).ifPresent(cart -> {
            cart.getItems().clear();
            cart.setUpdatedAt(Instant.now());
        });
    }

    private Cart newCart(Long userId) {
        Cart cart = new Cart();
        cart.setUserId(userId);
        cart.setUpdatedAt(Instant.now());
        return cart;
    }

    private Cart findCart(Long userId) {
        return cartRepository.findByUserId(userId).orElseThrow(() -> new NotFoundException("Cart is empty"));
    }

    private CartItem findItem(Cart cart, Long itemId) {
        return cart.getItems().stream()
                .filter(item -> item.getId() != null && item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Cart item not found: " + itemId));
    }

    private void requireSingleCurrency(Cart cart, ProductResponse product) {
        boolean mixed = cart.getItems().stream()
                .filter(item -> !item.getProductId().equals(product.id()))
                .anyMatch(item -> !item.getCurrency().equals(product.currency()));
        if (mixed) {
            throw new BusinessRuleException("A cart can only contain items in one currency");
        }
    }
}
