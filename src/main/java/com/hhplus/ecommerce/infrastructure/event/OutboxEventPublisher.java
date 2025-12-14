package com.hhplus.ecommerce.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhplus.ecommerce.domain.entity.OutboxEvent;
import com.hhplus.ecommerce.domain.event.DomainEvent;
import com.hhplus.ecommerce.domain.event.PaymentCompletedEvent;
import com.hhplus.ecommerce.domain.event.publisher.EventPublisher;
import com.hhplus.ecommerce.domain.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Outbox Pattern 기반 이벤트 발행 구현체
 *
 * 도메인 이벤트를 Outbox 테이블에 저장합니다.
 * 스케줄러(OutboxProcessor)가 주기적으로 PENDING 이벤트를 처리하여
 * 외부 시스템으로 전송합니다.
 *
 * 장점:
 * - 트랜잭션 일관성 보장 (이벤트 저장과 비즈니스 로직이 같은 트랜잭션)
 * - 외부 시스템 장애 시에도 이벤트 유실 방지
 * - 재시도 메커니즘 내장
 */
@Slf4j
@Component
public class OutboxEventPublisher implements EventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(OutboxEventRepository outboxEventRepository,
                               ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public <T extends DomainEvent> void publish(T event) {
        if (event instanceof PaymentCompletedEvent paymentEvent) {
            publishPaymentCompletedEvent(paymentEvent);
        } else {
            log.warn("지원하지 않는 이벤트 타입: {}", event.getClass().getSimpleName());
        }
    }

    /**
     * 결제 완료 이벤트를 Outbox에 저장
     *
     * @param event 결제 완료 이벤트
     */
    private void publishPaymentCompletedEvent(PaymentCompletedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = new OutboxEvent(
                    "PAYMENT_COMPLETED",
                    event.paymentId(),
                    payload
            );

            OutboxEvent saved = outboxEventRepository.save(outboxEvent);
            log.info("Outbox 이벤트 저장 완료: eventId={}, paymentId={}",
                    saved.getId(), event.paymentId());

        } catch (JsonProcessingException e) {
            log.error("이벤트 JSON 직렬화 실패: {}", event, e);
            throw new RuntimeException("Outbox 이벤트 저장 실패", e);
        }
    }
}
