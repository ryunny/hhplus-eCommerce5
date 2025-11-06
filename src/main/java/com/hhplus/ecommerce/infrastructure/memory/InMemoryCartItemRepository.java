package com.hhplus.ecommerce.infrastructure.memory;

import com.hhplus.ecommerce.domain.entity.CartItem;
import com.hhplus.ecommerce.domain.repository.CartItemRepository;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryCartItemRepository implements CartItemRepository {

    private final Map<Long, CartItem> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public CartItem save(CartItem cartItem) {
        if (cartItem.getId() == null) {
            Long newId = idGenerator.getAndIncrement();
            setId(cartItem, newId);
        }
        store.put(cartItem.getId(), cartItem);
        return cartItem;
    }

    @Override
    public Optional<CartItem> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<CartItem> findByUserId(Long userId) {
        return store.values().stream()
                .filter(item -> item.getUser().getId().equals(userId))
                .toList();
    }

    @Override
    public Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId) {
        return store.values().stream()
                .filter(item -> item.getUser().getId().equals(userId)
                        && item.getProduct().getId().equals(productId))
                .findFirst();
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }

    @Override
    public void deleteByUserId(Long userId) {
        List<Long> idsToDelete = store.values().stream()
                .filter(item -> item.getUser().getId().equals(userId))
                .map(CartItem::getId)
                .toList();

        idsToDelete.forEach(store::remove);
    }

    private void setId(CartItem cartItem, Long id) {
        try {
            Field idField = CartItem.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(cartItem, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set id", e);
        }
    }
}
