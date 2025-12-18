package com.hhplus.ecommerce.domain.entity;

import com.hhplus.ecommerce.domain.vo.DiscountRate;
import com.hhplus.ecommerce.domain.vo.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String couponType;

    @Column
    private DiscountRate discountRate;

    @Column
    private Money discountAmount;

    @Column
    private Money minOrderAmount;

    @Column(nullable = false)
    private Integer totalQuantity;

    @Column(nullable = false)
    private Integer issuedQuantity = 0;

    @Column(nullable = false)
    private LocalDateTime startDate;

    @Column(nullable = false)
    private LocalDateTime endDate;

    @Column(nullable = false)
    private boolean useQueue = false;

    public Coupon(String name, String couponType, DiscountRate discountRate,
                  Money discountAmount, Money minOrderAmount, Integer totalQuantity,
                  LocalDateTime startDate, LocalDateTime endDate) {
        validateTotalQuantity(totalQuantity);
        this.name = name;
        this.couponType = couponType;
        this.discountRate = discountRate;
        this.discountAmount = discountAmount;
        this.minOrderAmount = minOrderAmount;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = 0;
        this.startDate = startDate;
        this.endDate = endDate;
        this.useQueue = false;
    }

    public Coupon(String name, String couponType, DiscountRate discountRate,
                  Money discountAmount, Money minOrderAmount, Integer totalQuantity,
                  LocalDateTime startDate, LocalDateTime endDate, boolean useQueue) {
        validateTotalQuantity(totalQuantity);
        this.name = name;
        this.couponType = couponType;
        this.discountRate = discountRate;
        this.discountAmount = discountAmount;
        this.minOrderAmount = minOrderAmount;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = 0;
        this.startDate = startDate;
        this.endDate = endDate;
        this.useQueue = useQueue;
    }

    private void validateTotalQuantity(Integer totalQuantity) {
        if (totalQuantity == null || totalQuantity < 0) {
            throw new IllegalArgumentException("총 발급 수량은 0 이상이어야 합니다.");
        }
    }

    public boolean isIssuable() {
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(startDate) && now.isBefore(endDate)
                && issuedQuantity < totalQuantity;
    }

    public boolean hasStock() {
        return issuedQuantity < totalQuantity;
    }

    public void increaseIssuedQuantity() {
        if (!isIssuable()) {
            throw new IllegalStateException("쿠폰을 발급할 수 없습니다.");
        }
        this.issuedQuantity++;
    }

    public void decreaseIssuedQuantity() {
        if (this.issuedQuantity <= 0) {
            throw new IllegalStateException("발급된 쿠폰이 없습니다.");
        }
        this.issuedQuantity--;
    }

    public Money calculateDiscount(Money orderAmount) {
        if (minOrderAmount != null && orderAmount.isLessThan(minOrderAmount)) {
            return Money.zero();
        }

        if (discountRate != null) {
            return discountRate.calculateDiscountAmount(orderAmount);
        } else if (discountAmount != null) {
            return discountAmount;
        }

        return Money.zero();
    }
}
