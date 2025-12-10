package com.hhplus.ecommerce.infrastructure.event;

import com.hhplus.ecommerce.domain.entity.Product;
import com.hhplus.ecommerce.domain.event.*;
import com.hhplus.ecommerce.domain.service.ProductService;
import com.hhplus.ecommerce.domain.vo.Quantity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 재고 이벤트 핸들러
 *
 * 주문 생성/실패/확정 이벤트를 구독하여 재고를 관리합니다.
 */
@Slf4j
@Component
public class StockEventHandler {

    private final ProductService productService;
    private final ApplicationEventPublisher eventPublisher;

    // 주문별 재고 차감 기록 (보상 트랜잭션용)
    private final Map<Long, StockReservation> reservations = new HashMap<>();

    public StockEventHandler(ProductService productService,
                            ApplicationEventPublisher eventPublisher) {
        this.productService = productService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 주문 생성 → 재고 차감
     */
    @Async
    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        try {
            log.info("[재고] 재고 차감 시작: orderId={}, items={}", event.orderId(), event.items().size());

            String reservationId = UUID.randomUUID().toString();
            StockReservation reservation = new StockReservation(reservationId, event.orderId());

            // 각 상품의 재고 차감
            for (OrderCreatedEvent.OrderItem item : event.items()) {
                Quantity quantity = new Quantity(item.quantity());

                // 재고 차감 시도
                productService.decreaseStock(item.productId(), quantity);

                // 보상 트랜잭션을 위해 기록
                reservation.addItem(item.productId(), quantity);

                log.info("[재고] 재고 차감 완료: orderId={}, productId={}, quantity={}",
                    event.orderId(), item.productId(), quantity.getValue());
            }

            // 예약 정보 저장
            reservations.put(event.orderId(), reservation);

            // 성공 이벤트 발행
            eventPublisher.publishEvent(new StockReservedEvent(
                event.orderId(),
                reservationId
            ));

            log.info("[재고] 재고 차감 성공: orderId={}, reservationId={}", event.orderId(), reservationId);

        } catch (Exception e) {
            log.error("[재고] 재고 차감 실패: orderId={}, error={}", event.orderId(), e.getMessage(), e);

            // 실패 이벤트 발행
            eventPublisher.publishEvent(new StockReservationFailedEvent(
                event.orderId(),
                e.getMessage()
            ));
        }
    }

    /**
     * 주문 확정 → 재고 확정 (현재는 이미 차감되어 있으므로 로그만)
     *
     * 실제 프로덕션에서는 "예약" → "확정" 2단계로 구현할 수 있습니다.
     */
    @Async
    @EventListener
    public void handleOrderConfirmed(OrderConfirmedEvent event) {
        log.info("[재고] 재고 확정: orderId={}", event.orderId());

        // 예약 정보 제거 (더 이상 보상 트랜잭션 필요 없음)
        reservations.remove(event.orderId());
    }

    /**
     * 주문 실패 → 보상 트랜잭션 (재고 복구)
     */
    @Async
    @EventListener
    public void handleOrderFailed(OrderFailedEvent event) {
        // 재고 차감이 성공했었는지 확인
        if (!event.completedSteps().contains("STOCK")) {
            log.info("[재고] 보상 트랜잭션 불필요 (재고 차감 안됨): orderId={}", event.orderId());
            return;
        }

        StockReservation reservation = reservations.get(event.orderId());
        if (reservation == null) {
            log.error("[재고] 보상 트랜잭션 실패: 예약 정보 없음, orderId={}", event.orderId());
            return;
        }

        try {
            log.info("[재고] 보상 트랜잭션 시작 (재고 복구): orderId={}", event.orderId());

            // 차감했던 재고 복구
            for (StockReservation.ReservationItem item : reservation.getItems()) {
                productService.increaseStock(item.productId(), item.quantity());

                log.info("[재고] 재고 복구 완료: orderId={}, productId={}, quantity={}",
                    event.orderId(), item.productId(), item.quantity().getValue());
            }

            // 예약 정보 제거
            reservations.remove(event.orderId());

            log.info("[재고] 보상 트랜잭션 완료: orderId={}", event.orderId());

        } catch (Exception e) {
            log.error("[재고] 보상 트랜잭션 실패: orderId={}, error={}", event.orderId(), e.getMessage(), e);
            // TODO: Dead Letter Queue로 전송하여 수동 처리
        }
    }

    /**
     * 재고 예약 정보 (보상 트랜잭션용)
     */
    private static class StockReservation {
        private final String reservationId;
        private final Long orderId;
        private final java.util.List<ReservationItem> items = new java.util.ArrayList<>();

        public StockReservation(String reservationId, Long orderId) {
            this.reservationId = reservationId;
            this.orderId = orderId;
        }

        public void addItem(Long productId, Quantity quantity) {
            items.add(new ReservationItem(productId, quantity));
        }

        public java.util.List<ReservationItem> getItems() {
            return items;
        }

        private record ReservationItem(Long productId, Quantity quantity) {}
    }
}
