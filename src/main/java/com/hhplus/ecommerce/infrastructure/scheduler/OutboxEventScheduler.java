package com.hhplus.ecommerce.infrastructure.scheduler;

import com.hhplus.ecommerce.domain.entity.OutboxEvent;
import com.hhplus.ecommerce.domain.enums.OutboxStatus;
import com.hhplus.ecommerce.domain.event.*;
import com.hhplus.ecommerce.domain.repository.OutboxEventRepository;
import com.hhplus.ecommerce.domain.service.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox 이벤트 폴링 스케줄러 (재시도 전용)
 *
 * 역할:
 * - 즉시 발행이 실패한 이벤트의 재시도 처리
 * - PENDING 상태로 남아있는 이벤트를 폴링하여 재발행
 * - ORDER_CREATED, STOCK_RESERVED, PAYMENT_FAILED 등 내부 이벤트
 *
 * 정상 흐름:
 * 1. ChoreographyPlaceOrderUseCase가 즉시 발행 → 성공 시 바로 처리
 * 2. 실패 시 PENDING 상태로 Outbox에 남음
 * 3. 이 스케줄러가 주기적으로 재시도
 *
 * 외부 시스템 전송 이벤트(PAYMENT_COMPLETED)는 OutboxProcessor가 Kafka로 전송
 *
 * Transactional Outbox Pattern의 핵심:
 * 1. PENDING 상태의 이벤트를 조회
 * 2. 이벤트 재발행 (ApplicationEventPublisher)
 * 3. 성공 시 SUCCESS 상태로 변경
 * 4. 실패 시 재시도 카운트 증가
 *
 * 실행 주기: scheduler.outbox.fixed-delay
 * 초기 지연: scheduler.outbox.initial-delay
 */
@Slf4j
@Component
public class OutboxEventScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxService outboxService;
    private final ApplicationEventPublisher eventPublisher;

    public OutboxEventScheduler(OutboxEventRepository outboxEventRepository,
                               OutboxService outboxService,
                               ApplicationEventPublisher eventPublisher) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxService = outboxService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Outbox 이벤트 폴링 및 발행 (내부 이벤트만 처리)
     *
     * PAYMENT_COMPLETED는 OutboxProcessor가 Kafka로 전송
     */
    @Scheduled(fixedDelayString = "${scheduler.outbox.fixed-delay}",
               initialDelayString = "${scheduler.outbox.initial-delay}")
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, OutboxEvent.MAX_RETRY_COUNT);

        // PAYMENT_COMPLETED는 OutboxProcessor가 처리하므로 제외
        pendingEvents = pendingEvents.stream()
                .filter(event -> !"PAYMENT_COMPLETED".equals(event.getEventType()))
                .toList();

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("📦 Outbox 재시도 (내부 이벤트): {} 개의 실패한 이벤트 재처리 시작", pendingEvents.size());

        for (OutboxEvent outbox : pendingEvents) {
            try {
                // 1. PROCESSING 상태로 변경
                outbox.markAsProcessing();
                outboxEventRepository.save(outbox);

                // 2. 이벤트 역직렬화 및 발행
                Object event = deserializeEvent(outbox);
                eventPublisher.publishEvent(event);

                // 3. 성공 처리
                outbox.markAsSuccess();
                outboxEventRepository.save(outbox);

                log.info("✅ Outbox 이벤트 재시도 성공: eventType={}, outboxId={}, aggregateId={}",
                        outbox.getEventType(), outbox.getId(), outbox.getAggregateId());

            } catch (Exception e) {
                // 4. 실패 처리 (재시도 카운트 증가)
                outbox.incrementRetryCount(e.getMessage());
                outboxEventRepository.save(outbox);

                log.error("❌ Outbox 이벤트 재시도 실패: eventType={}, outboxId={}, retryCount={}, error={}",
                        outbox.getEventType(), outbox.getId(), outbox.getRetryCount(), e.getMessage());

                // 최대 재시도 횟수 초과 시 경고
                if (!outbox.canRetry()) {
                    log.error("🚨 Outbox 이벤트 재시도 횟수 초과 (FAILED 상태): eventType={}, outboxId={}",
                            outbox.getEventType(), outbox.getId());
                }
            }
        }

        log.info("📦 Outbox 재시도 완료");
    }

    /**
     * 이벤트 타입에 따라 역직렬화
     */
    private Object deserializeEvent(OutboxEvent outbox) {
        String eventType = outbox.getEventType();
        String payload = outbox.getPayload();

        return switch (eventType) {
            // Choreography 이벤트
            case "ORDER_CREATED" -> outboxService.deserializeEvent(payload, OrderCreatedEvent.class);
            case "STOCK_RESERVED" -> outboxService.deserializeEvent(payload, StockReservedEvent.class);
            case "STOCK_RESERVATION_FAILED" -> outboxService.deserializeEvent(payload, StockReservationFailedEvent.class);
            case "PAYMENT_COMPLETED" -> outboxService.deserializeEvent(payload, PaymentCompletedEvent.class);
            case "PAYMENT_FAILED" -> outboxService.deserializeEvent(payload, PaymentFailedEvent.class);
            case "COUPON_USED" -> outboxService.deserializeEvent(payload, CouponUsedEvent.class);
            case "COUPON_USAGE_FAILED" -> outboxService.deserializeEvent(payload, CouponUsageFailedEvent.class);
            case "ORDER_CONFIRMED" -> outboxService.deserializeEvent(payload, OrderConfirmedEvent.class);
            case "ORDER_FAILED" -> outboxService.deserializeEvent(payload, OrderFailedEvent.class);

            // Orchestration 이벤트 (하위 호환성)
            case "PAYMENT_COMPLETED_LEGACY" -> {
                // 기존 Payment 이벤트 처리 (필요 시)
                log.warn("⚠️ Legacy 이벤트: eventType={}", eventType);
                yield null;
            }

            default -> {
                log.error("❌ 알 수 없는 이벤트 타입: eventType={}", eventType);
                throw new IllegalArgumentException("알 수 없는 이벤트 타입: " + eventType);
            }
        };
    }
}
