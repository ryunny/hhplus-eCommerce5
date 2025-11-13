package com.hhplus.ecommerce.infrastructure.persistence;

import com.hhplus.ecommerce.domain.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserId(Long userId);

    /**
     * N+1 문제 해결: User를 Fetch Join으로 한 번에 조회
     */
    @Query("SELECT o FROM Order o JOIN FETCH o.user WHERE o.user.publicId = :publicId")
    List<Order> findByUserPublicId(@Param("publicId") String publicId);

    Optional<Order> findByOrderNumber(String orderNumber);
}
