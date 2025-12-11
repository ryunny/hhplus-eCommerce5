package com.hhplus.ecommerce.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhplus.ecommerce.domain.entity.OutboxEvent;
import com.hhplus.ecommerce.domain.entity.Payment;
import com.hhplus.ecommerce.domain.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox 이벤트 관리 서비스
 *
 * Transactional Outbox Pattern:
 * - 도메인 이벤트를 DB에 저장하여 유실 방지
 * - 스케줄러가 폴링하여 이벤트 발행
 * - 실패 시 재시도 (최대 3회)
 */
@Slf4j
@Service
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 범용 이벤트 저장 메서드
     *
     * REQUIRED: 호출자의 트랜잭션에 참여
     * - 호출자와 같은 트랜잭션에서 실행되어 함께 커밋 또는 롤백
     * - Transactional Outbox Pattern의 핵심: 비즈니스 로직과 이벤트 저장이 원자적으로 처리
     *
     * @param eventType 이벤트 타입 (예: "ORDER_CREATED")
     * @param aggregateId 집합 루트 ID (예: orderId)
     * @param event 이벤트 객체 (JSON으로 직렬화됨)
     * @return 저장된 OutboxEvent
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public OutboxEvent saveEvent(String eventType, Long aggregateId, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = new OutboxEvent(eventType, aggregateId, payload);
            OutboxEvent saved = outboxEventRepository.save(outboxEvent);

            log.info("✅ Outbox 이벤트 저장: eventType={}, aggregateId={}, outboxId={}",
                    eventType, aggregateId, saved.getId());

            return saved;

        } catch (JsonProcessingException e) {
            log.error("❌ 이벤트 직렬화 실패: eventType={}, aggregateId={}",
                    eventType, aggregateId, e);
            throw new RuntimeException("Outbox 이벤트 저장 실패", e);
        }
    }

    /**
     * Payment 완료 이벤트를 Outbox에 저장
     *
     * @param payment 결제 정보
     * @return 저장된 OutboxEvent
     */
    public OutboxEvent savePaymentCompletedEvent(Payment payment) {
        try {
            // Payment를 JSON으로 직렬화
            String payload = objectMapper.writeValueAsString(new PaymentEventPayload(
                    payment.getId(),
                    payment.getPaymentId(),
                    payment.getOrder().getId(),
                    payment.getPaidAmount().getAmount(),
                    payment.getStatus().name()
            ));

            OutboxEvent event = new OutboxEvent(
                    "PAYMENT_COMPLETED",
                    payment.getId(),
                    payload
            );

            OutboxEvent saved = outboxEventRepository.save(event);
            log.info("Outbox 이벤트 저장 완료: eventId={}, paymentId={}", saved.getId(), payment.getId());
            return saved;

        } catch (JsonProcessingException e) {
            log.error("Payment JSON 직렬화 실패: paymentId={}", payment.getId(), e);
            throw new RuntimeException("Outbox 이벤트 저장 실패", e);
        }
    }

    /**
     * JSON 페이로드를 지정된 타입으로 역직렬화
     *
     * @param payload JSON 문자열
     * @param eventClass 이벤트 클래스
     * @return 역직렬화된 이벤트 객체
     */
    public <T> T deserializeEvent(String payload, Class<T> eventClass) {
        try {
            return objectMapper.readValue(payload, eventClass);
        } catch (JsonProcessingException e) {
            log.error("❌ Payload 역직렬화 실패: eventClass={}, payload={}",
                    eventClass.getSimpleName(), payload, e);
            throw new RuntimeException("Payload 역직렬화 실패", e);
        }
    }

    /**
     * JSON 페이로드를 PaymentEventPayload로 역직렬화 (하위 호환성)
     *
     * @param payload JSON 문자열
     * @return PaymentEventPayload
     */
    public PaymentEventPayload deserializePayload(String payload) {
        return deserializeEvent(payload, PaymentEventPayload.class);
    }

    /**
     * Payment 이벤트 페이로드 (JSON 직렬화용 DTO)
     */
    public record PaymentEventPayload(
            Long paymentId,
            String paymentUuid,
            Long orderId,
            Long amount,
            String status
    ) {}
}
