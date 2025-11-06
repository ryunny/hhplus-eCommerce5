package com.hhplus.ecommerce.infrastructure.memory;

import com.hhplus.ecommerce.domain.entity.Product;
import com.hhplus.ecommerce.domain.repository.ProductRepository;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryProductRepository implements ProductRepository {

    private final Map<Long, Product> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Product save(Product product) {
        if (product.getId() == null) {
            // AUTO_INCREMENT 시뮬레이션: 새로운 ID 생성
            Long newId = idGenerator.getAndIncrement();
            setId(product, newId);
        }
        store.put(product.getId(), product);
        return product;
    }

    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Product> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<Product> findByCategoryId(Long categoryId) {
        return store.values().stream()
                .filter(product -> product.getCategory().getId().equals(categoryId))
                .toList();
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }

    // Reflection을 사용하여 private id 필드 설정 (테스트용)
    private void setId(Product product, Long id) {
        try {
            Field idField = Product.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(product, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("ID 설정 실패", e);
        }
    }
}
