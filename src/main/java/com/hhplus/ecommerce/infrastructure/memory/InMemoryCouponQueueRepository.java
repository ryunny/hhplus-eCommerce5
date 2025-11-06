package com.hhplus.ecommerce.infrastructure.memory;

import com.hhplus.ecommerce.domain.entity.CouponQueue;
import com.hhplus.ecommerce.domain.enums.CouponQueueStatus;
import com.hhplus.ecommerce.domain.repository.CouponQueueRepository;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class InMemoryCouponQueueRepository implements CouponQueueRepository {

    private final Map<Long, CouponQueue> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public CouponQueue save(CouponQueue couponQueue) {
        if (couponQueue.getId() == null) {
            Long newId = idGenerator.getAndIncrement();
            setId(couponQueue, newId);
        }
        store.put(couponQueue.getId(), couponQueue);
        return couponQueue;
    }

    @Override
    public Optional<CouponQueue> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<CouponQueue> findByUserIdAndCouponId(Long userId, Long couponId) {
        return store.values().stream()
                .filter(queue -> queue.getUser().getId().equals(userId)
                        && queue.getCoupon().getId().equals(couponId))
                .findFirst();
    }

    @Override
    public List<CouponQueue> findByCouponIdAndStatus(Long couponId, CouponQueueStatus status) {
        return store.values().stream()
                .filter(queue -> queue.getCoupon().getId().equals(couponId)
                        && queue.getStatus() == status)
                .sorted(Comparator.comparing(CouponQueue::getCreatedAt))
                .collect(Collectors.toList());
    }

    @Override
    public List<CouponQueue> findByCouponIdOrderByCreatedAtAsc(Long couponId) {
        return store.values().stream()
                .filter(queue -> queue.getCoupon().getId().equals(couponId))
                .sorted(Comparator.comparing(CouponQueue::getCreatedAt))
                .collect(Collectors.toList());
    }

    @Override
    public int countByCouponIdAndStatus(Long couponId, CouponQueueStatus status) {
        return (int) store.values().stream()
                .filter(queue -> queue.getCoupon().getId().equals(couponId)
                        && queue.getStatus() == status)
                .count();
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }

    private void setId(CouponQueue couponQueue, Long id) {
        try {
            Field idField = CouponQueue.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(couponQueue, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set id", e);
        }
    }
}
