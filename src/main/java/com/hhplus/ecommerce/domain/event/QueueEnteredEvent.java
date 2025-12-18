package com.hhplus.ecommerce.domain.event;

import java.time.Instant;

/**
 * 대기열 진입 이벤트
 *
 * 사용자가 대기열에 진입할 때 Kafka로 발행되는 이벤트
 * 순차 처리를 위해 queueId를 Key로 사용
 */
public record QueueEnteredEvent(
    String queueId,        // 대기열 ID
    Long userId,           // 사용자 ID
    Long queueNumber,      // 대기 번호
    Instant enteredAt      // 진입 시각
) {}
