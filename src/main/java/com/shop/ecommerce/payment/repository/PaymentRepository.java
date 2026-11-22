package com.shop.ecommerce.payment.repository;

import com.shop.ecommerce.payment.entity.Payment;
import com.shop.ecommerce.payment.entity.PaymentStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByOrderIdAndUserId(Long orderId, Long userId);

    Optional<Payment> findByProviderRef(String providerRef);

    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);

    @Modifying(clearAutomatically = true)
    @Query("update Payment p set p.externalId = :externalId, p.cardLast4 = :cardLast4 where p.providerRef = :providerRef")
    int updateProviderDetails(
            @Param("providerRef") String providerRef,
            @Param("externalId") String externalId,
            @Param("cardLast4") String cardLast4);
}
