package com.shop.ecommerce.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.ecommerce.payment.entity.PaymentStatus;
import org.junit.jupiter.api.Test;

class PaymentStatusTest {

    @Test
    void pending_canFinishEitherWay() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.SUCCEEDED)).isTrue();
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.FAILED)).isTrue();
    }

    @Test
    void pending_cannotStayPending() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.PENDING)).isFalse();
    }

    @Test
    void finishedStatuses_haveNoExit() {
        for (PaymentStatus terminal : new PaymentStatus[] {PaymentStatus.SUCCEEDED, PaymentStatus.FAILED}) {
            for (PaymentStatus next : PaymentStatus.values()) {
                assertThat(terminal.canTransitionTo(next)).isFalse();
            }
        }
    }
}
