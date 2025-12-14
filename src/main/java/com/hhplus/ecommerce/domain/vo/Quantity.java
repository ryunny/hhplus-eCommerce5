package com.hhplus.ecommerce.domain.vo;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class Quantity implements Serializable {

    private Integer value;

    public Quantity(Integer value) {
        validateValue(value);
        this.value = value;
    }

    private void validateValue(Integer value) {
        if (value == null) {
            throw new IllegalArgumentException("수량은 null일 수 없습니다.");
        }
        if (value < 0) {
            throw new IllegalArgumentException("수량은 0 이상이어야 합니다.");
        }
    }

    public static Quantity of(Integer value) {
        return new Quantity(value);
    }

    public Quantity add(Quantity other) {
        return new Quantity(this.value + other.value);
    }

    public Money multiply(Money unitPrice) {
        return new Money(unitPrice.getAmount() * this.value);
    }

    @Override
    public String toString() {
        return value + "개";
    }
}
