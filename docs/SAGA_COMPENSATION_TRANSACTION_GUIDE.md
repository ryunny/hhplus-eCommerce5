# Saga 패턴 & 보상 트랜잭션 구현 가이드

## 📋 목차
1. [개요](#개요)
2. [아키텍처](#아키텍처)
3. [구현 상세](#구현-상세)
4. [실행 흐름](#실행-흐름)
5. [테스트 시나리오](#테스트-시나리오)

---

## 개요

### 구현 목적
분산 트랜잭션 환경에서 **데이터 일관성을 보장**하면서 **각 도메인의 독립성**을 유지하기 위해 **Saga 패턴 (Choreography 방식)**을 구현했습니다.

### 해결하는 문제
| 문제 | 기존 방식 | 개선 후 (Saga 패턴) |
|-----|---------|-----------------|
| **부분 실패** | 재고만 수동 복구 | 모든 도메인 자동 복구 |
| **보상 트랜잭션 누락** | 쿠폰, 잔액 복구 안됨 | 모든 리소스 자동 복구 |
| **중앙 집중식** | UseCase가 모든 로직 관리 | 각 도메인이 독립 관리 |
| **확장성** | 새 도메인 추가 시 UseCase 수정 | 이벤트만 구독하면 됨 |
| **추적 어려움** | 로그만 존재 | Saga 상태 DB 저장 |

---

## 아키텍처

### Choreography 패턴 플로우

```
[사용자 요청]
     ↓
[PlaceOrderUseCase]
 - 주문 생성 (PENDING)
 - OrderCreatedEvent 발행
     ↓
┌────────────────────────────────────────┐
│      각 도메인이 병렬로 처리              │
├────────────┬───────────┬──────────────┤
│ Stock      │ Payment   │ Coupon       │
│ EventHandler│EventHandler│EventHandler │
│            │           │              │
│ 재고 차감  │ 결제 처리 │ 쿠폰 사용    │
│    ↓       │    ↓      │     ↓        │
│ SUCCESS    │ SUCCESS   │ SUCCESS      │
│    ↓       │    ↓      │     ↓        │
│StockReserved│PaymentCompleted│CouponUsed│
└────────────┴───────────┴──────────────┘
              ↓
    [OrderSagaEventHandler]
     - 모든 단계 성공 확인
              ↓
         ┌────┴────┐
      SUCCESS   FAILURE
         ↓          ↓
   OrderConfirmed OrderFailed
         ↓          ↓
     CONFIRMED   보상 트랜잭션
                    ↓
              각 도메인 복구
              (재고/결제/쿠폰)
```

---

## 구현 상세

### 1. Order 엔티티 확장

#### OrderStepStatus (VO)
```java
@Embeddable
public class OrderStepStatus {
    // 각 단계의 상태
    private StepResult stockReservation = StepResult.PENDING;
    private StepResult payment = StepResult.PENDING;
    private StepResult couponUsage = StepResult.PENDING;

    // 보상 트랜잭션에 필요한 리소스 ID
    private String stockReservationId;
    private Long paymentId;
    private Long userCouponId;

    // 실패 정보
    private String failureReason;
    private String failedStep;

    public boolean allCompleted() {
        return stockReservation == StepResult.SUCCESS
            && payment == StepResult.SUCCESS
            && couponUsage == StepResult.SUCCESS;
    }

    public List<String> getCompletedSteps() {
        // 보상 트랜잭션 대상 반환
    }
}
```

#### Order 엔티티
```java
@Entity
public class Order {
    @Enumerated(EnumType.STRING)
    private OrderStatus status;  // PENDING, CONFIRMED, FAILED

    @Embedded
    private OrderStepStatus stepStatus;

    public void markAsFailed(String reason) {
        this.status = OrderStatus.FAILED;
    }

    public void confirm() {
        this.status = OrderStatus.CONFIRMED;
    }
}
```

### 2. 이벤트 정의

```java
// 주문 생성 → 각 도메인이 구독
public record OrderCreatedEvent(
    Long orderId,
    Long userId,
    List<OrderItem> items,
    Money totalAmount,
    Money finalAmount,
    Long userCouponId
) {}

// 주문 확정 → 각 도메인이 리소스 확정
public record OrderConfirmedEvent(
    Long orderId,
    OrderStepStatus stepStatus
) {}

// 주문 실패 → 보상 트랜잭션 트리거
public record OrderFailedEvent(
    Long orderId,
    String reason,
    List<String> completedSteps  // 어떤 단계가 성공했는지
) {}

// 재고 이벤트
public record StockReservedEvent(Long orderId, String reservationId) {}
public record StockReservationFailedEvent(Long orderId, String reason) {}

// 결제 이벤트
public record PaymentCompletedEvent(Long orderId, Long paymentId, Money amount) {}
public record PaymentFailedEvent(Long orderId, String reason) {}

// 쿠폰 이벤트
public record CouponUsedEvent(Long orderId, Long userCouponId) {}
public record CouponUsageFailedEvent(Long orderId, String reason) {}
```

### 3. 이벤트 핸들러

#### OrderSagaEventHandler (조정자)
```java
@Component
public class OrderSagaEventHandler {

    // 성공 이벤트 수신
    @Async @Transactional @EventListener
    public void handleStockReserved(StockReservedEvent event) {
        Order order = orderRepository.findByIdWithLock(event.orderId());
        if (order.getStatus() != OrderStatus.PENDING) return;

        order.getStepStatus().markStockReserved(event.reservationId());
        orderRepository.save(order);

        checkAndConfirmOrder(order);  // 모든 단계 완료 확인
    }

    // 실패 이벤트 수신 → 보상 트랜잭션 트리거
    @Async @Transactional @EventListener
    public void handleStockReservationFailed(StockReservationFailedEvent event) {
        Order order = orderRepository.findByIdWithLock(event.orderId());
        order.getStepStatus().markStockReservationFailed(event.reason());
        order.markAsFailed(event.reason());
        orderRepository.save(order);

        // 보상 트랜잭션 이벤트 발행
        eventPublisher.publishEvent(new OrderFailedEvent(
            event.orderId(),
            event.reason(),
            order.getStepStatus().getCompletedSteps()
        ));
    }

    private void checkAndConfirmOrder(Order order) {
        if (order.getStepStatus().allCompleted()) {
            order.confirm();
            orderRepository.save(order);
            eventPublisher.publishEvent(new OrderConfirmedEvent(...));
        }
    }
}
```

#### StockEventHandler
```java
@Component
public class StockEventHandler {

    // 주문 생성 → 재고 차감
    @Async @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        try {
            for (OrderItem item : event.items()) {
                productService.decreaseStock(item.productId(), item.quantity());
            }

            eventPublisher.publishEvent(new StockReservedEvent(...));
        } catch (Exception e) {
            eventPublisher.publishEvent(new StockReservationFailedEvent(...));
        }
    }

    // 주문 실패 → 보상 트랜잭션 (재고 복구)
    @Async @EventListener
    public void handleOrderFailed(OrderFailedEvent event) {
        if (!event.completedSteps().contains("STOCK")) return;

        try {
            for (ReservationItem item : reservation.getItems()) {
                productService.increaseStock(item.productId(), item.quantity());
            }
        } catch (Exception e) {
            // Dead Letter Queue로 전송
        }
    }
}
```

#### PaymentEventHandler
```java
@Component
public class PaymentEventHandler {

    // 주문 생성 → 결제 처리
    @Async @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        try {
            userService.deductBalance(event.userId(), event.finalAmount());
            Payment payment = paymentService.createPayment(...);
            eventPublisher.publishEvent(new PaymentCompletedEvent(...));
        } catch (Exception e) {
            eventPublisher.publishEvent(new PaymentFailedEvent(...));
        }
    }

    // 주문 실패 → 보상 트랜잭션 (환불)
    @Async @EventListener
    public void handleOrderFailed(OrderFailedEvent event) {
        if (!event.completedSteps().contains("PAYMENT")) return;

        try {
            Payment payment = paymentRepository.findById(paymentId);
            userService.chargeBalance(userId, amount);  // 환불
            paymentService.cancelPayment(paymentId);
        } catch (Exception e) {
            // Dead Letter Queue로 전송
        }
    }
}
```

### 4. PlaceOrderUseCase 리팩토링

```java
@Service
public class PlaceOrderUseCase {

    @Transactional
    public Order execute(String publicId, CreateOrderRequest request) {
        // 1. 사용자 조회
        User user = userService.getUserByPublicId(publicId);

        // 2. 상품 조회 및 사전 검증 (빠른 실패)
        List<Product> products = productService.getProducts(productIds);
        productService.validateStock(products, quantities);

        // 3. 금액 계산
        Money totalAmount = orderService.calculateTotalAmount(products, quantities);
        Money finalAmount = totalAmount.subtract(discountAmount);

        // 4. 잔액 사전 검증 (빠른 실패)
        userService.validateBalance(user, finalAmount);

        // 5. 주문 생성 (PENDING 상태)
        Order order = orderService.createOrder(user, ...);

        // 6. OrderCreatedEvent 발행
        eventPublisher.publishEvent(new OrderCreatedEvent(...));

        // 7. 즉시 응답 (비동기 처리)
        return order;  // status = PENDING
    }
}
```

---

## 실행 흐름

### 정상 케이스 (모든 단계 성공)

```
1. PlaceOrderUseCase
   ├─ 주문 생성 (PENDING)
   └─ OrderCreatedEvent 발행

2. 병렬 처리 (비동기)
   ├─ StockEventHandler
   │  ├─ 재고 차감
   │  └─ StockReservedEvent 발행
   │
   ├─ PaymentEventHandler
   │  ├─ 잔액 차감 + 결제 생성
   │  └─ PaymentCompletedEvent 발행
   │
   └─ CouponEventHandler
      ├─ 쿠폰 사용
      └─ CouponUsedEvent 발행

3. OrderSagaEventHandler
   ├─ 각 성공 이벤트 수신
   ├─ Order.stepStatus 업데이트
   ├─ 모든 단계 완료 확인 ✅
   ├─ Order.status = CONFIRMED
   └─ OrderConfirmedEvent 발행

4. 각 도메인 최종 확정
   ├─ StockEventHandler: 재고 확정
   ├─ PaymentEventHandler: 결제 확정
   └─ CouponEventHandler: 쿠폰 확정
```

### 실패 케이스 (결제 실패 시나리오)

```
1. PlaceOrderUseCase
   ├─ 주문 생성 (PENDING)
   └─ OrderCreatedEvent 발행

2. 병렬 처리
   ├─ StockEventHandler
   │  ├─ 재고 차감 ✅
   │  └─ StockReservedEvent 발행
   │
   ├─ PaymentEventHandler
   │  ├─ 잔액 차감 시도
   │  ├─ 실패 (잔액 부족) ❌
   │  └─ PaymentFailedEvent 발행
   │
   └─ CouponEventHandler
      ├─ 쿠폰 사용 ✅
      └─ CouponUsedEvent 발행

3. OrderSagaEventHandler
   ├─ PaymentFailedEvent 수신
   ├─ Order.stepStatus.markPaymentFailed()
   ├─ Order.status = FAILED
   ├─ completedSteps = ["STOCK", "COUPON"]
   └─ OrderFailedEvent 발행

4. 보상 트랜잭션 (자동)
   ├─ StockEventHandler
   │  ├─ "STOCK" 포함 확인 ✅
   │  └─ 재고 복구 (increaseStock)
   │
   ├─ PaymentEventHandler
   │  ├─ "PAYMENT" 포함 확인 ❌
   │  └─ 환불 스킵
   │
   └─ CouponEventHandler
      ├─ "COUPON" 포함 확인 ✅
      └─ 쿠폰 복구 (cancelCoupon)

5. 최종 상태
   ├─ Order.status = FAILED
   ├─ 재고: 복구됨 ✅
   ├─ 결제: 실행 안됨 ✅
   └─ 쿠폰: 복구됨 ✅
```

---

## 테스트 시나리오

### 1. 정상 주문 (모든 단계 성공)
```
Given: 사용자 잔액 충분, 재고 충분
When: 주문 생성
Then:
  - Order.status = CONFIRMED
  - 재고 차감됨
  - 잔액 차감됨
  - 쿠폰 사용됨
```

### 2. 재고 부족 시나리오
```
Given: 재고 부족
When: 주문 생성
Then:
  - Order.status = FAILED
  - 재고 차감 안됨 (사전 검증에서 실패)
  - 잔액 차감 안됨
  - 쿠폰 사용 안됨
```

### 3. 잔액 부족 시나리오
```
Given: 잔액 부족
When: 주문 생성
Then:
  - Order.status = FAILED
  - 재고 복구됨 (보상 트랜잭션)
  - 잔액 차감 안됨
  - 쿠폰 복구됨 (보상 트랜잭션)
```

### 4. 결제 서비스 장애 시나리오
```
Given: 결제 서비스 다운
When: 주문 생성
Then:
  - Order.status = FAILED
  - 재고 복구됨
  - 쿠폰 복구됨
```

---

## 핵심 포인트

### 1. 비관적 락 사용
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Order> findByIdWithLock(Long id);
```
- 여러 이벤트 핸들러가 동시에 Order를 업데이트하는 것 방지

### 2. 멱등성 미구현 (TODO)
- 현재는 중복 이벤트 처리 시 중복 실행 가능
- 개선 방안: Idempotency Key 추가

### 3. Dead Letter Queue (TODO)
- 보상 트랜잭션 실패 시 현재는 로그만
- 개선 방안: DLQ로 전송하여 수동 처리

### 4. 타임아웃 관리 (TODO)
- 현재는 무한 대기 가능
- 개선 방안: 재고 예약에 TTL 추가

---

## 기존 방식과의 비교

| 항목 | 기존 (동기 방식) | 신규 (Saga 패턴) |
|-----|----------------|----------------|
| **트랜잭션** | 하나의 큰 트랜잭션 | 각 도메인별 독립 트랜잭션 |
| **보상 로직** | 수동 (재고만) | 자동 (모든 도메인) |
| **확장성** | 낮음 (중앙 집중) | 높음 (이벤트 기반) |
| **추적** | 로그만 | DB 상태 저장 |
| **성능** | 순차 처리 | 병렬 처리 |
| **장애 격리** | 전체 실패 | 도메인별 격리 |
| **코드 복잡도** | 낮음 | 높음 |

---

## 참고 자료

- [Saga Pattern - Chris Richardson](https://microservices.io/patterns/data/saga.html)
- [Spring Events Guide](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events)
- [Transaction-bound Events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)

---

## 작성일
2025-12-10

## 작성자
Claude Sonnet 4.5
