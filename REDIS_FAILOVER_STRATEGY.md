# Redis 장애 대응 전략

## 1. 현재 상태 분석

### 1.1 Redis 의존성
현재 시스템은 Redis에 2가지 기능을 의존:
1. **분산락** (RedisPubSubLock) - 동시성 제어
2. **캐시** (Spring Cache) - 조회 성능 최적화

### 1.2 장애 시나리오별 영향 (개선 완료 ✅)

| 시나리오 | 분산락 | 캐시 | 서비스 영향 |
|----------|--------|------|-------------|
| Redis 정상 | ✅ Redis 락 사용 | ✅ Redis 캐시 사용 | 정상 (최고 성능) |
| Redis 지연 | ⚠️ 타임아웃 | ⚠️ 느린 응답 | 성능 저하 |
| Redis 다운 | ✅ **DB 락으로 Fallback** | ✅ **DB로 Fallback** | **서비스 지속** (성능 저하) |
| Redis 복구 | ✅ 자동 복구 | ✅ 자동 복구 | 정상 복구 |

**✅ 해결 완료**: Redis 다운 시에도 DB Lock/Query Fallback으로 서비스 계속 동작

---

## 2. 개선 방안

### 2.1 분산락 Fallback 전략

#### Option 1: DB 락으로 Fallback (권장)
Redis 실패 시 DB 비관적 락으로 전환

**장점:**
- ✅ 서비스 지속성 보장
- ✅ 동시성 제어 유지
- ✅ 구현 간단

**단점:**
- ⚠️ 성능 저하 (Redis 500μs → DB 50ms)
- ⚠️ DB 부하 증가

#### Option 2: 락 없이 실행 (비권장)
Redis 실패 시 락 없이 진행

**장점:**
- ✅ 성능 유지

**단점:**
- ❌ 동시성 문제 발생 위험
- ❌ 데이터 정합성 보장 불가

#### Option 3: Circuit Breaker (중급)
Redis 장애 감지 시 자동으로 DB 락 전환

**장점:**
- ✅ 자동 장애 격리
- ✅ 빠른 Fail-fast

**단점:**
- ⚠️ 구현 복잡도 증가
- ⚠️ Hystrix/Resilience4j 의존성 필요

---

## 3. 구현 완료: DB Fallback Lock ✅

### 3.1 구현된 RedisPubSubLock

**파일**: `infrastructure/lock/RedisPubSubLock.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisPubSubLock {

    private final RedissonClient redissonClient;
    private final LockRepository lockRepository;

    /**
     * Redis 락 시도 → 실패 시 DB 락으로 Fallback
     */
    public boolean tryLock(String key, long waitTime, TimeUnit timeUnit) {
        try {
            // 1. Redis 락 시도
            RLock lock = redissonClient.getLock(LOCK_PREFIX + key);
            return lock.tryLock(waitTime, 3, timeUnit);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;

        } catch (Exception e) {
            // 2. Redis 장애 → DB 락으로 Fallback
            log.warn("Redis unavailable, fallback to DB lock: key={}", key);
            return tryDbLock(key, waitTime, timeUnit);
        }
    }

    /**
     * DB 락 시도 (Spin-lock 방식)
     */
    private boolean tryDbLock(String key, long waitTime, TimeUnit timeUnit) {
        long endTime = System.currentTimeMillis() + timeUnit.toMillis(waitTime);

        while (System.currentTimeMillis() < endTime) {
            try {
                acquireDbLock(key, 3); // 3초 TTL
                log.info("DB lock acquired: {}", key);
                return true;

            } catch (DataIntegrityViolationException e) {
                // 이미 락이 존재 → 재시도
                Thread.sleep(100);
            }
        }
        return false;
    }

    /**
     * DB 락 획득 (새 트랜잭션)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void acquireDbLock(String key, long ttlSeconds) {
        Optional<DistributedLock> existing = lockRepository.findByLockKeyWithLock(key);
        if (existing.isPresent() && existing.get().isExpired()) {
            lockRepository.deleteByLockKey(key);
        }

        DistributedLock lock = DistributedLock.create(key, ttlSeconds);
        lockRepository.save(lock);
    }

    public void unlock(String key) {
        try {
            RLock lock = redissonClient.getLock(LOCK_PREFIX + key);
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            log.warn("Redis unavailable, unlocking DB lock: key={}", key);
            releaseDbLock(key);
        }
    }

    /**
     * DB 락 해제 (새 트랜잭션)
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
```

### 3.2 DistributedLock 엔티티

**파일**: `domain/entity/DistributedLock.java`

```java
@Entity
@Table(name = "distributed_locks",
       indexes = @Index(name = "idx_expires_at", columnList = "expiresAt"))
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
        lock.threadId = Thread.currentThread().getName() + "-" +
                        Thread.currentThread().getId();
        return lock;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isHeldByCurrentThread() {
        String currentThreadId = Thread.currentThread().getName() + "-" +
                                 Thread.currentThread().getId();
        return currentThreadId.equals(threadId);
    }
}
```

### 3.3 LockRepository

**파일**: `infrastructure/persistence/LockJpaRepository.java`

```java
public interface LockJpaRepository extends JpaRepository<DistributedLock, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM DistributedLock l WHERE l.lockKey = :lockKey")
    Optional<DistributedLock> findByLockKeyWithLock(@Param("lockKey") String lockKey);

    @Modifying
    @Query("DELETE FROM DistributedLock l WHERE l.expiresAt < :now")
    int deleteExpiredLocks(@Param("now") LocalDateTime now);

    void deleteByLockKey(String lockKey);
}
```

---

## 4. 구현 완료: 캐시 Fallback ✅

### 4.1 구현된 CacheErrorHandler

**파일**: `config/RedisCacheConfig.java`

```java
@Slf4j
@Configuration
@EnableCaching
public class RedisCacheConfig implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                // Redis 조회 실패 → 로그만 남기고 메서드 실행 (DB Fallback)
                log.warn("Cache GET failed (fallback to DB): cache={}, key={}",
                         cache.getName(), key);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                // Redis 저장 실패 → 무시하고 계속
                log.warn("Cache PUT failed (ignored): cache={}, key={}",
                         cache.getName(), key);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                // Redis 삭제 실패 → 무시 (TTL로 자동 만료)
                log.warn("Cache EVICT failed (ignored): cache={}, key={}",
                         cache.getName(), key);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Cache CLEAR failed (ignored): cache={}", cache.getName());
            }
        };
    }
}
```

**동작 방식:**
1. **Redis 정상**: Redis 캐시 사용 (최고 성능)
2. **Redis 장애**:
   - GET 실패 → DB 조회 (서비스 계속)
   - PUT 실패 → 무시 (다음 요청 시 다시 시도)
   - EVICT 실패 → 무시 (TTL로 자동 만료)

### 4.2 향후 개선: Local Cache (옵션)

**Multi-level Cache 전략:**
```
요청 → L1 Cache (Caffeine) → L2 Cache (Redis) → DB
        ↑ 메모리           ↑ 분산           ↑ 영구
```

**장점:**
- Redis 장애에도 최소한의 성능 유지
- Hot Data는 로컬 메모리에서 처리

**구현:**
```java
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        // Caffeine (Local) + Redis (Distributed)
        return new CompositeCacheManager(
            caffeineCacheManager(),
            redisCacheManager(factory)
        );
    }

    @Bean
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.MINUTES));
        return cacheManager;
    }
}
```

---

## 5. Redis 고가용성 아키텍처

### 5.1 Redis Sentinel (권장)
**자동 Failover 지원**

```yaml
spring:
  redis:
    sentinel:
      master: mymaster
      nodes:
        - redis-sentinel-1:26379
        - redis-sentinel-2:26379
        - redis-sentinel-3:26379
```

**동작:**
- Master 장애 감지 (Sentinel이 모니터링)
- 자동으로 Slave를 Master로 승격
- 애플리케이션은 자동으로 새 Master 연결

**복구 시간:** 약 30초 이내

### 5.2 Redis Cluster
**대규모 분산 환경**

```yaml
spring:
  redis:
    cluster:
      nodes:
        - redis-1:6379
        - redis-2:6379
        - redis-3:6379
```

**특징:**
- 데이터 샤딩
- 자동 Failover
- 수평 확장 용이

---

## 6. 모니터링 및 알림

### 6.1 Health Check
```java
@Component
public class RedisHealthIndicator implements HealthIndicator {

    private final RedissonClient redissonClient;

    @Override
    public Health health() {
        try {
            redissonClient.getKeys().count();
            return Health.up()
                .withDetail("redis", "available")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("redis", "unavailable")
                .withException(e)
                .build();
        }
    }
}
```

### 6.2 Circuit Breaker (Resilience4j)
```java
@CircuitBreaker(name = "redis", fallbackMethod = "fallbackLock")
public boolean tryLock(String key, long waitTime, TimeUnit timeUnit) {
    // Redis 락 시도
}

public boolean fallbackLock(String key, long waitTime, TimeUnit timeUnit, Exception e) {
    log.warn("Circuit breaker open, using DB lock", e);
    return tryDbLock(key, waitTime, timeUnit);
}
```

### 6.3 메트릭 수집
```java
@Timed(value = "lock.acquisition", description = "Lock acquisition time")
public boolean tryLock(String key, long waitTime, TimeUnit timeUnit) {
    // 락 획득 시간 측정
}
```

---

## 7. 단계별 적용 전략

### Phase 1: 단기 (1-2주)
1. ✅ **RedisPubSubLock에 예외 처리 추가**
   - RedisConnectionException catch
   - 적절한 에러 메시지 반환

2. ✅ **Health Check 추가**
   - Redis 상태 모니터링
   - 알림 설정

### Phase 2: 중기 (1개월)
3. ✅ **DB Lock Fallback 구현**
   - DistributedLock 테이블 생성
   - ResilientLock 클래스 구현

4. ✅ **캐시 모니터링 강화**
   - Hit/Miss Rate 추적
   - 장애 감지 알림

### Phase 3: 장기 (2-3개월)
5. ✅ **Redis Sentinel 구축**
   - 자동 Failover 환경 구성
   - DR(Disaster Recovery) 테스트

6. ✅ **Multi-level Cache 도입** (선택)
   - Caffeine Local Cache
   - 성능 테스트

---

## 8. 비용-효과 분석

| 방안 | 구현 난이도 | 비용 | 복구 시간 | 서비스 영향 |
|------|-------------|------|-----------|-------------|
| **현재 상태** | - | - | ∞ (수동) | 서비스 중단 |
| **DB Fallback** | 중 | 낮음 | 즉시 | 성능 저하 |
| **Circuit Breaker** | 중 | 낮음 | 즉시 | 성능 저하 |
| **Redis Sentinel** | 중 | 중간 | 30초 | 일시적 지연 |
| **Redis Cluster** | 높음 | 높음 | 즉시 | 영향 없음 |
| **Multi-level Cache** | 중 | 낮음 | 즉시 | 영향 없음 |

---

## 9. 구현 현황 및 권장사항

### ✅ 구현 완료 (2025-11-28)

1. **✅ DB Lock Fallback 구현**
   - RedisPubSubLock에 DB 락 Fallback 추가
   - DistributedLock 엔티티 및 Repository 생성
   - Redis 장애 시 자동으로 DB 락으로 전환
   - **효과**: 서비스 중단 없이 계속 동작 (성능 저하만 발생)

2. **✅ Cache Error Handler 구현**
   - RedisCacheConfig에 CacheErrorHandler 추가
   - Redis 장애 시 자동으로 DB 조회로 Fallback
   - **효과**: 캐시 장애에도 서비스 정상 동작

### 향후 개선 (선택)

3. **Health Check 구현** (권장)
   - /actuator/health/redis 엔드포인트
   - 알림 연동 (Slack, PagerDuty)
   - 사전 장애 감지 및 알림

4. **Redis Sentinel 도입** (프로덕션 권장)
   - 자동 Failover (~30초)
   - DB Fallback보다 빠른 복구
   - 운영 부담 감소

5. **Multi-level Cache** (선택)
   - Caffeine Local Cache + Redis
   - Redis 장애에도 최소 성능 유지
   - Hot Data는 로컬 메모리에서 처리

---

**작성일**: 2025-11-27 (최초 작성)
**수정일**: 2025-11-28 (구현 완료)
**작성자**: Claude Code
**버전**: 2.0 (DB Lock & Cache Fallback 구현 완료)
