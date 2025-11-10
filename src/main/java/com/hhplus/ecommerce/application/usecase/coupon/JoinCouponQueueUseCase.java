package com.hhplus.ecommerce.application.usecase.coupon;

import com.hhplus.ecommerce.application.command.JoinCouponQueueCommand;
import com.hhplus.ecommerce.domain.entity.CouponQueue;
import com.hhplus.ecommerce.domain.service.CouponService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 대기열 진입 UseCase
 *
 * User Story: "사용자가 선착순 쿠폰 대기열에 진입한다"
 */
@Service
public class JoinCouponQueueUseCase {

    private final CouponService couponService;

    public JoinCouponQueueUseCase(CouponService couponService) {
        this.couponService = couponService;
    }

    @Transactional
    public CouponQueue execute(JoinCouponQueueCommand command) {
        return couponService.joinQueueByPublicId(command.publicId(), command.couponId());
    }
}
