package com.hhplus.ecommerce.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {
    PENDING("결제 대기"),
    PAID("결제 완료"),
    CONFIRMED("주문 확인"),
    SHIPPED("배송 중"),
    DELIVERED("배송 완료"),
    CANCELLED("취소됨"),
    FAILED("실패");  // 보상 트랜잭션 완료 후 최종 실패 상태

    private final String description;
}
