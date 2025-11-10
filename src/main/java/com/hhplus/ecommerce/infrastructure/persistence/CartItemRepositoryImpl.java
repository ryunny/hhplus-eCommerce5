package com.hhplus.ecommerce.infrastructure.persistence;

import com.hhplus.ecommerce.domain.entity.CartItem;
import com.hhplus.ecommerce.domain.repository.CartItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CartItemRepositoryImpl implements CartItemRepository {

    private final CartItemJpaRepository cartItemJpaRepository;

    @Override
    public CartItem save(CartItem cartItem) {
        return cartItemJpaRepository.save(cartItem);
    }

    @Override
    public Optional<CartItem> findById(Long id) {
        return cartItemJpaRepository.findById(id);
    }

    @Override
    public List<CartItem> findByUserId(Long userId) {
        return cartItemJpaRepository.findByUserId(userId);
    }

    @Override
    public Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId) {
        return cartItemJpaRepository.findByUserIdAndProductId(userId, productId);
    }

    @Override
    public void deleteById(Long id) {
        cartItemJpaRepository.deleteById(id);
    }

    @Override
    public void deleteByUserId(Long userId) {
        cartItemJpaRepository.deleteByUserId(userId);
    }
}
