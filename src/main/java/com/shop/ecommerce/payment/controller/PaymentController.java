package com.shop.ecommerce.payment.controller;

import com.shop.ecommerce.common.security.AuthenticatedUser;
import com.shop.ecommerce.payment.dto.PaymentRequest;
import com.shop.ecommerce.payment.dto.PaymentResponse;
import com.shop.ecommerce.payment.entity.PaymentStatus;
import com.shop.ecommerce.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payments")
@RestController
@RequestMapping("/api/orders/{orderId}/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(
            summary = "Pay for an order with a Stripe payment method",
            description = "The final result arrives asynchronously, so poll GET until the status is SUCCEEDED or "
                    + "FAILED. Repeating the request returns the existing payment.")
    @ApiResponse(responseCode = "202", description = "Payment created, status PENDING",
            content = @Content(schema = @Schema(implementation = PaymentResponse.class)))
    @ApiResponse(responseCode = "200", description = "Payment already final",
            content = @Content(schema = @Schema(implementation = PaymentResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid input")
    @ApiResponse(responseCode = "404", description = "Order not found")
    @ApiResponse(responseCode = "422", description = "Order cannot be paid in its status")
    @PostMapping
    public ResponseEntity<PaymentResponse> pay(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long orderId,
            @Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.initiatePayment(user.id(), orderId, request);
        HttpStatus status = response.status() == PaymentStatus.PENDING ? HttpStatus.ACCEPTED : HttpStatus.OK;

        return ResponseEntity.status(status).body(response);
    }

    @Operation(summary = "Get the payment of an order")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Order or payment not found")
    @GetMapping
    public PaymentResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long orderId) {
        return paymentService.getPayment(user, orderId);
    }
}
