package com.hhplus.ecommerce.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhplus.ecommerce.domain.entity.OutboxEvent;
import com.hhplus.ecommerce.domain.entity.Payment;
import com.hhplus.ecommerce.domain.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Outbox 이벤트 관리 서비스
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
     * JSON 페이로드를 PaymentEventPayload로 역직렬화
     *
     * @param payload JSON 문자열
     * @return PaymentEventPayload
     */
    public PaymentEventPayload deserializePayload(String payload) {
        try {
            return objectMapper.readValue(payload, PaymentEventPayload.class);
        } catch (JsonProcessingException e) {
            log.error("Payload 역직렬화 실패: {}", payload, e);
            throw new RuntimeException("Payload 역직렬화 실패", e);
        }
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
