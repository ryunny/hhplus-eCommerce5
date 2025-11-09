package com.hhplus.ecommerce.domain.repository;

import com.hhplus.ecommerce.domain.entity.CouponQueue;
import com.hhplus.ecommerce.domain.enums.CouponQueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponQueueRepository extends JpaRepository<CouponQueue, Long> {
    Optional<CouponQueue> findByUserIdAndCouponId(Long userId, Long couponId);

    List<CouponQueue> findByCouponIdAndStatus(Long couponId, CouponQueueStatus status);

    List<CouponQueue> findByCouponIdOrderByCreatedAtAsc(Long couponId);

    int countByCouponIdAndStatus(Long couponId, CouponQueueStatus status);
}
