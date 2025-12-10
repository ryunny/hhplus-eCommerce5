package com.hhplus.ecommerce.domain.event;

import java.util.List;

/**
 * 주문 실패 이벤트
 *
 * 하나라도 실패하면 발행됩니다.
 * 각 서비스는 completedSteps를 확인하여 보상 트랜잭션을 수행합니다.
 */
public record OrderFailedEvent(
    Long orderId,
    String reason,
    List<String> completedSteps  // 성공한 단계 목록 (보상 대상)
) {}
