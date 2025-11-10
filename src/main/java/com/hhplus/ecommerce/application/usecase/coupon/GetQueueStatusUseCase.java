package com.hhplus.ecommerce.application.usecase.coupon;

import com.hhplus.ecommerce.application.query.GetQueueStatusQuery;
import com.hhplus.ecommerce.domain.entity.CouponQueue;
import com.hhplus.ecommerce.domain.service.CouponService;
import org.springframework.stereotype.Service;

/**
 * 쿠폰 대기열 상태 조회 UseCase
 *
 * User Story: "사용자가 대기열 상태를 조회한다"
 */
@Service
public class GetQueueStatusUseCase {

    private final CouponService couponService;

    public GetQueueStatusUseCase(CouponService couponService) {
        this.couponService = couponService;
    }

    public CouponQueue execute(GetQueueStatusQuery query) {
        return couponService.getQueueStatusByPublicId(query.publicId(), query.couponId());
    }
}
