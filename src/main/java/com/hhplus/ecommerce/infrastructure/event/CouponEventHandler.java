package com.hhplus.ecommerce.infrastructure.event;

import com.hhplus.ecommerce.config.KafkaConfig;
import com.hhplus.ecommerce.domain.entity.Order;
import com.hhplus.ecommerce.domain.event.*;
import com.hhplus.ecommerce.domain.repository.OrderRepository;
import com.hhplus.ecommerce.domain.service.CouponService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 이벤트 핸들러 (Kafka Consumer)
 *
 * Kafka에서 주문 생성/실패 이벤트를 구독하여 쿠폰을 관리합니다.
 */
@Slf4j
@Component
public class CouponEventHandler {

    private final CouponService couponService;
    private final OrderRepository orderRepository;
    private final com.hhplus.ecommerce.domain.service.OutboxService outboxService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public CouponEventHandler(CouponService couponService,
                             OrderRepository orderRepository,
                             com.hhplus.ecommerce.domain.service.OutboxService outboxService,
                             KafkaTemplate<String, Object> kafkaTemplate) {
        this.couponService = couponService;
        this.orderRepository = orderRepository;
        this.outboxService = outboxService;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 주문 생성 → 쿠폰 사용 (Kafka Consumer)
     *
     * Kafka 토픽: order.created
     * - 쿠폰 사용 처리
     * - 성공/실패 이벤트를 Kafka로 발행
     *
     * @Transactional: 쿠폰 사용 + Outbox 저장을 하나의 트랜잭션으로 처리
     */
    @Transactional
    @KafkaListener(topics = KafkaConfig.ORDER_CREATED_TOPIC, groupId = "coupon-service")
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 쿠폰을 사용하지 않는 경우
        if (event.userCouponId() == null) {
            log.info("[쿠폰-Kafka] 쿠폰 미사용: orderId={}", event.orderId());

            // 쿠폰 사용 스킵 → 성공으로 간주
            CouponUsedEvent successEvent = new CouponUsedEvent(
                event.orderId(),
                null
            );
            outboxService.saveEvent("COUPON_USED", event.orderId(), successEvent);
            kafkaTemplate.send(
                KafkaConfig.COUPON_USED_TOPIC,
                event.orderId().toString(),
                successEvent
            );

            log.info("[쿠폰-Kafka] 쿠폰 미사용 처리 → Kafka 발행: orderId={}, topic={}",
                event.orderId(), KafkaConfig.COUPON_USED_TOPIC);
            return;
        }

        try {
            log.info("[쿠폰-Kafka] 쿠폰 사용 시작: orderId={}, userCouponId={}",
                event.orderId(), event.userCouponId());

            // 쿠폰 사용 처리 (이미 useCoupon 메서드가 있음)
            couponService.useCoupon(event.userCouponId(), event.userId());

            log.info("[쿠폰-Kafka] 쿠폰 사용 완료: orderId={}, userCouponId={}",
                event.orderId(), event.userCouponId());

            // 성공 이벤트를 Kafka로 발행
            CouponUsedEvent successEvent = new CouponUsedEvent(
                event.orderId(),
                event.userCouponId()
            );
            outboxService.saveEvent("COUPON_USED", event.orderId(), successEvent);
            kafkaTemplate.send(
                KafkaConfig.COUPON_USED_TOPIC,
                event.orderId().toString(),
                successEvent
            );

            log.info("[쿠폰-Kafka] 쿠폰 사용 성공 → Kafka 발행: orderId={}, topic={}",
                event.orderId(), KafkaConfig.COUPON_USED_TOPIC);

        } catch (Exception e) {
            log.error("[쿠폰-Kafka] 쿠폰 사용 실패: orderId={}, userCouponId={}, error={}",
                event.orderId(), event.userCouponId(), e.getMessage(), e);

            // 실패 이벤트를 Kafka로 발행
            CouponUsageFailedEvent failEvent = new CouponUsageFailedEvent(
                event.orderId(),
                e.getMessage()
            );
            outboxService.saveEvent("COUPON_USAGE_FAILED", event.orderId(), failEvent);
            kafkaTemplate.send(
                KafkaConfig.COUPON_USAGE_FAILED_TOPIC,
                event.orderId().toString(),
                failEvent
            );

            log.info("[쿠폰-Kafka] 쿠폰 사용 실패 → Kafka 발행: orderId={}, topic={}",
                event.orderId(), KafkaConfig.COUPON_USAGE_FAILED_TOPIC);
        }
    }

    /**
     * 주문 실패 → 보상 트랜잭션 (쿠폰 복구) (Kafka Consumer)
     */
    @Transactional
    @KafkaListener(topics = KafkaConfig.ORDER_FAILED_TOPIC, groupId = "coupon-service")
    public void handleOrderFailed(OrderFailedEvent event) {
        // 쿠폰 사용이 성공했었는지 확인
        if (!event.completedSteps().contains("COUPON")) {
            log.info("[쿠폰-Kafka] 보상 트랜잭션 불필요 (쿠폰 사용 안됨): orderId={}", event.orderId());
            return;
        }

        try {
            log.info("[쿠폰-Kafka] 보상 트랜잭션 시작 (쿠폰 복구): orderId={}", event.orderId());

            // 주문 조회하여 userCouponId 획득
            Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + event.orderId()));

            // 쿠폰을 사용했는지 확인
            if (order.getUserCoupon() != null) {
                Long userCouponId = order.getUserCoupon().getId();

                // 쿠폰 복구 (사용 취소)
                couponService.cancelCoupon(userCouponId);

                log.info("[쿠폰-Kafka] 쿠폰 복구 완료: orderId={}, userCouponId={}", event.orderId(), userCouponId);
            } else {
                log.info("[쿠폰-Kafka] 쿠폰 미사용 주문: orderId={}", event.orderId());
            }

            log.info("[쿠폰-Kafka] 보상 트랜잭션 완료: orderId={}", event.orderId());

        } catch (Exception e) {
            log.error("[쿠폰-Kafka] 보상 트랜잭션 실패: orderId={}, error={}", event.orderId(), e.getMessage(), e);
            // TODO: Dead Letter Queue로 전송하여 수동 처리
        }
    }
}
