# 이벤트 리스너와 트랜잭션 컨텍스트 문제 해결

## 문제 상황

### 기존 코드의 문제점

```java
// PlaceOrderUseCase.java (트랜잭션 없음)
public Order execute(...) {
    // 각 Service가 독립적인 트랜잭션으로 실행
    orderService.createOrder(...);           // 트랜잭션 1
    productService.decreaseStock(...);       // 트랜잭션 2
    userService.deductBalance(...);          // 트랜잭션 3
    orderService.updateOrderStatus(...);     // 트랜잭션 4 (마지막)

    // 모든 트랜잭션이 끝난 후 이벤트 발행
    eventPublisher.publishEvent(new OrderCompletedEvent(...));
    // ⚠️ 이 시점에는 활성 트랜잭션이 없음!
}
```

```java
// ProductRankingEventHandler.java (기존)
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleOrderCompleted(OrderCompletedEvent event) {
    // ❌ 트랜잭션 컨텍스트가 없으면 실행되지 않음!
}
```

### 핵심 문제

1. **트랜잭션 컨텍스트 부재**
   - `PlaceOrderUseCase`에는 `@Transactional`이 없음
   - 각 Service가 독립적인 트랜잭션으로 실행
   - 이벤트 발행 시점에는 모든 트랜잭션이 이미 커밋되고 종료된 상태

2. **`@TransactionalEventListener`의 동작 방식**
   - 기본적으로 **활성 트랜잭션이 있을 때만** 이벤트를 처리
   - `phase = AFTER_COMMIT`: 트랜잭션 커밋 후 실행
   - 트랜잭션이 없으면 `fallbackExecution = true`가 아닌 이상 **이벤트가 실행되지 않음**

3. **실제 발생 가능한 문제**
   - 주문은 정상 완료되지만 랭킹 업데이트가 실행되지 않음
   - 일관성 문제: 주문 데이터와 랭킹 데이터의 불일치

## 해결 방법 비교

### 방법 1: `fallbackExecution = true` 추가

```java
@TransactionalEventListener(
    phase = TransactionPhase.AFTER_COMMIT,
    fallbackExecution = true  // 트랜잭션 없어도 실행
)
public void handleOrderCompleted(OrderCompletedEvent event) {
    // ...
}
```

**장점:**
- 트랜잭션이 있으면 AFTER_COMMIT 동작, 없으면 즉시 실행
- 유연성 제공

**단점:**
- 의도가 불명확함 (트랜잭션이 있을 것으로 기대하는지, 없는 것이 정상인지 모호)
- `AFTER_COMMIT`이 실제로는 의미 없는데 코드에 남아있음

### 방법 2: `@EventListener` 사용 ✅ (선택됨)

```java
@Async
@EventListener
public void handleOrderCompleted(OrderCompletedEvent event) {
    // ...
}
```

**장점:**
- 의도가 명확함: "주문 완료 후 비동기로 처리"
- 트랜잭션 여부와 무관하게 동작
- UseCase의 트랜잭션 분리 설계와 일치

**단점:**
- 없음 (현재 설계에서는 가장 적합)

## 적용된 솔루션

### 1. ProductRankingEventHandler 수정

**변경 전:**
```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleOrderCompleted(OrderCompletedEvent event) {
```

**변경 후:**
```java
@EventListener
public void handleOrderCompleted(OrderCompletedEvent event) {
```

**추가된 주석:**
```java
/**
 * 참고: @TransactionalEventListener 대신 @EventListener를 사용하는 이유
 * - PlaceOrderUseCase에는 @Transactional이 없음 (각 Service가 독립 트랜잭션)
 * - @TransactionalEventListener(AFTER_COMMIT)는 활성 트랜잭션이 없으면 실행되지 않음
 * - 주문 완료 시점에는 이미 모든 Service 트랜잭션이 커밋된 상태이므로 @EventListener로 충분
 */
```

### 2. PlaceOrderUseCase 예외 처리 추가

**추가된 코드:**
```java
// 13. 주문 완료 이벤트 발행 (비동기로 랭킹 업데이트)
// 이벤트 발행 실패해도 주문 처리는 성공으로 간주합니다.
try {
    eventPublisher.publishEvent(new OrderCompletedEvent(order.getId(), orderItems));
} catch (Exception e) {
    // 이벤트 발행 실패는 로그만 남기고 주문 처리는 계속 진행
    // 주문은 이미 완료되었으므로 이벤트 발행 실패가 주문 성공을 막아서는 안됨
    log.error("주문 완료 이벤트 발행 실패 (주문은 정상 처리됨): orderId={}, error={}",
            order.getId(), e.getMessage(), e);
}
```

**추가 이유:**
- 주문은 이미 완료된 상태 (모든 Service 트랜잭션 커밋 완료)
- 이벤트 발행 실패가 주문 성공 응답을 막아서는 안됨
- 랭킹 업데이트는 핵심 비즈니스 로직이 아닌 부가 기능

## 트랜잭션 분리 전략과의 관계

### PlaceOrderUseCase의 설계 철학

```java
/**
 * UseCase는 여러 Service를 조합하는 계층이므로 트랜잭션을 가지지 않습니다.
 * 각 Service 메서드가 개별 트랜잭션으로 실행됩니다.
 *
 * 장점:
 * - 트랜잭션 범위 최소화 → Deadlock 위험 감소
 * - 각 Service가 Redis Lock + 짧은 DB Transaction 사용
 * - 동시성 제어는 각 Service에서 처리
 */
```

### 이벤트 처리와의 일관성

| 계층 | 트랜잭션 전략 | 이벤트 처리 |
|------|--------------|------------|
| **UseCase** | 트랜잭션 없음 | 이벤트 발행 (예외 처리) |
| **Service** | 독립 트랜잭션 | N/A |
| **EventHandler** | 트랜잭션 무관 | @EventListener + @Async |

**일관된 설계:**
- UseCase: 트랜잭션 분리로 Deadlock 최소화
- EventHandler: 트랜잭션 독립적으로 비동기 처리
- 결합도 낮춤: 주문 처리와 랭킹 업데이트의 완전한 분리

## 동작 흐름

### 개선 후 실제 실행 순서

```
[메인 스레드 - PlaceOrderUseCase.execute()]
1. orderService.createOrder()          → 트랜잭션 시작/커밋 (1)
2. productService.decreaseStock()      → 트랜잭션 시작/커밋 (2)
3. userService.deductBalance()         → 트랜잭션 시작/커밋 (3)
4. orderService.updateOrderStatus()    → 트랜잭션 시작/커밋 (4)
5. eventPublisher.publishEvent()       → 이벤트 등록 (즉시 반환)
6. return order                        → 사용자에게 응답

[별도 비동기 스레드 - @Async]
7. ProductRankingEventHandler.handleOrderCompleted() 실행
8. Redis 랭킹 업데이트
9. 성공/실패 여부와 무관하게 주문은 이미 완료된 상태
```

### 장애 격리

| 시나리오 | 결과 |
|---------|------|
| Redis 장애 발생 | 랭킹 업데이트 실패, 주문은 성공 |
| 이벤트 발행 실패 | 로그 기록, 주문은 성공 |
| 이벤트 핸들러 예외 | 주문은 이미 완료됨 (영향 없음) |

## Spring Event 관련 참고 자료

### @TransactionalEventListener의 제약사항

Spring 공식 문서에 따르면:

> If no transaction is running, the event is not processed at all unless `fallbackExecution()` has been enabled explicitly.

출처: [Spring Framework - Transaction-bound Events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)

### 트랜잭션 페이즈별 동작

| Phase | 설명 | 주의사항 |
|-------|------|---------|
| `BEFORE_COMMIT` | 트랜잭션 커밋 직전 | 여전히 트랜잭션 활성 상태 |
| `AFTER_COMMIT` | 트랜잭션 커밋 후 (기본값) | 트랜잭션 리소스는 활성일 수 있음 |
| `AFTER_ROLLBACK` | 롤백 후 | 트랜잭션 실패 시에만 |
| `AFTER_COMPLETION` | 커밋/롤백 후 | 결과와 무관하게 실행 |

출처: [TransactionalEventListener API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/event/TransactionalEventListener.html)

## 결론

### 핵심 배운 점

1. **`@TransactionalEventListener`는 트랜잭션 컨텍스트가 필요함**
   - UseCase에 `@Transactional`이 없으면 예상대로 동작하지 않을 수 있음
   - 트랜잭션이 여러 개로 분리된 경우 "어느 트랜잭션의 AFTER_COMMIT인가?" 모호함

2. **`@EventListener`가 더 명확한 경우도 있음**
   - 트랜잭션과 무관하게 이벤트만 처리하면 되는 경우
   - 비동기 처리가 목적이고, 트랜잭션 동기화가 불필요한 경우

3. **설계 일관성 중요**
   - UseCase의 트랜잭션 분리 전략
   - 이벤트 핸들러의 독립적 처리
   - 장애 격리 및 예외 처리 전략
   - 모든 계층이 일관된 철학을 따라야 함

### 적용 가이드

**언제 `@TransactionalEventListener`를 사용할까?**
- UseCase에 `@Transactional`이 있고
- 트랜잭션 커밋/롤백에 따라 이벤트 처리를 제어해야 할 때

**언제 `@EventListener`를 사용할까?**
- UseCase에 `@Transactional`이 없거나
- 트랜잭션 여부와 무관하게 비동기 처리만 하면 될 때
- 트랜잭션이 여러 개로 분리되어 있을 때 ✅ (현재 상황)

---

## 관련 파일

- `src/main/java/com/hhplus/ecommerce/application/usecase/order/PlaceOrderUseCase.java:179-188`
- `src/main/java/com/hhplus/ecommerce/infrastructure/event/ProductRankingEventHandler.java:17-39`
- `src/main/java/com/hhplus/ecommerce/domain/event/OrderCompletedEvent.java`

## 참고 문서

- [Spring Framework - Transaction-bound Events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)
- [TransactionalEventListener API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/event/TransactionalEventListener.html)
- [Spring puzzler: the @TransactionalEventListener](https://softice.dev/posts/spring_puzzler_transactional_event_listener/)
