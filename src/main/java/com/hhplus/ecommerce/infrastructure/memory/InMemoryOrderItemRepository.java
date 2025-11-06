package com.hhplus.ecommerce.infrastructure.memory;

import com.hhplus.ecommerce.domain.entity.OrderItem;
import com.hhplus.ecommerce.domain.entity.Payment;
import com.hhplus.ecommerce.domain.repository.OrderItemRepository;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class InMemoryOrderItemRepository implements OrderItemRepository {

    private final Map<Long, OrderItem> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public OrderItem save(OrderItem orderItem) {
        if (orderItem.getId() == null) {
            Long newId = idGenerator.getAndIncrement();
            setId(orderItem, newId);
        }
        store.put(orderItem.getId(), orderItem);
        return orderItem;
    }

    @Override
    public Optional<OrderItem> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<OrderItem> findByOrderId(Long orderId) {
        return store.values().stream()
                .filter(item -> item.getOrder().getId().equals(orderId))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }

    private void setId(OrderItem orderItem, Long id) {
        try {
            Field idField = OrderItem.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(orderItem, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set id", e);
        }
    }


    @Override
    public List<OrderItem> getOrderItemByTopFive() {
        LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);

        // 1️⃣ 최근 3일간의 주문 항목만 필터링하고
        // 2️⃣ 상품별로 총 판매 수량을 계산
        Map<Long, Integer> productSales = store.values().stream()
                .filter(item -> item.getCreatedAt().isAfter(threeDaysAgo))
                .collect(Collectors.groupingBy(
                        item -> item.getProduct().getId(),
                        Collectors.summingInt(item -> item.getQuantity().getValue())
                ));

        // 3️⃣ 판매량 기준으로 정렬 후 상위 5개 상품 ID 추출
        List<Long> topProductIds = productSales.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();

        // 4️⃣ 그 상품들의 OrderItem만 반환 (최근 3일 내)
        return store.values().stream()
                .filter(item -> topProductIds.contains(item.getProduct().getId()))
                .filter(item -> item.getCreatedAt().isAfter(threeDaysAgo))
                .toList();
    }
}
