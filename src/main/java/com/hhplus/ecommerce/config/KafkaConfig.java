package com.hhplus.ecommerce.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka 토픽 설정
 *
 * 1. Choreography 패턴: 내부 이벤트 (주문 처리 흐름)
 * 2. 외부 시스템: 결제 완료 이벤트 전송
 */
@Configuration
public class KafkaConfig {

    // ========================================
    // Choreography 패턴 토픽
    // ========================================

    /** 주문 생성 이벤트 */
    public static final String ORDER_CREATED_TOPIC = "order.created";

    /** 재고 예약 성공 */
    public static final String STOCK_RESERVED_TOPIC = "stock.reserved";

    /** 재고 예약 실패 */
    public static final String STOCK_RESERVATION_FAILED_TOPIC = "stock.reservation.failed";

    /** 결제 완료 (내부) */
    public static final String PAYMENT_COMPLETED_INTERNAL_TOPIC = "payment.completed.internal";

    /** 결제 실패 */
    public static final String PAYMENT_FAILED_TOPIC = "payment.failed";

    /** 쿠폰 사용 완료 */
    public static final String COUPON_USED_TOPIC = "coupon.used";

    /** 쿠폰 사용 실패 */
    public static final String COUPON_USAGE_FAILED_TOPIC = "coupon.usage.failed";

    /** 주문 실패 (보상 트랜잭션) */
    public static final String ORDER_FAILED_TOPIC = "order.failed";

    /** 주문 확정 (예약 확정) */
    public static final String ORDER_CONFIRMED_TOPIC = "order.confirmed";

    // ========================================
    // 외부 시스템 전송 토픽
    // ========================================

    /** 결제 완료 (외부 시스템) */
    public static final String PAYMENT_COMPLETED_TOPIC = "payment-completed";

    // ========================================
    // 토픽 생성
    // ========================================

    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder.name(ORDER_CREATED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic stockReservedTopic() {
        return TopicBuilder.name(STOCK_RESERVED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic stockReservationFailedTopic() {
        return TopicBuilder.name(STOCK_RESERVATION_FAILED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentCompletedInternalTopic() {
        return TopicBuilder.name(PAYMENT_COMPLETED_INTERNAL_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentFailedTopic() {
        return TopicBuilder.name(PAYMENT_FAILED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic couponUsedTopic() {
        return TopicBuilder.name(COUPON_USED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic couponUsageFailedTopic() {
        return TopicBuilder.name(COUPON_USAGE_FAILED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderFailedTopic() {
        return TopicBuilder.name(ORDER_FAILED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderConfirmedTopic() {
        return TopicBuilder.name(ORDER_CONFIRMED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * 결제 완료 이벤트 토픽 (외부 시스템)
     *
     * - 파티션: 3개 (병렬 처리를 위해)
     * - 복제본: 1개 (로컬 개발 환경)
     */
    @Bean
    public NewTopic paymentCompletedTopic() {
        return TopicBuilder.name(PAYMENT_COMPLETED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
