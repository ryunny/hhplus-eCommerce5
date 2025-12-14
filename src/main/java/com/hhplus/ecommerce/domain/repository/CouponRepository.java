package com.hhplus.ecommerce.domain.repository;

import com.hhplus.ecommerce.domain.entity.Coupon;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CouponRepository {
    Coupon save(Coupon coupon);

    Optional<Coupon> findById(Long id);

    List<Coupon> findAll();

    List<Coupon> findIssuableCoupons(LocalDateTime now);

    Optional<Coupon> findByIdWithLock(Long id);

    void deleteById(Long id);

    default Coupon findByIdOrThrow(Long id) {
        return findById(id)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + id));
    }

    default Coupon findByIdWithLockOrThrow(Long id) {
        return findByIdWithLock(id)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + id));
    }
}
