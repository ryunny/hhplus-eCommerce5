package com.hhplus.ecommerce.application.usecase.coupon;

import com.hhplus.ecommerce.domain.entity.CouponQueue;
import com.hhplus.ecommerce.domain.service.CouponService;
import com.hhplus.ecommerce.domain.service.RedisCouponQueueService;
import com.hhplus.ecommerce.domain.service.UserService;
import com.hhplus.ecommerce.presentation.dto.CouponQueueResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 대기열 상태 조회 UseCase (Fallback 패턴)
 *
 * User Story: "사용자가 대기열 상태를 조회한다"
 *
 * Fallback 전략:
 * - Redis 정상: Redis 기반 조회
 * - Redis 장애: DB 기반 조회로 자동 전환
 */
@Slf4j
@Service
public class GetRedisQueueStatusUseCase {

    private final RedisCouponQueueService redisCouponQueueService;
    private final CouponService couponService;
    private final UserService userService;

    public GetRedisQueueStatusUseCase(RedisCouponQueueService redisCouponQueueService,
                                      CouponService couponService,
                                      UserService userService) {
        this.redisCouponQueueService = redisCouponQueueService;
        this.couponService = couponService;
        this.userService = userService;
    }

    /**
     * 대기 상태 조회 (Public ID 기반, Fallback 지원)
     *
     * @param publicId 사용자 Public ID (UUID)
     * @param couponId 쿠폰 ID
     * @return 대기열 정보
     */
    public CouponQueueResponse execute(String publicId, Long couponId) {
        Long userId = userService.getUserByPublicId(publicId).getId();

        try {
            // 1차 시도: Redis 대기열 조회
            return redisCouponQueueService.getQueueStatus(userId, couponId);

        } catch (Exception e) {
            // 2차 시도: DB 대기열 조회로 Fallback
            log.warn("Redis 대기열 조회 실패, DB Fallback: userId={}, couponId={}, error={}",
                    userId, couponId, e.getMessage());

            CouponQueue dbQueue = couponService.getQueueStatus(userId, couponId);
            return CouponQueueResponse.from(dbQueue);
        }
    }
}