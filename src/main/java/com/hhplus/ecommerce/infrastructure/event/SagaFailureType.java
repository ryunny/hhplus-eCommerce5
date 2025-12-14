package com.hhplus.ecommerce.infrastructure.event;

import com.hhplus.ecommerce.domain.entity.Order;

/**
 * Saga 실패 타입
 *
 * 주문 처리 중 발생할 수 있는 실패 유형을 정의합니다.
 * Consumer 람다 대신 명시적인 타입으로 가독성을 향상시킵니다.
 */
public enum SagaFailureType {
    /**
     * 재고 예약 실패
     */
    STOCK_RESERVATION {
        @Override
        public void markFailure(Order order, String reason) {
            order.getStepStatus().markStockReservationFailed(reason);
        }
    },

    /**
     * 결제 실패
     */
    PAYMENT {
        @Override
        public void markFailure(Order order, String reason) {
            order.getStepStatus().markPaymentFailed(reason);
        }
    },

    /**
     * 쿠폰 사용 실패
     */
    COUPON_USAGE {
        @Override
        public void markFailure(Order order, String reason) {
            order.getStepStatus().markCouponUsageFailed(reason);
        }
    };

    /**
     * 주문 실패 상태를 기록합니다.
     *
     * @param order 주문
     * @param reason 실패 사유
     */
    public abstract void markFailure(Order order, String reason);
}
