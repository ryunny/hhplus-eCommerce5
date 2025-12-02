package com.hhplus.ecommerce.application.usecase.coupon;

import com.hhplus.ecommerce.domain.service.RedisCouponQueueService;
import com.hhplus.ecommerce.domain.service.UserService;
import com.hhplus.ecommerce.presentation.dto.CouponQueueResponse;
import org.springframework.stereotype.Service;

/**
 * Redis 대기열 상태 조회 UseCase
 *
 * User Story: "사용자가 대기열 상태를 조회한다 (Redis 기반)"
 */
@Service
public class GetRedisQueueStatusUseCase {

    private final RedisCouponQueueService queueService;
    private final UserService userService;

    public GetRedisQueueStatusUseCase(RedisCouponQueueService queueService,
                                      UserService userService) {
        this.queueService = queueService;
        this.userService = userService;
    }

    /**
     * 대기 상태 조회 (Public ID 기반)
     *
     * @param publicId 사용자 Public ID (UUID)
     * @param couponId 쿠폰 ID
     * @return 대기열 정보
     */
    public CouponQueueResponse execute(String publicId, Long couponId) {
        Long userId = userService.getUserByPublicId(publicId).getId();
        return queueService.getQueueStatus(userId, couponId);
    }
}