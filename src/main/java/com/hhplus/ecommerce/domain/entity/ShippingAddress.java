package com.hhplus.ecommerce.domain.entity;

import com.hhplus.ecommerce.domain.vo.Phone;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 배송지 정보 엔티티
 * 사용자의 배송지 주소록을 관리합니다.
 */
@Entity
@Table(name = "shipping_addresses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShippingAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User user;

    @Column(nullable = false, length = 50)
    private String addressName; // 예: "집", "회사", "부모님 댁"

    @Column(nullable = false, length = 100)
    private String recipientName;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false, length = 20)
    private Phone phone;

    @Column(nullable = false)
    private boolean isDefault = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ShippingAddress(User user, String addressName, String recipientName,
                          String address, Phone phone, boolean isDefault) {
        this.user = user;
        this.addressName = addressName;
        this.recipientName = recipientName;
        this.address = address;
        this.phone = phone;
        this.isDefault = isDefault;
        this.createdAt = LocalDateTime.now();
    }

    public void updateDefaultStatus(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public void update(String addressName, String recipientName, String address, Phone phone) {
        this.addressName = addressName;
        this.recipientName = recipientName;
        this.address = address;
        this.phone = phone;
    }
}
