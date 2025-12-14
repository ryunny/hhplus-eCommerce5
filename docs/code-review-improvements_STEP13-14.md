# 코드 리뷰 피드백 개선 사항

## 개요
이 문서는 코드 리뷰에서 받은 피드백을 반영하여 개선한 내용을 정리합니다.

---

## 1. @RequiredArgsConstructor 사용 권장 ✅

### 피드백
- Lombok의 `@RequiredArgsConstructor`를 사용하여 의존성 주입 코드의 가독성을 높이고 의존성 관리를 개선

### 개선 사항
- **현재 상태**: 프로젝트 전체에서 이미 `@RequiredArgsConstructor`를 활용 중
- **확인 결과**:
  - `CouponController`, `RedisQueueProcessor` 등 주요 클래스에서 `@RequiredArgsConstructor` 사용
  - `@Autowired`는 테스트 코드에서만 사용 중 (정상)

### 파일
- `CouponController.java:21`
- `RedisQueueProcessor.java:28`

---

## 2. 분산락 추상화 ✅

### 피드백
- 다양한 분산락 구현체를 추상화하여 재사용성을 높이고 유지보수를 용이하게 개선
- `@DistributedLock` 어노테이션을 활용한 선언적 분산락 적용 방식 권장

### 개선 사항

#### 2.1 분산락 어노테이션 구현
**새 파일**: `infrastructure/lock/DistributedLock.java`

```java
@DistributedLock(
    key = "#couponId",
    lockType = LockType.REDISSON_LOCK,
    waitTime = 3,
    leaseTime = 5
)
public void issueCoupon(Long couponId, String userId) {
    // 비즈니스 로직
}
```

**특징**:
- SpEL 표현식 지원으로 동적 키 생성
- 다양한 락 타입 지원 (Redisson Lock, Spin Lock, Pub/Sub Lock)
- 락 대기/유지 시간 커스터마이징 가능

#### 2.2 락 타입 열거형
**새 파일**: `infrastructure/lock/LockType.java`

```java
public enum LockType {
    REDISSON_LOCK,        // 기본 분산락
    REDISSON_SPIN_LOCK,   // 스핀락
    REDIS_PUB_SUB_LOCK    // Pub/Sub 기반
}
```

#### 2.3 AOP 구현
**새 파일**: `infrastructure/lock/DistributedLockAspect.java`

- Spring AOP를 활용한 자동 락 적용
- SpEL 파서를 사용한 동적 키 생성
- 락 획득 실패 시 `IllegalStateException` 발생

### 장점
1. **재사용성**: 어노테이션 선언만으로 분산락 적용 가능
2. **유지보수성**: 락 로직이 비즈니스 로직과 분리됨
3. **확장성**: 새로운 락 타입 추가가 용이
4. **가독성**: 코드가 간결하고 의도가 명확함

---

## 3. 불필요한 주석 제거 ✅

### 피드백
- 코드를 그대로 설명하는 불필요한 주석 제거
- 의미 있는 JavaDoc만 유지

### 개선 사항

#### 3.1 CouponController
**개선 전**:
```java
if (userCoupon == null) {
    // 대기열 방식: Redis → DB Fallback 적용
    return ResponseEntity.accepted()...
}

// 즉시 발급 방식
return ResponseEntity.ok(...);
```

**개선 후**:
```java
if (userCoupon == null) {
    return ResponseEntity.accepted()...
}

return ResponseEntity.ok(...);
```

- **위치**: `CouponController.java:66, 71`

#### 3.2 RedisKeyGenerator
**개선 전**:
```java
// ===== Lock 키 생성 =====
// ===== Coupon Domain =====
// ===== Product Domain =====
// ===== Cache 키 생성 =====
```

**개선 후**:
- 구분선 주석 모두 제거
- JavaDoc은 유지 (메서드 설명)

- **위치**: `RedisKeyGenerator.java:24, 39, 99, 121`

### 원칙
- ❌ 제거: 코드를 그대로 설명하는 주석
- ✅ 유지: 복잡한 로직을 설명하는 JavaDoc
- ✅ 유지: 비즈니스 요구사항을 설명하는 주석

---

## 4. @Scheduled 메서드에서 분산락 적용 ✅

### 피드백
- 다중 서버 환경에서 스케줄러 중복 실행 방지
- Redisson 분산락을 활용한 단일 실행 보장

### 개선 사항

#### 4.1 RedisQueueProcessor 개선
**새 의존성 추가**:
```java
private final RedissonClient redissonClient;
private final SchedulerProperties schedulerProperties;
```

**개선 전**:
```java
@Scheduled(fixedDelay = 10000)
public void processQueues() {
    // 모든 서버에서 동시 실행 (중복 처리 위험)
}
```

**개선 후**:
```java
@Scheduled(fixedDelayString = "${scheduler.queue.fixed-delay:10000}")
public void processQueues() {
    RLock lock = redissonClient.getLock(SCHEDULER_LOCK_KEY);

    try {
        boolean isLocked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);

        if (!isLocked) {
            log.debug("스케줄러 락 획득 실패. 다른 서버에서 실행 중입니다.");
            return;
        }

        try {
            // 비즈니스 로직
        } finally {
            lock.unlock();
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.error("스케줄러 락 대기 중 인터럽트 발생", e);
    }
}
```

**파일**: `RedisQueueProcessor.java:47-106`

### 특징
1. **단일 실행 보장**: 여러 서버 중 하나만 실행
2. **즉시 반환**: 락 획득 실패 시 대기하지 않고 다음 주기에 재시도
3. **안전한 락 해제**: finally 블록에서 확실히 해제
4. **설정 기반**: Properties 파일로 주기 조정 가능

---

## 5. 매직 넘버를 상수로 대체 ✅

### 피드백
- 하드코딩된 숫자를 명명된 상수로 대체하여 가독성 및 유지보수성 향상

### 개선 사항

#### 5.1 CacheProperties 클래스 생성
**새 파일**: `config/properties/CacheProperties.java`

```java
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "cache")
public class CacheProperties {
    private final Duration defaultTtl;
    private final Duration productsTtl;
    private final Duration couponsTtl;
    private final Duration issuableCouponsTtl;
    private final Duration usersTtl;
    private final Duration topProductsTtl;

    public CacheProperties() {
        this.defaultTtl = Duration.ofMinutes(10);
        this.productsTtl = Duration.ofMinutes(30);
        this.couponsTtl = Duration.ofMinutes(10);
        this.issuableCouponsTtl = Duration.ofMinutes(5);
        this.usersTtl = Duration.ofMinutes(5);
        this.topProductsTtl = Duration.ofMinutes(60);
    }
}
```

#### 5.2 RedisCacheConfig 개선
**개선 전**:
```java
.entryTtl(Duration.ofMinutes(10))
cacheConfigurations.put("products", defaultConfig.entryTtl(Duration.ofMinutes(30)));
```

**개선 후**:
```java
.entryTtl(cacheProperties.getDefaultTtl())
cacheConfigurations.put("products", defaultConfig.entryTtl(cacheProperties.getProductsTtl()));
```

**파일**: `RedisCacheConfig.java:53, 62, 73-77`

#### 5.3 SchedulerProperties 클래스 생성
**새 파일**: `config/properties/SchedulerProperties.java`

```java
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "scheduler.queue")
public class SchedulerProperties {
    private final long fixedDelay;
    private final int batchSize;

    public SchedulerProperties() {
        this.fixedDelay = 10000L; // 10초
        this.batchSize = 10;
    }
}
```

#### 5.4 RedisQueueProcessor 개선
**개선 전**:
```java
@Scheduled(fixedDelay = 10000)
int processed = queueService.processBatch(coupon.getId(), 10);
```

**개선 후**:
```java
@Scheduled(fixedDelayString = "${scheduler.queue.fixed-delay:10000}")
int processed = queueService.processBatch(coupon.getId(), schedulerProperties.getBatchSize());
```

**파일**: `RedisQueueProcessor.java:47, 81`

#### 5.5 RedisQueueProcessor 락 상수화
**추가된 상수**:
```java
private static final String SCHEDULER_LOCK_KEY = "lock:scheduler:processQueues";
private static final long LOCK_WAIT_TIME = 0L;
private static final long LOCK_LEASE_TIME = 30L;
```

**파일**: `RedisQueueProcessor.java:32-34`

### 장점
1. **의미 전달**: 숫자의 의미가 명확함
2. **중앙 관리**: 설정값을 한 곳에서 관리
3. **유연성**: 환경에 따라 다른 값 적용 가능
4. **타입 안전성**: Duration, TimeUnit 등 타입 활용

---

## 6. @ConfigurationProperties 클래스 활용 ✅

### 피드백
- `@Value` 대신 `@ConfigurationProperties`를 사용하여 관련 프로퍼티를 그룹화
- 불변 객체로 설계

### 개선 사항

#### 6.1 RedisProperties 클래스 생성
**새 파일**: `config/properties/RedisProperties.java`

```java
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "spring.data.redis")
public class RedisProperties {
    private final String host;
    private final int port;
}
```

**특징**:
- `@RequiredArgsConstructor`로 불변 객체 생성
- final 필드로 값 변경 불가

#### 6.2 RedissonConfig 개선
**개선 전**:
```java
@Value("${spring.data.redis.host}")
private String redisHost;

@Value("${spring.data.redis.port}")
private int redisPort;
```

**개선 후**:
```java
@RequiredArgsConstructor
@EnableConfigurationProperties(RedisProperties.class)
public class RedissonConfig {
    private final RedisProperties redisProperties;

    @Bean
    public RedissonClient redissonClient() {
        config.useSingleServer()
            .setAddress("redis://" + redisProperties.getHost() + ":" + redisProperties.getPort());
    }
}
```

**파일**: `RedissonConfig.java:12-17`

### 장점
1. **타입 안전성**: IDE 자동완성 지원
2. **검증 기능**: `@Validated` 추가 가능
3. **가독성**: 관련 설정이 한 클래스에 모임
4. **불변성**: final 필드로 안전성 보장
5. **테스트 용이성**: 객체 생성이 간단함

---

## 7. RedisTemplate 타입 구체화 검토 ✅

### 피드백
- RedisTemplate의 제네릭 타입을 구체화하여 성능 향상

### 검토 결과
**현재 상태**:
```java
// RedisCouponQueueService.java:48
private final RedisTemplate<String, String> redisTemplate;

// ProductRankingService.java:31
private final RedisTemplate<String, String> redisTemplate;
```

### 결론
- ✅ **이미 타입이 구체화되어 있음**
- `RedisTemplate<String, String>`으로 키와 값 모두 String 타입 지정
- 추가 개선 불필요

### 타입 구체화의 장점
1. **타입 안전성**: 컴파일 타임에 타입 체크
2. **성능 향상**: 불필요한 형변환 제거
3. **직렬화 최적화**: String 전용 직렬화 사용

---

## 개선 사항 요약

| 번호 | 피드백 | 상태 | 주요 변경 파일 |
|------|--------|------|----------------|
| 1 | @RequiredArgsConstructor 사용 | ✅ 이미 적용됨 | - |
| 2 | 분산락 추상화 | ✅ 완료 | `DistributedLock.java`, `DistributedLockAspect.java` |
| 3 | 불필요한 주석 제거 | ✅ 완료 | `CouponController.java`, `RedisKeyGenerator.java` |
| 4 | @Scheduled 분산락 적용 | ✅ 완료 | `RedisQueueProcessor.java` |
| 5 | 매직 넘버 상수화 | ✅ 완료 | `CacheProperties.java`, `SchedulerProperties.java` |
| 6 | @ConfigurationProperties 활용 | ✅ 완료 | `RedisProperties.java`, `RedissonConfig.java` |
| 7 | RedisTemplate 타입 구체화 | ✅ 이미 적용됨 | - |

---

## 새로 생성된 파일

### 1. Properties 클래스
- `config/properties/RedisProperties.java`
- `config/properties/CacheProperties.java`
- `config/properties/SchedulerProperties.java`

### 2. 분산락 추상화
- `infrastructure/lock/DistributedLock.java`
- `infrastructure/lock/LockType.java`
- `infrastructure/lock/DistributedLockAspect.java`

---

## 적용 가능한 application.yml 설정

```yaml
# Redis 설정
spring:
  data:
    redis:
      host: localhost
      port: 6379

# 캐시 TTL 설정
cache:
  default-ttl: 10m
  products-ttl: 30m
  coupons-ttl: 10m
  issuable-coupons-ttl: 5m
  users-ttl: 5m
  top-products-ttl: 60m

# 스케줄러 설정
scheduler:
  queue:
    fixed-delay: 10000  # 10초
    batch-size: 10
```

---

## 개선 효과

### 1. 코드 품질
- ✅ 불필요한 주석 제거로 가독성 향상
- ✅ 매직 넘버 제거로 유지보수성 향상
- ✅ Properties 클래스로 설정 관리 일원화

### 2. 아키텍처
- ✅ 분산락 추상화로 재사용성 대폭 향상
- ✅ AOP 활용으로 횡단 관심사 분리
- ✅ 다중 서버 환경에서 안전한 스케줄러 실행

### 3. 유지보수성
- ✅ 설정값을 Properties 파일로 외부화
- ✅ 불변 객체로 안전성 보장
- ✅ 명확한 책임 분리

### 4. 확장성
- ✅ 새로운 락 타입 추가 용이
- ✅ 설정 기반으로 환경별 다른 값 적용 가능
- ✅ 어노테이션 기반으로 분산락 적용 간편

---

## 참고 자료
- [Lombok @RequiredArgsConstructor](https://mangkyu.tistory.com/155)
- [Spring @ConfigurationProperties](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config.typesafe-configuration-properties)
- [Redisson Distributed Lock](https://github.com/redisson/redisson/wiki/8.-Distributed-locks-and-synchronizers)
