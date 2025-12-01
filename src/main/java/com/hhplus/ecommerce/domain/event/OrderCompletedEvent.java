package com.hhplus.ecommerce.domain.event;

import com.hhplus.ecommerce.domain.entity.OrderItem;

import java.util.List;

/**
 * 주문 완료 이벤트
 *
 * 트랜잭션 커밋 후 발행되어 비동기로 처리됩니다.
 * - 랭킹 업데이트
 * - 알림 발송
 * - 통계 수집 등
 */
public record OrderCompletedEvent(
        Long orderId,
        List<OrderItem> orderItems
) {
}
