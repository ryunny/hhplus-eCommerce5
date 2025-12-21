package com.hhplus.ecommerce.domain.event;

import java.time.Instant;

/**
 * 쿠폰 발급 실패 이벤트 (Dead Letter Queue)
 *
 * 쿠폰 발급 실패 시 Kafka DLQ로 발행되는 이벤트
 * 운영자가 수동으로 확인 및 처리
 */
public record CouponIssueFailedEvent(
    String requestId,      // 원본 요청 ID
    Long couponId,         // 쿠폰 ID
    Long userId,           // 사용자 ID
    String reason,         // 실패 사유
    Instant failedAt       // 실패 시각
) {}
