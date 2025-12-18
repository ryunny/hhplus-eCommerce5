package com.hhplus.ecommerce.domain.event;

import java.time.Instant;

/**
 * 쿠폰 발급 성공 이벤트
 *
 * 쿠폰이 성공적으로 발급되었을 때 Kafka로 발행되는 이벤트
 * 외부 알림 시스템 등에서 구독 가능
 */
public record CouponIssuedEvent(
    String requestId,      // 원본 요청 ID
    Long userCouponId,     // 발급된 사용자 쿠폰 ID
    Long couponId,         // 쿠폰 ID
    Long userId,           // 사용자 ID
    Instant issuedAt       // 발급 시각
) {}
