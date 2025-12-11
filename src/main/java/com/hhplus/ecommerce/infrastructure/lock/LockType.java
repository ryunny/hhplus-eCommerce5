package com.hhplus.ecommerce.infrastructure.lock;

/**
 * 분산락 구현 타입
 */
public enum LockType {
    /**
     * Redisson의 기본 Lock (RLock)
     * - 가장 일반적인 분산락
     * - 재진입 가능
     */
    REDISSON_LOCK,

    /**
     * Redisson의 Spin Lock
     * - 락 획득 시도 시 스핀 방식 사용
     * - 짧은 대기 시간에 적합
     */
    REDISSON_SPIN_LOCK,

    /**
     * Redis Pub/Sub 기반 Lock
     * - 대기 중인 스레드에게 락 해제 알림
     * - 많은 대기자가 있을 때 효율적
     */
    REDIS_PUB_SUB_LOCK
}
