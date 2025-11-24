package com.hhplus.ecommerce.infrastructure.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisPubSubLock {

    private final RedissonClient redissonClient;
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
        RLock lock = redissonClient.getLock(LOCK_PREFIX + key);
        try {
            return lock.tryLock(waitTime, 3, timeUnit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock interrupted: {}", key, e);
            return false;
        }
    }

    public void unlock(String key) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + key);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
