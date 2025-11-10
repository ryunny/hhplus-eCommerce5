package com.hhplus.ecommerce.application.usecase.coupon;

import com.hhplus.ecommerce.application.query.GetUserCouponsQuery;
import com.hhplus.ecommerce.domain.entity.UserCoupon;
import com.hhplus.ecommerce.domain.service.CouponService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 사용자 쿠폰 목록 조회 UseCase
 *
 * User Story: "사용자가 자신의 쿠폰 목록을 조회한다"
 */
@Service
public class GetUserCouponsUseCase {

    private final CouponService couponService;

    public GetUserCouponsUseCase(CouponService couponService) {
        this.couponService = couponService;
    }

    public List<UserCoupon> execute(GetUserCouponsQuery query) {
        return couponService.getUserCouponsByPublicId(query.publicId());
    }
}
