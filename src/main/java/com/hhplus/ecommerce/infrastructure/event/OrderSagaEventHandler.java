package com.hhplus.ecommerce.infrastructure.event;

import com.hhplus.ecommerce.config.KafkaConfig;
import com.hhplus.ecommerce.domain.entity.Order;
import com.hhplus.ecommerce.domain.enums.OrderStatus;
import com.hhplus.ecommerce.domain.event.*;
import com.hhplus.ecommerce.domain.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class OrderSagaEventHandler {

    private final OrderRepository orderRepository;
    private final com.hhplus.ecommerce.domain.service.OutboxService outboxService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderSagaEventHandler(OrderRepository orderRepository,
                                com.hhplus.ecommerce.domain.service.OutboxService outboxService,
                                KafkaTemplate<String, Object> kafkaTemplate) {
        this.orderRepository = orderRepository;
        this.outboxService = outboxService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    @KafkaListener(topics = KafkaConfig.STOCK_RESERVED_TOPIC, groupId = "order-saga-service")
    public void handleStockReserved(StockReservedEvent event) {
        Order order = orderRepository.findByIdWithLock(event.orderId())
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + event.orderId()));

        if (order.getStatus() != OrderStatus.PENDING) {
            return;
        }

        order.getStepStatus().markStockReserved(event.reservationId());
        orderRepository.save(order);
        checkAndConfirmOrder(order);
    }

    @Transactional
    @KafkaListener(topics = KafkaConfig.PAYMENT_COMPLETED_INTERNAL_TOPIC, groupId = "order-saga-service")
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        Order order = orderRepository.findByIdWithLock(event.orderId())
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + event.orderId()));

        if (order.getStatus() != OrderStatus.PENDING) {
            return;
        }

        order.getStepStatus().markPaymentCompleted(event.paymentId());
        orderRepository.save(order);
        checkAndConfirmOrder(order);
    }

    @Transactional
    @KafkaListener(topics = KafkaConfig.COUPON_USED_TOPIC, groupId = "order-saga-service")
    public void handleCouponUsed(CouponUsedEvent event) {
        Order order = orderRepository.findByIdWithLock(event.orderId())
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + event.orderId()));

        if (order.getStatus() != OrderStatus.PENDING) {
            return;
        }

        order.getStepStatus().markCouponUsed();
        orderRepository.save(order);
        checkAndConfirmOrder(order);
    }

    @Transactional
    @KafkaListener(topics = KafkaConfig.STOCK_RESERVATION_FAILED_TOPIC, groupId = "order-saga-service")
    public void handleStockReservationFailed(StockReservationFailedEvent event) {
        log.error("재고 예약 실패: orderId={}, reason={}", event.orderId(), event.reason());
        failOrder(event.orderId(), event.reason(), SagaFailureType.STOCK_RESERVATION);
    }

    @Transactional
    @KafkaListener(topics = KafkaConfig.PAYMENT_FAILED_TOPIC, groupId = "order-saga-service")
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.error("결제 실패: orderId={}, reason={}", event.orderId(), event.reason());
        failOrder(event.orderId(), event.reason(), SagaFailureType.PAYMENT);
    }

    @Transactional
    @KafkaListener(topics = KafkaConfig.COUPON_USAGE_FAILED_TOPIC, groupId = "order-saga-service")
    public void handleCouponUsageFailed(CouponUsageFailedEvent event) {
        log.error("쿠폰 사용 실패: orderId={}, reason={}", event.orderId(), event.reason());
        failOrder(event.orderId(), event.reason(), SagaFailureType.COUPON_USAGE);
    }

    private void checkAndConfirmOrder(Order order) {
        if (order.getStepStatus().allCompleted()) {
            order.confirm();
            orderRepository.save(order);

            OrderConfirmedEvent event = new OrderConfirmedEvent(
                order.getId(),
                order.getStepStatus()
            );
            outboxService.saveEvent("ORDER_CONFIRMED", order.getId(), event);
            kafkaTemplate.send(
                KafkaConfig.ORDER_CONFIRMED_TOPIC,
                order.getId().toString(),
                event
            );
        }
    }

    private void failOrder(Long orderId, String reason, SagaFailureType failureType) {
        Order order = orderRepository.findByIdWithLock(orderId)
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + orderId));

        if (order.getStatus() != OrderStatus.PENDING) {
            return;
        }

        failureType.markFailure(order, reason);
        order.markAsFailed(reason);
        orderRepository.save(order);

        log.error("주문 실패: orderId={}, failureType={}, reason={}", orderId, failureType, reason);

        OrderFailedEvent event = new OrderFailedEvent(
            orderId,
            reason,
            order.getStepStatus().getCompletedSteps()
        );
        outboxService.saveEvent("ORDER_FAILED", orderId, event);
        kafkaTemplate.send(
            KafkaConfig.ORDER_FAILED_TOPIC,
            orderId.toString(),
            event
        );
    }
}
