package com.hhplus.ecommerce.infrastructure.event;

import com.hhplus.ecommerce.domain.entity.Order;
import com.hhplus.ecommerce.domain.enums.OrderStatus;
import com.hhplus.ecommerce.domain.event.*;
import com.hhplus.ecommerce.domain.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    private final ApplicationEventPublisher eventPublisher;

    public OrderSagaEventHandler(OrderRepository orderRepository,
                                ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    // ========================================
    // 성공 이벤트 핸들러
    // ========================================

    /**
     * 재고 예약 성공 처리
     */
    @Async
    @Transactional
    @EventListener
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
     */
    @Async
    @Transactional
    @EventListener
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
     */
    @Async
    @Transactional
    @EventListener
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
     */
    @Async
    @Transactional
    @EventListener
    public void handleStockReservationFailed(StockReservationFailedEvent event) {
        log.error("[주문-Saga] 재고 예약 실패 수신: orderId={}, reason={}",
            event.orderId(), event.reason());

        failOrder(event.orderId(), event.reason(), order -> {
            order.getStepStatus().markStockReservationFailed(event.reason());
        });
    }

    /**
     * 결제 실패 처리
     */
    @Async
    @Transactional
    @EventListener
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.error("[주문-Saga] 결제 실패 수신: orderId={}, reason={}",
            event.orderId(), event.reason());

        failOrder(event.orderId(), event.reason(), order -> {
            order.getStepStatus().markPaymentFailed(event.reason());
        });
    }

    /**
     * 쿠폰 사용 실패 처리
     */
    @Async
    @Transactional
    @EventListener
    public void handleCouponUsageFailed(CouponUsageFailedEvent event) {
        log.error("[주문-Saga] 쿠폰 사용 실패 수신: orderId={}, reason={}",
            event.orderId(), event.reason());

        failOrder(event.orderId(), event.reason(), order -> {
            order.getStepStatus().markCouponUsageFailed(event.reason());
        });
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

            // 주문 확정 이벤트 발행 → 각 도메인이 예약을 확정
            eventPublisher.publishEvent(new OrderConfirmedEvent(
                order.getId(),
                order.getStepStatus()
            ));
        }
    }

    /**
     * 주문 실패 처리 및 보상 트랜잭션 트리거
     */
    private void failOrder(Long orderId, String reason,
                          java.util.function.Consumer<Order> statusUpdater) {
        Order order = orderRepository.findByIdWithLock(orderId)
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + orderId));

        // 이미 처리된 주문은 무시
        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("[주문-Saga] 이미 처리된 주문: orderId={}, status={}", orderId, order.getStatus());
            return;
        }

        // 실패 상태 업데이트
        statusUpdater.accept(order);
        order.markAsFailed(reason);
        orderRepository.save(order);

        log.error("[주문-Saga] 주문 실패 처리: orderId={}, reason={}", orderId, reason);

        // 보상 트랜잭션 이벤트 발행
        eventPublisher.publishEvent(new OrderFailedEvent(
            orderId,
            reason,
            order.getStepStatus().getCompletedSteps()  // 성공한 단계만 보상
        ));
    }
}
