package com.hhplus.ecommerce.application.usecase.coupon;

import com.hhplus.ecommerce.application.query.GetQueueStatusQuery;
import com.hhplus.ecommerce.domain.entity.CouponQueue;
import com.hhplus.ecommerce.domain.service.CouponService;
import org.springframework.stereotype.Service;

/**
 * 쿠폰 대기열 상태 조회 UseCase (DB 기반)
 *
 * User Story: "사용자가 대기열 상태를 조회한다"
 *
 * @deprecated Fallback 패턴이 적용된 GetRedisQueueStatusUseCase 사용 권장
 *             Redis 장애 시 자동으로 DB로 전환됩니다.
 */
@Deprecated
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
