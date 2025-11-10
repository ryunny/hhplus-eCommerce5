package com.hhplus.ecommerce.application.command;

/**
 * 쿠폰 발급 Command
 */
public record IssueCouponCommand(
        String publicId,
        Long couponId
) {
}
