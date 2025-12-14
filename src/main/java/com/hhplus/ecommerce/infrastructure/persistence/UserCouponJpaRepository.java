package com.hhplus.ecommerce.infrastructure.persistence;

import com.hhplus.ecommerce.domain.entity.UserCoupon;
import com.hhplus.ecommerce.domain.enums.CouponStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserCouponJpaRepository extends JpaRepository<UserCoupon, Long> {
    /**
     * N+1 문제 해결: User, Coupon을 Fetch Join으로 한 번에 조회
     */
    @Query("SELECT uc FROM UserCoupon uc " +
           "JOIN FETCH uc.user " +
           "JOIN FETCH uc.coupon " +
           "WHERE uc.user.id = :userId")
    List<UserCoupon> findByUserId(@Param("userId") Long userId);

    /**
     * N+1 문제 해결: User, Coupon을 Fetch Join으로 한 번에 조회
     */
    @Query("SELECT uc FROM UserCoupon uc " +
           "JOIN FETCH uc.user " +
           "JOIN FETCH uc.coupon " +
           "WHERE uc.status = :status")
    List<UserCoupon> findByStatus(@Param("status") CouponStatus status);

    /**
     * N+1 문제 해결: User, Coupon을 Fetch Join으로 한 번에 조회
     */
    @Query("SELECT uc FROM UserCoupon uc " +
           "JOIN FETCH uc.user " +
           "JOIN FETCH uc.coupon " +
           "WHERE uc.user.id = :userId AND uc.status = :status")
    List<UserCoupon> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") CouponStatus status);

    Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId);
}
