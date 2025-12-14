package com.hhplus.ecommerce.domain.entity;

import com.hhplus.ecommerce.domain.enums.OrderStatus;
import com.hhplus.ecommerce.domain.vo.Money;
import com.hhplus.ecommerce.domain.vo.OrderStepStatus;
import com.hhplus.ecommerce.domain.vo.Phone;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 36)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_coupon_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private UserCoupon userCoupon;

    // 배송지 정보 - 주문 시점의 스냅샷 저장 (이력 데이터 보존)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipping_address_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private ShippingAddress shippingAddress;

    @Column(nullable = false, length = 100)
    private String recipientName;

    @Column(nullable = false)
    private String address;

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

    // Saga 패턴: 각 단계의 상태 추적
    @Embedded
    private OrderStepStatus stepStatus = new OrderStepStatus();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Order(User user, UserCoupon userCoupon, ShippingAddress shippingAddress,
                 String recipientName, String address, Phone shippingPhone, Money totalAmount,
                 Money discountAmount, Money finalAmount, OrderStatus status) {
        this.user = user;
        this.userCoupon = userCoupon;
        this.shippingAddress = shippingAddress;
        this.recipientName = recipientName;
        this.address = address;
        this.shippingPhone = shippingPhone;
        this.totalAmount = totalAmount;
        this.discountAmount = discountAmount != null ? discountAmount : Money.zero();
        this.finalAmount = finalAmount;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public static Order createWithShippingAddress(User user, UserCoupon userCoupon,
                                                   ShippingAddress shippingAddress,
                                                   Money totalAmount, Money discountAmount,
                                                   Money finalAmount, OrderStatus status) {
        return new Order(user, userCoupon, shippingAddress,
                shippingAddress.getRecipientName(),
                shippingAddress.getAddress(),
                shippingAddress.getPhone(),
                totalAmount, discountAmount, finalAmount, status);
    }

    @PrePersist
    private void generateOrderNumber() {
        if (this.orderNumber == null) {
            this.orderNumber = UUID.randomUUID().toString();
        }
    }

    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
    }

    public void markAsFailed(String reason) {
        this.status = OrderStatus.FAILED;
    }

    public void confirm() {
        this.status = OrderStatus.CONFIRMED;
    }
}
