package com.hhplus.ecommerce.infrastructure.persistence;

import com.hhplus.ecommerce.domain.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {
    List<Product> findByCategoryId(Long categoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);

    /**
     * 여러 상품 조회 시 Category도 함께 조회 (N+1 문제 해결)
     */
    @Query("SELECT p FROM Product p JOIN FETCH p.category WHERE p.id IN :ids")
    List<Product> findAllByIdWithCategory(@Param("ids") List<Long> ids);

    /**
     * 조건부 업데이트: 재고가 충분할 때만 차감
     * @return 업데이트된 행의 개수 (1: 성공, 0: 실패)
     */
    @Modifying
    @Query(value = "UPDATE products SET stock = stock - :quantity " +
           "WHERE id = :id AND stock >= :quantity", nativeQuery = true)
    int decreaseStockConditionally(@Param("id") Long id, @Param("quantity") int quantity);
}
