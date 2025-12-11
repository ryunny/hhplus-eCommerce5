package com.hhplus.ecommerce.application.usecase.coupon;

import com.hhplus.ecommerce.domain.entity.Coupon;
import com.hhplus.ecommerce.domain.service.CouponService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 쿠폰 대기열 처리 UseCase (스케줄러)
 *
 * Background Job: 대기 중인 사용자들에게 쿠폰을 발급한다
 *
 * 실행 주기: scheduler.queue.fixed-delay
 * 배치 크기: scheduler.queue.batch-size
 */
@Slf4j
@Service
public class ProcessCouponQueueUseCase {

    private final CouponService couponService;

    public ProcessCouponQueueUseCase(CouponService couponService) {
        this.couponService = couponService;
    }

    /**
     * 스케줄러로 실행되는 대기열 처리
     */
    @Scheduled(fixedDelayString = "${scheduler.queue.fixed-delay}")
    public void execute() {
        List<Coupon> issuableCoupons = couponService.getIssuableCoupons();

        for (Coupon coupon : issuableCoupons) {
            couponService.processQueueForCoupon(coupon);
        }
    }
}
