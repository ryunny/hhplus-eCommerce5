package com.hhplus.ecommerce.infrastructure.scheduler;

import com.hhplus.ecommerce.config.properties.SchedulerProperties;
import com.hhplus.ecommerce.domain.entity.Coupon;
import com.hhplus.ecommerce.domain.repository.CouponRepository;
import com.hhplus.ecommerce.domain.service.RedisCouponQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis Sorted Set 대기열 배치 프로세서
 *
 * 주기적으로 대기열의 상위 N명에게 쿠폰을 발급합니다.
 * - 다중 서버 환경에서 Redisson 분산락을 사용하여 중복 실행 방지
 * - 락 획득 실패 시 해당 주기를 건너뛰고 다음 주기에 재시도
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(SchedulerProperties.class)
public class RedisQueueProcessor {

    private static final String SCHEDULER_LOCK_KEY = "lock:scheduler:processQueues";
    private static final long LOCK_WAIT_TIME = 0L;
    private static final long LOCK_LEASE_TIME = 30L;

    private final RedisCouponQueueService queueService;
    private final CouponRepository couponRepository;
    private final RedissonClient redissonClient;
    private final SchedulerProperties schedulerProperties;

    /**
     * Redis 대기열 배치 처리
     *
     * 스케줄러 설정에 따라 주기적으로 실행되며, 발급 가능한 모든 쿠폰의 대기열을 처리합니다.
     * 분산락을 사용하여 다중 서버 환경에서 중복 실행을 방지합니다.
     */
    @Scheduled(fixedDelayString = "${scheduler.queue.fixed-delay:10000}")
    public void processQueues() {
        RLock lock = redissonClient.getLock(SCHEDULER_LOCK_KEY);

        try {
            boolean isLocked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);

            if (!isLocked) {
                log.debug("스케줄러 락 획득 실패. 다른 서버에서 실행 중입니다.");
                return;
            }

            try {
                log.debug("Redis 대기열 배치 처리 시작");

                List<Coupon> issuableCoupons = couponRepository.findIssuableCoupons(LocalDateTime.now());

                if (issuableCoupons.isEmpty()) {
                    log.debug("발급 가능한 쿠폰이 없습니다.");
                    return;
                }

                int totalProcessed = 0;

                for (Coupon coupon : issuableCoupons) {
                    try {
                        Long waitingCount = queueService.getTotalWaiting(coupon.getId());

                        if (waitingCount == 0) {
                            continue;
                        }

                        log.info("대기열 처리 시작: couponId={}, waiting={}", coupon.getId(), waitingCount);

                        int processed = queueService.processBatch(coupon.getId(), schedulerProperties.getBatchSize());
                        totalProcessed += processed;

                        log.info("대기열 처리 완료: couponId={}, processed={}, remaining={}",
                                coupon.getId(), processed, queueService.getTotalWaiting(coupon.getId()));

                    } catch (Exception e) {
                        log.error("쿠폰 대기열 처리 중 오류: couponId={}", coupon.getId(), e);
                    }
                }

                if (totalProcessed > 0) {
                    log.info("Redis 대기열 배치 처리 완료: 총 {}명 발급", totalProcessed);
                }

            } finally {
                lock.unlock();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("스케줄러 락 대기 중 인터럽트 발생", e);
        } catch (Exception e) {
            log.error("Redis 대기열 배치 처리 실패", e);
        }
    }
}
