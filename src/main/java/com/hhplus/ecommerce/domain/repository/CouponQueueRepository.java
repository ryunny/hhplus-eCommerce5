package com.hhplus.ecommerce.domain.repository;

import com.hhplus.ecommerce.domain.entity.CouponQueue;
import com.hhplus.ecommerce.domain.enums.CouponQueueStatus;

import java.util.List;
import java.util.Optional;

public interface CouponQueueRepository {
    CouponQueue save(CouponQueue couponQueue);

    Optional<CouponQueue> findById(Long id);

    Optional<CouponQueue> findByUserIdAndCouponId(Long userId, Long couponId);

    List<CouponQueue> findByCouponIdAndStatus(Long couponId, CouponQueueStatus status);

    List<CouponQueue> findByCouponIdOrderByCreatedAtAsc(Long couponId);

    int countByCouponIdAndStatus(Long couponId, CouponQueueStatus status);

    void deleteById(Long id);

    default CouponQueue findByUserIdAndCouponIdOrThrow(Long userId, Long couponId) {
        return findByUserIdAndCouponId(userId, couponId)
                .orElseThrow(() -> new IllegalArgumentException("대기열에 진입하지 않았습니다."));
    }
}
