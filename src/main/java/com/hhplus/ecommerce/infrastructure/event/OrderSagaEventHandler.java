package com.hhplus.ecommerce.infrastructure.event;

import com.hhplus.ecommerce.domain.entity.Order;
import com.hhplus.ecommerce.domain.enums.OrderStatus;
import com.hhplus.ecommerce.domain.event.*;
import com.hhplus.ecommerce.domain.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 Saga 이벤트 핸들러
 *
 * Choreography 패턴으로 주문 상태를 관리합니다.
 * - 각 도메인의 성공/실패 이벤트를 수신
 * - 모든 단계 완료 시 주문 확정
 * - 하나라도 실패 시 보상 트랜잭션 트리거
 */
@Slf4j
@Component
public class OrderSagaEventHandler {

    private final OrderRepository orderRepository;
    private final com.hhplus.ecommerce.domain.service.OutboxService outboxService;

    public OrderSagaEventHandler(OrderRepository orderRepository,
                                com.hhplus.ecommerce.domain.service.OutboxService outboxService) {
        this.orderRepository = orderRepository;
        this.outboxService = outboxService;
    }

    // ========================================
    // 성공 이벤트 핸들러
    // ========================================

    /**
     * 재고 예약 성공 처리
     *
     * fallbackExecution = true: 트랜잭션 컨텍스트가 없어도 실행
     * - StockEventHandler에서 트랜잭션 없이 이벤트 발행하므로 필요
     */
    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleStockReserved(StockReservedEvent event) {
        log.info("[주문-Saga] 재고 예약 성공 수신: orderId={}, reservationId={}",
            event.orderId(), event.reservationId());

        Order order = orderRepository.findByIdWithLock(event.orderId())
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + event.orderId()));

        // PENDING 상태가 아니면 무시 (이미 완료 또는 실패)
        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("[주문-Saga] 이미 처리된 주문: orderId={}, status={}", event.orderId(), order.getStatus());
            return;
        }

        // 재고 예약 완료 표시
        order.getStepStatus().markStockReserved(event.reservationId());
        orderRepository.save(order);

        log.info("[주문-Saga] 재고 예약 상태 저장 완료: orderId={}", event.orderId());

        // 모든 단계 완료 확인
        checkAndConfirmOrder(order);
    }

    /**
     * 결제 완료 처리
     *
     * fallbackExecution = true: 트랜잭션 컨텍스트가 없어도 실행
     */
    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("[주문-Saga] 결제 완료 수신: orderId={}, paymentId={}",
            event.orderId(), event.paymentId());

        Order order = orderRepository.findByIdWithLock(event.orderId())
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + event.orderId()));

        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("[주문-Saga] 이미 처리된 주문: orderId={}, status={}", event.orderId(), order.getStatus());
            return;
        }

        // 결제 완료 표시
        order.getStepStatus().markPaymentCompleted(event.paymentId());
        orderRepository.save(order);

        log.info("[주문-Saga] 결제 완료 상태 저장 완료: orderId={}", event.orderId());

        // 모든 단계 완료 확인
        checkAndConfirmOrder(order);
    }

    /**
     * 쿠폰 사용 완료 처리
     *
     * fallbackExecution = true: 트랜잭션 컨텍스트가 없어도 실행
     */
    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleCouponUsed(CouponUsedEvent event) {
        log.info("[주문-Saga] 쿠폰 사용 완료 수신: orderId={}, userCouponId={}",
            event.orderId(), event.userCouponId());

        Order order = orderRepository.findByIdWithLock(event.orderId())
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + event.orderId()));

        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("[주문-Saga] 이미 처리된 주문: orderId={}, status={}", event.orderId(), order.getStatus());
            return;
        }

        // 쿠폰 사용 완료 표시
        order.getStepStatus().markCouponUsed(event.userCouponId());
        orderRepository.save(order);

        log.info("[주문-Saga] 쿠폰 사용 상태 저장 완료: orderId={}", event.orderId());

        // 모든 단계 완료 확인
        checkAndConfirmOrder(order);
    }

    // ========================================
    // 실패 이벤트 핸들러
    // ========================================

    /**
     * 재고 예약 실패 처리
     *
     * fallbackExecution = true: 트랜잭션 컨텍스트가 없어도 실행
     */
    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleStockReservationFailed(StockReservationFailedEvent event) {
        log.error("[주문-Saga] 재고 예약 실패 수신: orderId={}, reason={}",
            event.orderId(), event.reason());

        failOrder(event.orderId(), event.reason(), SagaFailureType.STOCK_RESERVATION);
    }

    /**
     * 결제 실패 처리
     *
     * fallbackExecution = true: 트랜잭션 컨텍스트가 없어도 실행
     */
    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.error("[주문-Saga] 결제 실패 수신: orderId={}, reason={}",
            event.orderId(), event.reason());

        failOrder(event.orderId(), event.reason(), SagaFailureType.PAYMENT);
    }

    /**
     * 쿠폰 사용 실패 처리
     *
     * fallbackExecution = true: 트랜잭션 컨텍스트가 없어도 실행
     */
    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleCouponUsageFailed(CouponUsageFailedEvent event) {
        log.error("[주문-Saga] 쿠폰 사용 실패 수신: orderId={}, reason={}",
            event.orderId(), event.reason());

        failOrder(event.orderId(), event.reason(), SagaFailureType.COUPON_USAGE);
    }

    // ========================================
    // 헬퍼 메서드
    // ========================================

    /**
     * 모든 단계 완료 확인 후 주문 확정
     */
    private void checkAndConfirmOrder(Order order) {
        if (order.getStepStatus().allCompleted()) {
            order.confirm();
            orderRepository.save(order);

            log.info("[주문-Saga] 주문 확정: orderId={}", order.getId());

            // 주문 확정 이벤트를 Outbox에 저장 → 각 도메인이 예약을 확정
            OrderConfirmedEvent event = new OrderConfirmedEvent(
                order.getId(),
                order.getStepStatus()
            );
            outboxService.saveEvent("ORDER_CONFIRMED", order.getId(), event);
        }
    }

    /**
     * 주문 실패 처리 및 보상 트랜잭션 트리거
     *
     * Consumer 람다 대신 SagaFailureType Enum을 사용하여 가독성을 향상시켰습니다.
     *
     * @param orderId 주문 ID
     * @param reason 실패 사유
     * @param failureType 실패 타입 (재고/결제/쿠폰)
     */
    private void failOrder(Long orderId, String reason, SagaFailureType failureType) {
        Order order = orderRepository.findByIdWithLock(orderId)
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + orderId));

        // 이미 처리된 주문은 무시
        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("[주문-Saga] 이미 처리된 주문: orderId={}, status={}", orderId, order.getStatus());
            return;
        }

        // 실패 타입별 상태 업데이트
        failureType.markFailure(order, reason);
        order.markAsFailed(reason);
        orderRepository.save(order);

        log.error("[주문-Saga] 주문 실패 처리: orderId={}, failureType={}, reason={}",
            orderId, failureType, reason);

        // 보상 트랜잭션 이벤트를 Outbox에 저장
        OrderFailedEvent event = new OrderFailedEvent(
            orderId,
            reason,
            order.getStepStatus().getCompletedSteps()  // 성공한 단계만 보상
        );
        outboxService.saveEvent("ORDER_FAILED", orderId, event);
    }
}
