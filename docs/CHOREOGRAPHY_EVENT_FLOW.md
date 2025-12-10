# Choreography 패턴 - 이벤트 흐름 가이드

## 개요

이 문서는 주문 시스템에서 Choreography 패턴을 사용한 이벤트 기반 아키텍처의 전체 흐름을 설명합니다.

## 패턴 비교

### Orchestration vs Choreography

| 구분 | Orchestration | Choreography |
|------|--------------|--------------|
| **관리 방식** | 중앙 관리자 (UseCase) | 분산 관리 (이벤트) |
| **실행 순서** | 명시적 (코드로 표현) | 암시적 (이벤트 체인) |
| **결합도** | 강한 결합 | 느슨한 결합 |
| **확장성** | 낮음 (UseCase 수정 필요) | 높음 (핸들러만 추가) |
| **디버깅** | 쉬움 (스택 트레이스) | 어려움 (비동기) |
| **트랜잭션** | 동기 (즉시 완료) | 비동기 (최종 일관성) |
| **주문 상태** | PAID (즉시 완료) | PENDING → CONFIRMED/FAILED |

## Choreography 이벤트 흐름

### 전체 흐름도

```
┌─────────────────────────────────────────────────────────────────────┐
│ 1. ChoreographyPlaceOrderUseCase                                    │
│    - 주문 생성 (PENDING)                                             │
│    - OrderCreatedEvent 발행                                         │
└───────────────────────────┬─────────────────────────────────────────┘
                            │
                            ▼
              ┌─────────────────────────┐
              │  OrderCreatedEvent      │
              │  (주문 생성 완료)        │
              └─────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
        ▼                   ▼                   ▼
┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│ 2-1. Stock    │   │ 2-2. Payment  │   │ 2-3. Coupon   │
│   Handler     │   │   Handler     │   │   Handler     │
│   (병렬 실행)  │   │   (병렬 실행)  │   │   (병렬 실행)  │
└───────────────┘   └───────────────┘   └───────────────┘
        │                   │                   │
        ▼                   ▼                   ▼
  재고 차감            잔액 차감            쿠폰 사용
        │                   │                   │
        ▼                   ▼                   ▼
┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│StockReserved  │   │PaymentCompleted│  │CouponUsed     │
│Event          │   │Event          │   │Event          │
└───────────────┘   └───────────────┘   └───────────────┘
        │                   │                   │
        └───────────────────┼───────────────────┘
                            ▼
              ┌─────────────────────────┐
              │ 3. OrderSagaEventHandler│
              │    (모든 이벤트 수집)    │
              └─────────────────────────┘
                            │
        ┌───────────────────┴───────────────────┐
        │                                       │
        ▼                                       ▼
┌─────────────────┐                   ┌─────────────────┐
│ 4-A. 모두 성공   │                   │ 4-B. 하나라도    │
│                 │                   │      실패        │
│ OrderConfirmed  │                   │ OrderFailed     │
│ Event           │                   │ Event           │
│ (주문 확정)      │                   │ (보상 트랜잭션)  │
└─────────────────┘                   └─────────────────┘
        │                                       │
        ▼                                       ▼
  주문 상태:                             보상 트랜잭션:
  CONFIRMED                              - 재고 복구
                                         - 환불 처리
                                         - 쿠폰 복구
                                         주문 상태: FAILED
```

## 상세 이벤트 흐름

### 1단계: 주문 생성

**UseCase**: `ChoreographyPlaceOrderUseCase`

```java
@Transactional
public Order execute(String publicId, CreateOrderRequest request) {
    // 1. 사용자 조회
    // 2. 상품 조회
    // 3. 사전 검증 (재고, 잔액)
    // 4. 주문 금액 계산
    // 5. 주문 생성 (PENDING 상태)
    Order order = orderService.createOrder(...);

    // 6. 이벤트 발행
    eventPublisher.publishEvent(new OrderCreatedEvent(...));

    return order; // PENDING 상태로 반환
}
```

**발행 이벤트**: `OrderCreatedEvent`
- `orderId`: 주문 ID
- `userId`: 사용자 ID
- `items`: 주문 상품 목록
- `totalAmount`: 총 금액
- `discountAmount`: 할인 금액
- `finalAmount`: 최종 금액
- `userCouponId`: 쿠폰 ID (선택)

### 2단계: 병렬 처리 (각 도메인)

#### 2-1. 재고 처리 (StockEventHandler)

**구독 이벤트**: `OrderCreatedEvent`

**처리 로직**:
```java
@Async
@EventListener
public void handleOrderCreated(OrderCreatedEvent event) {
    try {
        // 1. 각 상품의 재고 차감
        for (OrderItem item : event.items()) {
            productService.decreaseStock(item.productId(), item.quantity());
        }

        // 2. 예약 정보 저장 (보상 트랜잭션용)
        reservations.put(event.orderId(), reservation);

        // 3. 성공 이벤트 발행
        eventPublisher.publishEvent(new StockReservedEvent(
            event.orderId(),
            reservationId
        ));
    } catch (Exception e) {
        // 4. 실패 이벤트 발행
        eventPublisher.publishEvent(new StockReservationFailedEvent(
            event.orderId(),
            e.getMessage()
        ));
    }
}
```

**발행 이벤트**:
- 성공: `StockReservedEvent(orderId, reservationId)`
- 실패: `StockReservationFailedEvent(orderId, reason)`

#### 2-2. 결제 처리 (PaymentEventHandler)

**구독 이벤트**: `OrderCreatedEvent`

**처리 로직**:
```java
@Async
@EventListener
public void handleOrderCreated(OrderCreatedEvent event) {
    try {
        // 1. 잔액 차감
        userService.deductBalance(event.userId(), event.finalAmount());

        // 2. 결제 엔티티 생성
        Payment payment = paymentService.createPayment(order, event.finalAmount());

        // 3. 성공 이벤트 발행
        eventPublisher.publishEvent(new PaymentCompletedEvent(
            event.orderId(),
            payment.getId(),
            event.finalAmount()
        ));
    } catch (Exception e) {
        // 4. 실패 이벤트 발행
        eventPublisher.publishEvent(new PaymentFailedEvent(
            event.orderId(),
            e.getMessage()
        ));
    }
}
```

**발행 이벤트**:
- 성공: `PaymentCompletedEvent(orderId, paymentId, amount)`
- 실패: `PaymentFailedEvent(orderId, reason)`

#### 2-3. 쿠폰 처리 (CouponEventHandler)

**구독 이벤트**: `OrderCreatedEvent`

**처리 로직**:
```java
@Async
@EventListener
public void handleOrderCreated(OrderCreatedEvent event) {
    // 쿠폰을 사용하지 않는 경우
    if (event.userCouponId() == null) {
        eventPublisher.publishEvent(new CouponUsedEvent(
            event.orderId(),
            null
        ));
        return;
    }

    try {
        // 1. 쿠폰 사용 처리
        couponService.useCoupon(event.userCouponId(), event.userId());

        // 2. 성공 이벤트 발행
        eventPublisher.publishEvent(new CouponUsedEvent(
            event.orderId(),
            event.userCouponId()
        ));
    } catch (Exception e) {
        // 3. 실패 이벤트 발행
        eventPublisher.publishEvent(new CouponUsageFailedEvent(
            event.orderId(),
            e.getMessage()
        ));
    }
}
```

**발행 이벤트**:
- 성공: `CouponUsedEvent(orderId, userCouponId)`
- 실패: `CouponUsageFailedEvent(orderId, reason)`

### 3단계: Saga 조율 (OrderSagaEventHandler)

**구독 이벤트**:
- `StockReservedEvent`
- `PaymentCompletedEvent`
- `CouponUsedEvent`
- `StockReservationFailedEvent`
- `PaymentFailedEvent`
- `CouponUsageFailedEvent`

**성공 처리**:
```java
@Async
@Transactional
@EventListener
public void handleStockReserved(StockReservedEvent event) {
    Order order = orderRepository.findByIdWithLock(event.orderId());

    // 1. 재고 예약 완료 표시
    order.getStepStatus().markStockReserved(event.reservationId());
    orderRepository.save(order);

    // 2. 모든 단계 완료 확인
    checkAndConfirmOrder(order);
}

private void checkAndConfirmOrder(Order order) {
    if (order.getStepStatus().allCompleted()) {
        // 모든 단계 성공 → 주문 확정
        order.confirm(); // status = CONFIRMED
        orderRepository.save(order);

        eventPublisher.publishEvent(new OrderConfirmedEvent(
            order.getId(),
            order.getStepStatus()
        ));
    }
}
```

**실패 처리**:
```java
@Async
@Transactional
@EventListener
public void handleStockReservationFailed(StockReservationFailedEvent event) {
    Order order = orderRepository.findByIdWithLock(event.orderId());

    // 1. 실패 상태 업데이트
    order.getStepStatus().markStockReservationFailed(event.reason());
    order.markAsFailed(event.reason()); // status = FAILED
    orderRepository.save(order);

    // 2. 보상 트랜잭션 이벤트 발행
    eventPublisher.publishEvent(new OrderFailedEvent(
        orderId,
        reason,
        order.getStepStatus().getCompletedSteps()  // 성공한 단계만
    ));
}
```

### 4단계: 최종 처리

#### 4-A. 모두 성공 → 주문 확정

**발행 이벤트**: `OrderConfirmedEvent`

**결과**:
- 주문 상태: `CONFIRMED`
- 각 도메인의 예약 정보 삭제 (더 이상 보상 불필요)

#### 4-B. 하나라도 실패 → 보상 트랜잭션

**발행 이벤트**: `OrderFailedEvent`
- `orderId`: 주문 ID
- `reason`: 실패 사유
- `completedSteps`: 성공한 단계 목록 (예: `["STOCK", "PAYMENT"]`)

**보상 트랜잭션 처리**:

각 핸들러가 `OrderFailedEvent`를 구독하고 자신이 성공했던 작업을 되돌림:

```java
// StockEventHandler
@EventListener
public void handleOrderFailed(OrderFailedEvent event) {
    if (event.completedSteps().contains("STOCK")) {
        // 재고 복구
        for (ReservationItem item : reservation.getItems()) {
            productService.increaseStock(item.productId(), item.quantity());
        }
    }
}

// PaymentEventHandler
@EventListener
public void handleOrderFailed(OrderFailedEvent event) {
    if (event.completedSteps().contains("PAYMENT")) {
        // 환불 처리
        userService.chargeBalance(userId, payment.getPaidAmount());
        paymentService.cancelPayment(paymentId);
    }
}

// CouponEventHandler
@EventListener
public void handleOrderFailed(OrderFailedEvent event) {
    if (event.completedSteps().contains("COUPON")) {
        // 쿠폰 복구
        couponService.restoreCoupon(userCouponId);
    }
}
```

**결과**:
- 주문 상태: `FAILED`
- 모든 성공했던 작업이 롤백됨

## 이벤트 목록

### 주문 이벤트

| 이벤트 | 발행자 | 구독자 | 목적 |
|--------|--------|--------|------|
| `OrderCreatedEvent` | ChoreographyPlaceOrderUseCase | Stock/Payment/CouponEventHandler | 주문 생성 알림 |
| `OrderConfirmedEvent` | OrderSagaEventHandler | Stock/Payment/CouponEventHandler | 주문 확정 알림 |
| `OrderFailedEvent` | OrderSagaEventHandler | Stock/Payment/CouponEventHandler | 보상 트랜잭션 트리거 |

### 재고 이벤트

| 이벤트 | 발행자 | 구독자 | 목적 |
|--------|--------|--------|------|
| `StockReservedEvent` | StockEventHandler | OrderSagaEventHandler | 재고 예약 성공 알림 |
| `StockReservationFailedEvent` | StockEventHandler | OrderSagaEventHandler | 재고 예약 실패 알림 |

### 결제 이벤트

| 이벤트 | 발행자 | 구독자 | 목적 |
|--------|--------|--------|------|
| `PaymentCompletedEvent` | PaymentEventHandler | OrderSagaEventHandler | 결제 완료 알림 |
| `PaymentFailedEvent` | PaymentEventHandler | OrderSagaEventHandler | 결제 실패 알림 |

### 쿠폰 이벤트

| 이벤트 | 발행자 | 구독자 | 목적 |
|--------|--------|--------|------|
| `CouponUsedEvent` | CouponEventHandler | OrderSagaEventHandler | 쿠폰 사용 성공 알림 |
| `CouponUsageFailedEvent` | CouponEventHandler | OrderSagaEventHandler | 쿠폰 사용 실패 알림 |

## 주문 상태 전이

```
┌─────────┐
│ PENDING │  ← ChoreographyPlaceOrderUseCase (초기 상태)
└────┬────┘
     │
     │  (모든 단계 성공)
     ├──────────────────┐
     │                  │
     │                  │ (하나라도 실패)
     ▼                  ▼
┌───────────┐      ┌─────────┐
│ CONFIRMED │      │ FAILED  │
└───────────┘      └─────────┘
```

## 실패 시나리오 예시

### 시나리오 1: 재고 부족

```
1. OrderCreatedEvent 발행
2. StockEventHandler: 재고 차감 시도 → 실패
3. StockReservationFailedEvent 발행
4. OrderSagaEventHandler: 주문 실패 처리
5. OrderFailedEvent 발행
6. 보상 트랜잭션:
   - PaymentEventHandler: (재고 차감 안됐으므로 보상 불필요)
   - CouponEventHandler: (재고 차감 안됐으므로 보상 불필요)
7. 최종 상태: FAILED
```

### 시나리오 2: 잔액 부족

```
1. OrderCreatedEvent 발행
2. StockEventHandler: 재고 차감 성공 → StockReservedEvent
3. PaymentEventHandler: 잔액 차감 시도 → 실패 → PaymentFailedEvent
4. CouponEventHandler: 쿠폰 사용 성공 → CouponUsedEvent
5. OrderSagaEventHandler: 주문 실패 처리
6. OrderFailedEvent 발행 (completedSteps: ["STOCK", "COUPON"])
7. 보상 트랜잭션:
   - StockEventHandler: 재고 복구
   - CouponEventHandler: 쿠폰 복구
8. 최종 상태: FAILED
```

### 시나리오 3: 쿠폰 사용 실패

```
1. OrderCreatedEvent 발행
2. StockEventHandler: 재고 차감 성공 → StockReservedEvent
3. PaymentEventHandler: 결제 성공 → PaymentCompletedEvent
4. CouponEventHandler: 쿠폰 사용 실패 → CouponUsageFailedEvent
5. OrderSagaEventHandler: 주문 실패 처리
6. OrderFailedEvent 발행 (completedSteps: ["STOCK", "PAYMENT"])
7. 보상 트랜잭션:
   - StockEventHandler: 재고 복구
   - PaymentEventHandler: 환불 처리
8. 최종 상태: FAILED
```

## API 사용 예시

### Choreography 패턴으로 주문 생성

```bash
# 요청
POST /api/orders/choreography/{publicId}
Content-Type: application/json

{
  "items": [
    {
      "productId": 1,
      "quantity": 2
    }
  ],
  "userCouponId": 123,
  "recipientName": "홍길동",
  "shippingAddress": "서울시 강남구",
  "shippingPhone": "010-1234-5678"
}

# 응답 (즉시 반환)
{
  "orderId": 456,
  "orderNumber": "uuid-string",
  "status": "PENDING",  ← 아직 처리 중
  "finalAmount": 50000,
  ...
}
```

### 주문 상태 조회

```bash
# 요청
GET /api/orders/{orderNumber}

# 응답 (비동기 처리 후)
{
  "orderId": 456,
  "orderNumber": "uuid-string",
  "status": "CONFIRMED",  ← 모든 단계 완료
  "finalAmount": 50000,
  ...
}

# 또는 실패한 경우
{
  "orderId": 456,
  "orderNumber": "uuid-string",
  "status": "FAILED",  ← 하나라도 실패
  "finalAmount": 50000,
  ...
}
```

## 모니터링 및 디버깅

### 로그 패턴

각 핸들러는 다음과 같은 로그를 남깁니다:

```
[재고] 재고 차감 시작: orderId=123, items=2
[재고] 재고 차감 완료: orderId=123, productId=1, quantity=2
[재고] 재고 차감 성공: orderId=123, reservationId=uuid

[결제] 결제 처리 시작: orderId=123, amount=50000
[결제] 잔액 차감 완료: orderId=123, userId=456, amount=50000
[결제] 결제 생성 완료: orderId=123, paymentId=789
[결제] 결제 처리 성공: orderId=123, paymentId=789

[쿠폰] 쿠폰 사용 시작: orderId=123, userCouponId=100
[쿠폰] 쿠폰 사용 완료: orderId=123, userCouponId=100

[주문-Saga] 재고 예약 성공 수신: orderId=123, reservationId=uuid
[주문-Saga] 결제 완료 수신: orderId=123, paymentId=789
[주문-Saga] 쿠폰 사용 완료 수신: orderId=123, userCouponId=100
[주문-Saga] 주문 확정: orderId=123
```

### 디버깅 팁

1. **주문 상태가 PENDING에서 변하지 않는 경우**:
   - 각 핸들러의 로그 확인
   - 어떤 이벤트가 발행되지 않았는지 추적

2. **보상 트랜잭션이 실행되지 않는 경우**:
   - OrderFailedEvent가 발행되었는지 확인
   - completedSteps에 올바른 단계가 포함되어 있는지 확인

3. **비동기 처리 순서 이해**:
   - 각 핸들러는 `@Async`로 병렬 실행
   - 순서 보장 필요 시 이벤트 체인 사용

## 장단점 비교

### Choreography 패턴의 장점

1. **느슨한 결합**: Service 간 직접 의존 없음
2. **확장성**: 새로운 도메인 추가 시 이벤트만 구독
3. **병렬 처리**: 재고, 결제, 쿠폰을 동시에 처리
4. **보상 트랜잭션 자동화**: 이벤트로 자동 롤백

### Choreography 패턴의 단점

1. **복잡한 흐름**: 로직이 여러 핸들러에 분산
2. **디버깅 어려움**: 비동기 처리로 추적 복잡
3. **최종 일관성**: 즉시 완료되지 않음
4. **모니터링 복잡**: 여러 핸들러 상태 추적 필요

### 언제 Choreography를 사용하나?

✅ **사용 권장**:
- 마이크로서비스 환경
- 높은 확장성이 필요한 경우
- 도메인 간 독립성이 중요한 경우
- 병렬 처리로 성능 향상이 필요한 경우

❌ **사용 비권장**:
- 모놀리식 환경
- 트랜잭션 즉시 완료가 필요한 경우
- 간단한 비즈니스 로직
- 디버깅/모니터링 도구가 부족한 경우

## 관련 문서

- [Orchestration vs Choreography 상세 비교](./SAGA_PATTERN_COMPARISON.md)
- [보상 트랜잭션 가이드](./SAGA_COMPENSATION_TRANSACTION_GUIDE.md)
- [이벤트 리스너 트랜잭션 컨텍스트](./EVENT_LISTENER_TRANSACTION_CONTEXT.md)

## 참고

- UseCase 구현: `ChoreographyPlaceOrderUseCase.java`
- Saga 조율자: `OrderSagaEventHandler.java`
- 이벤트 핸들러: `StockEventHandler.java`, `PaymentEventHandler.java`, `CouponEventHandler.java`
