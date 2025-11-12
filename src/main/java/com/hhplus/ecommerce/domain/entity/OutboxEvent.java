package com.hhplus.ecommerce.domain.entity;

import com.hhplus.ecommerce.domain.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Outbox Pattern 이벤트 엔티티
 *
 * 외부 시스템으로 전송해야 할 이벤트를 DB에 저장하여
 * 트랜잭션 커밋 후 비동기로 안전하게 처리합니다.
 */
@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_status_retry", columnList = "status, retry_count"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 이벤트 타입 (예: PAYMENT_COMPLETED)
     */
    @Column(nullable = false, length = 50)
    private String eventType;

    /**
     * 집합 루트 ID (예: Payment ID)
     */
    @Column(nullable = false)
    private Long aggregateId;

    /**
     * 이벤트 페이로드 (JSON)
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    /**
     * 처리 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    /**
     * 재시도 횟수
     */
    @Column(nullable = false)
    private Integer retryCount = 0;

    /**
     * 최대 재시도 횟수
     */
    private static final int MAX_RETRY_COUNT = 3;

    /**
     * 생성 시각
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 처리 완료 시각
     */
    private LocalDateTime processedAt;

    /**
     * 실패 사유
     */
    @Column(length = 500)
    private String failedReason;

    public OutboxEvent(String eventType, Long aggregateId, String payload) {
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 상태 변경: PENDING -> PROCESSING
     */
    public void markAsProcessing() {
        if (this.status != OutboxStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 PROCESSING으로 변경 가능합니다.");
        }
        this.status = OutboxStatus.PROCESSING;
    }

    /**
     * 처리 성공
     */
    public void markAsSuccess() {
        this.status = OutboxStatus.SUCCESS;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * 재시도 증가 및 상태 복원
     */
    public void incrementRetryCount(String errorMessage) {
        this.retryCount++;
        this.failedReason = errorMessage;

        if (this.retryCount >= MAX_RETRY_COUNT) {
            // 최대 재시도 횟수 초과 시 FAILED 상태로 변경
            this.status = OutboxStatus.FAILED;
            this.processedAt = LocalDateTime.now();
        } else {
            // 재시도 가능하면 PENDING으로 복원
            this.status = OutboxStatus.PENDING;
        }
    }

    /**
     * 재시도 가능 여부
     */
    public boolean canRetry() {
        return this.retryCount < MAX_RETRY_COUNT;
    }
}
