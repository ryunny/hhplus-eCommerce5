package com.hhplus.ecommerce.infrastructure.persistence;

import com.hhplus.ecommerce.domain.entity.OutboxEvent;
import com.hhplus.ecommerce.domain.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, Long> {
    Optional<OutboxEvent> findByAggregateId(Long aggregateId);

    List<OutboxEvent> findByStatus(OutboxStatus status);

    List<OutboxEvent> findByStatusAndRetryCountLessThan(OutboxStatus status, int maxRetryCount);
}
