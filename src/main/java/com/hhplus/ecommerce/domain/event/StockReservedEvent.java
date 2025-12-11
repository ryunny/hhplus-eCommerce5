package com.hhplus.ecommerce.domain.event;

/**
 * 재고 예약 성공 이벤트
 */
public record StockReservedEvent(
    Long orderId,
    String reservationId
) {}
