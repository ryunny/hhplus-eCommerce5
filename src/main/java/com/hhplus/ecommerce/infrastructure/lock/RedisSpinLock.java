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
public class RedisSpinLock {

    private final RedissonClient redissonClient;
    private static final String LOCK_PREFIX = "lock:spin:";
    private static final long SPIN_TIMEOUT_MS = 3000;
    private static final long SLEEP_MS = 50;

    public boolean lock(String key) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + key);
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < SPIN_TIMEOUT_MS) {
            try {
                if (lock.tryLock(0, 3, TimeUnit.SECONDS)) {
                    return true;
                }
                Thread.sleep(SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Spin lock interrupted: {}", key, e);
                return false;
            }
        }

        log.warn("Spin lock timeout after {}ms: {}", SPIN_TIMEOUT_MS, key);
        return false;
    }

    public void unlock(String key) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + key);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
