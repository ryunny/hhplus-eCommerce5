package com.hhplus.ecommerce.domain.entity;

import com.hhplus.ecommerce.domain.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false)
    private Long aggregateId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(nullable = false)
    private Integer retryCount = 0;

    public static final int MAX_RETRY_COUNT = 3;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime publishedAt;

    private LocalDateTime consumedAt;

    private LocalDateTime completedAt;

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
     * Kafka로 메시지 발행 완료
     */
    public void markAsPublished() {
        if (this.status != OutboxStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 PUBLISHED로 변경 가능합니다.");
        }
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    /**
     * @deprecated 하위 호환성을 위한 메서드. markAsPublished() 사용 권장
     */
    @Deprecated
    public void markAsProcessing() {
        markAsPublished();
    }

    /**
     * @deprecated 하위 호환성을 위한 메서드. markAsCompleted() 사용 권장
     */
    @Deprecated
    public void markAsSuccess() {
        // PUBLISHED 상태에서 바로 COMPLETED로 변경 (기존 동작 유지)
        if (this.status == OutboxStatus.PUBLISHED) {
            this.status = OutboxStatus.CONSUMED;
            this.consumedAt = LocalDateTime.now();
        }
        if (this.status == OutboxStatus.CONSUMED) {
            this.status = OutboxStatus.COMPLETED;
            this.completedAt = LocalDateTime.now();
        }
    }

    /**
     * Consumer가 메시지 수신
     */
    public void markAsConsumed() {
        if (this.status != OutboxStatus.PUBLISHED) {
            throw new IllegalStateException("PUBLISHED 상태에서만 CONSUMED로 변경 가능합니다.");
        }
        this.status = OutboxStatus.CONSUMED;
        this.consumedAt = LocalDateTime.now();
    }

    /**
     * 처리 최종 완료
     */
    public void markAsCompleted() {
        if (this.status != OutboxStatus.CONSUMED) {
            throw new IllegalStateException("CONSUMED 상태에서만 COMPLETED로 변경 가능합니다.");
        }
        this.status = OutboxStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void incrementRetryCount(String errorMessage) {
        this.retryCount++;
        this.failedReason = errorMessage;

        if (this.retryCount >= MAX_RETRY_COUNT) {
            // 최대 재시도 횟수 초과 시 FAILED 상태로 변경
            this.status = OutboxStatus.FAILED;
            this.completedAt = LocalDateTime.now();
        } else {
            // 재시도 가능하면 PENDING으로 복원
            this.status = OutboxStatus.PENDING;
        }
    }

    public boolean canRetry() {
        return this.retryCount < MAX_RETRY_COUNT;
    }
}
