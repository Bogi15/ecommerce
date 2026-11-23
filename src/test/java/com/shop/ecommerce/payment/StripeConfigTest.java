package com.shop.ecommerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.ecommerce.payment.config.StripeConfig;
import org.junit.jupiter.api.Test;

class StripeConfigTest {

    private final StripeConfig config = new StripeConfig();

    @Test
    void missingSecretKey_failsWithVariableName() {
        assertThatThrownBy(() -> config.stripeClient("", "whsec_x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRIPE_SECRET_KEY");
    }

    @Test
    void missingWebhookSecret_failsWithVariableName() {
        assertThatThrownBy(() -> config.stripeClient("sk_test_x", " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRIPE_WEBHOOK_SECRET");
    }

    @Test
    void liveKey_isRefusedWithoutPrintingIt() {
        assertThatThrownBy(() -> config.stripeClient("sk_live_abc123", "whsec_x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test-mode")
                .hasMessageNotContaining("sk_live_abc123");
    }

    @Test
    void testKeyAndSecret_buildClient() {
        assertThat(config.stripeClient("sk_test_abc", "whsec_x")).isNotNull();
    }
}
