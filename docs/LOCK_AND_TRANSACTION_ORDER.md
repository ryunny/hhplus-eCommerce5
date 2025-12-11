# 락(Lock)과 트랜잭션(Transaction) 순서

## 핵심 원칙

**올바른 순서**:
```
1. 분산락 획득 (Redis Lock)
2. DB 트랜잭션 시작
3. 비즈니스 로직 수행
4. DB 트랜잭션 커밋
5. 분산락 해제
```

**잘못된 순서**:
```
1. DB 트랜잭션 시작  ← 문제!
2. 분산락 획득      ← 트랜잭션 안에서 락 획득
3. 비즈니스 로직
4. 분산락 해제
5. DB 트랜잭션 커밋
```

## 왜 락이 먼저여야 하는가?

### 잘못된 순서의 문제점

```java
@Transactional  // ❌ 트랜잭션이 먼저 시작
public void decreaseStock(Long productId, Quantity quantity) {
    // 이미 DB 커넥션 획득, 트랜잭션 시작

    String lockKey = "product:stock:" + productId;

    // 락 획득 시도
    if (!redisLock.tryLock(lockKey, 5000, TimeUnit.MILLISECONDS)) {
        throw new IllegalStateException("락 획득 실패");
    }

    try {
        // 비즈니스 로직
        Product product = productRepository.findById(productId);
        product.decreaseStock(quantity);
    } finally {
        redisLock.unlock(lockKey);
    }
}
```

**문제점**:
1. **DB 커넥션 낭비**: 락을 기다리는 동안(최대 5초) DB 커넥션 점유
2. **데드락 위험**:
   - 트랜잭션 타임아웃: 30초
   - 락 대기 시간: 5초
   - 여러 스레드가 락을 기다리면 DB 커넥션 풀 고갈
3. **성능 저하**: 락 대기 시간 동안 불필요하게 DB 리소스 사용

### 올바른 순서의 장점

```java
public void decreaseStock(Long productId, Quantity quantity) {
    String lockKey = "product:stock:" + productId;

    // 1. 락 획득 (트랜잭션 밖)
    if (!redisLock.tryLock(lockKey, 5000, TimeUnit.MILLISECONDS)) {
        throw new IllegalStateException("락 획득 실패");
    }

    try {
        // 2. 트랜잭션 실행 (짧은 시간만)
        decreaseStockTransaction(productId, quantity);

    } finally {
        // 3. 락 해제
        redisLock.unlock(lockKey);
    }
}

@Transactional  // ← 여기서 트랜잭션 시작
private void decreaseStockTransaction(Long productId, Quantity quantity) {
    Product product = productRepository.findById(productId);
    product.decreaseStock(quantity);
    // 트랜잭션 커밋 (자동)
}
```

**장점**:
1. **DB 커넥션 효율**: 트랜잭션은 짧은 시간만 열림
2. **데드락 방지**: 락을 기다리는 동안 DB 리소스 사용 안함
3. **성능 향상**: 트랜잭션 범위 최소화

## 현재 프로젝트 구조

### ProductService (올바른 구현)

```java
// ✅ Public 메서드: 트랜잭션 없음
public void decreaseStock(Long productId, Quantity quantity) {
    // 1. 사전 조회 (트랜잭션 밖 - 일반 SELECT)
    Product product = getProduct(productId);

    // 2. 사전 검증 (트랜잭션 밖)
    validateStock(product, quantity);

    // 3. 락 → 트랜잭션
    decreaseStockWithLock(productId, quantity);
}

// ✅ 락 처리 메서드: 트랜잭션 없음
private void decreaseStockWithLock(Long productId, Quantity quantity) {
    String lockKey = RedisKeyGenerator.productStockDecreaseLock(productId);

    // 1단계: 락 획득 (트랜잭션 밖)
    if (!pubSubLock.tryLock(lockKey, timeout, TimeUnit.MILLISECONDS)) {
        throw new IllegalStateException("재고 처리 중입니다.");
    }

    try {
        // 2단계: 트랜잭션 실행
        decreaseStockTransaction(productId, quantity);

    } finally {
        // 3단계: 락 해제 (반드시 실행)
        pubSubLock.unlock(lockKey);
    }
}

// ✅ 트랜잭션 메서드: 짧은 시간만 실행
@Transactional
public void decreaseStockTransaction(Long productId, Quantity quantity) {
    // 상품 조회 (SELECT FOR UPDATE는 아님, Redis 락이 동시성 보장)
    Product product = productRepository.findById(productId)
        .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다"));

    // 재검증 (동시성 문제 대비)
    if (!product.hasSufficientStock(quantity)) {
        throw new IllegalStateException("재고가 부족합니다");
    }

    // 재고 차감
    product.decreaseStock(quantity);
    // 더티 체킹으로 자동 저장 (UPDATE)
}
```

**실행 흐름**:
```
1. decreaseStock() 호출
   ├─ getProduct() - 일반 SELECT (캐시 우선)
   ├─ validateStock() - 메모리 검증
   └─ decreaseStockWithLock()
       ├─ 락 획득 (5초 대기)
       ├─ decreaseStockTransaction() ← 여기서 트랜잭션 시작
       │   ├─ findById() - SELECT
       │   ├─ decreaseStock() - 메모리 연산
       │   └─ flush() - UPDATE (커밋 시)
       └─ 락 해제
```

**트랜잭션 실행 시간**: 10~20ms (매우 짧음)
**락 보유 시간**: 20~30ms (트랜잭션보다 약간 길음)

### UserService, CouponService도 동일한 패턴

```java
// UserService
public void deductBalance(Long userId, Money amount) {
    String lockKey = RedisKeyGenerator.userBalanceLock(userId);

    if (!pubSubLock.tryLock(lockKey, timeout, TimeUnit.MILLISECONDS)) {
        throw new IllegalStateException("처리 중입니다.");
    }

    try {
        deductBalanceTransaction(userId, amount);
    } finally {
        pubSubLock.unlock(lockKey);
    }
}

@Transactional
private void deductBalanceTransaction(Long userId, Money amount) {
    User user = userRepository.findById(userId);
    user.deductBalance(amount);
}
```

## UseCase 레벨에서의 트랜잭션 처리

### Orchestration: UseCase에 @Transactional 없음

```java
// ✅ @Transactional 없음 (의도적)
public class OrchestrationPlaceOrderUseCase {

    public Order execute(String publicId, CreateOrderRequest request) {
        // 각 Service가 개별 트랜잭션으로 실행

        // productService.decreaseStock()
        //   → 락 획득 → 트랜잭션 시작 → 로직 → 트랜잭션 커밋 → 락 해제

        // couponService.useCoupon()
        //   → 락 획득 → 트랜잭션 시작 → 로직 → 트랜잭션 커밋 → 락 해제

        // userService.deductBalance()
        //   → 락 획득 → 트랜잭션 시작 → 로직 → 트랜잭션 커밋 → 락 해제

        return order;
    }
}
```

**왜 UseCase에 @Transactional이 없나?**:
1. 각 Service가 독립적으로 락 → 트랜잭션 관리
2. UseCase에 트랜잭션이 있으면 전체 로직이 하나의 트랜잭션에 묶임
3. 락을 기다리는 동안 트랜잭션이 열려있게 됨 (문제!)

**장점**:
- 트랜잭션 범위 최소화
- 각 Service에서 락 → 트랜잭션 순서 보장
- DB 커넥션 효율적 사용

**단점**:
- 하나의 원자적 트랜잭션이 아님
- 중간에 실패 시 수동 보상 트랜잭션 필요

### Choreography: UseCase에 @Transactional 있음 (괜찮음)

```java
// ✅ @Transactional 있음 (주문 생성만 처리하므로 괜찮음)
public class ChoreographyPlaceOrderUseCase {

    @Transactional
    public Order execute(String publicId, CreateOrderRequest request) {
        // 주문 생성만 함 (단순 INSERT)
        Order order = orderService.createOrder(...);

        // 이벤트 발행
        eventPublisher.publishEvent(new OrderCreatedEvent(...));

        return order;  // PENDING 상태
    }
}

// 재고 차감은 이벤트 핸들러에서 별도 처리
@Component
public class StockEventHandler {

    @Async  // ← 별도 스레드
    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 독립적인 실행 컨텍스트
        productService.decreaseStock(...);
        //   → 락 획득 → 트랜잭션 시작 → 로직 → 트랜잭션 커밋 → 락 해제
    }
}
```

**왜 여기는 @Transactional이 괜찮나?**:
1. UseCase는 주문 생성만 처리 (락 사용 안함)
2. 재고 차감은 **별도 스레드**에서 **독립적인 트랜잭션**으로 실행
3. 각 핸들러가 자체적으로 락 → 트랜잭션 순서 지킴

## 잘못된 설계 예시

### ❌ UseCase에 @Transactional + Service 호출

```java
@Transactional  // ❌ 문제의 시작
public class PlaceOrderUseCase {

    public Order execute(String publicId, CreateOrderRequest request) {
        // 이미 트랜잭션 시작됨

        // Service에서 락을 획득하려고 함
        productService.decreaseStock(...) {
            // 트랜잭션 → 락 순서 (잘못됨!)
            pubSubLock.tryLock(...);  // 여기서 락 획득 시도

            decreaseStockTransaction(...) {
                // 이미 열린 트랜잭션에 참여 (PROPAGATION_REQUIRED)
            }
        }

        // 5초 동안 락 대기 → DB 커넥션 점유
        couponService.useCoupon(...);

        // 또 5초 동안 락 대기 → DB 커넥션 점유
        userService.deductBalance(...);

        // 총 15초 동안 하나의 트랜잭션 + DB 커넥션 점유
        // 동시 요청 100개면 DB 커넥션 풀 고갈!

        return order;
    }
}
```

**문제점**:
1. **DB 커넥션 장시간 점유**: 15초 이상
2. **데드락 위험**: 여러 스레드가 동시 실행 시
3. **성능 저하**: DB 커넥션 풀 고갈
4. **트랜잭션 타임아웃**: 30초 초과 시 롤백

### ✅ 올바른 설계 (현재 구조)

```java
// @Transactional 없음
public class PlaceOrderUseCase {

    public Order execute(String publicId, CreateOrderRequest request) {
        // 각 Service가 독립 트랜잭션

        productService.decreaseStock(...);
        // 락 5초 + 트랜잭션 20ms → 총 5.02초

        couponService.useCoupon(...);
        // 락 5초 + 트랜잭션 20ms → 총 5.02초

        userService.deductBalance(...);
        // 락 5초 + 트랜잭션 20ms → 총 5.02초

        // 총 실행 시간: 15초 (순차 실행)
        // 하지만 각 트랜잭션은 20ms만 열림!
        // DB 커넥션 점유 시간: 60ms (0.06초)

        return order;
    }
}
```

**장점**:
1. **DB 커넥션 효율**: 60ms만 점유
2. **데드락 방지**: 각 트랜잭션이 독립적
3. **성능 향상**: DB 리소스 효율적 사용

## 트랜잭션 전파 레벨 (Propagation)

### Spring 기본값: REQUIRED

```java
@Transactional  // PROPAGATION_REQUIRED (기본값)
public void parentMethod() {
    childMethod();  // 기존 트랜잭션에 참여
}

@Transactional
public void childMethod() {
    // parentMethod의 트랜잭션에 참여
}
```

**이것이 문제의 원인**:
- UseCase에 @Transactional이 있으면
- Service의 @Transactional은 새 트랜잭션을 만들지 않고
- 기존 트랜잭션에 참여
- 따라서 락 획득 시점에 이미 트랜잭션이 열려있음

### 해결 방법 1: UseCase에 @Transactional 제거 (현재 방식)

```java
// @Transactional 없음
public class PlaceOrderUseCase {
    public Order execute(...) {
        productService.decreaseStock(...);
        // 새 트랜잭션 시작 (REQUIRED는 없으면 새로 생성)
    }
}
```

### 해결 방법 2: REQUIRES_NEW 사용 (비권장)

```java
@Transactional
public class PlaceOrderUseCase {
    public Order execute(...) {
        productService.decreaseStock(...);
        // REQUIRES_NEW면 새 트랜잭션 시작
    }
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void decreaseStockTransaction(...) {
    // 새 트랜잭션
}
```

**단점**:
- UseCase 트랜잭션이 여전히 열려있음
- 2개의 트랜잭션 동시 실행
- 복잡도 증가

**따라서 방법 1을 사용합니다.**

## 정리

### 올바른 패턴

```
Service Layer:
  - Public 메서드: @Transactional 없음
  - Private 메서드: 락 획득 → @Transactional 메서드 호출 → 락 해제

UseCase Layer:
  - Orchestration: @Transactional 없음 (Service가 개별 트랜잭션)
  - Choreography: @Transactional 있음 (주문 생성만, 나머지는 이벤트)
```

### 핵심 원칙

1. **락은 트랜잭션보다 먼저 획득**
2. **트랜잭션 범위 최소화**
3. **DB 커넥션 효율적 사용**
4. **UseCase에 @Transactional 지양** (Service가 독립 트랜잭션이 필요한 경우)

## 참고 자료

- [Spring Transaction Propagation](https://docs.spring.io/spring-framework/docs/current/reference/html/data-access.html#tx-propagation)
- [Distributed Lock Pattern](https://redis.io/docs/manual/patterns/distributed-locks/)
- [보상 트랜잭션 가이드](./SAGA_COMPENSATION_TRANSACTION_GUIDE.md)
