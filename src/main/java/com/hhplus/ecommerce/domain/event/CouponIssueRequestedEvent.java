package com.hhplus.ecommerce.domain.event;

import java.time.Instant;

/**
 * 쿠폰 발급 요청 이벤트
 *
 * 선착순 쿠폰 발급 요청 시 Kafka로 발행되는 이벤트
 */
public record CouponIssueRequestedEvent(
    String requestId,      // 요청 ID (멱등성 키, UUID)
    Long couponId,         // 쿠폰 ID
    Long userId,           // 사용자 ID
    Instant requestedAt    // 요청 시각
) {}
