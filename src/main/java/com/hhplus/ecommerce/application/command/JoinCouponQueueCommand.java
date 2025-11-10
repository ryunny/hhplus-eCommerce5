package com.hhplus.ecommerce.application.command;

/**
 * 쿠폰 대기열 진입 Command
 */
public record JoinCouponQueueCommand(
        String publicId,
        Long couponId
) {
}
