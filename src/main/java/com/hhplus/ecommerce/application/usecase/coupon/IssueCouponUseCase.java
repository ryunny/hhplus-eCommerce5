package com.hhplus.ecommerce.application.usecase.coupon;

import com.hhplus.ecommerce.application.command.IssueCouponCommand;
import com.hhplus.ecommerce.domain.entity.Coupon;
import com.hhplus.ecommerce.domain.entity.CouponQueue;
import com.hhplus.ecommerce.domain.entity.UserCoupon;
import com.hhplus.ecommerce.domain.service.CouponService;
import com.hhplus.ecommerce.domain.service.RedisCouponQueueService;
import com.hhplus.ecommerce.domain.service.UserService;
import com.hhplus.ecommerce.presentation.dto.CouponQueueResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 쿠폰 발급 UseCase (통합 + Fallback 패턴)
 *
 * User Story: "사용자가 쿠폰을 발급받는다"
 *
 * 쿠폰 설정에 따라 자동으로 즉시 발급 또는 대기열 발급을 선택합니다.
 * - useQueue = false: 즉시 발급 (동시성 제어 포함)
 * - useQueue = true: 대기열에 추가 (Redis → DB Fallback)
 *
 * Fallback 전략:
 * - Redis 정상: Redis Sorted Set 대기열 사용
 * - Redis 장애: DB 기반 대기열로 자동 전환
 * - 고가용성 보장: 성능은 떨어지지만 서비스는 계속 동작
 */
@Slf4j
@Service
public class IssueCouponUseCase {

    private final CouponService couponService;
    private final RedisCouponQueueService redisCouponQueueService;
    private final UserService userService;

    public IssueCouponUseCase(CouponService couponService,
                              RedisCouponQueueService redisCouponQueueService,
                              UserService userService) {
        this.couponService = couponService;
        this.redisCouponQueueService = redisCouponQueueService;
        this.userService = userService;
    }

    public UserCoupon execute(IssueCouponCommand command) {
        // 1. 쿠폰 정보 조회 (발급 방식 확인을 위해)
        Coupon coupon = couponService.getCoupon(command.couponId());

        // 2. 쿠폰 설정에 따라 발급 방식 선택
        if (coupon.isUseQueue()) {
            // 대기열 방식: Redis → DB Fallback
            joinQueueWithFallback(command.publicId(), command.couponId());
            return null; // 대기 중 (스케줄러가 처리)
        } else {
            // 즉시 발급 방식 (트랜잭션은 Service에서 관리)
            return couponService.issueCouponByPublicId(command.publicId(), command.couponId());
        }
    }

    /**
     * 대기열 진입 (Redis → DB Fallback)
     *
     * @param publicId 사용자 Public ID
     * @param couponId 쿠폰 ID
     */
    private void joinQueueWithFallback(String publicId, Long couponId) {
        Long userId = userService.getUserByPublicId(publicId).getId();

        try {
            // 1차 시도: Redis 대기열
            CouponQueueResponse response = redisCouponQueueService.joinQueue(userId, couponId);
            log.info("Redis 대기열 진입 성공: userId={}, couponId={}, position={}",
                    userId, couponId, response.queuePosition());

        } catch (Exception e) {
            // 2차 시도: DB 대기열로 Fallback
            log.warn("Redis 대기열 진입 실패, DB Fallback: userId={}, couponId={}, error={}",
                    userId, couponId, e.getMessage());

            CouponQueue dbQueue = couponService.joinQueue(userId, couponId);
            log.info("DB 대기열 진입 성공: userId={}, couponId={}, position={}",
                    userId, couponId, dbQueue.getQueuePosition());
        }
    }
}
