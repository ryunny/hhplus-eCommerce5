package com.hhplus.ecommerce.domain.event;

/**
 * 결제 실패 이벤트
 */
public record PaymentFailedEvent(
    Long orderId,
    String reason
) {}
