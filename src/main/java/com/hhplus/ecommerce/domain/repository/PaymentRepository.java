package com.hhplus.ecommerce.domain.repository;

import com.hhplus.ecommerce.domain.entity.Payment;

import java.util.Optional;

public interface PaymentRepository {
    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByPaymentId(String paymentId);

    void deleteById(Long id);
}
