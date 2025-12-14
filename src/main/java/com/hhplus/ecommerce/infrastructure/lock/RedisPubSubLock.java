package com.hhplus.ecommerce.infrastructure.lock;

import com.hhplus.ecommerce.domain.entity.DistributedLock;
import com.hhplus.ecommerce.domain.repository.LockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis Pub/Sub Lock (Redisson)
 *
 * Redis 정상: Redis 분산 락 사용
 * Redis 장애: DB 비관적 락으로 Fallback
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisPubSubLock {

    private final RedissonClient redissonClient;
    private final LockRepository lockRepository;
    private static final String LOCK_PREFIX = "lock:pubsub:";

    public void lock(String key) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + key);
        try {
            lock.lock(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to acquire lock: {}", key, e);
            throw new RuntimeException("Lock acquisition failed", e);
        }
    }

    public boolean tryLock(String key, long waitTime, TimeUnit timeUnit) {
        try {
            // 1. Redis 락 시도
            RLock lock = redissonClient.getLock(LOCK_PREFIX + key);
            return lock.tryLock(waitTime, 3, timeUnit);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock interrupted: {}", key, e);
            return false;

        } catch (Exception e) {
            // 2. Redis 장애 → DB 락으로 Fallback
            log.warn("Redis unavailable, fallback to DB lock: key={}, error={}", key, e.getMessage());
            return tryDbLock(key, waitTime, timeUnit);
        }
    }

    /**
     * DB 락 시도 (Redis Fallback)
     *
     * Spin-lock 방식으로 DB 비관적 락 획득 시도
     *
     * @param key 락 키
     * @param waitTime 최대 대기 시간
     * @param timeUnit 시간 단위
     * @return 락 획득 성공 여부
     */
    private boolean tryDbLock(String key, long waitTime, TimeUnit timeUnit) {
        long endTime = System.currentTimeMillis() + timeUnit.toMillis(waitTime);

        while (System.currentTimeMillis() < endTime) {
            try {
                // DB 락 획득 시도
                acquireDbLock(key, 3); // 3초 TTL
                log.info("DB lock acquired: {}", key);
                return true;

            } catch (DataIntegrityViolationException e) {
                // 이미 락이 존재함 (다른 스레드가 획득) → 재시도
                try {
                    Thread.sleep(100); // 100ms 대기 후 재시도
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }

            } catch (Exception e) {
                log.error("DB lock failed: {}", key, e);
                return false;
            }
        }

        // 타임아웃
        log.warn("DB lock timeout: {}", key);
        return false;
    }

    /**
     * DB 락 획득 (새 트랜잭션에서 실행)
     *
     * @param key 락 키
     * @param ttlSeconds TTL (초)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void acquireDbLock(String key, long ttlSeconds) {
        // 만료된 락 먼저 정리
        Optional<DistributedLock> existing = lockRepository.findByLockKeyWithLock(key);
        if (existing.isPresent() && existing.get().isExpired()) {
            lockRepository.deleteByLockKey(key);
        }

        // 새 락 생성
        DistributedLock lock = DistributedLock.create(key, ttlSeconds);
        lockRepository.save(lock);
    }

    public void unlock(String key) {
        try {
            // 1. Redis 락 해제 시도
            RLock lock = redissonClient.getLock(LOCK_PREFIX + key);
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }

        } catch (Exception e) {
            // 2. Redis 장애 → DB 락 해제
            log.warn("Redis unavailable, unlocking DB lock: key={}, error={}", key, e.getMessage());
            releaseDbLock(key);
        }
    }

    /**
     * DB 락 해제 (새 트랜잭션에서 실행)
     *
     * @param key 락 키
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void releaseDbLock(String key) {
        Optional<DistributedLock> existing = lockRepository.findByLockKeyWithLock(key);
        if (existing.isPresent() && existing.get().isHeldByCurrentThread()) {
            lockRepository.deleteByLockKey(key);
            log.info("DB lock released: {}", key);
        }
    }
}
