package com.shop.ecommerce.payment.service;

import com.shop.ecommerce.payment.entity.PaymentStatus;
import com.shop.ecommerce.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentLookupService {

    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public boolean hasPendingPayment(Long orderId) {
        return paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.PENDING);
    }
}
