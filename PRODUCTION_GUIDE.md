# 프로덕션 레벨 데이터베이스 가이드

## 🎯 현업 데이터베이스 철학

### DB는 "데이터 저장소"일 뿐, 검증은 애플리케이션에서!

```
❌ DB 레벨 제약조건
├─ Foreign Key
├─ Check 제약조건
├─ Trigger
└─ Stored Procedure

✅ 애플리케이션 레벨 관리
├─ Service 레이어 검증
├─ @Transactional
├─ 비즈니스 로직
└─ 에러 핸들링
```

---

## 🚫 사용하지 않는 것들

### 1. Foreign Key (FK)
```sql
-- ❌ 사용하지 않음
FOREIGN KEY (user_id) REFERENCES users(id)
```

**이유:**
- 성능: INSERT/UPDATE/DELETE 30-50% 느림
- 데드락: 참조 테이블까지 락 확장
- 샤딩: 다른 샤드 테이블 참조 불가
- 배포: 테이블 순서 의존성 복잡

### 2. CHECK 제약조건
```sql
-- ❌ 사용하지 않음
CHECK (balance >= 0)
CHECK (price > 0)
CHECK (issued_quantity <= total_quantity)
```

**이유:**
- 성능: 모든 INSERT/UPDATE마다 체크
- 유연성: 비즈니스 규칙 변경 시 ALTER TABLE 필요
- 중복: 애플리케이션에서 어차피 검증
- 에러: DB 에러 메시지가 불친절

### 3. Trigger
```sql
-- ❌ 사용하지 않음
CREATE TRIGGER update_stock
AFTER INSERT ON order_items
FOR EACH ROW
BEGIN
    UPDATE products SET stock = stock - NEW.quantity;
END;
```

**이유:**
- 디버깅: 코드에 보이지 않아 추적 어려움
- 성능: 숨은 오버헤드
- 유지보수: 로직 파편화
- 테스트: 단위 테스트 어려움

### 4. Stored Procedure
```sql
-- ❌ 사용하지 않음 (대부분의 경우)
CREATE PROCEDURE create_order(...)
BEGIN
    -- 복잡한 로직
END;
```

**이유:**
- 버전 관리: Git 관리 어려움
- 배포: DB 배포 별도 필요
- 테스트: 통합 테스트만 가능
- 언어: SQL은 복잡한 로직에 부적합

---

## ✅ 최소한만 사용

### 1. UNIQUE 제약조건 (꼭 필요한 것만)
```sql
-- ✅ 사용 (중복 방지가 필수인 경우)
UNIQUE KEY uk_email (email)
UNIQUE KEY uk_user_product (user_id, product_id)
```

**사용 기준:**
- 물리적으로 중복이 불가능해야 하는 경우
- 예: 이메일, (사용자, 상품) 조합

### 2. NOT NULL (기본 필드만)
```sql
-- ✅ 사용 (필수 필드만)
name VARCHAR(100) NOT NULL
email VARCHAR(255) NOT NULL
```

**사용 기준:**
- 절대 NULL이 될 수 없는 필드
- 예: 이름, 이메일, 가격

### 3. INDEX (성능을 위해 충분히)
```sql
-- ✅ 필수
INDEX idx_user_id (user_id)
INDEX idx_created_at (created_at)
INDEX idx_user_status (user_id, status)
```

**사용 기준:**
- WHERE 절에 자주 사용되는 컬럼
- JOIN에 사용되는 컬럼
- ORDER BY에 사용되는 컬럼

---

## 💡 애플리케이션 레벨 검증

### 1. 값 범위 검증 (CHECK 대신)

#### ❌ DB CHECK 제약조건
```sql
ALTER TABLE users ADD CONSTRAINT chk_balance_positive CHECK (balance >= 0);
```

#### ✅ Service 레이어 검증
```java
@Service
public class UserService {

    @Transactional
    public void deductBalance(Long userId, Money amount) {
        User user = getUser(userId);

        // 비즈니스 규칙 검증
        if (amount.getAmount() <= 0) {
            throw new InvalidAmountException("차감 금액은 0보다 커야 합니다: " + amount);
        }

        if (user.getBalance() < amount.getAmount()) {
            throw new InsufficientBalanceException(
                String.format("잔액 부족. 현재: %d원, 필요: %d원",
                    user.getBalance(), amount.getAmount())
            );
        }

        user.setBalance(user.getBalance() - amount.getAmount());
        userRepository.save(user);
    }
}
```

**장점:**
- ✅ 친절한 에러 메시지
- ✅ 비즈니스 규칙 변경 용이
- ✅ 단위 테스트 가능
- ✅ 로깅 및 모니터링 가능

### 2. 외래키 검증 (FK 대신)

#### ❌ DB Foreign Key
```sql
FOREIGN KEY (user_id) REFERENCES users(id)
```

#### ✅ Service 레이어 검증
```java
@Service
public class OrderService {

    private final UserService userService;
    private final ProductService productService;

    @Transactional
    public Order createOrder(Long userId, CreateOrderRequest request) {
        // 1. 사용자 존재 확인
        User user = userService.getUser(userId);  // 없으면 예외

        // 2. 상품 존재 확인
        List<Product> products = new ArrayList<>();
        for (OrderItemRequest item : request.getItems()) {
            Product product = productService.getProduct(item.getProductId());
            products.add(product);
        }

        // 3. 주문 생성
        Order order = new Order();
        order.setUserId(user.getId());
        return orderRepository.save(order);
    }
}
```

**장점:**
- ✅ 성능 저하 없음
- ✅ 데드락 위험 낮음
- ✅ 샤딩 가능
- ✅ 명확한 에러 메시지

### 3. 수량 제한 검증 (CHECK 대신)

#### ❌ DB CHECK 제약조건
```sql
ALTER TABLE coupons
ADD CONSTRAINT chk_issued_quantity
CHECK (issued_quantity <= total_quantity);
```

#### ✅ Service 레이어 검증
```java
@Service
public class CouponService {

    @Transactional
    public UserCoupon issueCoupon(Long userId, Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new EntityNotFoundException("쿠폰을 찾을 수 없습니다"));

        // 발급 가능 여부 검증
        if (coupon.getIssuedQuantity() >= coupon.getTotalQuantity()) {
            throw new CouponSoldOutException(
                String.format("쿠폰이 모두 소진되었습니다. (발급: %d/%d)",
                    coupon.getIssuedQuantity(), coupon.getTotalQuantity())
            );
        }

        // 쿠폰 발급
        coupon.setIssuedQuantity(coupon.getIssuedQuantity() + 1);
        couponRepository.save(coupon);

        // 사용자 쿠폰 생성
        UserCoupon userCoupon = new UserCoupon(userId, couponId);
        return userCouponRepository.save(userCoupon);
    }
}
```

---

## 🏗️ 프로덕션 스키마 구조

### 최소한의 제약조건만
```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,        -- NOT NULL만
    email VARCHAR(255) NOT NULL,
    balance BIGINT NOT NULL DEFAULT 0, -- CHECK 없음!

    UNIQUE KEY uk_email (email),       -- UNIQUE만 필수 항목
    INDEX idx_created_at (created_at)  -- INDEX는 충분히
) ENGINE=InnoDB;

-- ✅ FK 없음
-- ✅ CHECK 없음
-- ✅ Trigger 없음
```

---

## 📊 제약조건 비교

| 제약조건 | 사용 여부 | 이유 |
|---------|----------|------|
| **PRIMARY KEY** | ✅ 필수 | 기본적인 식별자 |
| **NOT NULL** | ✅ 최소한 | 필수 필드만 |
| **UNIQUE** | ✅ 최소한 | 중복 방지 필수만 |
| **INDEX** | ✅ 충분히 | 성능 필수 |
| **DEFAULT** | ✅ 선택 | 편의성 |
| **AUTO_INCREMENT** | ✅ 권장 | ID 자동 생성 |
| **Foreign Key** | ❌ 사용 안함 | 성능, 샤딩, 데드락 |
| **CHECK** | ❌ 사용 안함 | 유연성, 성능 |
| **TRIGGER** | ❌ 사용 안함 | 디버깅, 유지보수 |

---

## 🎯 실전 예시

### 주문 생성 프로세스

#### ❌ DB 제약조건 의존
```sql
-- 테이블 정의
CREATE TABLE orders (
    user_id BIGINT NOT NULL,
    final_amount BIGINT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id),
    CHECK (final_amount > 0)
);

-- 삽입 (DB가 검증)
INSERT INTO orders (user_id, final_amount) VALUES (999, -1000);
-- Error: FK constraint fails
-- Error: CHECK constraint fails
```

**문제:**
- 에러 메시지 불친절
- 성능 오버헤드
- 유연성 부족

#### ✅ 애플리케이션 검증
```sql
-- 테이블 정의 (제약조건 없음)
CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    final_amount BIGINT NOT NULL,
    INDEX idx_user_id (user_id)  -- 인덱스만
);
```

```java
@Service
@Transactional
public class OrderService {

    public Order createOrder(Long userId, CreateOrderRequest request) {
        // 1. 사용자 검증
        User user = userService.getUser(userId);
        if (user == null) {
            throw new EntityNotFoundException(
                "사용자를 찾을 수 없습니다. ID: " + userId
            );
        }

        // 2. 금액 검증
        Money finalAmount = calculateFinalAmount(request);
        if (finalAmount.getAmount() <= 0) {
            throw new InvalidAmountException(
                "주문 금액이 유효하지 않습니다: " + finalAmount
            );
        }

        // 3. 재고 검증
        for (OrderItemRequest item : request.getItems()) {
            Product product = productService.getProduct(item.getProductId());
            if (product.getStock() < item.getQuantity()) {
                throw new OutOfStockException(
                    String.format("%s의 재고가 부족합니다 (재고: %d, 요청: %d)",
                        product.getName(), product.getStock(), item.getQuantity())
                );
            }
        }

        // 4. 주문 생성 (DB는 단순 저장만)
        Order order = new Order();
        order.setUserId(user.getId());
        order.setFinalAmount(finalAmount.getAmount());
        return orderRepository.save(order);
    }
}
```

**장점:**
- ✅ 명확한 에러 메시지
- ✅ 비즈니스 로직 한 곳에 집중
- ✅ 테스트 용이
- ✅ 성능 우수

---

## 🔧 마이그레이션 전략

### 기존 제약조건 제거
```sql
-- FK 제거
ALTER TABLE orders DROP FOREIGN KEY fk_orders_users;
ALTER TABLE order_items DROP FOREIGN KEY fk_order_items_orders;

-- CHECK 제거
ALTER TABLE users DROP CONSTRAINT chk_balance_positive;
ALTER TABLE products DROP CONSTRAINT chk_stock_positive;
ALTER TABLE coupons DROP CONSTRAINT chk_issued_quantity;

-- 인덱스는 유지!
SHOW INDEX FROM orders;
```

---

## 📋 체크리스트

### DB 설계 시
- [ ] FK 사용하지 않기
- [ ] CHECK 제약조건 사용하지 않기
- [ ] Trigger/Procedure 최소화
- [ ] UNIQUE는 꼭 필요한 것만
- [ ] NOT NULL은 필수 필드만
- [ ] INDEX는 충분히 설정

### 애플리케이션 개발 시
- [ ] Service 레이어에서 모든 검증
- [ ] @Transactional로 원자성 보장
- [ ] 명확한 예외 메시지
- [ ] 비즈니스 로직 문서화
- [ ] 단위 테스트 작성

### 운영 시
- [ ] 정기적인 고아 레코드 체크
- [ ] 데이터 정합성 모니터링
- [ ] 인덱스 성능 모니터링
- [ ] 슬로우 쿼리 분석

---

## 🎓 결론

### 현업 DB 설계 원칙
1. **DB는 저장소**: 비즈니스 로직은 애플리케이션에
2. **최소 제약조건**: PK, UNIQUE, INDEX만
3. **성능 우선**: FK, CHECK 제거
4. **유연성 확보**: 비즈니스 규칙 변경 용이
5. **명확한 에러**: 애플리케이션에서 친절한 메시지

### 핵심 메시지
> "DB는 단순하게, 애플리케이션은 견고하게!"

---

## 📚 파일 가이드

1. **schema_production.sql** ⭐ 프로덕션용 (추천)
   - FK/CHECK 없음
   - 최소 제약조건
   - 현업 스타일

2. **NO_FK_GUIDE.md** - FK 없이 관리하기
   - 참조 무결성 관리
   - 고아 레코드 모니터링

3. **schema.sql** - 학습용
   - FK/CHECK 포함
   - 비교 학습용

이제 완전히 현업 스타일입니다! 🚀
