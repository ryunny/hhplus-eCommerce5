package com.hhplus.ecommerce.application.usecase.coupon;

import com.hhplus.ecommerce.application.command.JoinCouponQueueCommand;
import com.hhplus.ecommerce.domain.entity.CouponQueue;
import com.hhplus.ecommerce.domain.service.CouponService;
import org.springframework.stereotype.Service;

/**
 * 쿠폰 대기열 진입 UseCase (DB 기반)
 *
 * User Story: "사용자가 선착순 쿠폰 대기열에 진입한다"
 *
 * @deprecated Fallback 패턴이 적용된 JoinRedisQueueUseCase 사용 권장
 *             Redis 장애 시 자동으로 DB로 전환됩니다.
 *
 * UseCase는 여러 Service를 조합하는 계층이므로 트랜잭션은 Service에서 관리합니다.
 */
@Deprecated
@Service
public class JoinCouponQueueUseCase {

    private final CouponService couponService;

    public JoinCouponQueueUseCase(CouponService couponService) {
        this.couponService = couponService;
    }

    public CouponQueue execute(JoinCouponQueueCommand command) {
        return couponService.joinQueueByPublicId(command.publicId(), command.couponId());
    }
}
