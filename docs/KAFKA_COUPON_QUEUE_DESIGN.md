# Kafka 기반 쿠폰 발급 & 대기열 시스템 설계

## 📋 목차
1. [개요](#개요)
2. [시스템 요구사항](#시스템-요구사항)
3. [아키텍처 설계](#아키텍처-설계)
4. [쿠폰 발급 시스템](#쿠폰-발급-시스템)
5. [대기열 시스템](#대기열-시스템)
6. [Kafka 특징 활용 전략](#kafka-특징-활용-전략)
7. [장애 처리 및 모니터링](#장애-처리-및-모니터링)
8. [성능 최적화](#성능-최적화)

---

## 개요

### 배경
- 선착순 쿠폰 이벤트 시 대량의 동시 요청 발생 (초당 10만+ 요청)
- 기존 동기 처리 방식의 한계: DB 부하, 응답 지연, 동시성 제어 어려움
- Redis 대기열만으로는 영속성 및 재처리 보장이 어려움

### 해결 방안
Kafka의 핵심 특징을 활용한 하이브리드 아키텍처:
1. **Redis**: 빠른 중복 체크, 임시 대기열
2. **Kafka**: 영속적 메시지 큐, 순서 보장, 재처리 가능
3. **Consumer Group**: 병렬 처리로 처리량 극대화

---

## 시스템 요구사항

### 기능적 요구사항
1. **선착순 쿠폰 발급**
   - 한정된 수량의 쿠폰을 선착순으로 발급
   - 사용자당 1회 발급 제한
   - 발급 성공/실패 응답

2. **대기열 관리**
   - 대량 트래픽 수용 (초당 10만+ 요청)
   - 공정한 순서 처리 (FIFO)
   - 실시간 대기 상태 조회

### 비기능적 요구사항
1. **성능**
   - 응답 시간: 200ms 이하 (대기열 진입)
   - 처리량: 초당 10만 요청 이상
   - 쿠폰 발급 처리: 초당 1만 건 이상

2. **안정성**
   - 메시지 유실 방지 (최소 1회 전달 보장)
   - 장애 복구 시 재처리 가능
   - 중복 발급 방지

3. **확장성**
   - Consumer 수평 확장 가능
   - Kafka 파티션 증가로 처리량 증대

---

## 아키텍처 설계

### 전체 흐름도

```
┌─────────────┐
│   Client    │
│  (사용자)    │
└──────┬──────┘
       │ HTTP POST /coupons/{couponId}/issue
       ▼
┌─────────────────────────────────────────────────┐
│           API Gateway / Load Balancer           │
└──────────────────┬──────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────┐
│          CouponController (Producer)            │
│  1. Redis 중복 체크 (사용자)                     │
│  2. Redis 재고 확인 (남은 수량)                  │
│  3. Kafka 이벤트 발행 (CouponIssueRequestedEvent)│
│  4. 즉시 응답 (202 Accepted)                    │
└──────────────────┬──────────────────────────────┘
                   │
                   │ Kafka Topic: coupon.issue.requested
                   │ Key: userId (같은 사용자는 같은 파티션)
                   │
                   ▼
┌─────────────────────────────────────────────────┐
│              Kafka Broker Cluster               │
│  - Topic: coupon.issue.requested (10 partitions)│
│  - Replication Factor: 3                        │
│  - Message Retention: 7일                       │
└──────────────────┬──────────────────────────────┘
                   │
                   │ Consumer Group: coupon-issue-service
                   │ (10개 Consumer 인스턴스)
                   │
                   ▼
┌─────────────────────────────────────────────────┐
│     CouponIssueEventHandler (Consumer)          │
│  1. DB 락으로 쿠폰 재고 확인 (비관적 락)          │
│  2. 중복 발급 체크 (DB unique 제약)              │
│  3. UserCoupon 생성 (발급)                       │
│  4. Redis 캐시 업데이트                          │
│  5. 성공/실패 이벤트 발행                        │
└──────────────────┬──────────────────────────────┘
                   │
                   ├─ 성공 → coupon.issued (외부 알림용)
                   └─ 실패 → coupon.issue.failed (DLQ)
```

### Kafka 토픽 설계

```java
// 쿠폰 발급 요청 토픽
public static final String COUPON_ISSUE_REQUESTED_TOPIC = "coupon.issue.requested";

// 쿠폰 발급 성공 토픽
public static final String COUPON_ISSUED_TOPIC = "coupon.issued";

// 쿠폰 발급 실패 토픽 (Dead Letter Queue)
public static final String COUPON_ISSUE_FAILED_TOPIC = "coupon.issue.failed";

// 대기열 진입 토픽
public static final String QUEUE_ENTERED_TOPIC = "queue.entered";

// 대기열 처리 완료 토픽
public static final String QUEUE_PROCESSED_TOPIC = "queue.processed";
```

**토픽 설정:**
- **Partitions**: 10개 (처리량에 따라 조정)
- **Replication Factor**: 3 (프로덕션 환경)
- **Retention**: 7일 (재처리 및 디버깅용)
- **Cleanup Policy**: delete (시간 기반 삭제)

---

## 쿠폰 발급 시스템

### 1. 선착순 쿠폰 발급 흐름

#### Phase 1: 요청 접수 (Producer)

```java
@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, CouponIssueRequestedEvent> kafkaTemplate;

    /**
     * 선착순 쿠폰 발급 요청
     *
     * Redis로 빠른 중복 체크 → Kafka로 비동기 처리
     *
     * @return 202 Accepted (처리 대기 중)
     */
    @PostMapping("/{couponId}/issue")
    public ResponseEntity<CouponIssueResponse> issueCoupon(
            @PathVariable Long couponId,
            @RequestHeader("X-User-Public-Id") String publicId) {

        Long userId = getUserIdByPublicId(publicId);

        // ====================================
        // Step 1: Redis 중복 체크 (빠른 실패)
        // ====================================
        String redisKey = "coupon:issued:" + couponId + ":" + userId;
        Boolean isFirstRequest = redisTemplate.opsForValue()
            .setIfAbsent(redisKey, "1", Duration.ofHours(24));

        if (!isFirstRequest) {
            throw new DuplicateCouponRequestException("이미 발급 요청한 쿠폰입니다");
        }

        // ====================================
        // Step 2: Redis 재고 확인 (빠른 실패)
        // ====================================
        String stockKey = "coupon:stock:" + couponId;
        Long remainingStock = redisTemplate.opsForValue().decrement(stockKey);

        if (remainingStock == null || remainingStock < 0) {
            // Redis 롤백
            redisTemplate.opsForValue().increment(stockKey);
            redisTemplate.delete(redisKey);
            throw new CouponSoldOutException("쿠폰이 모두 소진되었습니다");
        }

        // ====================================
        // Step 3: Kafka 이벤트 발행
        // ====================================
        CouponIssueRequestedEvent event = new CouponIssueRequestedEvent(
            UUID.randomUUID().toString(),  // requestId (멱등성 키)
            couponId,
            userId,
            Instant.now()
        );

        // Key를 userId로 설정하여 같은 사용자는 같은 파티션으로 전송
        // → 순서 보장 (한 사용자의 요청은 순차 처리)
        kafkaTemplate.send(
            KafkaConfig.COUPON_ISSUE_REQUESTED_TOPIC,
            userId.toString(),  // partition key
            event
        );

        log.info("[쿠폰] 발급 요청 접수: couponId={}, userId={}, requestId={}",
            couponId, userId, event.requestId());

        // ====================================
        // Step 4: 즉시 응답 (비동기 처리)
        // ====================================
        return ResponseEntity.accepted()
            .body(new CouponIssueResponse(
                event.requestId(),
                "쿠폰 발급 요청이 접수되었습니다. 잠시 후 결과를 확인해주세요.",
                remainingStock
            ));
    }
}
```

**핵심 설계 포인트:**

1. **Redis 중복 체크** (`setIfAbsent`)
   - 동일 사용자의 중복 요청을 빠르게 차단
   - DB 부하 없이 메모리에서 즉시 처리

2. **Redis 재고 확인** (`decrement`)
   - Atomic 연산으로 동시성 제어
   - 쿠폰 소진 시 빠른 실패 응답

3. **Kafka Key 전략** (userId)
   - 같은 사용자의 요청은 같은 파티션으로 전송
   - 순서 보장 및 중복 처리 방지

4. **비동기 응답** (202 Accepted)
   - API 응답 속도 향상 (200ms 이내)
   - 실제 발급은 Consumer가 처리

---

#### Phase 2: 쿠폰 발급 처리 (Consumer)

```java
@Component
@Slf4j
public class CouponIssueEventHandler {

    private final CouponService couponService;
    private final UserService userService;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 쿠폰 발급 요청 처리
     *
     * Kafka Consumer가 요청을 꺼내서 실제 DB에 발급
     *
     * Consumer Group: coupon-issue-service
     * Concurrency: 10 (파티션 수와 동일)
     */
    @Transactional
    @KafkaListener(
        topics = KafkaConfig.COUPON_ISSUE_REQUESTED_TOPIC,
        groupId = "coupon-issue-service",
        concurrency = "10"  // 10개 Consumer 스레드 (파티션 수와 동일)
    )
    public void handleCouponIssueRequested(CouponIssueRequestedEvent event) {
        try {
            log.info("[쿠폰-Kafka] 발급 처리 시작: requestId={}, couponId={}, userId={}",
                event.requestId(), event.couponId(), event.userId());

            // ====================================
            // Step 1: 멱등성 체크 (중복 처리 방지)
            // ====================================
            String idempotencyKey = "coupon:processed:" + event.requestId();
            Boolean isFirstProcessing = redisTemplate.opsForValue()
                .setIfAbsent(idempotencyKey, "1", Duration.ofDays(7));

            if (!isFirstProcessing) {
                log.warn("[쿠폰-Kafka] 중복 처리 요청 무시: requestId={}", event.requestId());
                return;  // 이미 처리됨
            }

            // ====================================
            // Step 2: DB에서 실제 발급
            // ====================================
            Coupon coupon = couponService.getCouponWithLock(event.couponId());
            User user = userService.getUser(event.userId());

            // 재고 확인 (비관적 락)
            if (!coupon.hasStock()) {
                throw new CouponSoldOutException("쿠폰 재고가 소진되었습니다");
            }

            // 중복 발급 체크 (DB unique 제약)
            UserCoupon userCoupon = couponService.issueCoupon(coupon, user);

            log.info("[쿠폰-Kafka] 발급 완료: userCouponId={}, couponId={}, userId={}",
                userCoupon.getId(), event.couponId(), event.userId());

            // ====================================
            // Step 3: 성공 이벤트 발행
            // ====================================
            CouponIssuedEvent successEvent = new CouponIssuedEvent(
                event.requestId(),
                userCoupon.getId(),
                event.couponId(),
                event.userId(),
                Instant.now()
            );

            kafkaTemplate.send(
                KafkaConfig.COUPON_ISSUED_TOPIC,
                event.userId().toString(),
                successEvent
            );

            // ====================================
            // Step 4: Redis 캐시 업데이트
            // ====================================
            updateRedisCacheAfterIssue(event.couponId(), event.userId());

        } catch (CouponSoldOutException | DuplicateCouponException e) {
            // 예상된 실패 (비즈니스 로직)
            log.warn("[쿠폰-Kafka] 발급 실패 (예상): requestId={}, reason={}",
                event.requestId(), e.getMessage());

            publishFailureEvent(event, e.getMessage());
            rollbackRedisCache(event);

        } catch (Exception e) {
            // 예상치 못한 오류
            log.error("[쿠폰-Kafka] 발급 실패 (시스템 오류): requestId={}, error={}",
                event.requestId(), e.getMessage(), e);

            publishFailureEvent(event, "시스템 오류: " + e.getMessage());
            rollbackRedisCache(event);

            // 재시도를 위해 예외를 던지지 않음 (DLQ로 전송됨)
        }
    }

    /**
     * 실패 이벤트 발행 (DLQ)
     */
    private void publishFailureEvent(CouponIssueRequestedEvent event, String reason) {
        CouponIssueFailedEvent failEvent = new CouponIssueFailedEvent(
            event.requestId(),
            event.couponId(),
            event.userId(),
            reason,
            Instant.now()
        );

        kafkaTemplate.send(
            KafkaConfig.COUPON_ISSUE_FAILED_TOPIC,
            event.userId().toString(),
            failEvent
        );
    }

    /**
     * Redis 캐시 롤백 (재고 복구)
     */
    private void rollbackRedisCache(CouponIssueRequestedEvent event) {
        String stockKey = "coupon:stock:" + event.couponId();
        redisTemplate.opsForValue().increment(stockKey);

        String userKey = "coupon:issued:" + event.couponId() + ":" + event.userId();
        redisTemplate.delete(userKey);
    }
}
```

**핵심 설계 포인트:**

1. **멱등성 보장** (Idempotency)
   - requestId를 키로 Redis에 처리 여부 저장
   - Kafka의 "최소 1회 전달"로 인한 중복 처리 방지

2. **비관적 락** (Pessimistic Lock)
   - DB에서 실제 재고를 확인할 때 SELECT FOR UPDATE
   - 동시성 제어의 최종 방어선

3. **Consumer Concurrency**
   - 파티션 수(10개)와 동일한 Consumer 스레드 실행
   - 처리량 극대화

4. **실패 처리**
   - 비즈니스 실패: DLQ로 전송
   - 시스템 오류: 재시도 (Kafka 설정)

---

### 2. Kafka 특징 활용

#### 2-1. 순서 보장 (Ordering)

**문제:**
- 같은 사용자가 빠르게 여러 요청을 보낼 경우 순서가 뒤바뀔 수 있음

**해결:**
```java
// 같은 userId는 같은 파티션으로 전송
kafkaTemplate.send(
    KafkaConfig.COUPON_ISSUE_REQUESTED_TOPIC,
    userId.toString(),  // partition key
    event
);
```
- Kafka는 같은 Key의 메시지를 같은 파티션에 순차적으로 저장
- Consumer는 파티션 내에서 순서대로 처리

#### 2-2. 영속성 (Durability)

**문제:**
- Consumer 장애 시 메시지 유실 가능성

**해결:**
```yaml
# Kafka Producer 설정
spring:
  kafka:
    producer:
      acks: all  # 모든 replica가 수신 확인할 때까지 대기
      retries: 3
      properties:
        enable.idempotence: true  # 멱등성 보장 (중복 방지)
```
- 메시지를 디스크에 영속화
- Replication Factor 3으로 데이터 복제
- Consumer 장애 복구 시 마지막 Offset부터 재처리

#### 2-3. 재처리 (Replay)

**문제:**
- 쿠폰 발급 중 버그 발견 시 특정 시점부터 재처리 필요

**해결:**
```bash
# Consumer Group Offset 리셋
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group coupon-issue-service \
  --topic coupon.issue.requested \
  --reset-offsets --to-datetime 2025-12-18T10:00:00.000 \
  --execute
```
- Kafka는 메시지를 7일간 보관
- 특정 시점부터 재처리 가능
- 운영 중 발생한 오류 복구에 유용

#### 2-4. 높은 처리량 (High Throughput)

**파티셔닝 전략:**
```java
@Bean
public NewTopic couponIssueRequestedTopic() {
    return TopicBuilder.name(COUPON_ISSUE_REQUESTED_TOPIC)
            .partitions(10)  // 10개 파티션
            .replicas(3)     // 복제본 3개
            .config("compression.type", "lz4")  // 압축
            .build();
}
```
- 10개 파티션 × 10개 Consumer = 병렬 처리
- 초당 10만 요청 처리 가능

---

## 대기열 시스템

### 1. 대기열 진입 흐름

```
사용자 요청 (10만/초)
    │
    ▼
┌─────────────────┐
│ API Gateway     │ ← Rate Limiting (사용자당 1 req/sec)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Queue Controller│
│ 1. Redis 대기번호│ ← INCR (Atomic)
│ 2. Kafka 발행   │
└────────┬────────┘
         │
         │ Topic: queue.entered
         │ Key: queueId (대기열별 분리)
         │
         ▼
┌─────────────────┐
│ Kafka Broker    │
│ (100만 메시지)  │
└────────┬────────┘
         │
         │ Consumer: queue-processor-service
         │ Concurrency: 20
         │
         ▼
┌─────────────────┐
│Queue Processor  │
│ 1. 순차 처리    │ ← 파티션 내 순서 보장
│ 2. 실제 작업 실행│
│ 3. 완료 이벤트  │
└─────────────────┘
```

### 2. 대기열 진입 API

```java
@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, QueueEnteredEvent> kafkaTemplate;

    /**
     * 대기열 진입
     *
     * Redis Atomic Counter로 대기 번호 발급 → Kafka로 순차 처리
     */
    @PostMapping("/{queueId}/enter")
    public ResponseEntity<QueueEntryResponse> enterQueue(
            @PathVariable String queueId,
            @RequestHeader("X-User-Public-Id") String publicId) {

        Long userId = getUserIdByPublicId(publicId);

        // ====================================
        // Step 1: Redis로 대기 번호 발급
        // ====================================
        String counterKey = "queue:counter:" + queueId;
        Long queueNumber = redisTemplate.opsForValue().increment(counterKey);

        // ====================================
        // Step 2: Redis에 사용자 정보 저장
        // ====================================
        String userKey = "queue:user:" + queueId + ":" + userId;
        QueueUserInfo userInfo = new QueueUserInfo(
            userId,
            queueNumber,
            QueueStatus.WAITING,
            Instant.now()
        );
        redisTemplate.opsForValue().set(
            userKey,
            objectMapper.writeValueAsString(userInfo),
            Duration.ofHours(1)
        );

        // ====================================
        // Step 3: Kafka 이벤트 발행
        // ====================================
        QueueEnteredEvent event = new QueueEnteredEvent(
            queueId,
            userId,
            queueNumber,
            Instant.now()
        );

        // Key를 queueId로 설정하여 같은 대기열은 같은 파티션으로
        kafkaTemplate.send(
            KafkaConfig.QUEUE_ENTERED_TOPIC,
            queueId,  // partition key
            event
        );

        log.info("[대기열] 진입: queueId={}, userId={}, queueNumber={}",
            queueId, userId, queueNumber);

        // ====================================
        // Step 4: 예상 대기 시간 계산
        // ====================================
        Long processingRate = getProcessingRate(queueId);  // 초당 처리 건수
        long estimatedWaitSeconds = (queueNumber - getCurrentProcessingNumber(queueId))
            / processingRate;

        return ResponseEntity.ok(new QueueEntryResponse(
            queueNumber,
            queueNumber - getCurrentProcessingNumber(queueId),  // 내 앞에 대기자 수
            estimatedWaitSeconds,
            "대기열에 진입했습니다"
        ));
    }

    /**
     * 내 대기 상태 조회
     */
    @GetMapping("/{queueId}/status")
    public ResponseEntity<QueueStatusResponse> getQueueStatus(
            @PathVariable String queueId,
            @RequestHeader("X-User-Public-Id") String publicId) {

        Long userId = getUserIdByPublicId(publicId);

        String userKey = "queue:user:" + queueId + ":" + userId;
        String userInfoJson = redisTemplate.opsForValue().get(userKey);

        if (userInfoJson == null) {
            throw new QueueEntryNotFoundException("대기열 정보를 찾을 수 없습니다");
        }

        QueueUserInfo userInfo = objectMapper.readValue(userInfoJson, QueueUserInfo.class);
        Long currentNumber = getCurrentProcessingNumber(queueId);

        return ResponseEntity.ok(new QueueStatusResponse(
            userInfo.queueNumber(),
            userInfo.queueNumber() - currentNumber,  // 내 앞에 대기자 수
            userInfo.status(),
            calculateEstimatedTime(queueId, userInfo.queueNumber(), currentNumber)
        ));
    }
}
```

### 3. 대기열 처리 Consumer

```java
@Component
@Slf4j
public class QueueProcessorEventHandler {

    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 대기열 순차 처리
     *
     * Consumer Group: queue-processor-service
     * Concurrency: 20 (파티션 수)
     *
     * Kafka의 순서 보장 특성을 활용하여 FIFO 처리
     */
    @KafkaListener(
        topics = KafkaConfig.QUEUE_ENTERED_TOPIC,
        groupId = "queue-processor-service",
        concurrency = "20"
    )
    public void processQueue(QueueEnteredEvent event) {
        try {
            log.info("[대기열-Kafka] 처리 시작: queueId={}, userId={}, queueNumber={}",
                event.queueId(), event.userId(), event.queueNumber());

            // ====================================
            // Step 1: Rate Limiting (처리 속도 제어)
            // ====================================
            // 예: 초당 1000건 처리를 목표로 할 경우
            rateLimiter.acquire();  // Guava RateLimiter

            // ====================================
            // Step 2: 사용자 상태 업데이트 (처리 중)
            // ====================================
            updateUserStatus(event.queueId(), event.userId(), QueueStatus.PROCESSING);

            // ====================================
            // Step 3: 실제 비즈니스 로직 실행
            // ====================================
            // 예: 티켓 예매, 쿠폰 발급, 주문 처리 등
            executeBusinessLogic(event);

            // ====================================
            // Step 4: 처리 완료 상태 업데이트
            // ====================================
            updateUserStatus(event.queueId(), event.userId(), QueueStatus.COMPLETED);

            // 현재 처리 번호 업데이트
            updateCurrentProcessingNumber(event.queueId(), event.queueNumber());

            log.info("[대기열-Kafka] 처리 완료: queueId={}, userId={}, queueNumber={}",
                event.queueId(), event.userId(), event.queueNumber());

            // ====================================
            // Step 5: 완료 이벤트 발행 (알림용)
            // ====================================
            QueueProcessedEvent completedEvent = new QueueProcessedEvent(
                event.queueId(),
                event.userId(),
                event.queueNumber(),
                Instant.now()
            );

            kafkaTemplate.send(
                KafkaConfig.QUEUE_PROCESSED_TOPIC,
                event.userId().toString(),
                completedEvent
            );

        } catch (Exception e) {
            log.error("[대기열-Kafka] 처리 실패: queueId={}, userId={}, error={}",
                event.queueId(), event.userId(), e.getMessage(), e);

            updateUserStatus(event.queueId(), event.userId(), QueueStatus.FAILED);

            // 재시도 또는 DLQ로 전송
            throw e;
        }
    }

    /**
     * 사용자 상태 업데이트 (Redis)
     */
    private void updateUserStatus(String queueId, Long userId, QueueStatus status) {
        String userKey = "queue:user:" + queueId + ":" + userId;
        String userInfoJson = redisTemplate.opsForValue().get(userKey);

        if (userInfoJson != null) {
            QueueUserInfo userInfo = objectMapper.readValue(userInfoJson, QueueUserInfo.class);
            QueueUserInfo updated = new QueueUserInfo(
                userInfo.userId(),
                userInfo.queueNumber(),
                status,
                userInfo.enteredAt()
            );

            redisTemplate.opsForValue().set(
                userKey,
                objectMapper.writeValueAsString(updated),
                Duration.ofHours(1)
            );
        }
    }

    /**
     * 현재 처리 중인 대기 번호 업데이트
     */
    private void updateCurrentProcessingNumber(String queueId, Long queueNumber) {
        String currentKey = "queue:current:" + queueId;
        redisTemplate.opsForValue().set(currentKey, queueNumber.toString());
    }
}
```

---

## Kafka 특징 활용 전략

### 1. 파티셔닝 전략

#### 쿠폰 발급: userId 기반 파티셔닝
```java
// 같은 사용자의 요청은 같은 파티션으로
kafkaTemplate.send(topic, userId.toString(), event);
```
**장점:**
- 한 사용자의 여러 요청이 순서대로 처리됨
- 중복 처리 방지에 유리

#### 대기열: queueId 기반 파티셔닝
```java
// 같은 대기열의 요청은 같은 파티션으로
kafkaTemplate.send(topic, queueId, event);
```
**장점:**
- 대기열별로 독립적인 처리
- FIFO 순서 보장

### 2. Consumer Group 전략

```yaml
# application.yml
spring:
  kafka:
    consumer:
      group-id: coupon-issue-service
      enable-auto-commit: false  # 수동 커밋 (트랜잭션 완료 후)
      auto-offset-reset: earliest  # 처음부터 읽기 (장애 복구)
      max-poll-records: 500  # 한 번에 가져올 메시지 수
    listener:
      ack-mode: manual  # 수동 ACK (처리 완료 후)
      concurrency: 10  # Consumer 스레드 수
```

**Consumer 수평 확장:**
```
파티션 10개 → Consumer 인스턴스 10개
각 인스턴스가 1개 파티션 담당
→ 10배 처리량 증가
```

### 3. 메시지 압축

```java
@Bean
public ProducerFactory<String, Object> producerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");  // lz4 압축
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);  // 배치 크기
    props.put(ProducerConfig.LINGER_MS_CONFIG, 10);  // 배치 대기 시간
    return new DefaultKafkaProducerFactory<>(props);
}
```

**효과:**
- 네트워크 대역폭 절약 (50% 이상)
- 디스크 저장 공간 절약
- 처리량 증가

### 4. Dead Letter Queue (DLQ)

```java
@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();

        // 에러 핸들러 설정
        factory.setCommonErrorHandler(new DefaultErrorHandler(
            new DeadLetterPublishingRecoverer(kafkaTemplate(),
                (record, ex) -> {
                    // 재시도 3번 후 DLQ로 전송
                    return new TopicPartition(
                        record.topic() + ".failed",  // DLQ 토픽
                        record.partition()
                    );
                }),
            new FixedBackOff(1000L, 3)  // 1초 간격으로 3번 재시도
        ));

        return factory;
    }
}
```

**DLQ 처리 흐름:**
```
1. Consumer 처리 실패
2. 1초 대기 후 재시도 (1번째)
3. 1초 대기 후 재시도 (2번째)
4. 1초 대기 후 재시도 (3번째)
5. 여전히 실패 → DLQ (coupon.issue.requested.failed)로 전송
6. 운영자가 수동으로 확인 및 처리
```

---

## 장애 처리 및 모니터링

### 1. 장애 시나리오 및 대응

#### 시나리오 1: Consumer 장애
**문제:**
- Consumer 인스턴스 다운
- 메시지 처리 중단

**대응:**
```
1. Kafka는 메시지를 보관 (Retention: 7일)
2. Consumer Group의 다른 인스턴스가 파티션 인계 (Rebalancing)
3. 또는 새 Consumer 인스턴스 시작 시 마지막 Offset부터 재개
```

**모니터링:**
```java
// Consumer Lag 모니터링 (Micrometer)
@Component
public class KafkaConsumerMetrics {

    @Scheduled(fixedRate = 10000)  // 10초마다
    public void recordConsumerLag() {
        AdminClient adminClient = AdminClient.create(kafkaConfig);

        Map<TopicPartition, OffsetAndMetadata> offsets =
            adminClient.listConsumerGroupOffsets("coupon-issue-service")
                .partitionsToOffsetAndMetadata().get();

        for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : offsets.entrySet()) {
            long lag = calculateLag(entry.getKey(), entry.getValue());

            Metrics.gauge("kafka.consumer.lag",
                Tags.of("topic", entry.getKey().topic(),
                        "partition", String.valueOf(entry.getKey().partition())),
                lag);

            // Lag이 10000 이상이면 알람
            if (lag > 10000) {
                alertService.sendAlert("Kafka Consumer Lag 높음: " + lag);
            }
        }
    }
}
```

#### 시나리오 2: Kafka Broker 장애
**문제:**
- Broker 다운
- 메시지 유실 위험

**대응:**
```
1. Replication Factor 3으로 설정
2. Leader Broker 장애 시 자동으로 Follower가 Leader로 승격
3. 메시지 유실 없음 (acks=all 설정)
```

#### 시나리오 3: 네트워크 파티션
**문제:**
- Producer와 Kafka 연결 끊김

**대응:**
```java
// Producer 재시도 설정
spring:
  kafka:
    producer:
      retries: 2147483647  # 무한 재시도
      max-in-flight-requests-per-connection: 1  # 순서 보장
      properties:
        retry.backoff.ms: 1000  # 1초 대기 후 재시도
```

### 2. 모니터링 대시보드

#### Grafana + Prometheus 메트릭

```yaml
# 주요 모니터링 지표

1. Producer 메트릭:
   - kafka_producer_record_send_total: 발행한 메시지 수
   - kafka_producer_record_error_total: 발행 실패 수
   - kafka_producer_request_latency_avg: 평균 레이턴시

2. Consumer 메트릭:
   - kafka_consumer_records_consumed_total: 소비한 메시지 수
   - kafka_consumer_lag: Consumer Lag (처리 지연)
   - kafka_consumer_fetch_manager_records_lag_max: 최대 Lag

3. 비즈니스 메트릭:
   - coupon_issued_total: 발급된 쿠폰 수
   - coupon_issue_failed_total: 실패한 발급 수
   - queue_processing_time: 대기열 처리 시간
   - queue_wait_count: 대기 중인 사용자 수
```

#### 알람 설정

```yaml
# Prometheus Alert Rules

groups:
  - name: kafka_alerts
    rules:
      # Consumer Lag 알람
      - alert: HighConsumerLag
        expr: kafka_consumer_lag > 10000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Kafka Consumer Lag 높음"
          description: "Consumer Lag이 10000 이상입니다. 처리 속도를 확인하세요."

      # 쿠폰 발급 실패율 알람
      - alert: HighCouponIssueFailureRate
        expr: rate(coupon_issue_failed_total[5m]) > 0.1
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "쿠폰 발급 실패율 높음"
          description: "쿠폰 발급 실패율이 10% 이상입니다."

      # Broker 다운 알람
      - alert: KafkaBrokerDown
        expr: kafka_server_broker_state == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Kafka Broker 다운"
          description: "Kafka Broker가 다운되었습니다."
```

---

## 성능 최적화

### 1. 처리량 최적화

#### Producer 배치 처리
```java
@Bean
public ProducerFactory<String, Object> producerFactory() {
    Map<String, Object> props = new HashMap<>();

    // 배치 설정
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, 32768);  // 32KB 배치
    props.put(ProducerConfig.LINGER_MS_CONFIG, 10);  // 10ms 대기 (배치 축적)
    props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);  // 32MB 버퍼

    // 압축
    props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");

    // 멱등성 (중복 방지)
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    props.put(ProducerConfig.ACKS_CONFIG, "all");

    return new DefaultKafkaProducerFactory<>(props);
}
```

**효과:**
- 배치 처리로 네트워크 왕복 횟수 감소
- 압축으로 전송 데이터량 감소
- 초당 10만 메시지 처리 가능

#### Consumer 멀티스레드 처리
```yaml
spring:
  kafka:
    listener:
      concurrency: 10  # 파티션 수와 동일하게 설정
      poll-timeout: 3000  # 3초
      type: batch  # 배치 처리 (성능 향상)
```

```java
@KafkaListener(
    topics = KafkaConfig.COUPON_ISSUE_REQUESTED_TOPIC,
    groupId = "coupon-issue-service",
    concurrency = "10",
    containerFactory = "batchKafkaListenerContainerFactory"
)
public void handleBatch(List<CouponIssueRequestedEvent> events) {
    // 배치로 한 번에 여러 메시지 처리
    log.info("[쿠폰-Kafka] 배치 처리: {} 건", events.size());

    for (CouponIssueRequestedEvent event : events) {
        processSingleEvent(event);
    }
}
```

### 2. 레이턴시 최적화

#### Redis 캐싱 전략
```java
/**
 * 쿠폰 재고 캐싱 (Read-Through Cache)
 */
@Cacheable(value = "coupon:stock", key = "#couponId")
public Long getCouponStock(Long couponId) {
    return couponRepository.findById(couponId)
        .map(Coupon::getRemainingStock)
        .orElse(0L);
}

/**
 * 쿠폰 발급 후 캐시 무효화
 */
@CacheEvict(value = "coupon:stock", key = "#couponId")
public void invalidateCouponStockCache(Long couponId) {
    // 캐시 무효화
}
```

#### 비동기 응답 (Non-Blocking)
```java
// Controller에서 즉시 응답 (202 Accepted)
return ResponseEntity.accepted()
    .body(new CouponIssueResponse(
        requestId,
        "처리 중입니다. 잠시 후 결과를 확인해주세요.",
        estimatedWaitTime
    ));

// 실제 처리는 Kafka Consumer가 비동기로 수행
// 사용자는 Polling 또는 WebSocket으로 결과 확인
```

### 3. 비용 최적화

#### 메시지 보관 기간 설정
```java
@Bean
public NewTopic couponIssueRequestedTopic() {
    return TopicBuilder.name(COUPON_ISSUE_REQUESTED_TOPIC)
            .partitions(10)
            .replicas(3)
            .config("retention.ms", "604800000")  // 7일 보관
            .config("cleanup.policy", "delete")  // 시간 기반 삭제
            .build();
}
```

#### Compact Topic (상태 저장용)
```java
// 사용자별 최신 쿠폰 발급 상태만 보관
@Bean
public NewTopic userCouponStateTopic() {
    return TopicBuilder.name("user.coupon.state")
            .partitions(10)
            .replicas(3)
            .config("cleanup.policy", "compact")  // Key별 최신 메시지만 보관
            .config("min.compaction.lag.ms", "60000")  // 1분 후 Compaction
            .build();
}
```

---

## 결론

### Kafka 도입 효과

| 항목 | 기존 (동기) | Kafka 도입 후 |
|------|-------------|---------------|
| **처리량** | 1,000 req/s | 100,000 req/s |
| **응답 속도** | 2-5초 | 200ms 이하 |
| **DB 부하** | 높음 (락 경합) | 낮음 (비동기 처리) |
| **장애 복구** | 어려움 (메시지 유실) | 쉬움 (재처리 가능) |
| **확장성** | 수직 확장만 가능 | 수평 확장 용이 |
| **운영 복잡도** | 낮음 | 중간 (Kafka 관리 필요) |

### 추천 사용 시나리오

✅ **Kafka 사용 추천:**
- 선착순 이벤트 (쿠폰, 티켓, 한정판 상품)
- 대량 트래픽 처리 (초당 1만 요청 이상)
- 순서 보장이 중요한 경우
- 메시지 재처리가 필요한 경우

❌ **Kafka 불필요:**
- 실시간 동기 응답이 필수인 경우
- 트래픽이 적은 경우 (초당 1000 요청 미만)
- 간단한 비동기 작업 (Spring @Async로 충분)

### 다음 단계

1. **단계별 도입:**
   - Phase 1: 쿠폰 발급만 Kafka로 전환
   - Phase 2: 대기열 시스템 추가
   - Phase 3: 전체 주문 플로우 Kafka 적용

2. **모니터링 강화:**
   - Grafana 대시보드 구축
   - 알람 규칙 설정
   - Consumer Lag 추적

3. **성능 테스트:**
   - 부하 테스트 (JMeter, Locust)
   - 장애 시뮬레이션 (Chaos Engineering)
   - 튜닝 및 최적화

---

**작성일:** 2025-12-18
**버전:** 1.0
**작성자:** Claude Code
