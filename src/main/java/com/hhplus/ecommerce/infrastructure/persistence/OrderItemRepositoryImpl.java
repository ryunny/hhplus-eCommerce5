package com.hhplus.ecommerce.infrastructure.persistence;

import com.hhplus.ecommerce.domain.dto.ProductSalesDto;
import com.hhplus.ecommerce.domain.entity.OrderItem;
import com.hhplus.ecommerce.domain.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderItemRepositoryImpl implements OrderItemRepository {

    private final OrderItemJpaRepository orderItemJpaRepository;

    @Override
    public OrderItem save(OrderItem orderItem) {
        return orderItemJpaRepository.save(orderItem);
    }

    @Override
    public Optional<OrderItem> findById(Long id) {
        return orderItemJpaRepository.findById(id);
    }

    @Override
    public List<OrderItem> findByOrderId(Long orderId) {
        return orderItemJpaRepository.findByOrderId(orderId);
    }

    @Override
    public List<ProductSalesDto> getTopSellingProducts(LocalDateTime threeDaysAgo, int limit) {
        return orderItemJpaRepository.getTopSellingProducts(threeDaysAgo, limit);
    }

    @Override
    public void deleteById(Long id) {
        orderItemJpaRepository.deleteById(id);
    }
}
