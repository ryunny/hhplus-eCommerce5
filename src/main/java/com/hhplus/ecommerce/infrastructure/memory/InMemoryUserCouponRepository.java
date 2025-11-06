package com.hhplus.ecommerce.infrastructure.memory;

import com.hhplus.ecommerce.domain.entity.UserCoupon;
import com.hhplus.ecommerce.domain.enums.CouponStatus;
import com.hhplus.ecommerce.domain.repository.UserCouponRepository;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryUserCouponRepository implements UserCouponRepository {

    private final Map<Long, UserCoupon> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        if (userCoupon.getId() == null) {
            Long newId = idGenerator.getAndIncrement();
            setId(userCoupon, newId);
        }
        store.put(userCoupon.getId(), userCoupon);
        return userCoupon;
    }

    @Override
    public Optional<UserCoupon> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<UserCoupon> findByUserId(Long userId) {
        return store.values().stream()
                .filter(userCoupon -> userCoupon.getUser().getId().equals(userId))
                .toList();
    }

    @Override
    public List<UserCoupon> findByUserIdAndStatus(Long userId, CouponStatus status) {
        return store.values().stream()
                .filter(userCoupon -> userCoupon.getUser().getId().equals(userId)
                        && userCoupon.getStatus() == status)
                .toList();
    }

    @Override
    public Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId) {
        return store.values().stream()
                .filter(userCoupon -> userCoupon.getUser().getId().equals(userId)
                        && userCoupon.getCoupon().getId().equals(couponId))
                .findFirst();
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }

    private void setId(UserCoupon userCoupon, Long id) {
        try {
            Field idField = UserCoupon.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(userCoupon, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set id", e);
        }
    }
}
