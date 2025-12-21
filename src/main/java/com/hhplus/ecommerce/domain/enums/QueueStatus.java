package com.hhplus.ecommerce.domain.enums;

/**
 * 대기열 상태
 */
public enum QueueStatus {
    WAITING,      // 대기 중
    PROCESSING,   // 처리 중
    COMPLETED,    // 처리 완료
    FAILED        // 처리 실패
}
