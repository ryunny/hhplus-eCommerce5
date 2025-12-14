package com.hhplus.ecommerce.domain.event;

import com.hhplus.ecommerce.domain.vo.Money;

import java.util.List;

/**
 * 주문 생성 이벤트
 *
 * 주문이 생성되면 재고, 결제, 쿠폰 서비스가 이 이벤트를 구독하여
 * 각자의 작업을 병렬로 수행합니다.
 */
public record OrderCreatedEvent(
    Long orderId,
    Long userId,
    List<OrderItem> items,
    Money totalAmount,
    Money discountAmount,
    Money finalAmount,
    Long userCouponId  // null이면 쿠폰 미사용
) {
    public record OrderItem(
        Long productId,
        Integer quantity
    ) {}
}
