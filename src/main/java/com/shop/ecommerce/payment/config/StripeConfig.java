package com.shop.ecommerce.payment.config;

import com.stripe.StripeClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    private static final String TEST_KEY_PREFIX = "sk_test_";

    @Bean
    public StripeClient stripeClient(
            @Value("${app.payment.stripe.secret-key}") String secretKey,
            @Value("${app.payment.stripe.webhook-secret}") String webhookSecret) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("STRIPE_SECRET_KEY is not set");
        }

        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("STRIPE_WEBHOOK_SECRET is not set");
        }

        if (!secretKey.startsWith(TEST_KEY_PREFIX)) {
            throw new IllegalStateException("Only Stripe test-mode keys (sk_test_...) are allowed");
        }

        return new StripeClient(secretKey);
    }
}
