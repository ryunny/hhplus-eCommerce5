package com.hhplus.ecommerce.domain.enums;

/**
 * Outbox 이벤트 처리 상태
 */
public enum OutboxStatus {
    /**
     * 대기 중 - 아직 처리되지 않음
     */
    PENDING,

    /**
     * 처리 중 - 현재 처리 진행 중
     */
    PROCESSING,

    /**
     * 성공 - 처리 완료
     */
    SUCCESS,

    /**
     * 실패 - 재시도 횟수 초과로 처리 포기
     */
    FAILED
}
