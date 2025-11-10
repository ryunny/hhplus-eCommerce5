package com.hhplus.ecommerce.infrastructure.persistence;

import com.hhplus.ecommerce.domain.entity.CouponQueue;
import com.hhplus.ecommerce.domain.enums.CouponQueueStatus;
import com.hhplus.ecommerce.domain.repository.CouponQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CouponQueueRepositoryImpl implements CouponQueueRepository {

    private final CouponQueueJpaRepository couponQueueJpaRepository;

    @Override
    public CouponQueue save(CouponQueue couponQueue) {
        return couponQueueJpaRepository.save(couponQueue);
    }

    @Override
    public Optional<CouponQueue> findById(Long id) {
        return couponQueueJpaRepository.findById(id);
    }

    @Override
    public Optional<CouponQueue> findByUserIdAndCouponId(Long userId, Long couponId) {
        return couponQueueJpaRepository.findByUserIdAndCouponId(userId, couponId);
    }

    @Override
    public List<CouponQueue> findByCouponIdAndStatus(Long couponId, CouponQueueStatus status) {
        return couponQueueJpaRepository.findByCouponIdAndStatus(couponId, status);
    }

    @Override
    public List<CouponQueue> findByCouponIdOrderByCreatedAtAsc(Long couponId) {
        return couponQueueJpaRepository.findByCouponIdOrderByCreatedAtAsc(couponId);
    }

    @Override
    public int countByCouponIdAndStatus(Long couponId, CouponQueueStatus status) {
        return couponQueueJpaRepository.countByCouponIdAndStatus(couponId, status);
    }

    @Override
    public void deleteById(Long id) {
        couponQueueJpaRepository.deleteById(id);
    }
}
