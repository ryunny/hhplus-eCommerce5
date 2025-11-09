package com.hhplus.ecommerce.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Order order;

    @Column(nullable = false)
    private Long refundAmount;

    @Column
    private String reason;

    @Column(nullable = false, length = 20)
    private String status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Refund(Order order, Long refundAmount, String reason, String status) {
        this.order = order;
        this.refundAmount = refundAmount;
        this.reason = reason;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public void updateStatus(String newStatus) {
        this.status = newStatus;
    }
}
