package com.hhplus.ecommerce.domain.repository;

import com.hhplus.ecommerce.domain.dto.ProductSalesDto;
import com.hhplus.ecommerce.domain.entity.OrderItem;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderItemRepository {
    OrderItem save(OrderItem orderItem);

    Optional<OrderItem> findById(Long id);

    List<OrderItem> findByOrderId(Long orderId);

    /**
     * 인기 상품 Top N 조회
     * 최근 3일간 판매량 기준 상위 N개 상품의 통계를 반환합니다.
     */
    List<ProductSalesDto> getTopSellingProducts(LocalDateTime threeDaysAgo, int limit);

    void deleteById(Long id);
}
