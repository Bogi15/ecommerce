package com.shop.ecommerce.payment.controller;

import com.shop.ecommerce.common.security.AuthenticatedUser;
import com.shop.ecommerce.payment.dto.PaymentRequest;
import com.shop.ecommerce.payment.dto.PaymentResponse;
import com.shop.ecommerce.payment.entity.PaymentStatus;
import com.shop.ecommerce.payment.service.PaymentService;
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

@RestController
@RequestMapping("/api/orders/{orderId}/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> pay(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long orderId,
            @Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.initiatePayment(user.id(), orderId, request);
        HttpStatus status = response.status() == PaymentStatus.PENDING ? HttpStatus.ACCEPTED : HttpStatus.OK;

        return ResponseEntity.status(status).body(response);
    }

    @GetMapping
    public PaymentResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long orderId) {
        return paymentService.getPayment(user, orderId);
    }
}
