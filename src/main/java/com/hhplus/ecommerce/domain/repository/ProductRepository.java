package com.hhplus.ecommerce.domain.repository;

import com.hhplus.ecommerce.domain.entity.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);

    Optional<Product> findById(Long id);

    List<Product> findAll();

    List<Product> findAllById(Iterable<Long> ids);

    List<Product> findByCategoryId(Long categoryId);

    Optional<Product> findByIdWithLock(Long id);

    void deleteById(Long id);
}
