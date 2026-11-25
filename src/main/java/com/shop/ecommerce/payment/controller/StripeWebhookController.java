package com.shop.ecommerce.payment.controller;

import com.shop.ecommerce.payment.exception.InvalidWebhookException;
import com.shop.ecommerce.payment.provider.stripe.StripeEventTranslator;
import com.shop.ecommerce.payment.service.PaymentService;
import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Payment webhook")
@SecurityRequirements
@RestController
@RequestMapping("/api/payments/stripe/webhook")
public class StripeWebhookController {

    private final StripeClient stripeClient;
    private final StripeEventTranslator eventTranslator;
    private final PaymentService paymentService;
    private final String webhookSecret;

    public StripeWebhookController(
            StripeClient stripeClient,
            StripeEventTranslator eventTranslator,
            PaymentService paymentService,
            @Value("${app.payment.stripe.webhook-secret}") String webhookSecret) {
        this.stripeClient = stripeClient;
        this.eventTranslator = eventTranslator;
        this.paymentService = paymentService;
        this.webhookSecret = webhookSecret;
    }

    @Operation(
            summary = "Receive a Stripe event",
            description = "Called by Stripe only, not by shop users. Every request must carry a valid "
                    + "Stripe-Signature header and is otherwise rejected.")
    @ApiResponse(responseCode = "200", description = "Event accepted")
    @ApiResponse(responseCode = "400", description = "Missing or invalid signature")
    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody String payload,
            @Parameter(description = "Signature computed by Stripe with the webhook secret")
            @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        Event event = verify(payload, signature);

        eventTranslator.translate(event).ifPresent(paymentService::handleProviderResult);

        return ResponseEntity.ok().build();
    }

    private Event verify(String payload, String signature) {
        if (signature == null) {
            throw new InvalidWebhookException();
        }

        try {
            return stripeClient.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException ex) {
            throw new InvalidWebhookException();
        } catch (RuntimeException ex) {
            log.warn("Rejecting signed Stripe webhook with unreadable payload");
            throw new InvalidWebhookException();
        }
    }
}
