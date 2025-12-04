package com.hhplus.ecommerce.infrastructure.persistence;

import com.hhplus.ecommerce.domain.entity.DistributedLock;
import com.hhplus.ecommerce.domain.repository.LockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * LockRepository 구현체 (Adapter)
 * Domain Repository 인터페이스를 Infrastructure의 JpaRepository로 연결
 */
@Repository
@RequiredArgsConstructor
public class LockRepositoryImpl implements LockRepository {

    private final LockJpaRepository lockJpaRepository;

    @Override
    public DistributedLock save(DistributedLock lock) {
        return lockJpaRepository.save(lock);
    }

    @Override
    public Optional<DistributedLock> findByLockKeyWithLock(String lockKey) {
        return lockJpaRepository.findByLockKeyWithLock(lockKey);
    }

    @Override
    public void deleteByLockKey(String lockKey) {
        lockJpaRepository.deleteByLockKey(lockKey);
    }

    @Override
    public int deleteExpiredLocks(LocalDateTime now) {
        return lockJpaRepository.deleteExpiredLocks(now);
    }
}
