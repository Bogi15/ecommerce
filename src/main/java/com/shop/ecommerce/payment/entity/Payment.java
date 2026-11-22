package com.shop.ecommerce.payment.entity;

import com.shop.ecommerce.common.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "provider_ref", nullable = false, unique = true, length = 100)
    private String providerRef;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "external_id", unique = true, length = 100)
    private String externalId;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Payment pending(Long userId, Long orderId, BigDecimal amount, String currency) {
        Payment payment = new Payment();
        payment.userId = userId;
        payment.orderId = orderId;
        payment.amount = amount;
        payment.currency = currency;
        payment.providerRef = "pay_" + UUID.randomUUID();
        payment.status = PaymentStatus.PENDING;
        return payment;
    }

    public void markSucceeded() {
        transition(PaymentStatus.SUCCEEDED);
    }

    public void markFailed(String reason) {
        transition(PaymentStatus.FAILED);
        this.failureReason = reason;
    }

    private void transition(PaymentStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new BusinessRuleException("Invalid payment transition " + status + " -> " + next);
        }
        this.status = next;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
