package com.hhplus.ecommerce.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    public static DistributedLock create(String lockKey, long ttlSeconds) {
        DistributedLock lock = new DistributedLock();
        lock.lockKey = lockKey;
        lock.acquiredAt = LocalDateTime.now();
        lock.expiresAt = LocalDateTime.now().plusSeconds(ttlSeconds);
        lock.threadId = Thread.currentThread().getName() + "-" + Thread.currentThread().getId();
        return lock;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isHeldByCurrentThread() {
        String currentThreadId = Thread.currentThread().getName() + "-" + Thread.currentThread().getId();
        return currentThreadId.equals(threadId);
    }
}
