package com.hhplus.ecommerce.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "popular_products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PopularProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Integer rank;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, length = 200)
    private String productName;

    @Column(nullable = false)
    private Long price;

    @Column(nullable = false)
    private Integer totalSalesQuantity;

    @Column(nullable = false, length = 100)
    private String categoryName;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public PopularProduct(Integer rank, Long productId, String productName, Long price,
                         Integer totalSalesQuantity, String categoryName) {
        this.rank = rank;
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.totalSalesQuantity = totalSalesQuantity;
        this.categoryName = categoryName;
        this.updatedAt = LocalDateTime.now();
    }

    public void update(Long productId, String productName, Long price,
                      Integer totalSalesQuantity, String categoryName) {
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.totalSalesQuantity = totalSalesQuantity;
        this.categoryName = categoryName;
        this.updatedAt = LocalDateTime.now();
    }
}
