package com.hhplus.ecommerce.domain.event;

/**
 * 쿠폰 사용 실패 이벤트
 */
public record CouponUsageFailedEvent(
    Long orderId,
    String reason
) {}
