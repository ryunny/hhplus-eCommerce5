package com.hhplus.ecommerce.presentation.dto;

/**
 * 쿠폰 발급 요청 응답 DTO
 *
 * 202 Accepted와 함께 반환되며, 비동기 처리 중임을 알립니다.
 */
public record CouponIssueResponse(
    String requestId,          // 요청 ID (결과 조회용)
    String message,            // 안내 메시지
    Long remainingStock        // 남은 재고
) {}
