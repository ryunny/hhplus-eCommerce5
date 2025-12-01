package com.hhplus.ecommerce.infrastructure.event;

import com.hhplus.ecommerce.domain.event.OrderCompletedEvent;
import com.hhplus.ecommerce.domain.service.ProductRankingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 상품 랭킹 업데이트 이벤트 핸들러
 *
 * 주문 트랜잭션 커밋 후 비동기로 Redis 랭킹을 업데이트합니다.
 * - Redis 장애 시에도 주문 트랜잭션은 성공 처리
 * - 결제 처리와 랭킹 업데이트의 독립성 확보
 */
@Slf4j
@Component
public class ProductRankingEventHandler {

    private final ProductRankingService productRankingService;

    public ProductRankingEventHandler(ProductRankingService productRankingService) {
        this.productRankingService = productRankingService;
    }

    /**
     * 주문 완료 이벤트 수신 → Redis 랭킹 업데이트
     *
     * @param event 주문 완료 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        try {
            log.info("주문 완료 이벤트 수신 - 랭킹 업데이트 시작: orderId={}", event.orderId());
            productRankingService.recordSales(event.orderItems());
            log.info("랭킹 업데이트 완료: orderId={}", event.orderId());
        } catch (Exception e) {
            // Redis 장애 발생 시 로그만 남기고 예외는 전파하지 않음
            // 주문 트랜잭션은 이미 커밋되었으므로 영향 없음
            log.error("랭킹 업데이트 실패 (주문은 정상 처리됨): orderId={}, error={}",
                    event.orderId(), e.getMessage(), e);
        }
    }
}
