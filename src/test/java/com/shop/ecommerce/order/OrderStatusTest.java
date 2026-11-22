package com.shop.ecommerce.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.ecommerce.order.entity.OrderStatus;
import org.junit.jupiter.api.Test;

class OrderStatusTest {

    @Test
    void happyPath_isAllowed() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.STOCK_RESERVED)).isTrue();
        assertThat(OrderStatus.STOCK_RESERVED.canTransitionTo(OrderStatus.PAID)).isTrue();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.FULFILLED)).isTrue();
    }

    @Test
    void paymentFailureAndCancellation_areAllowedWhileAwaitingPayment() {
        assertThat(OrderStatus.STOCK_RESERVED.canTransitionTo(OrderStatus.PAYMENT_FAILED)).isTrue();
        assertThat(OrderStatus.STOCK_RESERVED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void terminalStatuses_haveNoExit() {
        for (OrderStatus terminal : new OrderStatus[] {
                OrderStatus.FULFILLED, OrderStatus.CANCELLED, OrderStatus.PAYMENT_FAILED}) {
            for (OrderStatus next : OrderStatus.values()) {
                assertThat(terminal.canTransitionTo(next)).isFalse();
            }
        }
    }

    @Test
    void paidOrder_cannotBeCancelledOrSkipped() {
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PAID)).isFalse();
    }
}
