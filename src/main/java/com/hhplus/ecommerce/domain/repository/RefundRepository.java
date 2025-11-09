package com.hhplus.ecommerce.domain.repository;

import com.hhplus.ecommerce.domain.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findByOrderId(Long orderId);
}
