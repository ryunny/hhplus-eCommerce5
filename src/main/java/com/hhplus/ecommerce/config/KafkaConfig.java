package com.hhplus.ecommerce.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka 토픽 설정
 *
 * 1. Choreography 패턴: 내부 이벤트 (주문 처리 흐름)
 * 2. 선착순 쿠폰 발급: 대량 트래픽 처리
 * 3. 대기열 시스템: 순차 처리 보장
 * 4. 외부 시스템: 결제 완료 이벤트 전송
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
    // 선착순 쿠폰 발급 토픽
    // ========================================

    /** 쿠폰 발급 요청 */
    public static final String COUPON_ISSUE_REQUESTED_TOPIC = "coupon.issue.requested";

    /** 쿠폰 발급 성공 */
    public static final String COUPON_ISSUED_TOPIC = "coupon.issued";

    /** 쿠폰 발급 실패 (Dead Letter Queue) */
    public static final String COUPON_ISSUE_FAILED_TOPIC = "coupon.issue.failed";

    // ========================================
    // 대기열 시스템 토픽
    // ========================================

    /** 대기열 진입 */
    public static final String QUEUE_ENTERED_TOPIC = "queue.entered";

    /** 대기열 처리 완료 */
    public static final String QUEUE_PROCESSED_TOPIC = "queue.processed";

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

    // ========================================
    // 선착순 쿠폰 발급 토픽 생성
    // ========================================

    /**
     * 쿠폰 발급 요청 토픽
     *
     * - 파티션: 10개 (높은 처리량)
     * - 복제본: 1개 (로컬 환경, 프로덕션에서는 3개)
     * - Retention: 7일 (재처리 가능)
     * - Compression: lz4 (네트워크 대역폭 절약)
     */
    @Bean
    public NewTopic couponIssueRequestedTopic() {
        return TopicBuilder.name(COUPON_ISSUE_REQUESTED_TOPIC)
                .partitions(10)
                .replicas(1)
                .config("retention.ms", "604800000")  // 7일
                .config("compression.type", "lz4")
                .build();
    }

    /**
     * 쿠폰 발급 성공 토픽
     */
    @Bean
    public NewTopic couponIssuedTopic() {
        return TopicBuilder.name(COUPON_ISSUED_TOPIC)
                .partitions(10)
                .replicas(1)
                .build();
    }

    /**
     * 쿠폰 발급 실패 토픽 (Dead Letter Queue)
     */
    @Bean
    public NewTopic couponIssueFailedTopic() {
        return TopicBuilder.name(COUPON_ISSUE_FAILED_TOPIC)
                .partitions(10)
                .replicas(1)
                .config("retention.ms", "2592000000")  // 30일 (문제 분석용)
                .build();
    }

    // ========================================
    // 대기열 시스템 토픽 생성
    // ========================================

    /**
     * 대기열 진입 토픽
     *
     * - 파티션: 20개 (매우 높은 처리량)
     * - 순서 보장: queueId를 Key로 사용
     */
    @Bean
    public NewTopic queueEnteredTopic() {
        return TopicBuilder.name(QUEUE_ENTERED_TOPIC)
                .partitions(20)
                .replicas(1)
                .config("retention.ms", "86400000")  // 1일
                .config("compression.type", "lz4")
                .build();
    }

    /**
     * 대기열 처리 완료 토픽
     */
    @Bean
    public NewTopic queueProcessedTopic() {
        return TopicBuilder.name(QUEUE_PROCESSED_TOPIC)
                .partitions(20)
                .replicas(1)
                .build();
    }
}
