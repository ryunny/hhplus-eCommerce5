package com.hhplus.ecommerce.domain.enums;

/**
 * Outbox 이벤트 처리 상태
 *
 * 상태 전이:
 * PENDING → PUBLISHED → CONSUMED → COMPLETED
 *         ↓
 *       FAILED (재시도 횟수 초과)
 */
public enum OutboxStatus {
    /**
     * 대기 중 - Outbox에 저장됨, Kafka 전송 대기
     */
    PENDING,

    /**
     * 발행됨 - Kafka로 메시지 전송 완료
     */
    PUBLISHED,

    /**
     * 수신됨 - Consumer가 메시지 수신하여 처리 시작
     */
    CONSUMED,

    /**
     * 완료 - Consumer의 처리가 최종 완료됨
     */
    COMPLETED,

    /**
     * 실패 - 재시도 횟수 초과로 처리 포기
     */
    FAILED
}
