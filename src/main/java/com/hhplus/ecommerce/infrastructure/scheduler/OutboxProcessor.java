package com.hhplus.ecommerce.infrastructure.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhplus.ecommerce.config.properties.SchedulerProperties;
import com.hhplus.ecommerce.domain.entity.OutboxEvent;
import com.hhplus.ecommerce.domain.entity.Payment;
import com.hhplus.ecommerce.domain.enums.OutboxStatus;
import com.hhplus.ecommerce.domain.event.PaymentCompletedEvent;
import com.hhplus.ecommerce.domain.repository.OutboxEventRepository;
import com.hhplus.ecommerce.domain.repository.PaymentRepository;
import com.hhplus.ecommerce.domain.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox Pattern 이벤트 처리 스케줄러
 *
 * 주기적으로 PENDING 상태의 Outbox 이벤트를 조회하여
 * 외부 시스템으로 전송합니다.
 */
@Slf4j
@Component
public class OutboxProcessor {

    private final OutboxEventRepository outboxEventRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;
    private final SchedulerProperties schedulerProperties;

    public OutboxProcessor(OutboxEventRepository outboxEventRepository,
                          PaymentRepository paymentRepository,
                          PaymentService paymentService,
                          ObjectMapper objectMapper,
                          SchedulerProperties schedulerProperties) {
        this.outboxEventRepository = outboxEventRepository;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
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
     * 개별 이벤트 처리
     *
     * @param event 처리할 Outbox 이벤트
     */
    @Transactional
    public void processEvent(OutboxEvent event) {
        try {
            // 상태 변경: PENDING -> PROCESSING
            event.markAsProcessing();
            outboxEventRepository.save(event);

            // 페이로드 역직렬화
            PaymentCompletedEvent payload = deserializePayload(event.getPayload());

            // Payment 조회
            Payment payment = paymentRepository.findByIdOrThrow(payload.paymentId());

            // 외부 API 호출 (데이터 플랫폼 전송)
            paymentService.sendToDataPlatform(payment);

            // 성공 처리
            event.markAsSuccess();
            outboxEventRepository.save(event);

            log.info("Outbox 이벤트 처리 성공: eventId={}, paymentId={}", event.getId(), payment.getId());

        } catch (Exception e) {
            // 실패 처리: 재시도 카운트 증가
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.length() > 500) {
                errorMessage = errorMessage.substring(0, 500);
            }
            event.incrementRetryCount(errorMessage);
            outboxEventRepository.save(event);

            if (event.canRetry()) {
                log.warn("Outbox 이벤트 처리 실패 (재시도 예정): eventId={}, retryCount={}, error={}",
                        event.getId(), event.getRetryCount(), e.getMessage());
            } else {
                log.error("Outbox 이벤트 처리 최종 실패: eventId={}, retryCount={}",
                        event.getId(), event.getRetryCount(), e);
            }
        }
    }

    /**
     * JSON 페이로드를 PaymentCompletedEvent로 역직렬화
     *
     * @param payload JSON 문자열
     * @return PaymentCompletedEvent
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
