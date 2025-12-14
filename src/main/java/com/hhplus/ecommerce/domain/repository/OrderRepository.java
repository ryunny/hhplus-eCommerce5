package com.hhplus.ecommerce.domain.repository;

import com.hhplus.ecommerce.domain.entity.Order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);

    Optional<Order> findById(Long id);

    /**
     * 비관적 락을 사용한 주문 조회
     * Saga 패턴에서 동시 업데이트 방지용
     */
    Optional<Order> findByIdWithLock(Long id);

    List<Order> findByUserId(Long userId);

    List<Order> findByUserPublicId(String publicId);

    Optional<Order> findByOrderNumber(String orderNumber);

    void deleteById(Long id);
}
