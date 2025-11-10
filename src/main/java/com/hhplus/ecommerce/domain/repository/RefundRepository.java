package com.hhplus.ecommerce.domain.repository;

import com.hhplus.ecommerce.domain.entity.Refund;

import java.util.List;
import java.util.Optional;

public interface RefundRepository {
    Refund save(Refund refund);

    Optional<Refund> findById(Long id);

    List<Refund> findByOrderId(Long orderId);

    void deleteById(Long id);
}
