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
}
