package com.hhplus.ecommerce.test.mocks;

import com.hhplus.ecommerce.domain.entity.Category;
import com.hhplus.ecommerce.domain.entity.Product;
import com.hhplus.ecommerce.domain.repository.ProductRepository;
import com.hhplus.ecommerce.domain.vo.Money;
import com.hhplus.ecommerce.domain.vo.Stock;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MockProductRepository implements ProductRepository {

    private final Map<Long, Product> products = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public MockProductRepository() {
        // 초기 데이터 - 테스트용 카테고리 생성
        Category electronics = createCategory(1L, "전자제품");

        // 테스트용 상품 생성
        Product laptop = createProduct(1L, electronics, "노트북", "고성능 노트북", 890000L, 10);
        Product mouse = createProduct(2L, electronics, "마우스", "무선 마우스", 35000L, 50);
        Product keyboard = createProduct(3L, electronics, "키보드", "기계식 키보드", 120000L, 30);

        products.put(laptop.getId(), laptop);
        products.put(mouse.getId(), mouse);
        products.put(keyboard.getId(), keyboard);

        idGenerator.set(4);
    }

    @Override
    public Product save(Product product) {
        if (product.getId() == null) {
            setId(product, idGenerator.getAndIncrement());
        }
        products.put(product.getId(), product);
        return product;
    }

    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(products.get(id));
    }

    @Override
    public List<Product> findAll() {
        return new ArrayList<>(products.values());
    }

    @Override
    public List<Product> findAllById(Iterable<Long> ids) {
        List<Product> result = new ArrayList<>();
        for (Long id : ids) {
            findById(id).ifPresent(result::add);
        }
        return result;
    }

    @Override
    public List<Product> findAllByIdWithCategory(List<Long> ids) {
        // Mock에서는 일반 조회와 동일하게 동작 (JOIN FETCH는 JPA 기능)
        return findAllById(ids);
    }

    @Override
    public List<Product> findByCategoryId(Long categoryId) {
        return products.values().stream()
                .filter(product -> product.getCategory().getId().equals(categoryId))
                .toList();
    }

    @Override
    public Optional<Product> findByIdWithLock(Long id) {
        // Mock에서는 락 없이 일반 조회와 동일하게 동작
        return findById(id);
    }

    @Override
    public void deleteById(Long id) {
        products.remove(id);
    }

    // Helper methods for creating test data
    private Category createCategory(Long id, String name) {
        Category category = new Category(name);
        setId(category, id);
        return category;
    }

    private Product createProduct(Long id, Category category, String name, String description, Long price, int stock) {
        Product product = new Product(category, name, description, new Money(price), new Stock(stock));
        setId(product, id);
        return product;
    }

    private void setId(Object entity, Long id) {
        try {
            java.lang.reflect.Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set id", e);
        }
    }

    // Test helper methods
    public void clear() {
        products.clear();
        idGenerator.set(1);
    }

    public int size() {
        return products.size();
    }
}
