package com.hhplus.ecommerce.domain.entity;

import com.hhplus.ecommerce.domain.vo.Money;
import com.hhplus.ecommerce.domain.vo.Quantity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Product product;

    @Column(nullable = false)
    private Quantity quantity;

    @Column(nullable = false)
    private Money unitPrice;

    @Column(nullable = false)
    private Money subtotal;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public OrderItem(Order order, Product product, Quantity quantity,
                     Money unitPrice, Money subtotal) {
        this.order = order;
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
        this.createdAt = LocalDateTime.now();
    }

    public static OrderItem create(Order order, Product product, Quantity quantity) {
        Money unitPrice = product.getPrice();
        Money subtotal = quantity.multiply(unitPrice);
        return new OrderItem(order, product, quantity, unitPrice, subtotal);
    }
}
