package com.hhplus.ecommerce.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 분산 락 엔티티 (Redis Fallback용 DB Lock)
 *
 * Redis 장애 시 DB 비관적 락으로 Fallback하기 위한 테이블
 * - Redis 정상: 이 테이블 사용 안됨
 * - Redis 장애: 이 테이블로 락 관리
 */
@Entity
@Table(name = "distributed_locks",
        indexes = @Index(name = "idx_expires_at", columnList = "expiresAt"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DistributedLock {

    @Id
    @Column(name = "lock_key", length = 255)
    private String lockKey;

    @Column(name = "acquired_at", nullable = false)
    private LocalDateTime acquiredAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "thread_id", length = 100)
    private String threadId;

    /**
     * 락 생성
     *
     * @param lockKey 락 키
     * @param ttlSeconds 만료 시간 (초)
     * @return DistributedLock 인스턴스
     */
    public static DistributedLock create(String lockKey, long ttlSeconds) {
        DistributedLock lock = new DistributedLock();
        lock.lockKey = lockKey;
        lock.acquiredAt = LocalDateTime.now();
        lock.expiresAt = LocalDateTime.now().plusSeconds(ttlSeconds);
        lock.threadId = Thread.currentThread().getName() + "-" + Thread.currentThread().getId();
        return lock;
    }

    /**
     * 락이 만료되었는지 확인
     *
     * @return 만료 여부
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 현재 스레드가 이 락을 소유하는지 확인
     *
     * @return 소유 여부
     */
    public boolean isHeldByCurrentThread() {
        String currentThreadId = Thread.currentThread().getName() + "-" + Thread.currentThread().getId();
        return currentThreadId.equals(threadId);
    }
}
