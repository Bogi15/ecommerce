package com.shop.ecommerce.payment.service;

import com.shop.ecommerce.common.exception.BusinessRuleException;
import com.shop.ecommerce.common.exception.NotFoundException;
import com.shop.ecommerce.common.security.AuthenticatedUser;
import com.shop.ecommerce.order.dto.OrderResponse;
import com.shop.ecommerce.order.entity.OrderStatus;
import com.shop.ecommerce.order.service.OrderService;
import com.shop.ecommerce.payment.dto.PaymentRequest;
import com.shop.ecommerce.payment.dto.PaymentResponse;
import com.shop.ecommerce.payment.entity.Payment;
import com.shop.ecommerce.payment.entity.PaymentStatus;
import com.shop.ecommerce.payment.event.PaymentFailedEvent;
import com.shop.ecommerce.payment.event.PaymentInitiatedEvent;
import com.shop.ecommerce.payment.event.PaymentSucceededEvent;
import com.shop.ecommerce.payment.provider.ProviderResult;
import com.shop.ecommerce.payment.repository.PaymentRepository;
import com.shop.ecommerce.user.entity.Role;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PaymentResponse initiatePayment(Long userId, Long orderId, PaymentRequest request) {
        OrderResponse order = orderService.getOrder(userId, orderId);
        Optional<Payment> existing = paymentRepository.findByOrderId(orderId);

        if (existing.isPresent()) {
            return PaymentResponse.from(existing.get());
        }

        if (order.status() != OrderStatus.STOCK_RESERVED) {
            throw new BusinessRuleException("Order in status " + order.status() + " cannot be paid");
        }

        Payment payment = paymentRepository.save(
                Payment.pending(userId, orderId, order.totalAmount(), order.currency()));
        eventPublisher.publishEvent(new PaymentInitiatedEvent(
                payment.getProviderRef(), orderId, payment.getAmount(), payment.getCurrency(),
                request.paymentMethodId()));

        return PaymentResponse.from(payment);
    }

    @Transactional
    public void recordProviderDetails(String providerRef, String externalId, String cardLast4) {
        paymentRepository.updateProviderDetails(providerRef, externalId, cardLast4);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(AuthenticatedUser user, Long orderId) {
        Optional<Payment> payment = user.role() == Role.ADMIN
                ? paymentRepository.findByOrderId(orderId)
                : paymentRepository.findByOrderIdAndUserId(orderId, user.id());

        return payment.map(PaymentResponse::from)
                .orElseThrow(() -> new NotFoundException("Payment not found for order " + orderId));
    }

    @Transactional
    public void handleProviderResult(ProviderResult result) {
        Optional<Payment> found = paymentRepository.findByProviderRef(result.providerRef());

        if (found.isEmpty()) {
            log.warn("Ignoring result for unknown provider reference {}", result.providerRef());
            return;
        }

        Payment payment = found.get();

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Ignoring result {} for payment {}: not PENDING (status {})",
                    result.outcome(), payment.getId(), payment.getStatus());
            return;
        }

        if (result.outcome() == PaymentStatus.SUCCEEDED && !matchesPayment(payment, result)) {
            log.warn("Ignoring result for payment {}: amount/currency mismatch (expected {} {}, got {} {})",
                    payment.getId(), minorUnits(payment), payment.getCurrency(), result.amountMinor(), result.currency());
            return;
        }

        if (result.outcome() == PaymentStatus.SUCCEEDED) {
            payment.markSucceeded();
            eventPublisher.publishEvent(new PaymentSucceededEvent(payment.getOrderId()));
        } else {
            payment.markFailed(result.failureReason());
            eventPublisher.publishEvent(new PaymentFailedEvent(payment.getOrderId()));
        }
    }

    private boolean matchesPayment(Payment payment, ProviderResult result) {
        return result.amountMinor() != null
                && result.amountMinor() == minorUnits(payment)
                && payment.getCurrency().equalsIgnoreCase(result.currency());
    }

    private long minorUnits(Payment payment) {
        return payment.getAmount().movePointRight(2).longValueExact();
    }
}
