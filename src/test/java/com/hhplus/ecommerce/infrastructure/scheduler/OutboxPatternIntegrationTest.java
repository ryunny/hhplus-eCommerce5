package com.hhplus.ecommerce.infrastructure.scheduler;

import com.hhplus.ecommerce.BaseIntegrationTest;
import com.hhplus.ecommerce.domain.entity.OutboxEvent;
import com.hhplus.ecommerce.domain.enums.OutboxStatus;
import com.hhplus.ecommerce.domain.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Transactional Outbox Pattern 통합 테스트
 *
 * Outbox Pattern의 핵심 기능을 검증합니다:
 * - 이벤트 DB 저장
 * - 스케줄러의 이벤트 처리
 * - 재시도 로직 (최대 3회)
 * - 실패 이벤트 처리
 */
class OutboxPatternIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    @DisplayName("이벤트가 DB에 정상적으로 저장된다")
    void saveEvent_Success() {
        // given
        String eventType = "OrderCreatedEvent";
        Long aggregateId = 123L;
        String payload = "{\"orderId\": 123}";

        // when
        OutboxEvent event = new OutboxEvent(eventType, aggregateId, payload);
        outboxEventRepository.save(event);

        // then
        OutboxEvent savedEvent = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(savedEvent.getEventType()).isEqualTo(eventType);
        assertThat(savedEvent.getAggregateId()).isEqualTo(aggregateId);
        assertThat(savedEvent.getPayload()).isEqualTo(payload);
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(savedEvent.getRetryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("PENDING 상태의 이벤트를 조회한다")
    void findPendingEvents_ReturnsPendingOnly() {
        // given - 여러 상태의 이벤트 생성
        OutboxEvent pending1 = new OutboxEvent("Event1", 1L, "{\"data\":1}");
        OutboxEvent pending2 = new OutboxEvent("Event2", 2L, "{\"data\":2}");
        outboxEventRepository.save(pending1);
        outboxEventRepository.save(pending2);

        OutboxEvent processing = new OutboxEvent("Event3", 3L, "{\"data\":3}");
        processing.markAsProcessing();
        outboxEventRepository.save(processing);

        OutboxEvent success = new OutboxEvent("Event4", 4L, "{\"data\":4}");
        success.markAsSuccess();
        outboxEventRepository.save(success);

        // when
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatus(OutboxStatus.PENDING);

        // then - PENDING 상태만 조회됨
        assertThat(pendingEvents).hasSize(2);
        assertThat(pendingEvents).extracting(OutboxEvent::getEventType)
                .containsExactlyInAnyOrder("Event1", "Event2");
    }

    @Test
    @DisplayName("이벤트 처리 성공 시 상태가 SUCCESS로 변경된다")
    void processEvent_Success_StatusChangedToSuccess() {
        // given
        OutboxEvent event = new OutboxEvent("OrderCompletedEvent", 456L, "{\"orderId\":456}");
        outboxEventRepository.save(event);

        // when - 이벤트를 SUCCESS로 마킹
        event.markAsSuccess();
        outboxEventRepository.save(event);

        // then
        OutboxEvent updatedEvent = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(updatedEvent.getStatus()).isEqualTo(OutboxStatus.SUCCESS);
        assertThat(updatedEvent.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("재시도 횟수가 증가한다")
    void incrementRetryCount_Success() {
        // given
        OutboxEvent event = new OutboxEvent("RetryEvent", 100L, "{\"test\":true}");
        outboxEventRepository.save(event);

        String errorMessage = "Connection timeout";

        // when - 재시도
        event.incrementRetryCount(errorMessage);
        outboxEventRepository.save(event);

        // then
        OutboxEvent updatedEvent = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(updatedEvent.getRetryCount()).isEqualTo(1);
        assertThat(updatedEvent.getFailedReason()).isEqualTo(errorMessage);
        assertThat(updatedEvent.getStatus()).isEqualTo(OutboxStatus.PENDING); // 재시도 가능하므로 PENDING 유지
    }

    @Test
    @DisplayName("재시도 횟수 초과 시 FAILED 상태로 변경된다")
    void exceedMaxRetry_StatusChangedToFailed() {
        // given
        OutboxEvent event = new OutboxEvent("FailedEvent", 200L, "{\"fail\":true}");
        outboxEventRepository.save(event);

        // when - 최대 재시도 횟수 초과 (3회)
        for (int i = 0; i < OutboxEvent.MAX_RETRY_COUNT; i++) {
            event.incrementRetryCount("시도 " + (i + 1) + " 실패");
        }
        outboxEventRepository.save(event);

        // then
        OutboxEvent failedEvent = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(failedEvent.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(failedEvent.getRetryCount()).isEqualTo(OutboxEvent.MAX_RETRY_COUNT);
        assertThat(failedEvent.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("canRetry() 메서드가 정상 동작한다")
    void canRetry_Works() {
        // given
        OutboxEvent event = new OutboxEvent("RetryTest", 300L, "{\"retry\":true}");
        outboxEventRepository.save(event);

        // then - 초기에는 재시도 가능
        assertThat(event.canRetry()).isTrue();

        // when - 1회 재시도
        event.incrementRetryCount("1차 실패");
        assertThat(event.canRetry()).isTrue();

        // when - 2회 재시도
        event.incrementRetryCount("2차 실패");
        assertThat(event.canRetry()).isTrue();

        // when - 3회 재시도 (최대 횟수 도달)
        event.incrementRetryCount("3차 실패");
        assertThat(event.canRetry()).isFalse();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    @DisplayName("여러 이벤트가 순서대로 처리된다")
    void multipleEvents_ProcessedInOrder() {
        // given - 3개의 이벤트 생성
        OutboxEvent event1 = new OutboxEvent("Event1", 1L, "{\"order\":1}");
        OutboxEvent event2 = new OutboxEvent("Event2", 2L, "{\"order\":2}");
        OutboxEvent event3 = new OutboxEvent("Event3", 3L, "{\"order\":3}");

        outboxEventRepository.save(event1);
        outboxEventRepository.save(event2);
        outboxEventRepository.save(event3);

        // when - PENDING 이벤트 조회
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatus(OutboxStatus.PENDING);

        // then - 생성 순서대로 조회됨
        assertThat(pendingEvents).hasSize(3);
        assertThat(pendingEvents.get(0).getEventType()).isEqualTo("Event1");
        assertThat(pendingEvents.get(1).getEventType()).isEqualTo("Event2");
        assertThat(pendingEvents.get(2).getEventType()).isEqualTo("Event3");
    }

    @Test
    @DisplayName("PROCESSING 상태 전환이 정상 동작한다")
    void markAsProcessing_Success() {
        // given
        OutboxEvent event = new OutboxEvent("ProcessingEvent", 400L, "{\"processing\":true}");
        outboxEventRepository.save(event);

        // when
        event.markAsProcessing();
        outboxEventRepository.save(event);

        // then
        OutboxEvent processingEvent = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(processingEvent.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
    }

    @Test
    @DisplayName("이벤트 전체 라이프사이클이 정상 동작한다")
    void eventLifecycle_Success() {
        // given - 이벤트 생성
        OutboxEvent event = new OutboxEvent("LifecycleEvent", 500L, "{\"lifecycle\":true}");
        outboxEventRepository.save(event);

        // when & then - 1. 초기 상태 확인
        OutboxEvent savedEvent = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(savedEvent.getRetryCount()).isEqualTo(0);

        // when & then - 2. 처리 시작
        savedEvent.markAsProcessing();
        outboxEventRepository.save(savedEvent);

        OutboxEvent processing = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(processing.getStatus()).isEqualTo(OutboxStatus.PROCESSING);

        // when & then - 3. 첫 번째 시도 실패 (재시도 1회)
        processing.incrementRetryCount("Connection timeout");
        outboxEventRepository.save(processing);

        OutboxEvent retried = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(retried.getRetryCount()).isEqualTo(1);
        assertThat(retried.getStatus()).isEqualTo(OutboxStatus.PENDING);

        // when & then - 4. 두 번째 시도 성공 (SUCCESS)
        retried.markAsProcessing();
        retried.markAsSuccess();
        outboxEventRepository.save(retried);

        OutboxEvent success = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(success.getStatus()).isEqualTo(OutboxStatus.SUCCESS);
        assertThat(success.getProcessedAt()).isNotNull();
        assertThat(success.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("동시에 여러 도메인 이벤트가 저장된다")
    void saveDifferentEventTypes_Success() {
        // given - 다양한 도메인 이벤트
        OutboxEvent event1 = new OutboxEvent("OrderCreatedEvent", 1L, "{\"orderId\":1}");
        OutboxEvent event2 = new OutboxEvent("StockDecreasedEvent", 100L, "{\"productId\":100,\"quantity\":5}");
        OutboxEvent event3 = new OutboxEvent("PaymentProcessedEvent", 1000L, "{\"paymentId\":\"PAY-123\"}");
        OutboxEvent event4 = new OutboxEvent("CouponUsedEvent", 50L, "{\"couponId\":50}");

        outboxEventRepository.save(event1);
        outboxEventRepository.save(event2);
        outboxEventRepository.save(event3);
        outboxEventRepository.save(event4);

        // when
        List<OutboxEvent> allEvents = outboxEventRepository.findByStatus(OutboxStatus.PENDING);

        // then
        assertThat(allEvents).hasSize(4);
        assertThat(allEvents).extracting(OutboxEvent::getEventType)
                .containsExactlyInAnyOrder(
                        "OrderCreatedEvent",
                        "StockDecreasedEvent",
                        "PaymentProcessedEvent",
                        "CouponUsedEvent"
                );

        // 모든 이벤트가 PENDING 상태로 저장됨
        assertThat(allEvents).allMatch(event -> event.getStatus() == OutboxStatus.PENDING);
    }
}
