package com.hhplus.ecommerce.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import com.hhplus.ecommerce.config.KafkaConfig;
import com.hhplus.ecommerce.domain.enums.QueueStatus;
import com.hhplus.ecommerce.domain.event.QueueEnteredEvent;
import com.hhplus.ecommerce.domain.event.QueueProcessedEvent;
import com.hhplus.ecommerce.domain.vo.QueueUserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 대기열 처리 이벤트 핸들러 (Kafka Consumer)
 *
 * Kafka에서 대기열 진입 이벤트를 수신하여 순차적으로 처리합니다.
 *
 * Consumer Group: queue-processor-service
 * Concurrency: 20 (파티션 수와 동일)
 *
 * 주요 기능:
 * 1. Kafka의 순서 보장 특성을 활용한 FIFO 처리
 * 2. Rate Limiting으로 처리 속도 제어
 * 3. 실제 비즈니스 로직 실행 (쿠폰 발급, 티켓 예매 등)
 * 4. 처리 완료 이벤트 발행
 */
@Slf4j
@Component
public class QueueProcessorEventHandler {

    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // Rate Limiter: 초당 1000건 처리 (조정 가능)
    private final RateLimiter rateLimiter = RateLimiter.create(1000.0);

    public QueueProcessorEventHandler(RedisTemplate<String, String> redisTemplate,
                                     KafkaTemplate<String, Object> kafkaTemplate,
                                     ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 대기열 순차 처리
     *
     * Kafka의 순서 보장 특성을 활용하여 FIFO 처리
     *
     * Consumer Group: queue-processor-service
     * Concurrency: 20 (파티션 수)
     */
    @KafkaListener(
        topics = KafkaConfig.QUEUE_ENTERED_TOPIC,
        groupId = "queue-processor-service",
        concurrency = "20"
    )
    public void processQueue(QueueEnteredEvent event) {
        try {
            log.info("[대기열-Kafka] 처리 시작: queueId={}, userId={}, queueNumber={}",
                event.queueId(), event.userId(), event.queueNumber());

            // ====================================
            // Step 1: Rate Limiting (처리 속도 제어)
            // ====================================
            // 예: 초당 1000건 처리를 목표로 할 경우
            rateLimiter.acquire();  // Guava RateLimiter

            // ====================================
            // Step 2: 사용자 상태 업데이트 (처리 중)
            // ====================================
            updateUserStatus(event.queueId(), event.userId(), QueueStatus.PROCESSING);

            // ====================================
            // Step 3: 실제 비즈니스 로직 실행
            // ====================================
            // 예: 티켓 예매, 쿠폰 발급, 주문 처리 등
            executeBusinessLogic(event);

            // ====================================
            // Step 4: 처리 완료 상태 업데이트
            // ====================================
            updateUserStatus(event.queueId(), event.userId(), QueueStatus.COMPLETED);

            // 현재 처리 번호 업데이트
            updateCurrentProcessingNumber(event.queueId(), event.queueNumber());

            log.info("[대기열-Kafka] 처리 완료: queueId={}, userId={}, queueNumber={}",
                event.queueId(), event.userId(), event.queueNumber());

            // ====================================
            // Step 5: 완료 이벤트 발행 (알림용)
            // ====================================
            QueueProcessedEvent completedEvent = new QueueProcessedEvent(
                event.queueId(),
                event.userId(),
                event.queueNumber(),
                Instant.now()
            );

            kafkaTemplate.send(
                KafkaConfig.QUEUE_PROCESSED_TOPIC,
                event.userId().toString(),
                completedEvent
            );

            log.info("[대기열-Kafka] 완료 이벤트 발행: queueId={}, userId={}", event.queueId(), event.userId());

        } catch (Exception e) {
            log.error("[대기열-Kafka] 처리 실패: queueId={}, userId={}, error={}",
                event.queueId(), event.userId(), e.getMessage(), e);

            updateUserStatus(event.queueId(), event.userId(), QueueStatus.FAILED);

            // 재시도 또는 DLQ로 전송
            throw e;
        }
    }

    /**
     * 실제 비즈니스 로직 실행
     *
     * 이 메서드는 실제 애플리케이션의 요구사항에 맞게 구현해야 합니다.
     * 예시:
     * - 티켓 예매: 티켓 재고 확인 및 예매 처리
     * - 쿠폰 발급: 쿠폰 발급 처리
     * - 주문 처리: 주문 생성 및 결제 처리
     */
    private void executeBusinessLogic(QueueEnteredEvent event) {
        // TODO: 실제 비즈니스 로직 구현
        // 예시: 티켓 예매 처리
        log.info("[대기열-Kafka] 비즈니스 로직 실행: queueId={}, userId={}",
            event.queueId(), event.userId());

        // 간단한 시뮬레이션 (실제로는 DB 작업, 외부 API 호출 등)
        try {
            Thread.sleep(100);  // 0.1초 처리 시간 시뮬레이션
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("처리 중 인터럽트 발생", e);
        }
    }

    /**
     * 사용자 상태 업데이트 (Redis)
     */
    private void updateUserStatus(String queueId, Long userId, QueueStatus status) {
        try {
            String userKey = "queue:user:" + queueId + ":" + userId;
            String userInfoJson = redisTemplate.opsForValue().get(userKey);

            if (userInfoJson != null) {
                QueueUserInfo userInfo = objectMapper.readValue(userInfoJson, QueueUserInfo.class);
                QueueUserInfo updated = new QueueUserInfo(
                    userInfo.userId(),
                    userInfo.queueNumber(),
                    status,
                    userInfo.enteredAt()
                );

                redisTemplate.opsForValue().set(
                    userKey,
                    objectMapper.writeValueAsString(updated),
                    Duration.ofHours(1)
                );

                log.debug("[대기열-Kafka] 사용자 상태 업데이트: queueId={}, userId={}, status={}",
                    queueId, userId, status);
            }
        } catch (Exception e) {
            log.error("[대기열-Kafka] 상태 업데이트 실패: queueId={}, userId={}, error={}",
                queueId, userId, e.getMessage());
        }
    }

    /**
     * 현재 처리 중인 대기 번호 업데이트
     */
    private void updateCurrentProcessingNumber(String queueId, Long queueNumber) {
        try {
            String currentKey = "queue:current:" + queueId;
            redisTemplate.opsForValue().set(currentKey, queueNumber.toString());

            log.debug("[대기열-Kafka] 현재 처리 번호 업데이트: queueId={}, queueNumber={}",
                queueId, queueNumber);
        } catch (Exception e) {
            log.error("[대기열-Kafka] 처리 번호 업데이트 실패: queueId={}, error={}",
                queueId, e.getMessage());
        }
    }
}
