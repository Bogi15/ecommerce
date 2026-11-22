package com.shop.ecommerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shop.ecommerce.payment.event.PaymentInitiatedEvent;
import com.shop.ecommerce.payment.provider.ProviderResult;
import com.shop.ecommerce.payment.provider.stripe.StripePaymentProvider;
import com.shop.ecommerce.payment.service.PaymentService;
import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.CardException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

class StripePaymentProviderTest {

    private static final PaymentInitiatedEvent EVENT =
            new PaymentInitiatedEvent("pay_abc", 100L, new BigDecimal("159.98"), "USD", "pm_card_visa");

    private StripeClient stripeClient;
    private PaymentService paymentService;
    private StripePaymentProvider provider;

    @BeforeEach
    void setUp() {
        stripeClient = mock(StripeClient.class, RETURNS_DEEP_STUBS);
        paymentService = mock(PaymentService.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        });
        provider = new StripePaymentProvider(stripeClient, taskScheduler, paymentService);
    }

    private PaymentIntent intent(String status) {
        PaymentIntent intent = mock(PaymentIntent.class, RETURNS_DEEP_STUBS);
        when(intent.getId()).thenReturn("pi_1");
        when(intent.getStatus()).thenReturn(status);
        when(intent.getPaymentMethodObject().getCard().getLast4()).thenReturn("4242");
        return intent;
    }

    private void stubCreateReturns(PaymentIntent intent) throws StripeException {
        when(stripeClient.v1().paymentIntents().create(any(PaymentIntentCreateParams.class), any(RequestOptions.class))).thenReturn(intent);
    }

    private void stubCreateThrows(StripeException ex) throws StripeException {
        when(stripeClient.v1().paymentIntents().create(any(PaymentIntentCreateParams.class), any(RequestOptions.class))).thenThrow(ex);
    }

    @Test
    void handOff_success_recordsDetailsAndWaitsForWebhook() throws StripeException {
        stubCreateReturns(intent("succeeded"));

        provider.onPaymentInitiated(EVENT);

        verify(paymentService).recordProviderDetails("pay_abc", "pi_1", "4242");
        verify(paymentService, never()).handleProviderResult(any());
        ArgumentCaptor<PaymentIntentCreateParams> params = ArgumentCaptor.forClass(PaymentIntentCreateParams.class);
        verify(stripeClient.v1().paymentIntents()).create(params.capture(), any(RequestOptions.class));
        assertThat(params.getValue().getAmount()).isEqualTo(15998L);
        assertThat(params.getValue().getCurrency()).isEqualTo("usd");
        assertThat(params.getValue().getPaymentMethod()).isEqualTo("pm_card_visa");
        assertThat(params.getValue().getConfirm()).isTrue();
        assertThat(params.getValue().getMetadata()).containsEntry("paymentRef", "pay_abc").containsEntry("orderId", "100");
    }

    @Test
    void handOff_requiresAction_failsWithAuthenticationReason() throws StripeException {
        stubCreateReturns(intent("requires_action"));

        provider.onPaymentInitiated(EVENT);

        verify(paymentService).recordProviderDetails("pay_abc", "pi_1", "4242");
        verify(paymentService).handleProviderResult(
                ProviderResult.failed("pay_abc", "Additional authentication required (not supported)"));
    }

    private <T extends StripeException> T stripeError(Class<T> type, String message) {
        T ex = mock(type, RETURNS_DEEP_STUBS);
        when(ex.getStripeError().getMessage()).thenReturn(message);
        return ex;
    }

    @Test
    void handOff_cardDeclined_failsWithStripeMessage() throws StripeException {
        stubCreateThrows(stripeError(CardException.class, "Your card was declined."));

        provider.onPaymentInitiated(EVENT);

        verify(paymentService).handleProviderResult(ProviderResult.failed("pay_abc", "Your card was declined."));
        verify(paymentService, never()).recordProviderDetails(any(), any(), any());
    }

    @Test
    void handOff_cardDeclinedWithoutDetails_failsWithGenericReason() throws StripeException {
        stubCreateThrows(new CardException(
                "Your card was declined.", "req_1", "card_declined", null, "generic_decline", null, 402, null));

        provider.onPaymentInitiated(EVENT);

        verify(paymentService).handleProviderResult(ProviderResult.failed("pay_abc", "Payment declined"));
    }

    @Test
    void handOff_otherStripeError_failsAsProviderUnavailable() throws StripeException {
        stubCreateThrows(mock(ApiException.class));

        provider.onPaymentInitiated(EVENT);

        verify(paymentService).handleProviderResult(ProviderResult.failed("pay_abc", "Payment provider unavailable"));
    }

    @Test
    void handOff_sendsIdempotencyKeyPerPayment() throws StripeException {
        stubCreateReturns(intent("succeeded"));

        provider.onPaymentInitiated(EVENT);

        ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);
        verify(stripeClient.v1().paymentIntents()).create(any(PaymentIntentCreateParams.class), options.capture());
        assertThat(options.getValue().getIdempotencyKey()).isEqualTo("payment-pay_abc");
    }

    @Test
    void handOff_unknownPaymentMethod_failsWithStripeMessage() throws StripeException {
        stubCreateThrows(stripeError(InvalidRequestException.class, "No such PaymentMethod: 'pm_nope'"));

        provider.onPaymentInitiated(EVENT);

        verify(paymentService).handleProviderResult(
                ProviderResult.failed("pay_abc", "No such PaymentMethod: 'pm_nope'"));
    }

    @Test
    void handOff_authenticationError_failsAsProviderUnavailable() throws StripeException {
        stubCreateThrows(mock(AuthenticationException.class));

        provider.onPaymentInitiated(EVENT);

        verify(paymentService).handleProviderResult(ProviderResult.failed("pay_abc", "Payment provider unavailable"));
    }

    @Test
    void handOff_connectionError_failsAsProviderUnavailable() throws StripeException {
        stubCreateThrows(mock(ApiConnectionException.class));

        provider.onPaymentInitiated(EVENT);

        verify(paymentService).handleProviderResult(ProviderResult.failed("pay_abc", "Payment provider unavailable"));
    }
}
