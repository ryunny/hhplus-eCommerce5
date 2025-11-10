package com.hhplus.ecommerce.infrastructure.persistence;

import com.hhplus.ecommerce.domain.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefundJpaRepository extends JpaRepository<Refund, Long> {
    List<Refund> findByOrderId(Long orderId);
}
