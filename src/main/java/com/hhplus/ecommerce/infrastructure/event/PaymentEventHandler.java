package com.hhplus.ecommerce.infrastructure.event;

import com.hhplus.ecommerce.config.KafkaConfig;
import com.hhplus.ecommerce.domain.entity.Order;
import com.hhplus.ecommerce.domain.entity.Payment;
import com.hhplus.ecommerce.domain.event.*;
import com.hhplus.ecommerce.domain.repository.OrderRepository;
import com.hhplus.ecommerce.domain.repository.PaymentRepository;
import com.hhplus.ecommerce.domain.service.PaymentService;
import com.hhplus.ecommerce.domain.service.UserService;
import com.hhplus.ecommerce.domain.vo.Money;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 이벤트 핸들러 (Kafka Consumer)
 *
 * Kafka에서 주문 생성/실패 이벤트를 구독하여 결제를 관리합니다.
 */
@Slf4j
@Component
public class PaymentEventHandler {

    private final UserService userService;
    private final PaymentService paymentService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final com.hhplus.ecommerce.domain.service.OutboxService outboxService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventHandler(UserService userService,
                              PaymentService paymentService,
                              OrderRepository orderRepository,
                              PaymentRepository paymentRepository,
                              com.hhplus.ecommerce.domain.service.OutboxService outboxService,
                              KafkaTemplate<String, Object> kafkaTemplate) {
        this.userService = userService;
        this.paymentService = paymentService;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.outboxService = outboxService;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 주문 생성 → 결제 처리 (Kafka Consumer)
     *
     * Kafka 토픽: order.created
     * - 잔액 차감 + 결제 엔티티 생성
     * - 성공/실패 이벤트를 Kafka로 발행
     */
    @KafkaListener(topics = KafkaConfig.ORDER_CREATED_TOPIC, groupId = "payment-service")
    public void handleOrderCreated(OrderCreatedEvent event) {
        try {
            log.info("[결제-Kafka] 결제 처리 시작: orderId={}, amount={}",
                event.orderId(), event.finalAmount().getAmount());

            Payment payment = processPaymentInTransaction(event);

            log.info("[결제-Kafka] 결제 생성 완료: orderId={}, paymentId={}",
                event.orderId(), payment.getId());

            // 성공 이벤트를 Kafka로 발행
            PaymentCompletedEvent successEvent = new PaymentCompletedEvent(
                payment.getId(),
                payment.getPaymentId(),
                event.orderId(),
                event.finalAmount().getAmount(),
                "COMPLETED"
            );
            kafkaTemplate.send(
                KafkaConfig.PAYMENT_COMPLETED_INTERNAL_TOPIC,
                event.orderId().toString(),
                successEvent
            );

            log.info("[결제-Kafka] 결제 처리 성공 → Kafka 발행: orderId={}, topic={}",
                event.orderId(), KafkaConfig.PAYMENT_COMPLETED_INTERNAL_TOPIC);

        } catch (IllegalStateException e) {
            // 잔액 부족
            log.error("[결제-Kafka] 결제 실패 (잔액 부족): orderId={}, error={}",
                event.orderId(), e.getMessage());

            PaymentFailedEvent failEvent = new PaymentFailedEvent(
                event.orderId(),
                "잔액 부족: " + e.getMessage()
            );
            kafkaTemplate.send(
                KafkaConfig.PAYMENT_FAILED_TOPIC,
                event.orderId().toString(),
                failEvent
            );

        } catch (Exception e) {
            log.error("[결제-Kafka] 결제 실패: orderId={}, error={}", event.orderId(), e.getMessage(), e);

            PaymentFailedEvent failEvent = new PaymentFailedEvent(
                event.orderId(),
                e.getMessage()
            );
            kafkaTemplate.send(
                KafkaConfig.PAYMENT_FAILED_TOPIC,
                event.orderId().toString(),
                failEvent
            );
        }
    }

    @Transactional
    protected Payment processPaymentInTransaction(OrderCreatedEvent event) {
        Order order = orderRepository.findById(event.orderId())
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + event.orderId()));

        // 1. 잔액 차감
        userService.deductBalance(event.userId(), event.finalAmount());

        log.info("[결제-Kafka] 잔액 차감 완료: orderId={}, userId={}, amount={}",
            event.orderId(), event.userId(), event.finalAmount().getAmount());

        // 2. 결제 엔티티 생성
        Payment payment = paymentService.createPayment(order, event.finalAmount());

        // 3. Outbox 저장
        PaymentCompletedEvent successEvent = new PaymentCompletedEvent(
            payment.getId(),
            payment.getPaymentId(),
            event.orderId(),
            event.finalAmount().getAmount(),
            "COMPLETED"
        );
        outboxService.saveEvent("PAYMENT_COMPLETED", event.orderId(), successEvent);

        return payment;
    }

    /**
     * 주문 실패 → 보상 트랜잭션 (환불) (Kafka Consumer)
     */
    @Transactional
    @KafkaListener(topics = KafkaConfig.ORDER_FAILED_TOPIC, groupId = "payment-service")
    public void handleOrderFailed(OrderFailedEvent event) {
        // 결제가 성공했었는지 확인
        if (!event.completedSteps().contains("PAYMENT")) {
            log.info("[결제-Kafka] 보상 트랜잭션 불필요 (결제 안됨): orderId={}", event.orderId());
            return;
        }

        try {
            log.info("[결제-Kafka] 보상 트랜잭션 시작 (환불): orderId={}", event.orderId());

            // 주문 조회
            Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + event.orderId()));

            // 결제 ID로 결제 조회
            Long paymentId = order.getStepStatus().getPaymentId();
            Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("결제를 찾을 수 없습니다: " + paymentId));

            // 잔액 복구 (chargeBalance를 환불로 사용)
            userService.chargeBalance(payment.getOrder().getUser().getId(), payment.getPaidAmount());

            log.info("[결제-Kafka] 환불 완료: orderId={}, userId={}, amount={}",
                event.orderId(), order.getUser().getId(), payment.getPaidAmount().getAmount());

            // 결제 취소 처리
            paymentService.cancelPayment(paymentId);

            log.info("[결제-Kafka] 보상 트랜잭션 완료: orderId={}", event.orderId());

        } catch (Exception e) {
            log.error("[결제-Kafka] 보상 트랜잭션 실패: orderId={}, error={}", event.orderId(), e.getMessage(), e);
            // TODO: Dead Letter Queue로 전송하여 수동 처리
        }
    }
}
