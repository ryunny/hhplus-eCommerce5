package com.hhplus.ecommerce.presentation.dto;

import com.hhplus.ecommerce.domain.entity.CouponQueue;
import com.hhplus.ecommerce.domain.enums.CouponQueueStatus;

import java.time.LocalDateTime;

public record CouponQueueResponse(
        Long queueId,
        Long userId,
        Long couponId,
        String couponName,
        CouponQueueStatus status,
        Integer queuePosition,
        String message,
        LocalDateTime createdAt,
        LocalDateTime processedAt
) {
    /**
     * DB 기반 CouponQueue에서 생성
     */
    public static CouponQueueResponse from(CouponQueue queue) {
        String message = switch (queue.getStatus()) {
            case WAITING -> queue.getQueuePosition() + "번째 대기 중입니다.";
            case PROCESSING -> "쿠폰 발급 처리 중입니다.";
            case COMPLETED -> "쿠폰 발급이 완료되었습니다!";
            case FAILED -> "발급 실패: " + (queue.getFailedReason() != null ? queue.getFailedReason() : "알 수 없는 오류");
        };

        return new CouponQueueResponse(
                queue.getId(),
                queue.getUser().getId(),
                queue.getCoupon().getId(),
                queue.getCoupon().getName(),
                queue.getStatus(),
                queue.getQueuePosition(),
                message,
                queue.getCreatedAt(),
                queue.getProcessedAt()
        );
    }

    /**
     * Redis Sorted Set 기반 대기열 응답 (간단한 버전)
     *
     * @param couponId 쿠폰 ID
     * @param queuePosition 대기 순번
     * @param totalWaiting 전체 대기 인원
     */
    public CouponQueueResponse(Long couponId, Integer queuePosition, Long totalWaiting) {
        this(
                null,  // queueId는 Redis 기반에서는 없음
                null,  // userId는 별도로 관리
                couponId,
                null,  // couponName은 필요 시 추가 조회
                CouponQueueStatus.WAITING,  // Redis에서는 항상 WAITING
                queuePosition,
                queuePosition + "번째 대기 중입니다. (전체 " + totalWaiting + "명)",
                LocalDateTime.now(),
                null
        );
    }
}
