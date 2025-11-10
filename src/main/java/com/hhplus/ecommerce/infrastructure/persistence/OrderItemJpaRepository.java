package com.hhplus.ecommerce.infrastructure.persistence;

import com.hhplus.ecommerce.domain.dto.ProductSalesDto;
import com.hhplus.ecommerce.domain.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderItemJpaRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);

    @Query("SELECT new com.hhplus.ecommerce.domain.dto.ProductSalesDto(oi.productId, p.name, SUM(oi.quantity), SUM(oi.totalPrice)) " +
           "FROM OrderItem oi " +
           "JOIN Product p ON oi.productId = p.id " +
           "JOIN Order o ON oi.orderId = o.id " +
           "WHERE o.createdAt >= :threeDaysAgo " +
           "GROUP BY oi.productId, p.name " +
           "ORDER BY SUM(oi.quantity) DESC")
    List<ProductSalesDto> getTopSellingProducts(@Param("threeDaysAgo") LocalDateTime threeDaysAgo, @Param("limit") int limit);
}
