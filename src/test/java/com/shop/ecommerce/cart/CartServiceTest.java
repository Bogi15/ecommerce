package com.shop.ecommerce.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shop.ecommerce.cart.dto.AddCartItemRequest;
import com.shop.ecommerce.cart.dto.CartResponse;
import com.shop.ecommerce.cart.entity.Cart;
import com.shop.ecommerce.cart.entity.CartItem;
import com.shop.ecommerce.cart.repository.CartRepository;
import com.shop.ecommerce.cart.service.CartService;
import com.shop.ecommerce.catalog.dto.ProductResponse;
import com.shop.ecommerce.catalog.service.ProductService;
import com.shop.ecommerce.common.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    CartRepository cartRepository;

    @Mock
    ProductService productService;

    @InjectMocks
    CartService cartService;

    private ProductResponse product(Long id, String currency) {
        return new ProductResponse(id, "SKU-" + id, "Product " + id, null, new BigDecimal("25.00"), currency, true, 1L);
    }

    private CartItem item(Long productId, int quantity, String currency) {
        CartItem item = new CartItem();
        item.setProductId(productId);
        item.setProductName("Product " + productId);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal("25.00"));
        item.setCurrency(currency);
        return item;
    }

    private Cart cartOf(Long userId, CartItem... items) {
        Cart cart = new Cart();
        cart.setUserId(userId);
        for (CartItem item : items) {
            cart.addItem(item);
        }
        return cart;
    }

    @Test
    void addItem_toNewCart_createsCartWithItemAndTotal() {
        when(productService.getById(10L)).thenReturn(product(10L, "USD"));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse response = cartService.addItem(1L, new AddCartItemRequest(10L, 2));

        assertThat(response.items()).hasSize(1);
        assertThat(response.totalAmount()).isEqualByComparingTo("50.00");
        assertThat(response.currency()).isEqualTo("USD");
    }

    @Test
    void addItem_sameProductAgain_increasesQuantity() {
        Cart cart = cartOf(1L, item(10L, 1, "USD"));
        when(productService.getById(10L)).thenReturn(product(10L, "USD"));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse response = cartService.addItem(1L, new AddCartItemRequest(10L, 2));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).quantity()).isEqualTo(3);
    }

    @Test
    void addItem_differentCurrency_isRejected() {
        Cart cart = cartOf(1L, item(11L, 1, "USD"));
        when(productService.getById(10L)).thenReturn(product(10L, "EUR"));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.addItem(1L, new AddCartItemRequest(10L, 1)))
                .isInstanceOf(BusinessRuleException.class);
        verify(cartRepository, never()).save(any(Cart.class));
    }

    @Test
    void addItem_overQuantityLimit_isRejected() {
        Cart cart = cartOf(1L, item(10L, 999, "USD"));
        when(productService.getById(10L)).thenReturn(product(10L, "USD"));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.addItem(1L, new AddCartItemRequest(10L, 5)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void getCart_withoutCart_returnsEmptyCart() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());

        CartResponse response = cartService.getCart(1L);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalAmount()).isEqualByComparingTo("0");
    }

    @Test
    void clear_removesAllItems() {
        Cart cart = cartOf(1L, item(10L, 1, "USD"), item(11L, 2, "USD"));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        cartService.clear(1L);

        assertThat(cart.getItems()).isEmpty();
    }
}
