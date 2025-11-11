package com.hhplus.ecommerce.test.mocks;

import com.hhplus.ecommerce.domain.entity.ShippingAddress;
import com.hhplus.ecommerce.domain.repository.ShippingAddressRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MockShippingAddressRepository implements ShippingAddressRepository {

    private final Map<Long, ShippingAddress> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public ShippingAddress save(ShippingAddress shippingAddress) {
        if (shippingAddress.getId() == null) {
            setId(shippingAddress, idGenerator.getAndIncrement());
        }
        store.put(shippingAddress.getId(), shippingAddress);
        return shippingAddress;
    }

    @Override
    public Optional<ShippingAddress> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<ShippingAddress> findByUserId(Long userId) {
        return store.values().stream()
                .filter(addr -> addr.getUser().getId().equals(userId))
                .toList();
    }

    @Override
    public Optional<ShippingAddress> findByUserIdAndIsDefault(Long userId, boolean isDefault) {
        return store.values().stream()
                .filter(addr -> addr.getUser().getId().equals(userId) && addr.isDefault() == isDefault)
                .findFirst();
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }

    private void setId(ShippingAddress shippingAddress, Long id) {
        try {
            java.lang.reflect.Field idField = ShippingAddress.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(shippingAddress, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set id", e);
        }
    }

    public void clear() {
        store.clear();
        idGenerator.set(1);
    }

    public int size() {
        return store.size();
    }
}
