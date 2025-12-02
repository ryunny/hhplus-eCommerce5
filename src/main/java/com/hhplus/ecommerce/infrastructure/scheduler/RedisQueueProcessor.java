package com.hhplus.ecommerce.infrastructure.scheduler;

import com.hhplus.ecommerce.domain.entity.Coupon;
import com.hhplus.ecommerce.domain.repository.CouponRepository;
import com.hhplus.ecommerce.domain.service.RedisCouponQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Redis Sorted Set 대기열 배치 프로세서
 *
 * 주기적으로 대기열의 상위 N명에게 쿠폰을 발급합니다.
 * - 실행 주기: 10초마다
 * - 처리 개수: 한 번에 최대 10명
 *
 * 장점:
 * - Redis는 Single Thread라 동시성 문제 없음
 * - 락 불필요
 * - 빠른 처리 속도 (O(log N + batch_size))
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisQueueProcessor {

    private final RedisCouponQueueService queueService;
    private final CouponRepository couponRepository;

    /**
     * Redis 대기열 배치 처리
     *
     * 10초마다 실행되며, 발급 가능한 모든 쿠폰의 대기열을 처리합니다.
     */
    @Scheduled(fixedDelay = 10000) // 10초마다 실행
    public void processQueues() {
        try {
            log.debug("Redis 대기열 배치 처리 시작");

            // 1. 발급 가능한 모든 쿠폰 조회
            List<Coupon> issuableCoupons = couponRepository.findIssuableCoupons(LocalDateTime.now());

            if (issuableCoupons.isEmpty()) {
                log.debug("발급 가능한 쿠폰이 없습니다.");
                return;
            }

            int totalProcessed = 0;

            // 2. 각 쿠폰별 대기열 처리
            for (Coupon coupon : issuableCoupons) {
                try {
                    // 대기 인원 확인
                    Long waitingCount = queueService.getTotalWaiting(coupon.getId());

                    if (waitingCount == 0) {
                        continue; // 대기 인원 없음
                    }

                    log.info("대기열 처리 시작: couponId={}, waiting={}", coupon.getId(), waitingCount);

                    // 상위 10명 처리
                    int processed = queueService.processBatch(coupon.getId(), 10);
                    totalProcessed += processed;

                    log.info("대기열 처리 완료: couponId={}, processed={}, remaining={}",
                            coupon.getId(), processed, queueService.getTotalWaiting(coupon.getId()));

                } catch (Exception e) {
                    log.error("쿠폰 대기열 처리 중 오류: couponId={}", coupon.getId(), e);
                    // 다음 쿠폰 계속 처리
                }
            }

            if (totalProcessed > 0) {
                log.info("Redis 대기열 배치 처리 완료: 총 {}명 발급", totalProcessed);
            }

        } catch (Exception e) {
            log.error("Redis 대기열 배치 처리 실패", e);
        }
    }
}
