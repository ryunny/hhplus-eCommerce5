package com.hhplus.ecommerce.application.usecase.coupon;

import com.hhplus.ecommerce.domain.service.CouponService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 쿠폰 대기 순번 업데이트 UseCase (스케줄러)
 *
 * @deprecated 성능 문제로 사용 중단. {@link com.hhplus.ecommerce.infrastructure.scheduler.RedisQueueProcessor} 사용 권장
 *
 * 문제점:
 * - 5초마다 모든 쿠폰의 모든 대기열 조회 및 업데이트
 * - 쿠폰 100개 × 대기 1000명 = 100,000번 UPDATE (매 5초마다!)
 * - DB 부하 극심, 서버 다운 위험
 *
 * 대안:
 * - {@link com.hhplus.ecommerce.infrastructure.scheduler.RedisQueueProcessor}: Redis Sorted Set 기반
 * - {@link com.hhplus.ecommerce.domain.service.RedisCouponQueueService}: O(log N) 성능
 *
 * 주의: 이 스케줄러를 비활성화하려면 @Scheduled 주석 처리 필요
 *
 * Background Job: "5초마다 대기 순번을 업데이트한다"
 */
@Deprecated
@Service
public class UpdateQueuePositionsUseCase {

    private final CouponService couponService;

    public UpdateQueuePositionsUseCase(CouponService couponService) {
        this.couponService = couponService;
    }

    /**
     * 스케줄러로 실행되는 대기 순번 업데이트
     *
     * @deprecated {@link com.hhplus.ecommerce.infrastructure.scheduler.RedisQueueProcessor} 사용 권장
     */
    @Deprecated
    // @Scheduled(fixedDelay = 5000)  // 성능 문제로 비활성화 - Redis 기반 대기열 사용 권장
    public void execute() {
        couponService.updateQueuePositions();
    }
}
