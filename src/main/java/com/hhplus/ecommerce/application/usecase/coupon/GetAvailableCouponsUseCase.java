package com.hhplus.ecommerce.application.usecase.coupon;

import com.hhplus.ecommerce.application.query.GetAvailableCouponsQuery;
import com.hhplus.ecommerce.domain.entity.UserCoupon;
import com.hhplus.ecommerce.domain.service.CouponService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 사용 가능한 쿠폰 조회 UseCase
 *
 * User Story: "사용자가 사용 가능한 쿠폰을 조회한다"
 */
@Service
public class GetAvailableCouponsUseCase {

    private final CouponService couponService;

    public GetAvailableCouponsUseCase(CouponService couponService) {
        this.couponService = couponService;
    }

    public List<UserCoupon> execute(GetAvailableCouponsQuery query) {
        return couponService.getAvailableCouponsByPublicId(query.publicId());
    }
}
