package com.hhplus.ecommerce.domain.repository;

import com.hhplus.ecommerce.domain.dto.ProductSalesDto;
import com.hhplus.ecommerce.domain.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);

    /**
     * 인기 상품 Top N 조회 (개선된 메서드)
     * 최근 3일간 판매량 기준 상위 N개 상품의 통계를 반환합니다.
     *
     * @param threeDaysAgo 3일 전 날짜
     * @param limit 조회할 상품 개수
     * @return 상품 판매 통계 DTO 목록
     */
    @Query("SELECT new com.hhplus.ecommerce.domain.dto.ProductSalesDto(oi.productId, p.name, SUM(oi.quantity), SUM(oi.totalPrice)) " +
           "FROM OrderItem oi " +
           "JOIN Product p ON oi.productId = p.id " +
           "JOIN Order o ON oi.orderId = o.id " +
           "WHERE o.createdAt >= :threeDaysAgo " +
           "GROUP BY oi.productId, p.name " +
           "ORDER BY SUM(oi.quantity) DESC")
    List<ProductSalesDto> getTopSellingProducts(@Param("threeDaysAgo") LocalDateTime threeDaysAgo, @Param("limit") int limit);
}
