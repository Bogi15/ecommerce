package com.shop.ecommerce.payment.provider.stripe;

import com.shop.ecommerce.payment.entity.PaymentStatus;
import com.shop.ecommerce.payment.provider.ProviderResult;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StripeEventTranslator {

    private static final String SUCCEEDED = "payment_intent.succeeded";
    private static final String FAILED = "payment_intent.payment_failed";

    public Optional<ProviderResult> translate(Event event) {
        Optional<StripeObject> object = dataObject(event);

        if (!SUCCEEDED.equals(event.getType()) && !FAILED.equals(event.getType())) {
            log.debug("Ignoring Stripe event {} of type {}", event.getId(), event.getType());
            return Optional.empty();
        }

        if (object.isEmpty() || !(object.get() instanceof PaymentIntent intent)) {
            log.warn("Ignoring Stripe event {}: no payment intent in payload", event.getId());
            return Optional.empty();
        }

        String paymentRef = intent.getMetadata() == null ? null : intent.getMetadata().get("paymentRef");

        if (paymentRef == null) {
            log.warn("Ignoring Stripe event {} without paymentRef", event.getId());
            return Optional.empty();
        }

        if (SUCCEEDED.equals(event.getType())) {
            return Optional.of(new ProviderResult(
                    paymentRef, PaymentStatus.SUCCEEDED, null, intent.getAmount(), intent.getCurrency()));
        }

        return Optional.of(new ProviderResult(
                paymentRef, PaymentStatus.FAILED, failureReason(intent), intent.getAmount(), intent.getCurrency()));
    }

    private Optional<StripeObject> dataObject(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

        if (deserializer.getObject().isPresent()) {
            return deserializer.getObject();
        }

        try {
            log.warn("Stripe event {} has API version {}; deserializing anyway", event.getId(), event.getApiVersion());
            return Optional.ofNullable(deserializer.deserializeUnsafe());
        } catch (EventDataObjectDeserializationException ex) {
            log.warn("Could not read Stripe event {}", event.getId());
            return Optional.empty();
        }
    }

    private String failureReason(PaymentIntent intent) {
        return intent.getLastPaymentError() != null && intent.getLastPaymentError().getMessage() != null
                ? intent.getLastPaymentError().getMessage()
                : "Payment failed";
    }
}
