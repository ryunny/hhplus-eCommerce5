package com.hhplus.ecommerce.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String ORDER_CREATED_TOPIC = "order.created";
    public static final String STOCK_RESERVED_TOPIC = "stock.reserved";
    public static final String STOCK_RESERVATION_FAILED_TOPIC = "stock.reservation.failed";
    public static final String PAYMENT_COMPLETED_INTERNAL_TOPIC = "payment.completed.internal";
    public static final String PAYMENT_FAILED_TOPIC = "payment.failed";
    public static final String COUPON_USED_TOPIC = "coupon.used";
    public static final String COUPON_USAGE_FAILED_TOPIC = "coupon.usage.failed";
    public static final String ORDER_FAILED_TOPIC = "order.failed";
    public static final String ORDER_CONFIRMED_TOPIC = "order.confirmed";
    public static final String COUPON_ISSUE_REQUESTED_TOPIC = "coupon.issue.requested";
    public static final String COUPON_ISSUED_TOPIC = "coupon.issued";
    public static final String COUPON_ISSUE_FAILED_TOPIC = "coupon.issue.failed";
    public static final String QUEUE_ENTERED_TOPIC = "queue.entered";
    public static final String QUEUE_PROCESSED_TOPIC = "queue.processed";
    public static final String PAYMENT_COMPLETED_TOPIC = "payment-completed";

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

    @Bean
    public NewTopic paymentCompletedTopic() {
        return TopicBuilder.name(PAYMENT_COMPLETED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic couponIssueRequestedTopic() {
        return TopicBuilder.name(COUPON_ISSUE_REQUESTED_TOPIC)
                .partitions(10)
                .replicas(1)
                .config("retention.ms", "604800000")
                .config("compression.type", "lz4")
                .build();
    }

    @Bean
    public NewTopic couponIssuedTopic() {
        return TopicBuilder.name(COUPON_ISSUED_TOPIC)
                .partitions(10)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic couponIssueFailedTopic() {
        return TopicBuilder.name(COUPON_ISSUE_FAILED_TOPIC)
                .partitions(10)
                .replicas(1)
                .config("retention.ms", "2592000000")
                .build();
    }

    @Bean
    public NewTopic queueEnteredTopic() {
        return TopicBuilder.name(QUEUE_ENTERED_TOPIC)
                .partitions(20)
                .replicas(1)
                .config("retention.ms", "86400000")
                .config("compression.type", "lz4")
                .build();
    }

    @Bean
    public NewTopic queueProcessedTopic() {
        return TopicBuilder.name(QUEUE_PROCESSED_TOPIC)
                .partitions(20)
                .replicas(1)
                .build();
    }
}
