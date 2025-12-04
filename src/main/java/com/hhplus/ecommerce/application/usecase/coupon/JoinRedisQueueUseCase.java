package com.hhplus.ecommerce.application.usecase.coupon;

import com.hhplus.ecommerce.domain.entity.CouponQueue;
import com.hhplus.ecommerce.domain.service.CouponService;
import com.hhplus.ecommerce.domain.service.RedisCouponQueueService;
import com.hhplus.ecommerce.domain.service.UserService;
import com.hhplus.ecommerce.presentation.dto.CouponQueueResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 선착순 쿠폰 대기열 진입 UseCase (Fallback 패턴)
 *
 * User Story: "사용자가 선착순 쿠폰 대기열에 진입한다"
 *
 * 우선순위:
 * 1. Redis Sorted Set (고성능, O(log N))
 * 2. DB 대기열 (Fallback, Redis 장애 시)
 *
 * Fallback 전략:
 * - Redis 정상: Redis 기반 대기열 사용
 * - Redis 장애: DB 기반 대기열로 자동 전환 (사용자는 모름)
 * - 고가용성 보장: 성능은 떨어지지만 서비스는 계속 동작
 */
@Slf4j
@Service
public class JoinRedisQueueUseCase {

    private final RedisCouponQueueService redisCouponQueueService;
    private final CouponService couponService;
    private final UserService userService;

    public JoinRedisQueueUseCase(RedisCouponQueueService redisCouponQueueService,
                                 CouponService couponService,
                                 UserService userService) {
        this.redisCouponQueueService = redisCouponQueueService;
        this.couponService = couponService;
        this.userService = userService;
    }

    /**
     * 대기열 진입 (Public ID 기반, Fallback 지원)
     *
     * @param publicId 사용자 Public ID (UUID)
     * @param couponId 쿠폰 ID
     * @return 대기열 정보
     */
    public CouponQueueResponse execute(String publicId, Long couponId) {
        Long userId = userService.getUserByPublicId(publicId).getId();

        try {
            // 1차 시도: Redis 대기열
            return redisCouponQueueService.joinQueue(userId, couponId);

        } catch (Exception e) {
            // 2차 시도: DB 대기열로 Fallback
            log.warn("Redis 대기열 진입 실패, DB Fallback: userId={}, couponId={}, error={}",
                    userId, couponId, e.getMessage());

            CouponQueue dbQueue = couponService.joinQueue(userId, couponId);
            return CouponQueueResponse.from(dbQueue);
        }
    }
}