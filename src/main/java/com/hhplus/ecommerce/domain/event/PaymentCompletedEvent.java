package com.hhplus.ecommerce.domain.event;

import com.hhplus.ecommerce.domain.entity.Payment;

/**
 * 결제 완료 도메인 이벤트
 *
 * 결제가 완료되었을 때 발행되는 이벤트입니다.
 * 외부 시스템(데이터 플랫폼)으로 전송될 데이터를 포함합니다.
 */
public record PaymentCompletedEvent(
        Long paymentId,
        String paymentUuid,
        Long orderId,
        Long amount,
        String status
) implements DomainEvent {
    public static PaymentCompletedEvent from(Payment payment) {
        return new PaymentCompletedEvent(
                payment.getId(),
                payment.getPaymentId(),
                payment.getOrder().getId(),
                payment.getPaidAmount().getAmount(),
                payment.getStatus().name()
        );
    }
}
