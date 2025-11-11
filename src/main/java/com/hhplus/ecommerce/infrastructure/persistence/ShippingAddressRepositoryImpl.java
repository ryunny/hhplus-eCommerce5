package com.hhplus.ecommerce.infrastructure.persistence;

import com.hhplus.ecommerce.domain.entity.ShippingAddress;
import com.hhplus.ecommerce.domain.repository.ShippingAddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 배송지 Repository 구현체
 * Infrastructure 계층의 어댑터 (Domain Repository → JPA Repository 연결)
 */
@Repository
@RequiredArgsConstructor
public class ShippingAddressRepositoryImpl implements ShippingAddressRepository {

    private final ShippingAddressJpaRepository shippingAddressJpaRepository;

    @Override
    public ShippingAddress save(ShippingAddress shippingAddress) {
        return shippingAddressJpaRepository.save(shippingAddress);
    }

    @Override
    public Optional<ShippingAddress> findById(Long id) {
        return shippingAddressJpaRepository.findById(id);
    }

    @Override
    public List<ShippingAddress> findByUserId(Long userId) {
        return shippingAddressJpaRepository.findByUserId(userId);
    }

    @Override
    public Optional<ShippingAddress> findByUserIdAndIsDefault(Long userId, boolean isDefault) {
        return shippingAddressJpaRepository.findByUserIdAndIsDefault(userId, isDefault);
    }

    @Override
    public void deleteById(Long id) {
        shippingAddressJpaRepository.deleteById(id);
    }
}
