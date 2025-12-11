package com.hhplus.ecommerce.domain.repository;

import com.hhplus.ecommerce.domain.entity.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);

    Optional<Product> findById(Long id);

    List<Product> findAll();

    List<Product> findAllById(Iterable<Long> ids);

    /**
     * 여러 상품 조회 시 Category도 함께 조회 (N+1 문제 해결)
     */
    List<Product> findAllByIdWithCategory(List<Long> ids);

    List<Product> findByCategoryId(Long categoryId);

    Optional<Product> findByIdWithLock(Long id);

    void deleteById(Long id);
}
