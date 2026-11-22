package com.shop.ecommerce.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.ecommerce.payment.entity.PaymentStatus;
import com.shop.ecommerce.payment.provider.ProviderResult;
import com.shop.ecommerce.payment.provider.stripe.StripeEventTranslator;
import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.net.ApiResource;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StripeEventTranslatorTest {

    private final StripeEventTranslator translator = new StripeEventTranslator();

    static String paymentIntentEvent(String type, String metadata, String lastPaymentError) {
        return """
                {
                  "id": "evt_1",
                  "object": "event",
                  "api_version": "%s",
                  "type": "%s",
                  "data": {
                    "object": {
                      "id": "pi_1",
                      "object": "payment_intent",
                      "amount": 15998,
                      "currency": "usd",
                      "status": "succeeded",
                      "metadata": %s,
                      "last_payment_error": %s
                    }
                  }
                }
                """.formatted(Stripe.API_VERSION, type, metadata, lastPaymentError);
    }

    static String succeededEvent() {
        return paymentIntentEvent(
                "payment_intent.succeeded", "{\"paymentRef\": \"pay_abc\", \"orderId\": \"100\"}", "null");
    }

    private Event parse(String json) {
        return ApiResource.GSON.fromJson(json, Event.class);
    }

    @Test
    void succeededEvent_becomesSucceededResultWithAmountAndCurrency() {
        Optional<ProviderResult> result = translator.translate(parse(succeededEvent()));

        assertThat(result).contains(new ProviderResult("pay_abc", PaymentStatus.SUCCEEDED, null, 15998L, "usd"));
    }

    @Test
    void failedEvent_becomesFailedResultWithStripeMessage() {
        String json = paymentIntentEvent(
                "payment_intent.payment_failed",
                "{\"paymentRef\": \"pay_abc\"}",
                "{\"message\": \"Your card was declined.\", \"code\": \"card_declined\"}");

        Optional<ProviderResult> result = translator.translate(parse(json));

        assertThat(result).contains(
                new ProviderResult("pay_abc", PaymentStatus.FAILED, "Your card was declined.", 15998L, "usd"));
    }

    @Test
    void unusedEventType_isIgnored() {
        String json = paymentIntentEvent("payment_intent.created", "{\"paymentRef\": \"pay_abc\"}", "null");

        assertThat(translator.translate(parse(json))).isEmpty();
    }

    @Test
    void eventWithoutPaymentRef_isIgnored() {
        String json = paymentIntentEvent("payment_intent.succeeded", "{}", "null");

        assertThat(translator.translate(parse(json))).isEmpty();
    }
}
