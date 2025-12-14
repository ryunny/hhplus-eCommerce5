package com.hhplus.ecommerce.domain.repository;

import com.hhplus.ecommerce.domain.entity.Payment;

import java.util.Optional;

public interface PaymentRepository {
    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByPaymentId(String paymentId);

    void deleteById(Long id);

    default Payment findByIdOrThrow(Long id) {
        return findById(id)
                .orElseThrow(() -> new IllegalArgumentException("결제를 찾을 수 없습니다: " + id));
    }

    default Payment findByPaymentIdOrThrow(String paymentId) {
        return findByPaymentId(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("결제를 찾을 수 없습니다: " + paymentId));
    }
}
