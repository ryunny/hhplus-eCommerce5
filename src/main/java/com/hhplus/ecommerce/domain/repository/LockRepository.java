package com.hhplus.ecommerce.domain.repository;

import com.hhplus.ecommerce.domain.entity.DistributedLock;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 분산 락 Repository 인터페이스
 * Domain 계층에서 정의하는 순수 인터페이스 (프레임워크 독립적)
 *
 * Redis 장애 시 DB 락으로 Fallback하기 위한 Repository
 */
public interface LockRepository {
    /**
     * 락 저장 (락 획득)
     *
     * @param lock 락 엔티티
     * @return 저장된 락
     */
    DistributedLock save(DistributedLock lock);

    /**
     * 락 조회 (비관적 락 사용)
     *
     * @param lockKey 락 키
     * @return 락 엔티티 (Optional)
     */
    Optional<DistributedLock> findByLockKeyWithLock(String lockKey);

    /**
     * 락 삭제 (락 해제)
     *
     * @param lockKey 락 키
     */
    void deleteByLockKey(String lockKey);

    /**
     * 만료된 락 삭제 (배치 정리용)
     *
     * @param now 현재 시각
     * @return 삭제된 락 개수
     */
    int deleteExpiredLocks(LocalDateTime now);
}
