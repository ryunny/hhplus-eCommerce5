package com.hhplus.ecommerce.domain.event;

import com.hhplus.ecommerce.domain.vo.Money;

/**
 * 결제 완료 이벤트
 */
public record PaymentCompletedEvent(
    Long orderId,
    Long paymentId,
    Money amount
) {}
