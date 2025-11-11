package com.hhplus.ecommerce.infrastructure.persistence;

import com.hhplus.ecommerce.domain.entity.ShippingAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 배송지 JPA Repository
 * Infrastructure 계층의 JPA 구현체
 */
public interface ShippingAddressJpaRepository extends JpaRepository<ShippingAddress, Long> {
    List<ShippingAddress> findByUserId(Long userId);

    Optional<ShippingAddress> findByUserIdAndIsDefault(Long userId, boolean isDefault);
}
