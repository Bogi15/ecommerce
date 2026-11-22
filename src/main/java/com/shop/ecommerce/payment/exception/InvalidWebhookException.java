package com.shop.ecommerce.payment.exception;

public class InvalidWebhookException extends RuntimeException {

    public InvalidWebhookException() {
        super("Invalid notification");
    }
}
