package com.hhplus.ecommerce.test.mocks;

import com.hhplus.ecommerce.domain.entity.Coupon;
import com.hhplus.ecommerce.domain.entity.User;
import com.hhplus.ecommerce.domain.entity.UserCoupon;
import com.hhplus.ecommerce.domain.enums.CouponStatus;
import com.hhplus.ecommerce.domain.repository.UserCouponRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MockUserCouponRepository implements UserCouponRepository {

    private final Map<Long, UserCoupon> userCoupons = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public MockUserCouponRepository() {
        // 초기 테스트 데이터는 별도로 추가 가능
        idGenerator.set(1);
    }

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        if (userCoupon.getId() == null) {
            setId(userCoupon, idGenerator.getAndIncrement());
            setIssuedAt(userCoupon, LocalDateTime.now());
        }
        userCoupons.put(userCoupon.getId(), userCoupon);
        return userCoupon;
    }

    @Override
    public Optional<UserCoupon> findById(Long id) {
        return Optional.ofNullable(userCoupons.get(id));
    }

    @Override
    public List<UserCoupon> findAll() {
        return new ArrayList<>(userCoupons.values());
    }

    @Override
    public List<UserCoupon> findByUserId(Long userId) {
        return userCoupons.values().stream()
                .filter(uc -> uc.getUser().getId().equals(userId))
                .toList();
    }

    @Override
    public List<UserCoupon> findByUserIdAndStatus(Long userId, CouponStatus status) {
        return userCoupons.values().stream()
                .filter(uc -> uc.getUser().getId().equals(userId)
                        && uc.getStatus() == status)
                .toList();
    }

    @Override
    public List<UserCoupon> findByStatus(CouponStatus status) {
        return userCoupons.values().stream()
                .filter(uc -> uc.getStatus() == status)
                .toList();
    }

    @Override
    public Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId) {
        return userCoupons.values().stream()
                .filter(uc -> uc.getUser().getId().equals(userId)
                        && uc.getCoupon().getId().equals(couponId))
                .findFirst();
    }

    @Override
    public void deleteById(Long id) {
        userCoupons.remove(id);
    }

    // Helper methods for creating test data
    public UserCoupon createUserCoupon(Long id, User user, Coupon coupon,
                                       CouponStatus status, LocalDateTime expiresAt) {
        UserCoupon userCoupon = new UserCoupon(user, coupon, status, expiresAt);
        setId(userCoupon, id);
        setIssuedAt(userCoupon, LocalDateTime.now());
        return userCoupon;
    }

    private void setId(UserCoupon userCoupon, Long id) {
        try {
            java.lang.reflect.Field idField = UserCoupon.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(userCoupon, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set id", e);
        }
    }

    private void setIssuedAt(UserCoupon userCoupon, LocalDateTime issuedAt) {
        try {
            java.lang.reflect.Field issuedAtField = UserCoupon.class.getDeclaredField("issuedAt");
            issuedAtField.setAccessible(true);
            issuedAtField.set(userCoupon, issuedAt);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set issuedAt", e);
        }
    }

    // Test helper methods
    public void clear() {
        userCoupons.clear();
        idGenerator.set(1);
    }

    public int size() {
        return userCoupons.size();
    }
}
