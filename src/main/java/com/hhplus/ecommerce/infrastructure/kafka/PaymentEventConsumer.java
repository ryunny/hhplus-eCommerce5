package com.hhplus.ecommerce.infrastructure.kafka;

import com.hhplus.ecommerce.config.KafkaConfig;
import com.hhplus.ecommerce.domain.entity.OutboxEvent;
import com.hhplus.ecommerce.domain.entity.Payment;
import com.hhplus.ecommerce.domain.event.PaymentCompletedEvent;
import com.hhplus.ecommerce.domain.repository.OutboxEventRepository;
import com.hhplus.ecommerce.domain.repository.PaymentRepository;
import com.hhplus.ecommerce.domain.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 완료 이벤트 Kafka Consumer
 *
 * Kafka에서 결제 완료 이벤트를 수신하여
 * 외부 시스템(데이터 플랫폼)으로 전송하고
 * Outbox 상태를 업데이트합니다.
 */
@Slf4j
@Component
public class PaymentEventConsumer {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final OutboxEventRepository outboxEventRepository;

    public PaymentEventConsumer(PaymentRepository paymentRepository,
                                PaymentService paymentService,
                                OutboxEventRepository outboxEventRepository) {
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.outboxEventRepository = outboxEventRepository;
    }

    /**
     * 결제 완료 이벤트 처리
     *
     * 1. Kafka에서 메시지 수신
     * 2. Outbox 상태: PUBLISHED -> CONSUMED
     * 3. 외부 시스템(데이터 플랫폼)으로 전송
     * 4. Outbox 상태: CONSUMED -> COMPLETED
     */
    @KafkaListener(topics = KafkaConfig.PAYMENT_COMPLETED_TOPIC,
                   groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(PaymentCompletedEvent event) {
        log.info("Kafka 메시지 수신: paymentId={}, orderId={}", event.paymentId(), event.orderId());

        try {
            // 1. Outbox 조회 및 상태 업데이트: PUBLISHED -> CONSUMED
            OutboxEvent outboxEvent = outboxEventRepository.findByAggregateId(event.paymentId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Outbox 이벤트를 찾을 수 없습니다: paymentId=" + event.paymentId()));

            outboxEvent.markAsConsumed();
            outboxEventRepository.save(outboxEvent);

            log.info("Outbox 상태 업데이트 (CONSUMED): eventId={}, paymentId={}",
                    outboxEvent.getId(), event.paymentId());

            // 2. Payment 조회
            Payment payment = paymentRepository.findByIdOrThrow(event.paymentId());

            // 3. 외부 시스템(데이터 플랫폼)으로 전송
            paymentService.sendToDataPlatform(payment);

            log.info("데이터 플랫폼 전송 완료: paymentId={}", event.paymentId());

            // 4. Outbox 상태 업데이트: CONSUMED -> COMPLETED
            outboxEvent.markAsCompleted();
            outboxEventRepository.save(outboxEvent);

            log.info("결제 이벤트 처리 완료: paymentId={}, orderId={}", event.paymentId(), event.orderId());

        } catch (Exception e) {
            log.error("결제 이벤트 처리 실패: paymentId={}, error={}", event.paymentId(), e.getMessage(), e);
            // 예외를 다시 던져서 Kafka가 재시도하도록 함
            throw new RuntimeException("결제 이벤트 처리 실패", e);
        }
    }
}
