package com.hhplus.ecommerce.domain.event;

import com.hhplus.ecommerce.domain.vo.OrderStepStatus;

/**
 * 주문 확정 이벤트
 *
 * 모든 단계(재고, 결제, 쿠폰)가 성공하면 발행됩니다.
 * 각 서비스는 예약된 리소스를 실제로 확정합니다.
 */
public record OrderConfirmedEvent(
    Long orderId,
    OrderStepStatus stepStatus  // 각 단계의 리소스 ID 포함
) {}
