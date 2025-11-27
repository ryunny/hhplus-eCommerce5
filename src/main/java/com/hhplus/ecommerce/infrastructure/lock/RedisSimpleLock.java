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
public class RedisSimpleLock {

    private final RedissonClient redissonClient;
    private static final String LOCK_PREFIX = "lock:";

    public boolean tryLock(String key) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + key);
        return lock.tryLock();
    }

    public void unlock(String key) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + key);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
