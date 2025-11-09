package com.hhplus.ecommerce.domain.entity;

import com.hhplus.ecommerce.domain.enums.CouponQueueStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupon_queues")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Coupon coupon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponQueueStatus status;

    @Column(nullable = false)
    private Integer queuePosition;

    @Column
    private LocalDateTime processedAt;

    @Column
    private String failedReason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public CouponQueue(User user, Coupon coupon, CouponQueueStatus status, Integer queuePosition) {
        this.user = user;
        this.coupon = coupon;
        this.status = status;
        this.queuePosition = queuePosition;
        this.createdAt = LocalDateTime.now();
    }

    public void updateStatus(CouponQueueStatus newStatus) {
        this.status = newStatus;
        if (newStatus == CouponQueueStatus.COMPLETED || newStatus == CouponQueueStatus.FAILED) {
            this.processedAt = LocalDateTime.now();
        }
    }

    public void setFailedReason(String reason) {
        this.failedReason = reason;
        this.status = CouponQueueStatus.FAILED;
        this.processedAt = LocalDateTime.now();
    }

    public void updateQueuePosition(Integer newPosition) {
        this.queuePosition = newPosition;
    }
}
