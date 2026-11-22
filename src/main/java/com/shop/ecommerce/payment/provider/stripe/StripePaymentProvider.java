package com.shop.ecommerce.payment.provider.stripe;

import com.shop.ecommerce.payment.event.PaymentInitiatedEvent;
import com.shop.ecommerce.payment.provider.ProviderResult;
import com.shop.ecommerce.payment.service.PaymentService;
import com.stripe.StripeClient;
import com.stripe.exception.CardException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentIntentCreateParams.AutomaticPaymentMethods;
import com.stripe.param.PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class StripePaymentProvider {

    private static final String REQUIRES_ACTION = "requires_action";
    private static final String AUTHENTICATION_REQUIRED = "Additional authentication required (not supported)";
    private static final String PROVIDER_UNAVAILABLE = "Payment provider unavailable";
    private static final String PAYMENT_DECLINED = "Payment declined";

    private final StripeClient stripeClient;
    private final TaskScheduler taskScheduler;
    private final PaymentService paymentService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentInitiated(PaymentInitiatedEvent event) {
        taskScheduler.schedule(() -> handOff(event), Instant.now());
    }

    private void handOff(PaymentInitiatedEvent event) {
        String ref = event.providerRef();
        PaymentIntentCreateParams params = createParams(event);
        RequestOptions options = RequestOptions.builder().setIdempotencyKey("payment-" + ref).build();

        try {
            PaymentIntent intent = stripeClient.v1().paymentIntents().create(params, options);
            paymentService.recordProviderDetails(ref, intent.getId(), cardLast4(intent));

            if (REQUIRES_ACTION.equals(intent.getStatus())) {
                paymentService.handleProviderResult(ProviderResult.failed(ref, AUTHENTICATION_REQUIRED));
            }
        } catch (CardException | InvalidRequestException ex) {
            paymentService.handleProviderResult(ProviderResult.failed(ref, readableMessage(ex)));
        } catch (StripeException ex) {
            log.error("Stripe hand-off failed for {}", ref, ex);
            paymentService.handleProviderResult(ProviderResult.failed(ref, PROVIDER_UNAVAILABLE));
        } catch (RuntimeException ex) {
            log.error("Failed to apply Stripe hand-off result {}", ref, ex);
        }
    }

    private PaymentIntentCreateParams createParams(PaymentInitiatedEvent event) {
        return PaymentIntentCreateParams.builder()
                .setAmount(event.amount().movePointRight(2).longValueExact())
                .setCurrency(event.currency().toLowerCase())
                .setConfirm(true)
                .setAutomaticPaymentMethods(AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .setAllowRedirects(AllowRedirects.NEVER)
                        .build())
                .setPaymentMethod(event.paymentMethodId())
                .putMetadata("paymentRef", event.providerRef())
                .putMetadata("orderId", event.orderId().toString())
                .addExpand("payment_method")
                .build();
    }

    private String readableMessage(StripeException ex) {
        if (ex.getStripeError() != null && ex.getStripeError().getMessage() != null) {
            return ex.getStripeError().getMessage();
        }

        return ex.getUserMessage() != null ? ex.getUserMessage() : PAYMENT_DECLINED;
    }

    private String cardLast4(PaymentIntent intent) {
        PaymentMethod paymentMethod = intent.getPaymentMethodObject();

        return paymentMethod != null && paymentMethod.getCard() != null
                ? paymentMethod.getCard().getLast4()
                : null;
    }
}
