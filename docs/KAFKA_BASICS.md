# Kafka 기본 개념 학습 문서

## 목차
1. [Kafka란 무엇인가?](#kafka란-무엇인가)
2. [핵심 개념](#핵심-개념)
3. [왜 Kafka를 사용하는가?](#왜-kafka를-사용하는가)
4. [Kafka 아키텍처](#kafka-아키텍처)
5. [메시지 전달 보장](#메시지-전달-보장)
6. [실제 사용 예시](#실제-사용-예시)
7. [우리 프로젝트에서의 활용](#우리-프로젝트에서의-활용)

---

## Kafka란 무엇인가?

Apache Kafka는 **분산 이벤트 스트리밍 플랫폼**입니다.

### 간단 비유
```
Kafka = 대용량 고속도로 + 물류 창고

- 생산자(Producer): 택배를 보내는 사람
- Kafka: 물류 센터 (메시지를 저장하고 전달)
- 소비자(Consumer): 택배를 받는 사람
```

### 특징
- **고성능**: 초당 수백만 개의 메시지 처리
- **확장성**: 서버를 추가하여 처리량 증가
- **내구성**: 메시지를 디스크에 저장 (데이터 손실 방지)
- **분산 시스템**: 여러 서버에 데이터 복제
- **순서 보장**: 같은 파티션 내에서 메시지 순서 유지

---

## 핵심 개념

### 1. Producer (생산자)
**메시지를 Kafka에 발행하는 애플리케이션**

```java
// Spring Boot에서 Producer 예시
@Service
public class OrderService {

    @Autowired
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    public void createOrder(Order order) {
        // 주문 생성
        orderRepository.save(order);

        // Kafka에 이벤트 발행
        OrderEvent event = new OrderEvent(order.getId(), order.getAmount());
        kafkaTemplate.send("order.created", event);
    }
}
```

**역할:**
- Kafka 브로커에 메시지 전송
- 메시지를 어느 토픽, 어느 파티션에 보낼지 결정
- 실패 시 재시도 처리

---

### 2. Consumer (소비자)
**Kafka로부터 메시지를 읽는 애플리케이션**

```java
// Spring Boot에서 Consumer 예시
@Service
public class PaymentService {

    @KafkaListener(topics = "order.created", groupId = "payment-service")
    public void handleOrderCreated(OrderEvent event) {
        // 주문 이벤트 수신
        log.info("주문 생성됨: {}", event.getOrderId());

        // 결제 처리
        processPayment(event);
    }
}
```

**역할:**
- Kafka 브로커로부터 메시지 읽기
- 읽은 위치(offset) 관리
- Consumer Group으로 부하 분산

---

### 3. Broker (브로커)
**Kafka 서버 (메시지를 저장하고 전달하는 노드)**

```
┌─────────────────────────────────────────┐
│         Kafka Cluster                   │
│                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐
│  │ Broker 1 │  │ Broker 2 │  │ Broker 3 │
│  └──────────┘  └──────────┘  └──────────┘
│                                         │
└─────────────────────────────────────────┘
```

**역할:**
- 메시지 저장 (디스크에 영구 저장)
- Producer로부터 메시지 수신
- Consumer에게 메시지 전달
- 다른 브로커와 데이터 복제

**프로덕션 구성:**
- 최소 3개의 브로커 (고가용성)
- 데이터 복제 (Replication)
- 브로커 하나가 죽어도 서비스 유지

---

### 4. Topic (토픽)
**메시지를 분류하는 카테고리 (채널, 주제)**

```
Kafka Cluster
├── Topic: order.created      (주문 생성 이벤트)
├── Topic: payment.completed  (결제 완료 이벤트)
├── Topic: coupon.issued      (쿠폰 발급 이벤트)
└── Topic: user.registered    (회원가입 이벤트)
```

**특징:**
- 논리적인 메시지 분류
- 여러 Producer가 같은 토픽에 발행 가능
- 여러 Consumer가 같은 토픽 구독 가능
- 토픽은 여러 파티션으로 구성

---

### 5. Partition (파티션)
**토픽을 물리적으로 나눈 단위 (병렬 처리의 핵심)**

```
Topic: order.created (3개 파티션)

┌─────────────────────────────────────────────┐
│  Partition 0                                │
│  [msg1] [msg4] [msg7] [msg10] ...          │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│  Partition 1                                │
│  [msg2] [msg5] [msg8] [msg11] ...          │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│  Partition 2                                │
│  [msg3] [msg6] [msg9] [msg12] ...          │
└─────────────────────────────────────────────┘
```

**왜 파티션이 필요한가?**

1. **병렬 처리**
```
파티션 1개:
Consumer 1 → Partition 0 (처리량: 100/초)

파티션 3개:
Consumer 1 → Partition 0 (100/초)
Consumer 2 → Partition 1 (100/초)
Consumer 3 → Partition 2 (100/초)
→ 총 처리량: 300/초
```

2. **순서 보장**
- 같은 파티션 내에서는 메시지 순서 보장
- 같은 키(key)를 가진 메시지는 같은 파티션에 저장

```java
// 같은 사용자의 메시지는 같은 파티션에 저장 (순서 보장)
kafkaTemplate.send("user.events",
    userId.toString(),  // key (같은 userId는 같은 파티션)
    event               // value
);
```

---

### 6. Offset (오프셋)
**파티션 내 메시지의 위치 (읽은 지점 추적)**

```
Partition 0:
┌────┬────┬────┬────┬────┬────┬────┐
│ 0  │ 1  │ 2  │ 3  │ 4  │ 5  │ 6  │ ...
└────┴────┴────┴────┴────┴────┴────┘
            ↑
      Consumer의 현재 offset = 2
      (0, 1, 2까지 읽음, 다음은 3부터)
```

**특징:**
- Consumer가 어디까지 읽었는지 기록
- 장애 발생 시 마지막 offset부터 재개
- Consumer Group별로 별도 관리

---

### 7. Consumer Group (컨슈머 그룹)
**같은 토픽을 구독하는 Consumer들의 묶음**

```
Topic: order.created (3개 파티션)

Consumer Group A: payment-service
├─ Consumer 1 → Partition 0
├─ Consumer 2 → Partition 1
└─ Consumer 3 → Partition 2

Consumer Group B: notification-service
├─ Consumer 1 → Partition 0, 1, 2

→ 같은 메시지를 두 서비스가 독립적으로 처리!
```

**특징:**
- 같은 그룹 내 Consumer는 파티션을 나눠서 처리 (부하 분산)
- 다른 그룹은 독립적으로 메시지 소비 (같은 메시지 중복 처리 가능)
- Consumer가 죽으면 다른 Consumer가 파티션 인계 (Rebalancing)

---

### 8. Zookeeper
**Kafka 클러스터를 관리하는 코디네이터**

```
역할:
├─ 브로커 상태 추적 (살아있는지 확인)
├─ 리더 선출 (파티션 Leader/Follower 관리)
├─ 메타데이터 저장 (토픽, 파티션 정보)
└─ 컨트롤러 브로커 선출
```

**참고:** Kafka 3.3+ 부터는 KRaft 모드로 Zookeeper 없이 실행 가능

---

## 왜 Kafka를 사용하는가?

### 1. 비동기 처리 (성능 향상)

**Before (동기):**
```java
public Order createOrder(OrderRequest request) {
    Order order = saveOrder(request);         // 100ms
    decreaseStock(order);                     // 200ms
    processPayment(order);                    // 500ms
    sendEmail(order);                         // 300ms
    updateAnalytics(order);                   // 100ms

    return order;  // 총 1200ms 소요
}
```

**After (Kafka):**
```java
public Order createOrder(OrderRequest request) {
    Order order = saveOrder(request);         // 100ms
    kafkaTemplate.send("order.created", order); // 5ms

    return order;  // 총 105ms 소요! (11배 빠름)
}

// 나머지는 비동기로 처리
@KafkaListener(topics = "order.created")
public void handleOrderCreated(OrderEvent event) {
    decreaseStock(event);
    processPayment(event);
    sendEmail(event);
    updateAnalytics(event);
}
```

---

### 2. 서비스 간 결합도 감소 (MSA)

**Before (강한 결합):**
```java
// OrderService가 모든 서비스에 직접 의존
public class OrderService {
    @Autowired StockService stockService;
    @Autowired PaymentService paymentService;
    @Autowired EmailService emailService;

    public Order createOrder() {
        // 모든 서비스를 직접 호출
        stockService.decrease(...);
        paymentService.process(...);
        emailService.send(...);
    }
}
```

**After (느슨한 결합):**
```java
// OrderService는 Kafka만 알면 됨
public class OrderService {
    @Autowired KafkaTemplate kafkaTemplate;

    public Order createOrder() {
        // 이벤트만 발행
        kafkaTemplate.send("order.created", event);
    }
}

// 각 서비스가 독립적으로 이벤트 구독
@Service
class StockService {
    @KafkaListener(topics = "order.created")
    void handle(OrderEvent e) { ... }
}

@Service
class PaymentService {
    @KafkaListener(topics = "order.created")
    void handle(OrderEvent e) { ... }
}
```

---

### 3. 확장성

**수평 확장 (Scale Out):**
```
처리량이 부족할 때:
1. 파티션 추가 (3개 → 6개)
2. Consumer 추가 (3개 → 6개)
→ 처리량 2배!

브로커 부하가 높을 때:
1. 브로커 추가 (3대 → 6대)
2. 파티션 재분배
→ 브로커당 부하 절반!
```

---

### 4. 내구성 & 신뢰성

**메시지 영구 저장:**
```
Kafka는 메시지를 디스크에 저장!

Consumer가 죽어도 메시지 손실 없음:
1. Consumer 재시작
2. 마지막 offset부터 다시 읽기
3. 모든 메시지 처리 보장
```

**복제 (Replication):**
```
Replication Factor = 3

Partition 0:
├─ Broker 1: Leader   ⭐
├─ Broker 2: Replica
└─ Broker 3: Replica

Broker 1이 죽어도:
→ Broker 2가 Leader로 승격
→ 서비스 무중단!
```

---

### 5. 대량 데이터 처리

```
처리량:
├─ 초당 수백만 메시지 처리
├─ 테라바이트 단위 데이터 저장
└─ 수천 개의 Producer/Consumer 동시 처리

사용 사례:
├─ 실시간 로그 수집 (하루 수십억 건)
├─ 이벤트 스트리밍 (IoT 센서 데이터)
└─ 데이터 파이프라인 (빅데이터 처리)
```

---

## Kafka 아키텍처

### 전체 구조

```
┌───────────────────────────────────────────────────────────────┐
│                    Kafka Cluster                              │
│                                                               │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│   │  Broker 1    │  │  Broker 2    │  │  Broker 3    │      │
│   │              │  │              │  │              │      │
│   │ Topic: order │  │ Topic: order │  │ Topic: order │      │
│   │ - P0 (L)     │  │ - P1 (L)     │  │ - P2 (L)     │      │
│   │ - P1 (R)     │  │ - P2 (R)     │  │ - P0 (R)     │      │
│   │ - P2 (R)     │  │ - P0 (R)     │  │ - P1 (R)     │      │
│   └──────────────┘  └──────────────┘  └──────────────┘      │
│                                                               │
└───────────────────────────────────────────────────────────────┘
          ↑                                          ↓
          │                                          │
┌─────────────────┐                        ┌─────────────────┐
│   Producers     │                        │   Consumers     │
│                 │                        │                 │
│ ┌─────────────┐ │                        │ ┌─────────────┐ │
│ │Order Service│ │                        │ │Stock Service│ │
│ └─────────────┘ │                        │ └─────────────┘ │
│                 │                        │                 │
│ ┌─────────────┐ │                        │ ┌─────────────┐ │
│ │User Service │ │                        │ │Pay Service  │ │
│ └─────────────┘ │                        │ └─────────────┘ │
└─────────────────┘                        └─────────────────┘
```

**범례:**
- L: Leader (읽기/쓰기 담당)
- R: Replica (복제본, 백업)
- P0, P1, P2: Partition 0, 1, 2

---

### 메시지 흐름

```
1. Producer가 메시지 발행
   ↓
2. Kafka가 파티션 결정 (키 기반 해싱 또는 라운드로빈)
   ↓
3. Leader 파티션에 메시지 저장
   ↓
4. Replica에 복제
   ↓
5. Producer에게 ACK 응답
   ↓
6. Consumer가 메시지 읽기
   ↓
7. 처리 완료 후 offset commit
```

---

### 파티션 Leader/Follower

```
Topic: order.created (3 파티션, Replication Factor 3)

Partition 0:
├─ Broker 1: Leader   ⭐ (읽기/쓰기 처리)
├─ Broker 2: Follower   (복제만)
└─ Broker 3: Follower   (복제만)

Partition 1:
├─ Broker 2: Leader   ⭐
├─ Broker 3: Follower
└─ Broker 1: Follower

Partition 2:
├─ Broker 3: Leader   ⭐
├─ Broker 1: Follower
└─ Broker 2: Follower

→ 각 브로커가 일부 파티션의 Leader 역할 (부하 분산)
```

**Leader의 역할:**
- Producer로부터 메시지 받기
- Consumer에게 메시지 전달
- Follower에게 복제 명령

**Follower의 역할:**
- Leader의 메시지를 복제
- Leader 장애 시 Leader로 승격 대기

---

## 메시지 전달 보장

### 1. At Most Once (최대 한 번)
```
Producer → Kafka (ACK 기다리지 않음)

장점: 가장 빠름
단점: 메시지 손실 가능

사용 사례: 로그 수집 (일부 손실 허용)
```

### 2. At Least Once (최소 한 번)
```
Producer → Kafka → ACK 받을 때까지 재시도

장점: 메시지 손실 없음
단점: 중복 가능 (네트워크 지연으로 ACK 못 받고 재전송)

사용 사례: 대부분의 경우 (중복 제거 로직 추가)
```

### 3. Exactly Once (정확히 한 번)
```
Producer → Kafka (트랜잭션 + 멱등성)

장점: 메시지 손실 없음 + 중복 없음
단점: 가장 느림, 복잡

사용 사례: 금융 거래, 결제
```

**Spring Boot 설정:**
```yaml
spring:
  kafka:
    producer:
      acks: all  # 모든 Replica가 저장할 때까지 대기
      retries: 3
      enable-idempotence: true  # 멱등성 (중복 방지)
```

---

## 실제 사용 예시

### 예시 1: 주문 생성 이벤트

```java
// 1. Producer: 주문 생성
@Service
public class OrderService {

    @Autowired
    private KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        // 주문 저장
        Order order = orderRepository.save(new Order(request));

        // 이벤트 발행
        OrderCreatedEvent event = new OrderCreatedEvent(
            order.getId(),
            order.getUserId(),
            order.getAmount()
        );

        kafkaTemplate.send("order.created",
            order.getId().toString(),  // key (같은 주문은 같은 파티션)
            event
        );

        return order;
    }
}

// 2. Consumer: 재고 차감
@Service
public class StockService {

    @KafkaListener(
        topics = "order.created",
        groupId = "stock-service"
    )
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("재고 차감 시작: orderId={}", event.getOrderId());

        for (OrderItem item : event.getItems()) {
            productService.decreaseStock(item.getProductId(), item.getQuantity());
        }

        // 성공 이벤트 발행
        kafkaTemplate.send("stock.reserved",
            new StockReservedEvent(event.getOrderId())
        );
    }
}

// 3. Consumer: 결제 처리
@Service
public class PaymentService {

    @KafkaListener(
        topics = "order.created",
        groupId = "payment-service"
    )
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("결제 처리 시작: orderId={}", event.getOrderId());

        Payment payment = processPayment(event);

        // 성공 이벤트 발행
        kafkaTemplate.send("payment.completed",
            new PaymentCompletedEvent(payment.getId(), event.getOrderId())
        );
    }
}
```

---

### 예시 2: 선착순 쿠폰 발급 (대량 트래픽)

```java
// 1. Producer: 쿠폰 발급 요청
@Service
public class CouponService {

    public CouponQueueResponse requestCoupon(Long userId, Long couponId) {
        // Redis에 순위 저장
        Long position = redisCouponQueue.add(userId, couponId);

        // Kafka로 이벤트 발행 (비동기 처리)
        CouponIssueRequestedEvent event = new CouponIssueRequestedEvent(
            userId,
            couponId,
            System.currentTimeMillis()
        );

        kafkaTemplate.send("coupon.issue.requested",
            userId.toString(),
            event
        );

        return new CouponQueueResponse(position);
    }
}

// 2. Consumer: 쿠폰 발급 처리 (10개 병렬)
@Service
public class CouponIssueConsumer {

    @KafkaListener(
        topics = "coupon.issue.requested",
        groupId = "coupon-issue-processor",
        concurrency = "10"  // 10개 Consumer 병렬 처리
    )
    public void handleCouponIssueRequested(CouponIssueRequestedEvent event) {
        try {
            // 쿠폰 발급
            UserCoupon userCoupon = couponService.issueCoupon(
                event.getUserId(),
                event.getCouponId()
            );

            // 성공 이벤트 발행
            kafkaTemplate.send("coupon.issued",
                new CouponIssuedEvent(userCoupon.getId(), event.getUserId())
            );

        } catch (CouponSoldOutException e) {
            // 실패 이벤트 발행
            kafkaTemplate.send("coupon.issue.failed",
                new CouponIssueFailedEvent(event.getUserId(), "품절")
            );
        }
    }
}

// 3. Consumer: 알림 전송
@Service
public class NotificationService {

    @KafkaListener(topics = {"coupon.issued", "coupon.issue.failed"})
    public void handleCouponResult(Object event) {
        if (event instanceof CouponIssuedEvent issued) {
            sendPushNotification(issued.getUserId(), "쿠폰 발급 성공!");
        } else if (event instanceof CouponIssueFailedEvent failed) {
            sendPushNotification(failed.getUserId(), "쿠폰 발급 실패: " + failed.getReason());
        }
    }
}
```

**성능 비교:**
```
폴링 방식 (스케줄러):
- 1초마다 DB 조회
- 처리량: 10개/초
- 100만 요청: 27시간

Kafka 방식:
- 이벤트 기반 즉시 처리
- 10개 Consumer 병렬 처리
- 처리량: 100개/초
- 100만 요청: 2.7시간 (10배 빠름!)
```

---

## 우리 프로젝트에서의 활용

### 현재 사용 중

#### 1. PAYMENT_COMPLETED → 외부 시스템 전송

```java
// OutboxProcessor.java
@Scheduled(fixedDelayString = "${scheduler.outbox.fixed-delay}")
public void processOutboxEvents() {
    List<OutboxEvent> pendingEvents = outboxEventRepository
        .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, MAX_RETRY_COUNT);

    for (OutboxEvent event : pendingEvents) {
        // Kafka로 전송
        kafkaTemplate.send(
            "payment.completed",
            event.getAggregateId().toString(),
            event.getPayload()
        );
    }
}
```

**흐름:**
```
결제 완료 → Outbox 저장 → OutboxProcessor(5초마다)
→ Kafka 발행 → 외부 데이터 플랫폼 (Consumer)
```

---

### 추가 예정

#### 1. Choreography 패턴의 내부 이벤트 (MSA 분리 시)

**현재 (ApplicationEventPublisher):**
```java
// 같은 앱 안에서만 동작
eventPublisher.publishEvent(new OrderCreatedEvent(...));

@TransactionalEventListener
public void handleOrderCreated(OrderCreatedEvent event) { ... }
```

**Kafka 적용 후:**
```java
// 다른 서비스에도 전달 가능
kafkaTemplate.send("order.created", event);

@KafkaListener(topics = "order.created")
public void handleOrderCreated(OrderCreatedEvent event) { ... }
```

---

#### 2. 선착순 쿠폰 발급 (성능 개선)

**현재 (폴링 방식):**
```java
@Scheduled(fixedDelay = 1000)  // 1초마다 DB 조회
public void processQueue() {
    List<CouponQueue> waiting = couponQueueRepository.findWaiting();
    // 처리
}
```

**Kafka 적용 후:**
```java
// 요청 즉시 처리 (폴링 불필요)
kafkaTemplate.send("coupon.issue.requested", event);

@KafkaListener(topics = "coupon.issue.requested", concurrency = "10")
public void handleCouponIssue(CouponIssueRequestedEvent event) {
    // 10개 Consumer 병렬 처리
}
```

---

## Spring Boot Kafka 설정

### application.yml

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092  # Kafka 브로커 주소

    # Producer 설정
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all  # 모든 Replica가 저장할 때까지 대기
      retries: 3
      enable-idempotence: true  # 멱등성 (중복 방지)

    # Consumer 설정
    consumer:
      group-id: ecommerce-service
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: com.hhplus.ecommerce.domain.event
      auto-offset-reset: earliest  # 가장 오래된 메시지부터 읽기
      enable-auto-commit: false  # 수동 commit (신뢰성)
```

---

## 모니터링 & 운영

### 주요 메트릭

```
Producer:
├─ 처리량 (messages/sec)
├─ 지연시간 (latency)
├─ 실패율 (error rate)
└─ 재시도 횟수

Consumer:
├─ 처리량 (messages/sec)
├─ Lag (처리 지연, 얼마나 밀렸는지)
├─ 처리 시간
└─ 실패율

Broker:
├─ CPU, 메모리, 디스크 사용량
├─ 네트워크 I/O
├─ 파티션 리더 분포
└─ Under-replicated 파티션 수
```

### Consumer Lag 확인

```bash
# Kafka Consumer Lag 조회
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group payment-service \
  --describe

# 출력:
# TOPIC           PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
# order.created   0          1000            1000            0
# order.created   1          1000            1050            50  ← 50개 밀림!
# order.created   2          1000            1000            0
```

**Lag이 크면?**
- Consumer 처리 속도가 느림
- Consumer 수를 늘려 병렬 처리
- 파티션 수 증가 고려

---

## 참고 자료

- [Apache Kafka 공식 문서](https://kafka.apache.org/documentation/)
- [Confluent Kafka 튜토리얼](https://docs.confluent.io/platform/current/tutorials/examples/clients/docs/java.html)
- [Spring for Apache Kafka](https://spring.io/projects/spring-kafka)
- [우리 프로젝트의 Saga 패턴 비교](./SAGA_PATTERN_COMPARISON.md)
- [Choreography 이벤트 흐름](./CHOREOGRAPHY_EVENT_FLOW.md)

---

## 마치며

Kafka는 강력하지만 **필요할 때만 사용**해야 합니다.

**Kafka가 필요한 경우:**
- 대량 트래픽 (초당 수만~수백만 메시지)
- MSA 환경 (서비스 간 비동기 통신)
- 이벤트 기반 아키텍처
- 메시지 손실 불가 (금융, 결제)
- 실시간 데이터 스트리밍

**Kafka가 불필요한 경우:**
- 소규모 프로젝트 (모놀리식)
- 낮은 트래픽 (초당 수십 건)
- 단순 비동기 처리 (Redis Queue로 충분)
- 동기 처리가 더 적합한 경우

**우리 프로젝트:**
- 현재: PAYMENT_COMPLETED만 Kafka 사용 (외부 전송)
- 계획: 선착순 쿠폰에 적용 (성능 개선)
- 미래: MSA 분리 시 모든 이벤트에 적용
