package com.hhplus.ecommerce.domain.event.publisher;

import com.hhplus.ecommerce.domain.event.DomainEvent;

/**
 * 이벤트 발행 인터페이스
 *
 * 비즈니스 로직에서 이벤트를 발행할 때 사용하는 추상화 계층입니다.
 * 구현체를 교체함으로써 다양한 이벤트 발행 메커니즘을 지원할 수 있습니다:
 * - OutboxEventPublisher: Outbox Pattern 기반 (현재)
 * - KafkaEventPublisher: Kafka 기반 (향후 확장)
 * - SpringEventPublisher: Spring ApplicationEventPublisher 기반
 *
 * 이를 통해 비즈니스 로직과 이벤트 발행 메커니즘을 분리하여
 * 응집도를 높이고 유지보수성을 향상시킵니다.
 */
public interface EventPublisher {

    /**
     * 도메인 이벤트를 발행합니다.
     *
     * @param event 발행할 도메인 이벤트
     * @param <T> 도메인 이벤트 타입
     */
    <T extends DomainEvent> void publish(T event);
}
