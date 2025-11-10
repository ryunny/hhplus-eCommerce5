package com.hhplus.ecommerce.application.query;

/**
 * 쿠폰 대기열 상태 조회 Query
 */
public record GetQueueStatusQuery(
        String publicId,
        Long couponId
) {
}
