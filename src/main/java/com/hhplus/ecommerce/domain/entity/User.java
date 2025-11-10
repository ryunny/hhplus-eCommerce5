package com.hhplus.ecommerce.domain.entity;

import com.hhplus.ecommerce.domain.vo.Email;
import com.hhplus.ecommerce.domain.vo.Money;
import com.hhplus.ecommerce.domain.vo.Phone;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 36)
    private String publicId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true)
    private Email email;

    @Column(nullable = false, length = 20)
    private Phone phone;

    @Column(nullable = false)
    private Money balance;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public User(String name, Email email, Phone phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.balance = Money.zero();
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    private void generatePublicId() {
        if (this.publicId == null) {
            this.publicId = UUID.randomUUID().toString();
        }
    }

    public void chargeBalance(Money amount) {
        if (amount.getAmount() <= 0) {
            throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다.");
        }
        this.balance = this.balance.add(amount);
    }

    public void deductBalance(Money amount) {
        if (amount.getAmount() <= 0) {
            throw new IllegalArgumentException("차감 금액은 0보다 커야 합니다.");
        }
        if (this.balance.isLessThan(amount)) {
            throw new IllegalStateException("잔액이 부족합니다.");
        }
        this.balance = this.balance.subtract(amount);
    }

    public boolean hasEnoughBalance(Money required) {
        return this.balance.isGreaterThanOrEqual(required);
    }
}
