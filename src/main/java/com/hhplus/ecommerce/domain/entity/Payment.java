package com.hhplus.ecommerce.domain.entity;

import com.hhplus.ecommerce.domain.enums.DataTransmissionStatus;
import com.hhplus.ecommerce.domain.enums.PaymentStatus;
import com.hhplus.ecommerce.domain.vo.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 36)
    private String paymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Order order;

    @Column(nullable = false)
    private Money paidAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DataTransmissionStatus dataTransmissionStatus;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Payment(Order order, Money paidAmount, PaymentStatus status,
                   DataTransmissionStatus dataTransmissionStatus) {
        this.order = order;
        this.paidAmount = paidAmount;
        this.status = status;
        this.dataTransmissionStatus = dataTransmissionStatus;
        this.createdAt = LocalDateTime.now();
    }

    public void updateStatus(PaymentStatus newStatus) {
        this.status = newStatus;
    }

    @PrePersist
    private void generatePaymentId() {
        if (this.paymentId == null) {
            this.paymentId = UUID.randomUUID().toString();
        }
    }

    public void updateDataTransmissionStatus(DataTransmissionStatus newStatus) {
        this.dataTransmissionStatus = newStatus;
    }
}
