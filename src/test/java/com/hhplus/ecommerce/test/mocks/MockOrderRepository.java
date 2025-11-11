package com.hhplus.ecommerce.test.mocks;

import com.hhplus.ecommerce.domain.entity.Order;
import com.hhplus.ecommerce.domain.entity.User;
import com.hhplus.ecommerce.domain.entity.UserCoupon;
import com.hhplus.ecommerce.domain.enums.OrderStatus;
import com.hhplus.ecommerce.domain.repository.OrderRepository;
import com.hhplus.ecommerce.domain.vo.Money;
import com.hhplus.ecommerce.domain.vo.Phone;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MockOrderRepository implements OrderRepository {

    private final Map<Long, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public MockOrderRepository() {
        // 초기 테스트 데이터는 별도로 추가 가능
        idGenerator.set(1);
    }

    @Override
    public Order save(Order order) {
        if (order.getId() == null) {
            setId(order, idGenerator.getAndIncrement());
            setCreatedAt(order, LocalDateTime.now());
        }
        orders.put(order.getId(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(orders.get(id));
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        return orders.values().stream()
                .filter(order -> order.getUser().getId().equals(userId))
                .toList();
    }

    @Override
    public List<Order> findByUserPublicId(String publicId) {
        return orders.values().stream()
                .filter(order -> order.getUser().getPublicId().equals(publicId))
                .toList();
    }

    @Override
    public Optional<Order> findByOrderNumber(String orderNumber) {
        return orders.values().stream()
                .filter(order -> order.getOrderNumber().equals(orderNumber))
                .findFirst();
    }

    @Override
    public void deleteById(Long id) {
        orders.remove(id);
    }

    // Helper methods for creating test data
    public Order createOrder(Long id, User user, UserCoupon userCoupon,
                            String recipientName, String address,
                            String phone, Long totalAmount,
                            Long discountAmount, Long finalAmount,
                            OrderStatus status) {
        Order order = new Order(user, userCoupon, null,
                recipientName, address, new Phone(phone),
                new Money(totalAmount), new Money(discountAmount), new Money(finalAmount), status);
        setId(order, id);
        setCreatedAt(order, LocalDateTime.now());
        return order;
    }

    private void setId(Order order, Long id) {
        try {
            java.lang.reflect.Field idField = Order.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(order, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set id", e);
        }
    }

    private void setCreatedAt(Order order, LocalDateTime createdAt) {
        try {
            java.lang.reflect.Field createdAtField = Order.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(order, createdAt);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set createdAt", e);
        }
    }

    // Test helper methods
    public void clear() {
        orders.clear();
        idGenerator.set(1);
    }

    public int size() {
        return orders.size();
    }
}
