package com.hhplus.ecommerce.infrastructure.memory;

import com.hhplus.ecommerce.domain.entity.Refund;
import com.hhplus.ecommerce.domain.repository.RefundRepository;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class InMemoryRefundRepository implements RefundRepository {

    private final Map<Long, Refund> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Refund save(Refund refund) {
        if (refund.getId() == null) {
            Long newId = idGenerator.getAndIncrement();
            setId(refund, newId);
        }
        store.put(refund.getId(), refund);
        return refund;
    }

    @Override
    public Optional<Refund> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Refund> findByOrderId(Long orderId) {
        return store.values().stream()
                .filter(refund -> refund.getOrder().getId().equals(orderId))
                .collect(Collectors.toList());
    }

    @Override
    public List<Refund> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }

    private void setId(Refund refund, Long id) {
        try {
            Field idField = Refund.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(refund, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set id", e);
        }
    }
}
