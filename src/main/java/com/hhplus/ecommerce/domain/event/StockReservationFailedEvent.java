package com.hhplus.ecommerce.domain.event;

/**
 * 재고 예약 실패 이벤트
 */
public record StockReservationFailedEvent(
    Long orderId,
    String reason
) {}
