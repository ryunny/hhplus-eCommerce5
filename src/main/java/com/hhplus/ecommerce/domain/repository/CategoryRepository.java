package com.hhplus.ecommerce.domain.repository;

import com.hhplus.ecommerce.domain.entity.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {
    Category save(Category category);

    Optional<Category> findById(Long id);

    List<Category> findAll();

    void deleteById(Long id);
}
