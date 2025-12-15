package com.hhplus.ecommerce.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka 토픽 설정
 *
 * 결제 완료 이벤트를 외부 시스템으로 전송하기 위한 토픽을 생성합니다.
 */
@Configuration
public class KafkaConfig {

    public static final String PAYMENT_COMPLETED_TOPIC = "payment-completed";

    /**
     * 결제 완료 이벤트 토픽
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
