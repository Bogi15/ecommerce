package com.shop.ecommerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.shop.ecommerce.payment.controller.StripeWebhookController;
import com.shop.ecommerce.payment.entity.PaymentStatus;
import com.shop.ecommerce.payment.exception.InvalidWebhookException;
import com.shop.ecommerce.payment.provider.ProviderResult;
import com.shop.ecommerce.payment.provider.stripe.StripeEventTranslator;
import com.shop.ecommerce.payment.service.PaymentService;
import com.stripe.StripeClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class StripeWebhookControllerTest {

    private static final String SECRET = "whsec_test_secret";

    private PaymentService paymentService;
    private StripeWebhookController controller;

    @BeforeEach
    void setUp() {
        paymentService = mock(PaymentService.class);
        controller = new StripeWebhookController(
                new StripeClient("sk_test_dummy"), new StripeEventTranslator(), paymentService, SECRET);
    }

    private static String sign(String payload, String secret, long timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }

    private void assertRejected(String payload, String signature) {
        assertThatThrownBy(() -> controller.receive(payload, signature)).isInstanceOf(InvalidWebhookException.class);
        verifyNoInteractions(paymentService);
    }

    @Test
    void genuineSucceededEvent_isApplied() throws Exception {
        String payload = StripeEventTranslatorTest.succeededEvent();

        var response = controller.receive(payload, sign(payload, SECRET, now()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(paymentService).handleProviderResult(
                new ProviderResult("pay_abc", PaymentStatus.SUCCEEDED, null, 15998L, "usd"));
    }

    @Test
    void invalidSignature_isRejected() {
        String payload = StripeEventTranslatorTest.succeededEvent();

        assertRejected(payload, "t=" + now() + ",v1=deadbeef");
    }

    @Test
    void alteredPayload_isRejected() throws Exception {
        String payload = StripeEventTranslatorTest.succeededEvent();
        String signature = sign(payload, SECRET, now());

        assertRejected(payload.replace("15998", "1"), signature);
    }

    @Test
    void wrongSecret_isRejected() throws Exception {
        String payload = StripeEventTranslatorTest.succeededEvent();

        assertRejected(payload, sign(payload, "whsec_someone_else", now()));
    }

    @Test
    void missingHeader_isRejected() {
        assertRejected(StripeEventTranslatorTest.succeededEvent(), null);
    }

    @Test
    void staleTimestamp_isRejected() throws Exception {
        String payload = StripeEventTranslatorTest.succeededEvent();

        assertRejected(payload, sign(payload, SECRET, now() - 600));
    }

    @Test
    void unusedEventType_isAcknowledgedWithoutChanges() throws Exception {
        String payload = StripeEventTranslatorTest.paymentIntentEvent(
                "payment_intent.created", "{\"paymentRef\": \"pay_abc\"}", "null");

        var response = controller.receive(payload, sign(payload, SECRET, now()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(paymentService, never()).handleProviderResult(any());
    }
}
