package com.hhplus.ecommerce.domain.event;

import java.time.Instant;

/**
 * 대기열 처리 완료 이벤트
 *
 * 대기열에서 사용자의 작업이 처리 완료되었을 때 Kafka로 발행되는 이벤트
 * 알림 시스템 등에서 구독 가능
 */
public record QueueProcessedEvent(
    String queueId,        // 대기열 ID
    Long userId,           // 사용자 ID
    Long queueNumber,      // 처리된 대기 번호
    Instant processedAt    // 처리 완료 시각
) {}
