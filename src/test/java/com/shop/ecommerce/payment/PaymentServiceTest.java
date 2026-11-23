package com.shop.ecommerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shop.ecommerce.common.exception.BusinessRuleException;
import com.shop.ecommerce.common.exception.NotFoundException;
import com.shop.ecommerce.common.security.AuthenticatedUser;
import com.shop.ecommerce.order.dto.OrderResponse;
import com.shop.ecommerce.order.entity.OrderStatus;
import com.shop.ecommerce.order.service.OrderService;
import com.shop.ecommerce.payment.dto.PaymentRequest;
import com.shop.ecommerce.payment.dto.PaymentResponse;
import com.shop.ecommerce.payment.entity.Payment;
import com.shop.ecommerce.payment.entity.PaymentStatus;
import com.shop.ecommerce.payment.event.PaymentFailedEvent;
import com.shop.ecommerce.payment.event.PaymentInitiatedEvent;
import com.shop.ecommerce.payment.event.PaymentSucceededEvent;
import com.shop.ecommerce.payment.provider.ProviderResult;
import com.shop.ecommerce.payment.repository.PaymentRepository;
import com.shop.ecommerce.payment.service.PaymentService;
import com.shop.ecommerce.user.entity.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final PaymentRequest VISA = new PaymentRequest("pm_card_visa");
    private static final AuthenticatedUser SHOPPER = new AuthenticatedUser(1L, "shopper@shop.local", Role.CUSTOMER);
    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(99L, "admin@shop.local", Role.ADMIN);

    @Mock
    PaymentRepository paymentRepository;

    @Mock
    OrderService orderService;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    PaymentService paymentService;

    private OrderResponse order(OrderStatus status) {
        return new OrderResponse(100L, status, new BigDecimal("159.98"), "USD", List.of(), Instant.now());
    }

    private Payment pendingPayment() {
        return Payment.pending(1L, 100L, new BigDecimal("159.98"), "USD");
    }

    private ProviderResult succeeded(Payment payment) {
        return new ProviderResult(payment.getProviderRef(), PaymentStatus.SUCCEEDED, null, 15998L, "usd");
    }

    @Test
    void initiatePayment_savesPendingPaymentAndPublishesInitiatedEvent() {
        when(orderService.getOrder(1L, 100L)).thenReturn(order(OrderStatus.STOCK_RESERVED));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.initiatePayment(1L, 100L, VISA);

        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.amount()).isEqualByComparingTo("159.98");
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.cardLast4()).isNull();
        ArgumentCaptor<PaymentInitiatedEvent> event = ArgumentCaptor.forClass(PaymentInitiatedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().providerRef()).startsWith("pay_");
        assertThat(event.getValue().orderId()).isEqualTo(100L);
        assertThat(event.getValue().amount()).isEqualByComparingTo("159.98");
        assertThat(event.getValue().currency()).isEqualTo("USD");
        assertThat(event.getValue().paymentMethodId()).isEqualTo("pm_card_visa");
    }

    @Test
    void initiatePayment_rejectsOrderNotStockReserved() {
        when(orderService.getOrder(1L, 100L)).thenReturn(order(OrderStatus.CANCELLED));

        assertThatThrownBy(() -> paymentService.initiatePayment(1L, 100L, VISA))
                .isInstanceOf(BusinessRuleException.class);
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void initiatePayment_existingPayment_returnsItWithoutSavingOrPublishing() {
        Payment existing = pendingPayment();
        when(orderService.getOrder(1L, 100L)).thenReturn(order(OrderStatus.STOCK_RESERVED));
        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.initiatePayment(1L, 100L, VISA);

        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void initiatePayment_existingPayment_returnedEvenIfOrderNoLongerStockReserved() {
        Payment existing = pendingPayment();
        existing.markSucceeded();
        when(orderService.getOrder(1L, 100L)).thenReturn(order(OrderStatus.PAID));
        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.initiatePayment(1L, 100L, VISA);

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void recordProviderDetails_delegatesToBulkUpdate() {
        paymentService.recordProviderDetails("pay_x", "pi_1", "4242");

        verify(paymentRepository).updateProviderDetails("pay_x", "pi_1", "4242");
    }

    @Test
    void handleProviderResult_success_marksSucceededAndPublishesSucceededEvent() {
        Payment payment = pendingPayment();
        when(paymentRepository.findByProviderRef(payment.getProviderRef())).thenReturn(Optional.of(payment));

        paymentService.handleProviderResult(succeeded(payment));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        ArgumentCaptor<PaymentSucceededEvent> event = ArgumentCaptor.forClass(PaymentSucceededEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().orderId()).isEqualTo(100L);
    }

    @Test
    void handleProviderResult_failure_marksFailedWithReasonAndPublishesFailedEvent() {
        Payment payment = pendingPayment();
        when(paymentRepository.findByProviderRef(payment.getProviderRef())).thenReturn(Optional.of(payment));

        paymentService.handleProviderResult(ProviderResult.failed(payment.getProviderRef(), "Card declined"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("Card declined");
        ArgumentCaptor<PaymentFailedEvent> event = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().orderId()).isEqualTo(100L);
    }

    @Test
    void handleProviderResult_paymentNotPending_isIgnored() {
        Payment payment = pendingPayment();
        payment.markSucceeded();
        when(paymentRepository.findByProviderRef(payment.getProviderRef())).thenReturn(Optional.of(payment));

        paymentService.handleProviderResult(succeeded(payment));
        paymentService.handleProviderResult(ProviderResult.failed(payment.getProviderRef(), "Card declined"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getFailureReason()).isNull();
        verifyNoInteractions(eventPublisher);
    }

    private void assertSuccessIgnored(Long amountMinor, String currency) {
        Payment payment = pendingPayment();
        when(paymentRepository.findByProviderRef(payment.getProviderRef())).thenReturn(Optional.of(payment));

        paymentService.handleProviderResult(
                new ProviderResult(payment.getProviderRef(), PaymentStatus.SUCCEEDED, null, amountMinor, currency));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void handleProviderResult_amountMismatch_isIgnored() {
        assertSuccessIgnored(1L, "usd");
    }

    @Test
    void handleProviderResult_currencyMismatch_isIgnored() {
        assertSuccessIgnored(15998L, "eur");
    }

    @Test
    void handleProviderResult_successWithoutAmount_isIgnored() {
        assertSuccessIgnored(null, null);
    }

    @Test
    void handleProviderResult_currencyIsCaseInsensitive() {
        Payment payment = pendingPayment();
        when(paymentRepository.findByProviderRef(payment.getProviderRef())).thenReturn(Optional.of(payment));

        paymentService.handleProviderResult(
                new ProviderResult(payment.getProviderRef(), PaymentStatus.SUCCEEDED, null, 15998L, "USD"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void handleProviderResult_unknownRef_doesNothing() {
        when(paymentRepository.findByProviderRef("pay_unknown")).thenReturn(Optional.empty());

        paymentService.handleProviderResult(
                new ProviderResult("pay_unknown", PaymentStatus.SUCCEEDED, null, 15998L, "usd"));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void getPayment_owner_returnsPayment() {
        when(paymentRepository.findByOrderIdAndUserId(100L, 1L)).thenReturn(Optional.of(pendingPayment()));

        PaymentResponse response = paymentService.getPayment(SHOPPER, 100L);

        assertThat(response.orderId()).isEqualTo(100L);
        verify(paymentRepository, never()).findByOrderId(any());
    }

    @Test
    void getPayment_admin_returnsAnyPayment() {
        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(pendingPayment()));

        PaymentResponse response = paymentService.getPayment(ADMIN, 100L);

        assertThat(response.orderId()).isEqualTo(100L);
        verify(paymentRepository, never()).findByOrderIdAndUserId(any(), any());
    }

    @Test
    void getPayment_otherShopper_throwsNotFound() {
        AuthenticatedUser other = new AuthenticatedUser(2L, "other@shop.local", Role.CUSTOMER);
        when(paymentRepository.findByOrderIdAndUserId(100L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPayment(other, 100L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getPayment_noPayment_throwsNotFound() {
        when(paymentRepository.findByOrderIdAndUserId(100L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPayment(SHOPPER, 100L)).isInstanceOf(NotFoundException.class);
    }
}
