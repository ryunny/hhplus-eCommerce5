package com.hhplus.ecommerce.domain.repository;

import com.hhplus.ecommerce.domain.entity.UserCoupon;
import com.hhplus.ecommerce.domain.enums.CouponStatus;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {
    UserCoupon save(UserCoupon userCoupon);

    Optional<UserCoupon> findById(Long id);

    List<UserCoupon> findAll();

    List<UserCoupon> findByUserId(Long userId);

    List<UserCoupon> findByStatus(CouponStatus status);

    List<UserCoupon> findByUserIdAndStatus(Long userId, CouponStatus status);

    Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId);

    void deleteById(Long id);

    default UserCoupon findByIdOrThrow(Long id) {
        return findById(id)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + id));
    }
}
