package com.hhplus.ecommerce.infrastructure.event;

import com.hhplus.ecommerce.domain.entity.OrderItem;
import com.hhplus.ecommerce.domain.event.OrderCompletedEvent;
import com.hhplus.ecommerce.domain.event.OrderConfirmedEvent;
import com.hhplus.ecommerce.domain.repository.OrderItemRepository;
import com.hhplus.ecommerce.domain.service.ProductRankingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 상품 랭킹 업데이트 이벤트 핸들러
 *
 * 주문 완료 후 비동기로 Redis 랭킹을 업데이트합니다.
 * - Redis 장애 시에도 주문 처리는 성공
 * - 결제 처리와 랭킹 업데이트의 독립성 확보
 *
 * 두 가지 이벤트 처리:
 * 1. OrderCompletedEvent (Orchestration 패턴)
 * 2. OrderConfirmedEvent (Choreography 패턴)
 */
@Slf4j
@Component
public class ProductRankingEventHandler {

    private final ProductRankingService productRankingService;
    private final OrderItemRepository orderItemRepository;

    public ProductRankingEventHandler(ProductRankingService productRankingService,
                                     OrderItemRepository orderItemRepository) {
        this.productRankingService = productRankingService;
        this.orderItemRepository = orderItemRepository;
    }

    /**
     * 주문 완료 이벤트 수신 → Redis 랭킹 업데이트 (Orchestration 패턴)
     *
     * @EventListener 사용 이유:
     * - Orchestration UseCase에는 @Transactional이 없음
     * - 각 Service가 독립 트랜잭션으로 실행
     *
     * @param event 주문 완료 이벤트
     */
    @Async
    @EventListener
    public void handleOrderCompleted(OrderCompletedEvent event) {
        try {
            log.info("[인기상품] 주문 완료 이벤트 수신 (Orchestration): orderId={}", event.orderId());
            productRankingService.recordSales(event.orderItems());
            log.info("[인기상품] 랭킹 업데이트 완료: orderId={}", event.orderId());
        } catch (Exception e) {
            // Redis 장애 발생 시 로그만 남기고 예외는 전파하지 않음
            // 주문 트랜잭션은 이미 커밋되었으므로 영향 없음
            log.error("[인기상품] 랭킹 업데이트 실패 (주문은 정상 처리됨): orderId={}, error={}",
                    event.orderId(), e.getMessage(), e);
        }
    }

    /**
     * 주문 확정 이벤트 수신 → Redis 랭킹 업데이트 (Choreography 패턴)
     *
     * @TransactionalEventListener 사용:
     * - OrderSagaEventHandler가 @Transactional 내에서 이벤트 발행
     * - AFTER_COMMIT으로 주문 확정이 확실히 커밋된 후 실행
     *
     * fallbackExecution = true:
     * - OrderSagaEventHandler에서 Outbox 저장 시 REQUIRED 전파 사용
     * - 혹시 트랜잭션 컨텍스트가 없어도 실행되도록 보장
     *
     * @param event 주문 확정 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleOrderConfirmed(OrderConfirmedEvent event) {
        try {
            log.info("[인기상품] 주문 확정 이벤트 수신 (Choreography): orderId={}", event.orderId());

            // 주문 아이템 조회
            List<OrderItem> orderItems = orderItemRepository.findByOrderId(event.orderId());

            if (orderItems.isEmpty()) {
                log.warn("[인기상품] 주문 아이템이 없습니다: orderId={}", event.orderId());
                return;
            }

            productRankingService.recordSales(orderItems);
            log.info("[인기상품] 랭킹 업데이트 완료: orderId={}, items={}", event.orderId(), orderItems.size());
        } catch (Exception e) {
            // Redis 장애 발생 시 로그만 남기고 예외는 전파하지 않음
            log.error("[인기상품] 랭킹 업데이트 실패 (주문은 정상 처리됨): orderId={}, error={}",
                    event.orderId(), e.getMessage(), e);
        }
    }
}
