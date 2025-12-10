# Saga 패턴 비교: Orchestration vs Choreography

## 개요

이 프로젝트는 **교육용**으로 두 가지 Saga 패턴을 모두 구현하여 비교할 수 있도록 설계되었습니다.

## 빠른 비교

| 특징 | Orchestration | Choreography |
|------|--------------|--------------|
| **UseCase** | `OrchestrationPlaceOrderUseCase` | `ChoreographyPlaceOrderUseCase` |
| **API 엔드포인트** | `/api/orders/orchestration/{publicId}` | `/api/orders/choreography/{publicId}` |
| **주문 완료 시간** | 즉시 (동기) | 비동기 (수백ms~수초) |
| **주문 초기 상태** | PAID | PENDING |
| **주문 최종 상태** | PAID | CONFIRMED / FAILED |
| **보상 트랜잭션** | UseCase에서 수동 관리 | 이벤트로 자동 관리 |
| **실행 순서** | 명시적 (코드로 표현) | 암시적 (이벤트 체인) |

## 상세 비교

### 1. 아키텍처 구조

#### Orchestration (중앙 관리자)

```
┌───────────────────────────────────────────┐
│   OrchestrationPlaceOrderUseCase          │
│   (중앙 관리자 - 모든 것을 제어)           │
│                                           │
│   execute() {                             │
│     1. userService.getUser()              │
│     2. productService.decreaseStock() ←┐  │
│     3. couponService.useCoupon()       │  │
│     4. userService.deductBalance()     │  │
│     5. paymentService.createPayment()  │  │
│     6. orderService.updateStatus()     │  │
│                                        │  │
│     try { ... }                        │  │
│     catch (Exception e) {              │  │
│       // 보상 트랜잭션                  │  │
│       productService.increaseStock() ──┘  │
│       couponService.restoreCoupon()       │
│       userService.chargeBalance()         │
│     }                                     │
│                                           │
│     // 부가 기능만 이벤트로              │
│     eventPublisher.publish(               │
│       OrderCompletedEvent                 │
│     )                                     │
│   }                                       │
└───────────────────────────────────────────┘
         │ 직접 의존
         ▼
  ┌─────────────────┐
  │ Service Layer   │
  │ - UserService   │
  │ - ProductService│
  │ - CouponService │
  │ - PaymentService│
  └─────────────────┘
```

**특징**:
- UseCase가 모든 Service를 **직접 호출**
- 실행 순서가 **코드로 명시**
- 보상 트랜잭션을 **UseCase에서 관리**
- 부가 기능만 이벤트로 처리 (랭킹, 알림 등)

#### Choreography (이벤트 중심)

```
┌───────────────────────────────────────────┐
│   ChoreographyPlaceOrderUseCase           │
│   (시작만 트리거)                          │
│                                           │
│   execute() {                             │
│     1. 주문 생성 (PENDING)                │
│     2. eventPublisher.publish(            │
│          OrderCreatedEvent                │
│        )                                  │
│     3. return order  // 여기서 끝!        │
│   }                                       │
└───────────────────────────────────────────┘
         │ 이벤트 발행
         ▼
  ┌─────────────────────┐
  │ OrderCreatedEvent   │
  └─────────────────────┘
         │
         ├──────────┬──────────┬──────────┐
         ▼          ▼          ▼          ▼
  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
  │  Stock   │ │ Payment  │ │  Coupon  │ │  Saga    │
  │ Handler  │ │ Handler  │ │ Handler  │ │ Handler  │
  └──────────┘ └──────────┘ └──────────┘ └──────────┘
         │          │          │          │
         │ (병렬 실행 - 비동기)  │          │
         │          │          │          │
         ▼          ▼          ▼          ▼
  성공/실패   성공/실패   성공/실패    결과 수집
   이벤트     이벤트     이벤트      및 조율
         │          │          │          │
         └──────────┴──────────┴──────────┘
                    │
                    ▼
          OrderConfirmedEvent
             또는
          OrderFailedEvent
```

**특징**:
- UseCase는 **주문 생성 + 이벤트만 발행**
- 각 핸들러가 **독립적으로 이벤트 구독**
- 보상 트랜잭션도 **이벤트로 자동 처리**
- OrderSagaEventHandler가 전체 **상태 추적**

### 2. 실행 흐름 비교

#### Orchestration: 동기 + 명시적

```java
// 1. 사용자 조회
User user = userService.getUserByPublicId(publicId);

// 2. 재고 차감
try {
    for (Product product : products) {
        productService.decreaseStock(product.getId(), quantity);
        decreasedProducts.add(product);  // 보상용 기록
    }

    // 3. 쿠폰 사용
    if (userCouponId != null) {
        couponService.useCoupon(userCouponId, user.getId());
        couponUsed = true;  // 보상용 플래그
    }

    // 4. 잔액 차감
    userService.deductBalanceByPublicId(publicId, finalAmount);
    balanceDeducted = true;  // 보상용 플래그

    // 5. 결제 생성
    payment = paymentService.createPayment(order, finalAmount);

    // 6. 주문 상태 변경 (PAID)
    orderService.updateOrderStatus(order.getId(), OrderStatus.PAID);

} catch (Exception e) {
    // ⚠️ 보상 트랜잭션 (수동)
    if (balanceDeducted) {
        userService.chargeBalanceByPublicId(publicId, finalAmount);
    }
    if (couponUsed) {
        couponService.restoreCoupon(userCouponId);
    }
    for (Product product : decreasedProducts) {
        productService.increaseStock(product.getId(), quantity);
    }
    throw e;
}

// 7. 부가 기능 (비동기)
eventPublisher.publishEvent(new OrderCompletedEvent(...));

return order;  // PAID 상태
```

**실행 시간**: 100~300ms (동기 처리)

#### Choreography: 비동기 + 이벤트 체인

```java
// 1. 주문 생성 (PENDING)
Order order = orderService.createOrder(
    user, userCoupon, recipientName, address, shippingPhone,
    totalAmount, discountAmount, finalAmount
);

// 2. 이벤트 발행
eventPublisher.publishEvent(new OrderCreatedEvent(
    order.getId(),
    user.getId(),
    eventItems,
    totalAmount,
    discountAmount,
    finalAmount,
    userCouponId
));

return order;  // PENDING 상태 (아직 처리 중!)

// --- 이후는 이벤트 핸들러들이 비동기로 처리 ---

// StockEventHandler:
//   - 재고 차감 → StockReservedEvent 발행

// PaymentEventHandler:
//   - 잔액 차감 + 결제 생성 → PaymentCompletedEvent 발행

// CouponEventHandler:
//   - 쿠폰 사용 → CouponUsedEvent 발행

// OrderSagaEventHandler:
//   - 모든 이벤트 수집
//   - 모두 성공 → OrderConfirmedEvent → status = CONFIRMED
//   - 하나라도 실패 → OrderFailedEvent → 자동 보상 트랜잭션
```

**실행 시간**:
- API 응답: 50~100ms (주문 생성만)
- 최종 완료: 200~500ms (비동기 처리 후)

### 3. 보상 트랜잭션 비교

#### Orchestration: 수동 보상

```java
try {
    // 핵심 로직
    productService.decreaseStock(productId, quantity);
    decreasedProducts.add(product);  // 👈 보상용 기록

    couponService.useCoupon(userCouponId, userId);
    couponUsed = true;  // 👈 보상용 플래그

    userService.deductBalance(userId, amount);
    balanceDeducted = true;  // 👈 보상용 플래그

} catch (Exception e) {
    // 👉 수동으로 역순 실행
    if (balanceDeducted) {
        userService.chargeBalance(userId, amount);
    }
    if (couponUsed) {
        couponService.restoreCoupon(userCouponId);
    }
    for (Product product : decreasedProducts) {
        productService.increaseStock(product.getId(), quantity);
    }

    throw new IllegalStateException("주문 처리 실패", e);
}
```

**특징**:
- ✅ 명확한 보상 로직 (코드로 표현)
- ✅ 즉시 롤백 (동기)
- ❌ 보상 로직을 수동으로 관리
- ❌ 새로운 도메인 추가 시 보상 로직도 추가 필요

#### Choreography: 자동 보상

```java
// OrderSagaEventHandler
@EventListener
public void handlePaymentFailed(PaymentFailedEvent event) {
    Order order = orderRepository.findById(event.orderId());

    // 실패 처리
    order.markAsFailed(event.reason());
    orderRepository.save(order);

    // 👉 보상 트랜잭션 이벤트 발행
    eventPublisher.publishEvent(new OrderFailedEvent(
        orderId,
        reason,
        order.getStepStatus().getCompletedSteps()  // ["STOCK", "COUPON"]
    ));
}

// 각 핸들러가 자동으로 보상
@EventListener
public void handleOrderFailed(OrderFailedEvent event) {
    if (event.completedSteps().contains("STOCK")) {
        // 👉 재고만 복구 (자동)
        productService.increaseStock(productId, quantity);
    }
}

@EventListener
public void handleOrderFailed(OrderFailedEvent event) {
    if (event.completedSteps().contains("COUPON")) {
        // 👉 쿠폰만 복구 (자동)
        couponService.restoreCoupon(userCouponId);
    }
}
```

**특징**:
- ✅ 보상 로직 자동화 (이벤트로)
- ✅ 새로운 도메인 추가 시 이벤트만 구독
- ✅ 각 도메인이 자신의 보상만 책임
- ❌ 비동기 보상 (즉시 롤백 아님)
- ❌ 디버깅 복잡

### 4. 주문 상태 전이

#### Orchestration

```
주문 생성
    │
    ▼
[처리 중...]  ← 모든 로직 동기 실행
    │
    ├─ 성공 → PAID (즉시)
    │
    └─ 실패 → 예외 발생 (주문 저장 안됨)
```

**API 응답**:
```json
{
  "orderId": 123,
  "status": "PAID",  ← 이미 완료됨
  "finalAmount": 50000
}
```

#### Choreography

```
주문 생성
    │
    ▼
 PENDING  ← API 응답 (처리 중)
    │
    ├─ [비동기 처리...]
    │   ├─ 재고 차감
    │   ├─ 결제 처리
    │   └─ 쿠폰 사용
    │
    ├─ 모두 성공 → CONFIRMED
    │
    └─ 하나라도 실패 → FAILED (자동 보상)
```

**API 응답 (즉시)**:
```json
{
  "orderId": 123,
  "status": "PENDING",  ← 아직 처리 중
  "finalAmount": 50000
}
```

**이후 조회 (처리 완료 후)**:
```json
{
  "orderId": 123,
  "status": "CONFIRMED",  ← 완료
  "finalAmount": 50000
}
```

### 5. 코드 복잡도

#### Orchestration

**UseCase**: 약 200 라인
- 핵심 로직: 150 라인
- 보상 트랜잭션: 50 라인

**이벤트 핸들러**: 1개
- `ProductRankingEventHandler` (부가 기능)

**총 복잡도**: 낮음 (로직이 한 곳에)

#### Choreography

**UseCase**: 약 100 라인
- 핵심 로직: 50 라인
- 이벤트 발행: 50 라인

**이벤트 핸들러**: 4개
- `OrderSagaEventHandler`: 약 150 라인
- `StockEventHandler`: 약 120 라인
- `PaymentEventHandler`: 약 100 라인
- `CouponEventHandler`: 약 80 라인

**총 복잡도**: 높음 (로직이 분산)

### 6. 성능 비교

#### Orchestration

```
API 요청
  │
  ├─ 사용자 조회          10ms
  ├─ 재고 차감 (락)       50ms
  ├─ 쿠폰 사용 (락)       30ms
  ├─ 잔액 차감 (락)       40ms
  ├─ 결제 생성           20ms
  └─ 주문 상태 업데이트   10ms
  │
  ▼
총 소요 시간: 160ms (동기)
API 응답: PAID
```

#### Choreography

```
API 요청
  │
  ├─ 주문 생성           20ms
  └─ 이벤트 발행         10ms
  │
  ▼
API 응답: 30ms (PENDING)

[백그라운드]
  │
  ├─ 재고 차감 ──┐
  ├─ 결제 처리 ──┼─ 병렬 실행 (50ms)
  └─ 쿠폰 사용 ──┘
  │
  ├─ Saga 조율           20ms
  │
  ▼
총 소요 시간: 100ms
최종 상태: CONFIRMED
```

**결론**:
- API 응답 속도: Choreography 우세 (30ms vs 160ms)
- 전체 처리 시간: Choreography 우세 (100ms vs 160ms, 병렬 처리)
- 사용자 체감: Orchestration 우세 (즉시 완료)

### 7. 확장성 비교

#### Orchestration: 새로운 도메인 추가 시

```java
// ❌ UseCase 수정 필요
public Order execute(...) {
    // 기존 로직
    productService.decreaseStock(...);
    couponService.useCoupon(...);
    userService.deductBalance(...);

    // 👇 새로운 도메인 추가
    pointService.earnPoints(...);  // 포인트 적립

    try {
        // ...
    } catch (Exception e) {
        // 👇 보상 로직도 추가
        pointService.cancelPoints(...);
        // ...
    }
}
```

**단점**: UseCase를 수정해야 함 (기존 코드 영향)

#### Choreography: 새로운 도메인 추가 시

```java
// ✅ 새로운 핸들러만 추가
@Component
public class PointEventHandler {

    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 포인트 적립
        pointService.earnPoints(event.userId(), event.finalAmount());

        eventPublisher.publishEvent(new PointEarnedEvent(...));
    }

    @EventListener
    public void handleOrderFailed(OrderFailedEvent event) {
        if (event.completedSteps().contains("POINT")) {
            // 포인트 취소
            pointService.cancelPoints(...);
        }
    }
}
```

**장점**: 기존 코드 수정 없음 (이벤트만 구독)

### 8. 장단점 요약

#### Orchestration

| 장점 | 단점 |
|------|------|
| ✅ 명확한 실행 순서 | ❌ 강한 결합 (Service 의존) |
| ✅ 디버깅 용이 | ❌ 확장성 낮음 |
| ✅ 동기 처리 (즉시 완료) | ❌ UseCase 복잡도 증가 |
| ✅ 트랜잭션 관리 단순 | ❌ 병렬 처리 불가 |
| ✅ 성능 예측 가능 | ❌ 새 도메인 추가 시 수정 필요 |

#### Choreography

| 장점 | 단점 |
|------|------|
| ✅ 느슨한 결합 | ❌ 복잡한 흐름 |
| ✅ 확장성 높음 | ❌ 디버깅 어려움 |
| ✅ 병렬 처리 가능 | ❌ 최종 일관성 (즉시 완료 아님) |
| ✅ 보상 트랜잭션 자동화 | ❌ 모니터링 복잡 |
| ✅ 새 도메인 추가 용이 | ❌ 비동기 처리 (응답 시간 증가 체감) |

## 선택 가이드

### Orchestration을 사용하세요

✅ **다음 경우에 적합**:
- 모놀리식 아키텍처
- 간단한 비즈니스 로직
- 트랜잭션 즉시 완료가 중요
- 팀이 작고 도메인이 자주 변하지 않음
- 디버깅과 모니터링 도구가 부족

### Choreography를 사용하세요

✅ **다음 경우에 적합**:
- 마이크로서비스 아키텍처
- 복잡한 비즈니스 로직
- 높은 확장성이 필요
- 도메인이 자주 추가/변경됨
- 병렬 처리로 성능 향상이 필요
- 충분한 모니터링 인프라

## 실무 권장사항

### 일반적인 웹 서비스

대부분의 경우 **Orchestration + 이벤트** 하이브리드 방식 권장:

```java
public Order execute(...) {
    // 핵심 로직: 동기 처리 (Orchestration)
    try {
        재고차감();
        쿠폰사용();
        결제처리();
        주문확정();
    } catch (Exception e) {
        수동_보상_트랜잭션();
    }

    // 부가 기능: 비동기 처리 (이벤트)
    eventPublisher.publish(new OrderCompletedEvent());
    // → 이메일 발송
    // → 알림 전송
    // → 랭킹 업데이트
    // → 데이터 플랫폼 전송

    return order;  // PAID
}
```

**이유**:
- 핵심 로직은 안정성과 즉시 완료가 중요
- 부가 기능은 실패해도 주문에 영향 없음
- 디버깅과 유지보수가 쉬움
- 대부분의 요구사항을 충족

### 마이크로서비스 환경

**Choreography** 권장:
- 서비스 간 독립성이 중요
- 각 서비스가 자체 DB를 가짐
- 분산 트랜잭션 불가피
- 확장과 배포가 독립적

## API 테스트

### Orchestration 테스트

```bash
# 주문 생성 (동기)
curl -X POST http://localhost:8080/api/orders/orchestration/user-uuid \
  -H "Content-Type: application/json" \
  -d '{
    "items": [{"productId": 1, "quantity": 2}],
    "recipientName": "홍길동",
    "shippingAddress": "서울시",
    "shippingPhone": "010-1234-5678"
  }'

# 응답 (즉시)
{
  "orderId": 123,
  "status": "PAID",  ← 이미 완료
  "finalAmount": 50000
}
```

### Choreography 테스트

```bash
# 주문 생성 (비동기)
curl -X POST http://localhost:8080/api/orders/choreography/user-uuid \
  -H "Content-Type: application/json" \
  -d '{
    "items": [{"productId": 1, "quantity": 2}],
    "recipientName": "홍길동",
    "shippingAddress": "서울시",
    "shippingPhone": "010-1234-5678"
  }'

# 응답 (즉시)
{
  "orderId": 123,
  "orderNumber": "uuid-string",
  "status": "PENDING",  ← 처리 중
  "finalAmount": 50000
}

# 잠시 후 조회
curl http://localhost:8080/api/orders/uuid-string

# 응답 (처리 완료 후)
{
  "orderId": 123,
  "orderNumber": "uuid-string",
  "status": "CONFIRMED",  ← 완료
  "finalAmount": 50000
}
```

## 관련 문서

- [Choreography 이벤트 흐름 상세](./CHOREOGRAPHY_EVENT_FLOW.md)
- [보상 트랜잭션 가이드](./SAGA_COMPENSATION_TRANSACTION_GUIDE.md)
- [이벤트 리스너 트랜잭션 컨텍스트](./EVENT_LISTENER_TRANSACTION_CONTEXT.md)

## 결론

- **교육용**: 두 패턴을 모두 구현하여 차이점 학습
- **실무**: 대부분 Orchestration + 이벤트 하이브리드 사용
- **MSA**: Choreography가 더 적합
- **선택 기준**: 팀 역량, 인프라, 요구사항에 따라 결정
