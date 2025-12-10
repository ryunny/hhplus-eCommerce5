package com.hhplus.ecommerce.domain.event;

/**
 * 쿠폰 사용 성공 이벤트
 */
public record CouponUsedEvent(
    Long orderId,
    Long userCouponId
) {}
