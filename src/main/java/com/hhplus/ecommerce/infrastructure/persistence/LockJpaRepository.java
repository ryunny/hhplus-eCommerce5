package com.hhplus.ecommerce.infrastructure.persistence;

import com.hhplus.ecommerce.domain.entity.DistributedLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * JPA Repository 인터페이스 (분산 락)
 * Infrastructure 계층에서 Spring Data JPA 의존성 처리
 */
public interface LockJpaRepository extends JpaRepository<DistributedLock, String> {

    /**
     * 락 조회 (비관적 락 사용 - SELECT FOR UPDATE)
     *
     * @return 락 엔티티 (Optional)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM DistributedLock l WHERE l.lockKey = :lockKey")
    Optional<DistributedLock> findByLockKeyWithLock(@Param("lockKey") String lockKey);

    /**
     * 만료된 락 삭제 (배치 정리용)
     *
     * @return 삭제된 락 개수
     */
    @Modifying
    @Query("DELETE FROM DistributedLock l WHERE l.expiresAt < :now")
    int deleteExpiredLocks(@Param("now") LocalDateTime now);

    /**
     * 락 키로 삭제 (락 해제)
     */
    void deleteByLockKey(String lockKey);
}
