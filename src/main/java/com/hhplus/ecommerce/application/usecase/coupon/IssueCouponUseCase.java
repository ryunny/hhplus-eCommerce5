package com.hhplus.ecommerce.application.usecase.coupon;

import com.hhplus.ecommerce.application.command.IssueCouponCommand;
import com.hhplus.ecommerce.domain.entity.Coupon;
import com.hhplus.ecommerce.domain.entity.UserCoupon;
import com.hhplus.ecommerce.domain.service.CouponService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 발급 UseCase (통합)
 *
 * User Story: "사용자가 쿠폰을 발급받는다"
 *
 * 쿠폰 설정에 따라 자동으로 즉시 발급 또는 대기열 발급을 선택합니다.
 * - useQueue = false: 즉시 발급 (동시성 제어 포함)
 * - useQueue = true: 대기열에 추가 (스케줄러가 순차 처리)
 */
@Service
public class IssueCouponUseCase {

    private final CouponService couponService;

    public IssueCouponUseCase(CouponService couponService) {
        this.couponService = couponService;
    }

    @Transactional
    public UserCoupon execute(IssueCouponCommand command) {
        // 1. 쿠폰 정보 조회 (발급 방식 확인을 위해)
        Coupon coupon = couponService.getCoupon(command.couponId());

        // 2. 쿠폰 설정에 따라 발급 방식 선택
        if (coupon.isUseQueue()) {
            // 대기열 방식: 대기열에 추가만 하고 반환
            couponService.joinQueueByPublicId(command.publicId(), command.couponId());
            return null; // 대기 중 (스케줄러가 처리)
        } else {
            // 즉시 발급 방식
            return couponService.issueCouponByPublicId(command.publicId(), command.couponId());
        }
    }
}
