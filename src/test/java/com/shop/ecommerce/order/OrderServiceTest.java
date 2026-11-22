package com.shop.ecommerce.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shop.ecommerce.cart.dto.CartItemResponse;
import com.shop.ecommerce.cart.dto.CartResponse;
import com.shop.ecommerce.cart.service.CartService;
import com.shop.ecommerce.catalog.dto.ProductResponse;
import com.shop.ecommerce.catalog.service.ProductService;
import com.shop.ecommerce.common.exception.BusinessRuleException;
import com.shop.ecommerce.inventory.exception.InsufficientStockException;
import com.shop.ecommerce.inventory.service.InventoryService;
import com.shop.ecommerce.order.dto.OrderResponse;
import com.shop.ecommerce.order.entity.Order;
import com.shop.ecommerce.order.entity.OrderStatus;
import com.shop.ecommerce.order.repository.OrderRepository;
import com.shop.ecommerce.order.service.OrderService;
import com.shop.ecommerce.payment.event.PaymentFailedEvent;
import com.shop.ecommerce.payment.event.PaymentSucceededEvent;
import com.shop.ecommerce.payment.service.PaymentLookupService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    OrderRepository orderRepository;

    @Mock
    CartService cartService;

    @Mock
    ProductService productService;

    @Mock
    InventoryService inventoryService;

    @Mock
    PaymentLookupService paymentLookupService;

    @InjectMocks
    OrderService orderService;

    private Order order(OrderStatus status) {
        Order order = new Order();
        order.setId(100L);
        order.setUserId(1L);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setCurrency("USD");
        return order;
    }

    private void stubCartWithOneItem() {
        CartItemResponse cartItem = new CartItemResponse(
                1L, 10L, "Laptop", 2, new BigDecimal("40.00"), new BigDecimal("80.00"));
        when(cartService.getCart(1L)).thenReturn(
                new CartResponse(1L, List.of(cartItem), new BigDecimal("80.00"), "USD"));
        when(productService.getById(10L)).thenReturn(
                new ProductResponse(10L, "LAP-1", "Laptop", null, new BigDecimal("50.00"), "USD", true, 1L));
    }

    @Test
    void createOrder_reservesStockAndClearsCart() {
        when(orderRepository.findByUserIdAndIdempotencyKey(1L, "key-1")).thenReturn(Optional.empty());
        stubCartWithOneItem();
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });

        OrderResponse response = orderService.createOrder(1L, "key-1");

        assertThat(response.status()).isEqualTo(OrderStatus.STOCK_RESERVED);
        assertThat(response.totalAmount()).isEqualByComparingTo("100.00");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("50.00");
        verify(inventoryService).reserve(eq(100L), anyList());
        verify(cartService).clear(1L);
    }

    @Test
    void createOrder_sameIdempotencyKey_returnsExistingOrderWithoutSideEffects() {
        when(orderRepository.findByUserIdAndIdempotencyKey(1L, "key-1"))
                .thenReturn(Optional.of(order(OrderStatus.STOCK_RESERVED)));

        OrderResponse response = orderService.createOrder(1L, "key-1");

        assertThat(response.id()).isEqualTo(100L);
        verifyNoInteractions(cartService, inventoryService);
    }

    @Test
    void createOrder_emptyCart_isRejected() {
        when(orderRepository.findByUserIdAndIdempotencyKey(1L, "key-1")).thenReturn(Optional.empty());
        when(cartService.getCart(1L)).thenReturn(CartResponse.empty());

        assertThatThrownBy(() -> orderService.createOrder(1L, "key-1")).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(inventoryService);
    }

    @Test
    void createOrder_insufficientStock_propagatesAndKeepsCart() {
        when(orderRepository.findByUserIdAndIdempotencyKey(1L, "key-1")).thenReturn(Optional.empty());
        stubCartWithOneItem();
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });
        doThrow(new InsufficientStockException(10L)).when(inventoryService).reserve(eq(100L), anyList());

        assertThatThrownBy(() -> orderService.createOrder(1L, "key-1"))
                .isInstanceOf(InsufficientStockException.class);
        verify(cartService, never()).clear(any());
    }

    @Test
    void cancelOrder_withReservedStock_releasesStock() {
        Order order = order(OrderStatus.STOCK_RESERVED);
        when(orderRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.cancelOrder(1L, 100L);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryService).release(100L);
    }

    @Test
    void cancelOrder_alreadyPaid_isRejected() {
        when(orderRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(order(OrderStatus.PAID)));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, 100L)).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(inventoryService);
    }

    @Test
    void cancelOrder_rejectedWhilePaymentPending() {
        Order order = order(OrderStatus.STOCK_RESERVED);
        when(orderRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(order));
        when(paymentLookupService.hasPendingPayment(100L)).thenReturn(true);

        assertThatThrownBy(() -> orderService.cancelOrder(1L, 100L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Payment in progress");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.STOCK_RESERVED);
        verify(inventoryService, never()).release(any());
    }

    @Test
    void cancelOrder_allowedWhenNoPendingPayment() {
        Order order = order(OrderStatus.STOCK_RESERVED);
        when(orderRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(order));
        when(paymentLookupService.hasPendingPayment(100L)).thenReturn(false);

        OrderResponse response = orderService.cancelOrder(1L, 100L);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryService).release(100L);
    }

    @Test
    void onPaymentSucceeded_marksOrderPaidAndCommitsStock() {
        Order order = order(OrderStatus.STOCK_RESERVED);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        orderService.onPaymentSucceeded(new PaymentSucceededEvent(100L));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(inventoryService).commit(100L);
    }

    @Test
    void onPaymentFailed_marksOrderPaymentFailedAndReleasesStock() {
        Order order = order(OrderStatus.STOCK_RESERVED);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        orderService.onPaymentFailed(new PaymentFailedEvent(100L));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        verify(inventoryService).release(100L);
    }
}
