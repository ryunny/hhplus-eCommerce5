package com.hhplus.ecommerce.domain.vo;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 주문 각 단계의 상태를 추적하는 Value Object
 *
 * Saga 패턴에서 어떤 단계가 성공했는지 추적하여
 * 실패 시 보상 트랜잭션 대상을 결정합니다.
 */
@Embeddable
@Getter
@NoArgsConstructor
public class OrderStepStatus {

    /**
     * 각 단계의 상태
     */
    @Enumerated(EnumType.STRING)
    private StepResult stockReservation = StepResult.PENDING;

    @Enumerated(EnumType.STRING)
    private StepResult payment = StepResult.PENDING;

    @Enumerated(EnumType.STRING)
    private StepResult couponUsage = StepResult.PENDING;

    /**
     * 각 단계에서 생성된 리소스 ID (보상 트랜잭션에 필요)
     */
    private String stockReservationId;
    private Long paymentId;
    private Long userCouponId;

    /**
     * 실패 정보
     */
    private String failureReason;
    private String failedStep;

    // Setters for state transitions
    public void markStockReserved(String reservationId) {
        this.stockReservation = StepResult.SUCCESS;
        this.stockReservationId = reservationId;
    }

    public void markStockReservationFailed(String reason) {
        this.stockReservation = StepResult.FAILED;
        this.failureReason = reason;
        this.failedStep = "STOCK_RESERVATION";
    }

    public void markPaymentCompleted(Long paymentId) {
        this.payment = StepResult.SUCCESS;
        this.paymentId = paymentId;
    }

    public void markPaymentFailed(String reason) {
        this.payment = StepResult.FAILED;
        this.failureReason = reason;
        this.failedStep = "PAYMENT";
    }

    public void markCouponUsed(Long userCouponId) {
        this.couponUsage = StepResult.SUCCESS;
        this.userCouponId = userCouponId;
    }

    public void markCouponUsageFailed(String reason) {
        this.couponUsage = StepResult.FAILED;
        this.failureReason = reason;
        this.failedStep = "COUPON_USAGE";
    }

    /**
     * 모든 단계가 성공했는지 확인
     */
    public boolean allCompleted() {
        return stockReservation == StepResult.SUCCESS
            && payment == StepResult.SUCCESS
            && couponUsage == StepResult.SUCCESS;
    }

    /**
     * 하나라도 실패했는지 확인
     */
    public boolean anyFailed() {
        return stockReservation == StepResult.FAILED
            || payment == StepResult.FAILED
            || couponUsage == StepResult.FAILED;
    }

    /**
     * 성공한 단계 목록 반환 (보상 트랜잭션 대상 결정)
     */
    public List<String> getCompletedSteps() {
        List<String> completed = new ArrayList<>();
        if (stockReservation == StepResult.SUCCESS) {
            completed.add("STOCK");
        }
        if (payment == StepResult.SUCCESS) {
            completed.add("PAYMENT");
        }
        if (couponUsage == StepResult.SUCCESS) {
            completed.add("COUPON");
        }
        return completed;
    }

    /**
     * 재고 예약 성공 여부
     */
    public boolean isStockReserved() {
        return stockReservation == StepResult.SUCCESS;
    }

    /**
     * 결제 완료 여부
     */
    public boolean isPaymentCompleted() {
        return payment == StepResult.SUCCESS;
    }

    /**
     * 쿠폰 사용 완료 여부
     */
    public boolean isCouponUsed() {
        return couponUsage == StepResult.SUCCESS;
    }

    /**
     * 단계 결과 Enum
     */
    public enum StepResult {
        PENDING,   // 대기 중
        SUCCESS,   // 성공
        FAILED     // 실패
    }
}
