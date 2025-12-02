package com.hhplus.ecommerce.application.usecase.coupon;

import com.hhplus.ecommerce.domain.service.RedisCouponQueueService;
import com.hhplus.ecommerce.domain.service.UserService;
import com.hhplus.ecommerce.presentation.dto.CouponQueueResponse;
import org.springframework.stereotype.Service;

/**
 * Redis Sorted Set 기반 선착순 쿠폰 대기열 진입 UseCase
 *
 * User Story: "사용자가 선착순 쿠폰 대기열에 진입한다 (Redis 기반)"
 *
 * 특징:
 * - O(log N) 성능
 * - 원자적 연산 (락 불필요)
 * - 자동 선착순 정렬
 */
@Service
public class JoinRedisQueueUseCase {

    private final RedisCouponQueueService queueService;
    private final UserService userService;

    public JoinRedisQueueUseCase(RedisCouponQueueService queueService,
                                 UserService userService) {
        this.queueService = queueService;
        this.userService = userService;
    }

    /**
     * 대기열 진입 (Public ID 기반)
     *
     * @param publicId 사용자 Public ID (UUID)
     * @param couponId 쿠폰 ID
     * @return 대기열 정보
     */
    public CouponQueueResponse execute(String publicId, Long couponId) {
        Long userId = userService.getUserByPublicId(publicId).getId();
        return queueService.joinQueue(userId, couponId);
    }
}