package com.hhplus.ecommerce.domain.entity;

import com.hhplus.ecommerce.domain.vo.Quantity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "cart_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Product product;

    @Column(nullable = false)
    private Quantity quantity;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public CartItem(User user, Product product, Quantity quantity) {
        this.user = user;
        this.product = product;
        this.quantity = quantity;
        this.createdAt = LocalDateTime.now();
    }

    public void updateQuantity(Quantity newQuantity) {
        this.quantity = newQuantity;
    }

    public void increaseQuantity(Quantity amount) {
        this.quantity = this.quantity.add(amount);
    }
}
