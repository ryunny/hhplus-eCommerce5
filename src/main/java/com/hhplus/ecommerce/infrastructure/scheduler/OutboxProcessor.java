package com.hhplus.ecommerce.infrastructure.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhplus.ecommerce.config.KafkaConfig;
import com.hhplus.ecommerce.config.properties.SchedulerProperties;
import com.hhplus.ecommerce.domain.entity.OutboxEvent;
import com.hhplus.ecommerce.domain.enums.OutboxStatus;
import com.hhplus.ecommerce.domain.event.PaymentCompletedEvent;
import com.hhplus.ecommerce.domain.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox Pattern 이벤트 처리 스케줄러
 *
 * 주기적으로 PENDING 상태의 Outbox 이벤트를 조회하여
 * Kafka로 전송합니다.
 */
@Slf4j
@Component
public class OutboxProcessor {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final SchedulerProperties schedulerProperties;

    public OutboxProcessor(OutboxEventRepository outboxEventRepository,
                          KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate,
                          ObjectMapper objectMapper,
                          SchedulerProperties schedulerProperties) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.schedulerProperties = schedulerProperties;
    }

    /**
     * Outbox 이벤트 처리 (설정: scheduler.outbox.fixed-delay)
     */
    @Scheduled(fixedDelayString = "${scheduler.outbox.fixed-delay}")
    public void processOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, OutboxEvent.MAX_RETRY_COUNT);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Outbox 이벤트 처리 시작: {} 건", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            processEvent(event);
        }
    }

    /**
     * 개별 이벤트 처리 - Kafka로 메시지 전송
     */
    public void processEvent(OutboxEvent event) {
        try {
            // 페이로드 역직렬화
            PaymentCompletedEvent payload = deserializePayload(event.getPayload());

            // Kafka로 메시지 전송
            kafkaTemplate.send(KafkaConfig.PAYMENT_COMPLETED_TOPIC,
                              payload.orderId().toString(),
                              payload);

            // 상태 변경 (별도 트랜잭션)
            markEventAsPublished(event);

            log.info("Outbox 이벤트 Kafka 전송 성공: eventId={}, paymentId={}, orderId={}",
                    event.getId(), payload.paymentId(), payload.orderId());

        } catch (Exception e) {
            // 실패 처리: 재시도 카운트 증가 (별도 트랜잭션)
            incrementRetryCount(event, e);

            if (event.canRetry()) {
                log.warn("Outbox 이벤트 Kafka 전송 실패 (재시도 예정): eventId={}, retryCount={}, error={}",
                        event.getId(), event.getRetryCount(), e.getMessage());
            } else {
                log.error("Outbox 이벤트 Kafka 전송 최종 실패: eventId={}, retryCount={}",
                        event.getId(), event.getRetryCount(), e);
            }
        }
    }

    @Transactional
    protected void markEventAsPublished(OutboxEvent event) {
        event.markAsPublished();
        outboxEventRepository.save(event);
    }

    @Transactional
    protected void incrementRetryCount(OutboxEvent event, Exception e) {
        String errorMessage = e.getMessage();
        if (errorMessage != null && errorMessage.length() > 500) {
            errorMessage = errorMessage.substring(0, 500);
        }
        event.incrementRetryCount(errorMessage);
        outboxEventRepository.save(event);
    }

    /**
     * JSON 페이로드를 PaymentCompletedEvent로 역직렬화
     */
    private PaymentCompletedEvent deserializePayload(String payload) {
        try {
            return objectMapper.readValue(payload, PaymentCompletedEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Payload 역직렬화 실패: {}", payload, e);
            throw new RuntimeException("Payload 역직렬화 실패", e);
        }
    }
}
