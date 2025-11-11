package com.hhplus.ecommerce.domain.repository;

import com.hhplus.ecommerce.domain.entity.ShippingAddress;

import java.util.List;
import java.util.Optional;

/**
 * 배송지 Repository 인터페이스
 * Domain 계층에서 정의하는 순수 인터페이스 (프레임워크 독립적)
 */
public interface ShippingAddressRepository {
    ShippingAddress save(ShippingAddress shippingAddress);

    Optional<ShippingAddress> findById(Long id);

    List<ShippingAddress> findByUserId(Long userId);

    Optional<ShippingAddress> findByUserIdAndIsDefault(Long userId, boolean isDefault);

    void deleteById(Long id);
}
