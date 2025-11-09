package com.hhplus.ecommerce.domain.entity;

import com.hhplus.ecommerce.domain.enums.OrderStatus;
import com.hhplus.ecommerce.domain.vo.Money;
import com.hhplus.ecommerce.domain.vo.Phone;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_coupon_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private UserCoupon userCoupon;

    @Column(nullable = false, length = 100)
    private String recipientName;

    @Column(nullable = false)
    private String shippingAddress;

    @Column(nullable = false, length = 20)
    private Phone shippingPhone;

    @Column(nullable = false)
    private Money totalAmount;

    @Column(nullable = false)
    private Money discountAmount;

    @Column(nullable = false)
    private Money finalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Order(User user, UserCoupon userCoupon, String recipientName,
                 String shippingAddress, Phone shippingPhone, Money totalAmount,
                 Money discountAmount, Money finalAmount, OrderStatus status) {
        this.user = user;
        this.userCoupon = userCoupon;
        this.recipientName = recipientName;
        this.shippingAddress = shippingAddress;
        this.shippingPhone = shippingPhone;
        this.totalAmount = totalAmount;
        this.discountAmount = discountAmount != null ? discountAmount : Money.zero();
        this.finalAmount = finalAmount;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
    }
}
