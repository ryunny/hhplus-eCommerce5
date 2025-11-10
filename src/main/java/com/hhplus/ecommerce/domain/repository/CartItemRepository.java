package com.hhplus.ecommerce.domain.repository;

import com.hhplus.ecommerce.domain.entity.CartItem;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository {
    CartItem save(CartItem cartItem);

    Optional<CartItem> findById(Long id);

    List<CartItem> findByUserId(Long userId);

    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);

    void deleteById(Long id);

    void deleteByUserId(Long userId);
}
