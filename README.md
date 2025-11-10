# E-Commerce API

항해플러스 3주차 과제 - 이커머스 시스템 백엔드 API

## 📋 프로젝트 개요

사용자가 상품을 주문하고 결제할 수 있는 이커머스 시스템입니다. DDD/Clean Architecture를 기반으로 설계되었으며, 동시성 제어와 보안을 고려한 실전형 백엔드 시스템입니다.

## ✨ 주요 기능

### 1. 사용자 기능
- 잔액 충전 (비관적 락을 통한 동시성 제어)
- 잔액 조회

### 2. 상품 기능
- 전체 상품 목록 조회
- 상품 상세 조회
- 인기 상품 조회 (최근 3일간 판매량 기준 Top N)

### 3. 장바구니 기능
- 장바구니에 상품 추가
- 장바구니 조회
- 장바구니 상품 제거
- 장바구니 전체 비우기

### 4. 주문/결제 기능
- 주문 생성 및 결제 (트랜잭션 기반)
- 주문 상세 조회
- 사용자 주문 목록 조회
- 재고 차감 및 잔액 차감 (원자적 처리)

### 5. 쿠폰 기능
- 발급 가능한 쿠폰 목록 조회
- **즉시 발급 방식** (ReentrantLock 기반 동시성 제어)
- **대기열 방식** (선착순 쿠폰, 스케줄러 기반 순차 처리)
- 사용자 쿠폰 조회
- 사용 가능한 쿠폰 조회
- 대기열 진입 및 상태 조회

## 🛠 기술 스택

### Backend
- **Java 17**
- **Spring Boot 3.x**
- **Spring Data JPA**
- **MySQL 8.0**

### Architecture & Patterns
- **DDD (Domain-Driven Design)**
- **Clean Architecture**
- **CQRS (Command Query Responsibility Segregation)**
- **Value Object Pattern**

### Concurrency Control
- **비관적 락 (Pessimistic Locking)** - 잔액 충전/차감
- **ReentrantLock** - 쿠폰 발급
- **JPA 더티 체킹 (Dirty Checking)** - 트랜잭션 내 자동 저장

### Build Tool
- **Gradle 8.x**

## 🏗 아키텍처 구조

```
com.hhplus.ecommerce
├── domain                    # 도메인 계층
│   ├── entity               # 엔티티
│   ├── vo                   # 값 객체 (Money, Quantity, Email, Phone)
│   ├── enums                # 열거형
│   ├── repository           # 리포지토리 인터페이스
│   └── service              # 도메인 서비스 (비즈니스 로직)
├── application              # 애플리케이션 계층
│   ├── command              # Command DTO (CUD 작업)
│   ├── query                # Query DTO (Read 작업)
│   └── usecase              # UseCase (User Story별 구현)
│       ├── cart
│       ├── user
│       ├── coupon
│       ├── product
│       └── order
└── presentation             # 프레젠테이션 계층
    ├── controller           # REST API 컨트롤러
    └── dto                  # Response DTO
```

### 계층별 역할

#### Domain Layer
- **Entity**: 비즈니스 규칙과 상태를 가진 핵심 도메인 객체
- **Value Object**: 불변 객체로 도메인 개념 표현 (Money, Quantity 등)
- **Domain Service**: 여러 엔티티에 걸친 비즈니스 로직 처리

#### Application Layer
- **UseCase**: 하나의 User Story를 표현하는 클래스 (단일 책임)
- **Command/Query**: CQRS 패턴에 따른 입력 DTO 분리

#### Presentation Layer
- **Controller**: REST API 엔드포인트
- **Response DTO**: 클라이언트 응답 포맷

## 🔐 보안 강화 (UUID)

API에서 순차적 ID 노출로 인한 **IDOR (Insecure Direct Object Reference)** 취약점을 방지하기 위해 UUID를 도입했습니다.

### 설계 원칙
- **내부 PK**: `BIGINT` (성능 최적화)
- **외부 공개 ID**: `UUID` (보안 강화)

### 적용 대상
| Entity | 내부 PK | 외부 UUID | 용도 |
|--------|---------|-----------|------|
| User | id (Long) | publicId (String) | 사용자 식별 |
| Order | id (Long) | orderNumber (String) | 주문 조회 |
| Payment | id (Long) | paymentId (String) | 결제 조회 |

### API 경로 예시
```
❌ 이전: GET /api/users/1/balance
✅ 현재: GET /api/users/550e8400-e29b-41d4-a716-446655440000/balance

❌ 이전: GET /api/orders/123
✅ 현재: GET /api/orders/7c9e6679-7425-40de-944b-e07fc1f90ae7
```

## 📡 API 명세

### User API
```
POST   /api/users/{publicId}/balance/charge    # 잔액 충전
GET    /api/users/{publicId}/balance            # 잔액 조회
```

### Product API
```
GET    /api/products                             # 전체 상품 조회
GET    /api/products/{productId}                 # 상품 상세 조회
GET    /api/products/popular                     # 인기 상품 조회
```

### Cart API
```
POST   /api/carts/{publicId}/items               # 장바구니 추가
GET    /api/carts/{publicId}                     # 장바구니 조회
DELETE /api/carts/items/{cartItemId}             # 장바구니 상품 제거
DELETE /api/carts/{publicId}/clear               # 장바구니 비우기
```

### Order API
```
POST   /api/orders/{publicId}                    # 주문 생성
GET    /api/orders/{orderNumber}                 # 주문 상세 조회
GET    /api/orders/user/{publicId}               # 사용자 주문 목록
```

### Coupon API
```
GET    /api/coupons/issuable                     # 발급 가능한 쿠폰 목록
POST   /api/coupons/{couponId}/issue/{publicId}  # 쿠폰 발급 (즉시/대기열 자동 선택)
GET    /api/coupons/user/{publicId}              # 사용자 쿠폰 목록
GET    /api/coupons/user/{publicId}/available    # 사용 가능한 쿠폰 목록

# 대기열 API
POST   /api/coupons/{couponId}/queue/join/{publicId}    # 대기열 진입
GET    /api/coupons/{couponId}/queue/status/{publicId}  # 대기 상태 조회
```

## 🚀 실행 방법

### 1. 필수 요구사항
- Java 17 이상
- MySQL 8.0 이상
- Gradle 8.x

### 2. 데이터베이스 설정
```sql
CREATE DATABASE ecommerce;
```

### 3. application.yml 설정
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ecommerce
    username: your_username
    password: your_password
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
```

### 4. 빌드 및 실행
```bash
# 빌드
./gradlew build

# 실행
./gradlew bootRun
```

### 5. API 테스트
```bash
# 잔액 조회
curl http://localhost:8080/api/users/{publicId}/balance

# 상품 목록 조회
curl http://localhost:8080/api/products
```

## 💡 주요 구현 사항

### 1. UseCase 패턴 (Single Responsibility)
```java
@Service
public class ChargeBalanceUseCase {
    // User Story: "사용자가 잔액을 충전한다"
    @Transactional
    public User execute(ChargeBalanceCommand command) {
        Money amount = new Money(command.amount());
        return userService.chargeBalanceByPublicId(command.publicId(), amount);
    }
}
```

### 2. 동시성 제어 (비관적 락)
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT u FROM User u WHERE u.publicId = :publicId")
Optional<User> findByPublicIdWithLock(@Param("publicId") String publicId);
```

### 3. 쿠폰 발급 동시성 제어 (ReentrantLock)
```java
@Transactional
public UserCoupon issueCoupon(Long userId, Long couponId) {
    ReentrantLock lock = couponLocks.computeIfAbsent(couponId, k -> new ReentrantLock(true));
    try {
        if (!lock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("쿠폰 발급 요청이 혼잡합니다.");
        }
        try {
            // Race Condition 방지
            if (!coupon.isIssuable()) {
                throw new IllegalStateException("쿠폰의 모든 수량이 소진되었습니다.");
            }
            coupon.increaseIssuedQuantity();
            // ...
        } finally {
            lock.unlock();
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("쿠폰 발급이 중단되었습니다.", e);
    }
}
```

### 4. Value Object 활용
```java
@Embeddable
public class Money {
    private Long amount;

    public Money add(Money other) {
        return new Money(this.amount + other.amount);
    }

    public Money subtract(Money other) {
        return new Money(this.amount - other.amount);
    }
}
```

## 📊 ERD 주요 테이블

```
users (사용자)
├── id (PK, BIGINT)
├── public_id (UUID, UNIQUE)  ⭐ 보안
├── name
├── email (UNIQUE)
├── phone
└── balance

orders (주문)
├── id (PK, BIGINT)
├── order_number (UUID, UNIQUE)  ⭐ 보안
├── user_id (FK)
├── total_amount
├── discount_amount
├── final_amount
└── status

payments (결제)
├── id (PK, BIGINT)
├── payment_id (UUID, UNIQUE)  ⭐ 보안
├── order_id (FK)
├── paid_amount
├── status
└── data_transmission_status

products (상품)
├── id (PK, BIGINT)
├── name
├── price
└── stock

coupons (쿠폰)
├── id (PK, BIGINT)
├── name
├── discount_type (FIXED/PERCENTAGE)
├── discount_value
├── quantity
├── issued_quantity
└── use_queue (대기열 사용 여부)

user_coupons (사용자 쿠폰)
├── id (PK, BIGINT)
├── user_id (FK)
├── coupon_id (FK)
├── status (UNUSED/USED/EXPIRED)
└── expires_at

coupon_queues (쿠폰 대기열)
├── id (PK, BIGINT)
├── user_id (FK)
├── coupon_id (FK)
├── status (WAITING/PROCESSING/COMPLETED/FAILED)
└── queue_position
```

## 🔄 주문 플로우

```
1. 장바구니에 상품 추가
   └─> 재고 검증

2. 주문 생성 요청
   ├─> 상품 조회 및 재고 검증
   ├─> 주문 금액 계산
   ├─> 쿠폰 적용 (선택적)
   ├─> 잔액 검증
   ├─> 주문 생성
   ├─> 주문 아이템 생성 및 재고 차감
   ├─> 잔액 차감 (비관적 락)
   ├─> 결제 생성
   ├─> 주문 상태 변경 (PENDING → PAID)
   └─> 데이터 플랫폼 전송

⚠️ 예외 발생 시 @Transactional에 의해 자동 롤백
```

## 🎯 학습 포인트

1. **DDD/Clean Architecture 적용**
   - 도메인 중심 설계
   - 계층 간 의존성 방향 준수
   - UseCase를 통한 User Story 표현

2. **동시성 제어**
   - 비관적 락 vs 낙관적 락
   - ReentrantLock의 공정성(fairness) 보장
   - 트랜잭션과 락의 관계

3. **보안**
   - IDOR 취약점 이해 및 대응
   - UUID를 활용한 보안 강화
   - 성능과 보안의 균형

4. **JPA**
   - 더티 체킹 활용
   - JPQL 쿼리
   - 페치 전략 (LAZY/EAGER)

5. **CQRS 패턴**
   - Command와 Query 분리
   - 읽기/쓰기 최적화

## 📝 License

This project is created for educational purposes.

---

**개발자**: 항해플러스 백엔드 3기
**프로젝트 기간**: 2025년 1월
