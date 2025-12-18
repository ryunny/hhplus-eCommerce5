package com.hhplus.ecommerce.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhplus.ecommerce.config.KafkaConfig;
import com.hhplus.ecommerce.domain.entity.User;
import com.hhplus.ecommerce.domain.enums.QueueStatus;
import com.hhplus.ecommerce.domain.event.QueueEnteredEvent;
import com.hhplus.ecommerce.domain.service.UserService;
import com.hhplus.ecommerce.domain.vo.QueueUserInfo;
import com.hhplus.ecommerce.presentation.dto.QueueEntryResponse;
import com.hhplus.ecommerce.presentation.dto.QueueStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;

/**
 * 대기열 컨트롤러 (Kafka 기반)
 *
 * 대량 트래픽을 순차적으로 처리하기 위한 대기열 시스템
 *
 * 주요 기능:
 * 1. 대기열 진입 (Redis Counter + Kafka)
 * 2. 대기 상태 조회 (Redis에서 실시간 조회)
 * 3. Kafka를 통한 순차 처리 보장
 */
@Slf4j
@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueController {

    private final UserService userService;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, QueueEnteredEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 대기열 진입
     *
     * Redis Atomic Counter로 대기 번호 발급 → Kafka로 순차 처리
     *
     * 흐름:
     * 1. Redis로 대기 번호 발급 (INCR)
     * 2. Redis에 사용자 정보 저장
     * 3. Kafka 이벤트 발행 (QueueEnteredEvent)
     * 4. 예상 대기 시간 계산 후 응답
     *
     * @param queueId 대기열 ID (예: "coupon-12345", "ticket-concert-1")
     * @param publicId 사용자 Public ID
     * @return 대기 번호, 내 앞 대기자 수, 예상 대기 시간
     */
    @PostMapping("/{queueId}/enter/{publicId}")
    public ResponseEntity<QueueEntryResponse> enterQueue(
            @PathVariable String queueId,
            @PathVariable String publicId) {

        User user = userService.getUserByPublicId(publicId);
        Long userId = user.getId();

        try {
            // ====================================
            // Step 1: Redis로 대기 번호 발급
            // ====================================
            String counterKey = "queue:counter:" + queueId;
            Long queueNumber = redisTemplate.opsForValue().increment(counterKey);

            if (queueNumber == null) {
                throw new IllegalStateException("대기 번호 발급 실패");
            }

            log.info("[대기열] 대기 번호 발급: queueId={}, userId={}, queueNumber={}",
                queueId, userId, queueNumber);

            // ====================================
            // Step 2: Redis에 사용자 정보 저장
            // ====================================
            String userKey = "queue:user:" + queueId + ":" + userId;
            QueueUserInfo userInfo = new QueueUserInfo(
                userId,
                queueNumber,
                QueueStatus.WAITING,
                Instant.now()
            );

            redisTemplate.opsForValue().set(
                userKey,
                objectMapper.writeValueAsString(userInfo),
                Duration.ofHours(1)
            );

            log.info("[대기열] 사용자 정보 저장: queueId={}, userId={}", queueId, userId);

            // ====================================
            // Step 3: Kafka 이벤트 발행
            // ====================================
            QueueEnteredEvent event = new QueueEnteredEvent(
                queueId,
                userId,
                queueNumber,
                Instant.now()
            );

            // Key를 queueId로 설정하여 같은 대기열은 같은 파티션으로
            // → FIFO 순서 보장
            kafkaTemplate.send(
                KafkaConfig.QUEUE_ENTERED_TOPIC,
                queueId,  // partition key
                event
            );

            log.info("[대기열] Kafka 이벤트 발행: queueId={}, userId={}, queueNumber={}",
                queueId, userId, queueNumber);

            // ====================================
            // Step 4: 예상 대기 시간 계산
            // ====================================
            Long currentProcessingNumber = getCurrentProcessingNumber(queueId);
            Long processingRate = getProcessingRate(queueId);  // 초당 처리 건수 (기본 100)

            long waitingCount = queueNumber - currentProcessingNumber;
            long estimatedWaitSeconds = waitingCount / processingRate;

            log.info("[대기열] 진입 완료: queueId={}, userId={}, queueNumber={}, waitingCount={}, estimatedWait={}s",
                queueId, userId, queueNumber, waitingCount, estimatedWaitSeconds);

            return ResponseEntity.ok(new QueueEntryResponse(
                queueNumber,
                waitingCount,
                estimatedWaitSeconds,
                "대기열에 진입했습니다. 잠시만 기다려주세요."
            ));

        } catch (Exception e) {
            log.error("[대기열] 진입 실패: queueId={}, userId={}, error={}",
                queueId, userId, e.getMessage(), e);
            throw new IllegalStateException("대기열 진입에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * 내 대기 상태 조회
     *
     * Redis에서 실시간으로 대기 상태를 조회합니다.
     *
     * @param queueId 대기열 ID
     * @param publicId 사용자 Public ID
     * @return 대기 번호, 내 앞 대기자 수, 상태, 예상 대기 시간
     */
    @GetMapping("/{queueId}/status/{publicId}")
    public ResponseEntity<QueueStatusResponse> getQueueStatus(
            @PathVariable String queueId,
            @PathVariable String publicId) {

        User user = userService.getUserByPublicId(publicId);
        Long userId = user.getId();

        try {
            String userKey = "queue:user:" + queueId + ":" + userId;
            String userInfoJson = redisTemplate.opsForValue().get(userKey);

            if (userInfoJson == null) {
                throw new IllegalStateException("대기열 정보를 찾을 수 없습니다");
            }

            QueueUserInfo userInfo = objectMapper.readValue(userInfoJson, QueueUserInfo.class);
            Long currentNumber = getCurrentProcessingNumber(queueId);
            Long processingRate = getProcessingRate(queueId);

            long waitingCount = Math.max(0, userInfo.queueNumber() - currentNumber);
            long estimatedWaitSeconds = waitingCount / Math.max(1, processingRate);

            log.info("[대기열] 상태 조회: queueId={}, userId={}, queueNumber={}, status={}, waitingCount={}",
                queueId, userId, userInfo.queueNumber(), userInfo.status(), waitingCount);

            return ResponseEntity.ok(new QueueStatusResponse(
                userInfo.queueNumber(),
                waitingCount,
                userInfo.status().name(),
                estimatedWaitSeconds
            ));

        } catch (Exception e) {
            log.error("[대기열] 상태 조회 실패: queueId={}, userId={}, error={}",
                queueId, userId, e.getMessage());
            throw new IllegalStateException("대기 상태 조회에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * 현재 처리 중인 대기 번호 조회
     */
    private Long getCurrentProcessingNumber(String queueId) {
        String currentKey = "queue:current:" + queueId;
        String currentNumber = redisTemplate.opsForValue().get(currentKey);
        return currentNumber != null ? Long.parseLong(currentNumber) : 0L;
    }

    /**
     * 처리 속도 조회 (초당 처리 건수)
     *
     * 실제 운영 환경에서는 실시간 처리 속도를 측정하여 사용
     */
    private Long getProcessingRate(String queueId) {
        String rateKey = "queue:rate:" + queueId;
        String rate = redisTemplate.opsForValue().get(rateKey);
        return rate != null ? Long.parseLong(rate) : 100L;  // 기본값: 초당 100건
    }
}
