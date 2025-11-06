# E-Commerce 프로젝트

## 프로젝트 개요
선착순 쿠폰 발급, 재고 관리, 주문/결제 기능을 포함한 E-Commerce 백엔드 시스템입니다.

## 기술 스택
- Java 17
- Spring Boot 3.5.7
- Lombok
- JUnit 5

## 아키텍처

### 레이어드 아키텍처 (4계층)
```
src/main/java/com/hhplus/ecommerce/
├── presentation/     # Controller, DTO, Exception Handler
├── application/      # UseCase (Business Logic)
├── domain/          # Entity, Value Object, Repository Interface
└── infrastructure/  # InMemory Repository 구현체
```

### 의존성 방향
```
Infrastructure → Domain ← Application ← Presentation
```
- Domain은 다른 계층에 의존하지 않음
- Application은 Domain에만 의존
- Presentation은 Application에 의존
- Infrastructure는 Domain의 인터페이스를 구현

## 도메인 모델

### Entity
- **User**: 사용자 정보 및 잔액 관리
- **Product**: 상품 정보 및 재고 관리
- **Category**: 상품 카테고리
- **Coupon**: 쿠폰 정보 및 발급 수량 관리
- **UserCoupon**: 사용자에게 발급된 쿠폰
- **CouponQueue**: 쿠폰 발급 대기열
- **Order**: 주문 정보
- **OrderItem**: 주문 항목
- **Payment**: 결제 정보
- **CartItem**: 장바구니 항목
- **Refund**: 환불 정보

### Value Object
- **Money**: 금액을 나타내는 불변 객체
- **Quantity**: 수량을 나타내는 불변 객체
- **Stock**: 재고를 나타내는 불변 객체
- **Email**: 이메일 주소 (형식 검증 포함)
- **Phone**: 전화번호 (형식 검증 포함)
- **DiscountRate**: 할인율 (0~100%)

## 주요 기능

### 1. 상품 관리
- 상품 조회 (전체, 단일, 카테고리별)
- 재고 차감/복구
- 인기 상품 통계 (최근 3일 Top 5)

### 2. 장바구니
- 장바구니 추가/조회/삭제
- 장바구니 전체 비우기

### 3. 선착순 쿠폰
- **통합 쿠폰 발급 API**: 쿠폰 설정에 따라 자동으로 즉시 발급 또는 대기열 발급 선택
  - `useQueue = false`: 즉시 발급 (ReentrantLock으로 동시성 제어)
  - `useQueue = true`: 대기열 진입 (스케줄러가 순차 처리)
- 쿠폰 조회 (발급 가능 쿠폰, 사용자별 쿠폰)
- 쿠폰 사용/취소
- 쿠폰 만료 처리 (스케줄러)
- 대기열 시스템 (대기 순번 조회, 스케줄러 기반 순차 발급)

### 4. 주문/결제
- 주문 생성 및 결제 처리 (동시성 제어)
- 쿠폰 적용
- 재고 차감
- 잔액 차감
- 주문/결제 내역 조회

### 5. 사용자 잔액 관리
- 잔액 충전
- 잔액 조회

## 동시성 제어

### 1. 선착순 쿠폰 발급

#### 통합 쿠폰 발급 API 설계
쿠폰 발급 방식은 쿠폰 설정에 따라 자동으로 선택됩니다:
```java
public UserCoupon issueCoupon(Long userId, Long couponId) {
    // 1. 쿠폰 정보 조회 (발급 방식 확인)
    Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + couponId));

    // 2. 쿠폰 설정에 따라 발급 방식 선택
    if (coupon.isUseQueue()) {
        // 대기열 방식: 대기열에 추가만 하고 반환
        joinQueueInternal(userId, couponId);
        return null; // 대기 중 (스케줄러가 처리)
    } else {
        // 즉시 발급 방식
        return issueCouponImmediately(userId, couponId);
    }
}
```

#### 설계 장점
1. **단일 API**: 클라이언트는 하나의 API만 호출하면 됨
2. **유연성**: 쿠폰별로 발급 방식을 다르게 설정 가능
3. **확장성**: 새로운 발급 방식 추가 시 기존 코드 변경 최소화
4. **명확한 책임**: 발급 방식 결정은 Domain(Coupon)의 책임

#### 문제점 분석
선착순 쿠폰 발급 시 여러 사용자가 동시에 요청하면 **Race Condition**이 발생할 수 있습니다:
- 쿠폰 발급 가능 수량 확인과 발급 사이의 시간 간격에서 동시 접근
- 결과적으로 설정한 수량보다 많이 발급되는 문제 발생

#### 해결 방법: ReentrantLock (Mutex)
```java
private final ConcurrentHashMap<Long, ReentrantLock> couponLocks = new ConcurrentHashMap<>();

public UserCoupon issueCoupon(Long userId, Long couponId) {
    // 쿠폰별 공정한 락 획득 (FIFO 순서 보장)
    ReentrantLock lock = couponLocks.computeIfAbsent(couponId, k -> new ReentrantLock(true));

    try {
        if (!lock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("쿠폰 발급 요청이 혼잡합니다.");
        }

        // Critical Section: 쿠폰 발급 로직
        // 1. 발급 가능 여부 확인
        // 2. 쿠폰 발급 수량 증가
        // 3. UserCoupon 생성

    } finally {
        lock.unlock();
    }
}
```

#### 선택 이유
1. **공정성(Fairness)**: `ReentrantLock(true)`로 FIFO 순서 보장
2. **타임아웃**: `tryLock(timeout)`으로 데드락 방지
3. **쿠폰별 독립적인 락**: ConcurrentHashMap을 사용해 쿠폰마다 별도 락 관리
4. **재진입 가능**: 같은 스레드가 여러 번 락을 획득할 수 있음

#### 대안 검토
- **synchronized**: 공정성 보장 없음, 타임아웃 불가
- **Semaphore**: 카운팅이 필요 없는 단순 Mutex에는 과도
- **Atomic Operations**: 복잡한 비즈니스 로직에 부적합
- **Database Lock**: 인메모리 환경에서 사용 불가

### 2. 주문/결제 동시성 제어

#### 문제점 분석
주문/결제 시 두 가지 Race Condition 발생 가능:
1. **재고 차감**: 여러 주문이 동시에 같은 상품 구매
2. **잔액 차감**: 사용자가 동시에 여러 주문 생성

#### 해결 방법: 다중 락 (User Lock + Product Lock)
```java
private final ConcurrentHashMap<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();
private final ConcurrentHashMap<Long, ReentrantLock> productLocks = new ConcurrentHashMap<>();

public Order createOrderAndPay(Long userId, CreateOrderRequest request) {
    // 1. 사용자 락 획득 (잔액 보호)
    ReentrantLock userLock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock(true));
    userLock.lock();

    try {
        // 2. 상품별 락 획득 (재고 보호)
        for (상품 : 주문상품목록) {
            ReentrantLock productLock = productLocks.computeIfAbsent(productId, k -> new ReentrantLock(true));
            productLock.lock();
        }

        try {
            // Critical Section:
            // - 재고 확인 및 차감
            // - 잔액 확인 및 차감
            // - 주문 생성
            // - 결제 처리
        } finally {
            // 상품 락 해제 (역순)
        }
    } finally {
        userLock.unlock();
    }
}
```

#### 데드락 방지
- **락 획득 순서 일관성**: User Lock → Product Lock 순서 고정
- **타임아웃**: `tryLock(timeout)` 사용
- **락 해제 보장**: finally 블록에서 unlock

### 3. 보상 트랜잭션(Compensation Transaction) 패턴

#### 문제점 분석
인메모리 환경에서는 `@Transactional`이 동작하지 않아 다음과 같은 **데이터 불일치** 문제 발생:

```
1. 쿠폰 사용 ✅
2. 주문 생성 ✅
3. 재고 차감 ✅
4. 잔액 차감 ✅
5. 결제 생성 ❌ (예외 발생!)

→ 쿠폰은 사용됨, 재고는 차감됨, 잔액도 차감됨
→ 하지만 결제는 없음 → 데이터 불일치!
```

#### 핵심 도전 과제: "무엇을 했는지" 정확히 추적하기

보상 트랜잭션의 가장 큰 어려움은 **"어디까지 실행되었는지"를 정확히 알아야 한다**는 점입니다:

**잘못된 접근 (안한 것을 복구하려는 문제):**
```java
boolean balanceDeducted = false;  // 플래그로 추적

user.deductBalance(finalAmount);  // 1. 메모리 변경
// 여기서 예외 발생!
userRepository.save(user);        // 2. 저장 (실행 안됨)
balanceDeducted = true;           // 3. 플래그 설정 (실행 안됨)

// 문제: balanceDeducted = false이지만 실제로는 메모리에서 차감됨!
```

**개선된 접근 (실행 추적 리스트):**
```java
List<String> executedSteps = new ArrayList<>();

user.deductBalance(finalAmount);     // 1. 메모리 변경
userRepository.save(user);           // 2. 저장
executedSteps.add("BALANCE_DEDUCTED"); // 3. 성공 후에만 추적

// save() 성공한 것만 추적되므로 정확한 복구 가능!
```

#### 해결 방법: 실행 추적 기반 보상 트랜잭션
```java
public Order createOrderAndPay(Long userId, CreateOrderRequest request) {
    // 실행 추적 리스트: save() 성공한 작업만 기록
    List<String> executedSteps = new ArrayList<>();

    // 보상용 데이터
    User user = null;
    UserCoupon usedCoupon = null;
    Order createdOrder = null;
    List<Product> stockDecreasedProducts = new ArrayList<>();
    List<Quantity> decreasedQuantities = new ArrayList<>();
    Money deductedAmount = Money.zero();

    try {
        // 1. 쿠폰 사용
        if (userCouponId != null) {
            userCoupon.use();
            userCouponRepository.save(userCoupon);
            // 저장 성공 후에만 추적
            usedCoupon = userCoupon;
            executedSteps.add("COUPON_USED");
        }

        // 2. 주문 생성
        Order order = new Order(...);
        orderRepository.save(order);
        // 저장 성공 후에만 추적
        createdOrder = order;
        executedSteps.add("ORDER_CREATED");

        // 3. 재고 차감
        for (Product product : products) {
            product.decreaseStock(quantity);
            productRepository.save(product);
            // 저장 성공 후에만 추적
            stockDecreasedProducts.add(product);
            decreasedQuantities.add(quantity);
            executedSteps.add("STOCK_DECREASED:" + product.getId());
        }

        // 4. 잔액 차감
        user.deductBalance(finalAmount);
        userRepository.save(user);
        // 저장 성공 후에만 추적
        deductedAmount = finalAmount;
        executedSteps.add("BALANCE_DEDUCTED");

        // 5. 결제 생성
        Payment payment = new Payment(...);
        paymentRepository.save(payment);
        // 저장 성공 후에만 추적
        executedSteps.add("PAYMENT_CREATED");

        return order;
    } catch (Exception e) {
        // executedSteps를 확인하여 "실제로 실행된 작업만" 복구
        compensateTransaction(executedSteps, user, usedCoupon, createdOrder,
                            stockDecreasedProducts, decreasedQuantities, deductedAmount);
        throw e;
    }
}

private void compensateTransaction(List<String> executedSteps, ...) {
    // 역순으로 복구 (나중에 실행된 것부터 롤백)
    for (int i = executedSteps.size() - 1; i >= 0; i--) {
        String step = executedSteps.get(i);

        if (step.equals("BALANCE_DEDUCTED")) {
            user.chargeBalance(deductedAmount);
            userRepository.save(user);

        } else if (step.startsWith("STOCK_DECREASED:")) {
            Long productId = extractProductId(step);
            Product product = findProduct(productId, stockDecreasedProducts);
            product.increaseStock(quantity);
            productRepository.save(product);

        } else if (step.equals("ORDER_CREATED")) {
            createdOrder.updateStatus(OrderStatus.CANCELLED);
            orderRepository.save(createdOrder);

        } else if (step.equals("COUPON_USED")) {
            usedCoupon.cancel();
            userCouponRepository.save(usedCoupon);
        }
    }
}
```

#### 핵심 개선 사항

1. **정확한 실행 추적**
   - `executedSteps` 리스트로 "실제로 save() 성공한 작업만" 기록
   - save() 이전 예외: 추적 안됨 → 복구 안함 (올바름)
   - save() 이후 예외: 추적됨 → 복구함 (올바름)

2. **역순 복구**
   - 나중에 실행된 작업부터 롤백
   - 예: PAYMENT → BALANCE → STOCK → ORDER → COUPON

3. **상품별 추적**
   - `"STOCK_DECREASED:1"`, `"STOCK_DECREASED:2"` 형태로 개별 추적
   - 어떤 상품이 차감되었는지 정확히 파악

#### 장점
- **정확한 복구**: "실제로 실행된 작업만" 복구 (안한 것 복구 안함)
- **데이터 일관성**: 예외 발생 시 자동 롤백으로 일관성 유지
- **추적 가능**: 주문은 CANCELLED 상태로 남아 이력 추적 가능
- **디버깅 용이**: executedSteps로 어디까지 진행되었는지 명확히 확인

#### 한계 및 주의사항
- **완벽한 ACID 아님**: save()와 executedSteps.add() 사이에 예외 가능
- **인메모리 특성**: Repository.save()가 거의 항상 성공하므로 실용적
- **보상 실패 가능**: 보상 트랜잭션 자체가 실패할 수 있음 → 로깅/알림 필요
- **일시적 불일치**: 롤백 완료 전까지 데이터 불일치 상태 존재

### 4. 대기열 기반 쿠폰 발급

#### 구조
```
사용자 요청 → CouponQueue 생성 → 스케줄러가 순차 처리 → 쿠폰 발급
```

#### 장점
- 서버 부하 분산
- 순차 처리로 Race Condition 원천 차단
- 사용자에게 대기 순번 제공 가능

## 데이터 저장

### InMemory Repository
DB를 사용하지 않고 모든 데이터를 메모리에 저장합니다:
- `ConcurrentHashMap`을 사용하여 Thread-Safe 보장
- ID는 `AtomicLong`으로 자동 증가
- 애플리케이션 재시작 시 데이터 초기화

```java
public class InMemoryCouponRepository implements CouponRepository {
    private final Map<Long, Coupon> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Coupon save(Coupon coupon) {
        if (coupon.getId() == null) {
            Long newId = idGenerator.getAndIncrement();
            setId(coupon, newId);  // 리플렉션으로 ID 설정
        }
        store.put(coupon.getId(), coupon);
        return coupon;
    }

    private void setId(Coupon coupon, Long id) {
        try {
            Field idField = Coupon.class.getDeclaredField("id");
            idField.setAccessible(true);  // private 필드 접근 가능하게
            idField.set(coupon, id);       // 강제로 값 설정
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set id", e);
        }
    }
}
```

### ID 불변성 보장 (Reflection 사용 이유)

**왜 public setId()가 없나요?**

Entity의 ID는 **한 번 생성되면 절대 변경되어서는 안됩니다**. 이를 위해 다음과 같이 설계했습니다:

```java
// ❌ 잘못된 설계: public setter
@Getter
public class Coupon {
    private Long id;

    public void setId(Long id) {  // 누구나 ID 변경 가능! 위험!
        this.id = id;
    }
}

// 비즈니스 로직에서 실수로 ID 변경 가능
Coupon coupon = couponRepository.findById(1L).get();
coupon.setId(999L);  // 💥 심각한 버그!

// ✅ 올바른 설계: setter 없음 + 리플렉션
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {
    private Long id;  // setter 없음 (불변)

    // ID 설정 방법이 없음! → Repository만 리플렉션으로 설정 가능
}
```

**장점:**
1. **ID 불변성**: 비즈니스 로직에서 ID 변경 불가능
2. **명확한 책임**: Repository만 ID 생명주기 관리
3. **JPA 철학**: JPA도 `@GeneratedValue`로 리플렉션 사용
4. **Domain 순수성**: 인프라 세부사항(ID 할당)이 Domain에 노출 안됨

**JPA와의 유사성:**
```java
// JPA도 똑같이 리플렉션 사용
@Entity
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // setter 없음!

    // JPA가 내부적으로 리플렉션으로 ID 주입
}
```

## 테스트

### 단위 테스트
- Entity 비즈니스 로직 테스트
- Value Object 검증 테스트
- UseCase 테스트 (Mock 활용)

### 통합 테스트
- 동시성 테스트 (CouponConcurrencyTest, OrderConcurrencyTest)
- ExecutorService를 활용한 멀티스레드 테스트
- Race Condition 방지 검증

### 테스트 실행
```bash
./gradlew test
```

## 프로젝트 특징

### 1. DB 없는 순수 인메모리 구현
- JPA, Hibernate 의존성 제거
- 순수 Java로 Repository 패턴 구현
- ConcurrentHashMap으로 Thread-Safe 보장

### 2. Domain-Driven Design
- Entity는 비즈니스 로직을 포함
- Value Object로 값의 불변성과 유효성 보장
- Repository 인터페이스로 Infrastructure 분리

### 3. 동시성 제어
- Java의 ReentrantLock 활용
- 공정성(Fairness) 보장으로 선착순 구현
- 데드락 방지 전략 적용

### 4. 확장 가능한 설계
- Repository 인터페이스로 추후 DB 전환 용이
- UseCase 중심 설계로 비즈니스 로직 재사용성
- DTO로 계층 간 데이터 전달

## 트레이드오프

### 인메모리 저장소
**장점**
- 빠른 속도
- 설정 불필요
- 테스트 용이

**단점**
- 재시작 시 데이터 손실
- 메모리 제한
- 확장성 제한

### ReentrantLock
**장점**
- 공정성 보장
- 타임아웃 설정 가능
- 재진입 가능

**단점**
- 단일 서버에서만 동작
- 분산 환경에서는 Redis 등 필요

## 향후 개선 사항
1. 실제 DB 연동 (JPA)
2. Redis를 활용한 분산 락
3. 메시지 큐(Kafka/RabbitMQ)를 활용한 비동기 처리
4. 캐시 레이어 추가
5. API 문서화 (Swagger/Spring REST Docs)
