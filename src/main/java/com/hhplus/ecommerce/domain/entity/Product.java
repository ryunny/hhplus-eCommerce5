package com.hhplus.ecommerce.domain.entity;

import com.hhplus.ecommerce.domain.vo.Money;
import com.hhplus.ecommerce.domain.vo.Quantity;
import com.hhplus.ecommerce.domain.vo.Stock;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Category category;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Money price;

    @Column(nullable = false)
    private Stock stock;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Product(Category category, String name, String description, Money price, Stock stock) {
        this.category = category;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.createdAt = LocalDateTime.now();
    }

    public void decreaseStock(Quantity quantity) {
        if (quantity.getValue() <= 0) {
            throw new IllegalArgumentException("차감할 수량은 0보다 커야 합니다.");
        }
        this.stock = this.stock.decrease(quantity);
    }

    public void increaseStock(Quantity quantity) {
        if (quantity.getValue() <= 0) {
            throw new IllegalArgumentException("추가할 수량은 0보다 커야 합니다.");
        }
        this.stock = this.stock.increase(quantity);
    }

    public boolean hasSufficientStock(Quantity requiredQuantity) {
        return this.stock.isSufficientFor(requiredQuantity);
    }
}
