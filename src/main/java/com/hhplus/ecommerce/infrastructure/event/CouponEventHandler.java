package com.hhplus.ecommerce.infrastructure.event;

import com.hhplus.ecommerce.domain.entity.Order;
import com.hhplus.ecommerce.domain.event.*;
import com.hhplus.ecommerce.domain.repository.OrderRepository;
import com.hhplus.ecommerce.domain.service.CouponService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 쿠폰 이벤트 핸들러
 *
 * 주문 생성/실패 이벤트를 구독하여 쿠폰을 관리합니다.
 */
@Slf4j
@Component
public class CouponEventHandler {

    private final CouponService couponService;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CouponEventHandler(CouponService couponService,
                             OrderRepository orderRepository,
                             ApplicationEventPublisher eventPublisher) {
        this.couponService = couponService;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 주문 생성 → 쿠폰 사용
     *
     * AFTER_COMMIT: UseCase의 트랜잭션이 커밋된 후 실행
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 쿠폰을 사용하지 않는 경우
        if (event.userCouponId() == null) {
            log.info("[쿠폰] 쿠폰 미사용: orderId={}", event.orderId());

            // 쿠폰 사용 스킵 → 성공으로 간주
            eventPublisher.publishEvent(new CouponUsedEvent(
                event.orderId(),
                null
            ));
            return;
        }

        try {
            log.info("[쿠폰] 쿠폰 사용 시작: orderId={}, userCouponId={}",
                event.orderId(), event.userCouponId());

            // 쿠폰 사용 처리 (이미 useCoupon 메서드가 있음)
            couponService.useCoupon(event.userCouponId(), event.userId());

            log.info("[쿠폰] 쿠폰 사용 완료: orderId={}, userCouponId={}",
                event.orderId(), event.userCouponId());

            // 성공 이벤트 발행
            eventPublisher.publishEvent(new CouponUsedEvent(
                event.orderId(),
                event.userCouponId()
            ));

        } catch (Exception e) {
            log.error("[쿠폰] 쿠폰 사용 실패: orderId={}, userCouponId={}, error={}",
                event.orderId(), event.userCouponId(), e.getMessage(), e);

            // 실패 이벤트 발행
            eventPublisher.publishEvent(new CouponUsageFailedEvent(
                event.orderId(),
                e.getMessage()
            ));
        }
    }

    /**
     * 주문 실패 → 보상 트랜잭션 (쿠폰 복구)
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderFailed(OrderFailedEvent event) {
        // 쿠폰 사용이 성공했었는지 확인
        if (!event.completedSteps().contains("COUPON")) {
            log.info("[쿠폰] 보상 트랜잭션 불필요 (쿠폰 사용 안됨): orderId={}", event.orderId());
            return;
        }

        try {
            log.info("[쿠폰] 보상 트랜잭션 시작 (쿠폰 복구): orderId={}", event.orderId());

            // 주문 조회하여 userCouponId 획득
            Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + event.orderId()));

            // 쿠폰을 사용했는지 확인
            if (order.getUserCoupon() != null) {
                Long userCouponId = order.getUserCoupon().getId();

                // 쿠폰 복구 (사용 취소)
                couponService.restoreCoupon(userCouponId);

                log.info("[쿠폰] 쿠폰 복구 완료: orderId={}, userCouponId={}", event.orderId(), userCouponId);
            } else {
                log.info("[쿠폰] 쿠폰 미사용 주문: orderId={}", event.orderId());
            }

            log.info("[쿠폰] 보상 트랜잭션 완료: orderId={}", event.orderId());

        } catch (Exception e) {
            log.error("[쿠폰] 보상 트랜잭션 실패: orderId={}, error={}", event.orderId(), e.getMessage(), e);
            // TODO: Dead Letter Queue로 전송하여 수동 처리
        }
    }
}
