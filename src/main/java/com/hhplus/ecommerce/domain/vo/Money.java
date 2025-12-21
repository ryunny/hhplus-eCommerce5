package com.hhplus.ecommerce.domain.vo;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class Money implements Serializable {

    private Long amount;

    public Money(Long amount) {
        validateAmount(amount);
        this.amount = amount;
    }

    private void validateAmount(Long amount) {
        if (amount == null) {
            throw new IllegalArgumentException("금액은 null일 수 없습니다.");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("금액은 0 이상이어야 합니다.");
        }
    }

    public Money add(Money other) {
        return new Money(this.amount + other.amount);
    }

    public Money subtract(Money other) {
        return new Money(this.amount - other.amount);
    }

    public Money multiply(int multiplier) {
        return new Money(this.amount * multiplier);
    }

    public boolean isGreaterThan(Money other) {
        return this.amount > other.amount;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        return this.amount >= other.amount;
    }

    public boolean isLessThan(Money other) {
        return this.amount < other.amount;
    }

    public static Money of(Long amount) {
        return new Money(amount);
    }

    public static Money zero() {
        return new Money(0L);
    }

    @Override
    public String toString() {
        return amount + "원";
    }
}
