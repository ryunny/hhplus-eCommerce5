# 데이터베이스 설정 가이드

## 📋 목차
1. [데이터베이스 생성](#1-데이터베이스-생성)
2. [스키마 적용](#2-스키마-적용)
3. [연결 설정](#3-연결-설정)
4. [JPA 설정](#4-jpa-설정)
5. [마이그레이션 가이드](#5-마이그레이션-가이드)

---

## 1. 데이터베이스 생성

### MySQL 접속
```bash
mysql -u root -p
```

### 데이터베이스 생성
```sql
CREATE DATABASE ecommerce CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 사용자 생성 (선택사항)
CREATE USER 'ecommerce_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON ecommerce.* TO 'ecommerce_user'@'localhost';
FLUSH PRIVILEGES;
```

### 데이터베이스 선택
```sql
USE ecommerce;
```

---

## 2. 스키마 적용

### 방법 1: MySQL 명령어로 직접 실행
```bash
mysql -u root -p ecommerce < schema.sql
```

### 방법 2: MySQL Workbench 사용
1. MySQL Workbench 실행
2. Connection 생성 및 접속
3. `File` → `Open SQL Script` → `schema.sql` 선택
4. 실행 (번개 아이콘 클릭)

### 방법 3: DBeaver 사용
1. DBeaver 실행
2. Connection 생성 및 접속
3. SQL Editor 열기
4. `schema.sql` 파일 내용 복사 후 실행

---

## 3. 연결 설정

### application.yml
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ecommerce?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
    username: ecommerce_user
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: validate  # 스키마는 schema.sql로 관리, JPA는 검증만
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.MySQL8Dialect
```

### application.properties
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/ecommerce?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
spring.datasource.username=ecommerce_user
spring.datasource.password=your_password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect
```

---

## 4. JPA 설정

### build.gradle 의존성 추가
```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    runtimeOnly 'com.mysql:mysql-connector-j'
}
```

### Entity 매핑 예시

#### User Entity
```java
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;  // Email VO → String 변환

    @Column(nullable = false, length = 20)
    private String phone;  // Phone VO → String 변환

    @Column(nullable = false)
    private Long balance;  // Money VO → Long 변환

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
```

#### Product Entity (FK 매핑)
```java
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Long price;  // Money VO → Long 변환

    @Column(nullable = false)
    private Integer stock;  // Stock VO → Integer 변환

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

### JPA Repository
```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdWithLock(@Param("id") Long id);
}

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByCategoryId(Long categoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);
}
```

---

## 5. 마이그레이션 가이드

### 인메모리 → DB 전환 단계

#### Step 1: Entity 수정
Value Object를 기본 타입으로 변환
```java
// Before (인메모리)
private Money balance;
private Email email;
private Phone phone;

// After (DB)
private Long balance;
private String email;
private String phone;
```

#### Step 2: Repository 전환
```java
// Before (인메모리)
public interface UserRepository {
    User save(User user);
    Optional<User> findById(Long id);
}

// After (JPA)
public interface UserRepository extends JpaRepository<User, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdWithLock(@Param("id") Long id);
}
```

#### Step 3: Service 수정
비관적 락 적용
```java
@Service
@Transactional
public class ProductService {

    public void decreaseStock(Long productId, Quantity quantity) {
        // 비관적 락 적용
        Product product = productRepository.findByIdWithLock(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다"));

        product.decreaseStock(quantity);
        // 별도 save() 호출 불필요 (더티 체킹)
    }
}
```

#### Step 4: UseCase 수정
```java
@Service
public class OrderUseCase {

    @Transactional  // ← 추가
    public Order createOrderAndPay(Long userId, CreateOrderRequest request) {
        // 락 제거 (DB 락으로 대체)
        // 보상 트랜잭션 제거 (@Transactional이 자동 롤백)

        // Service 호출 (동일)
        User user = userService.getUser(userId);
        // ...
    }
}
```

#### Step 5: 락 코드 제거
```java
// Before
private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

// After
// 제거! DB 락 사용
```

---

## 6. 테스트 설정

### H2 인메모리 DB (테스트용)
```yaml
# application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password:

  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true

  h2:
    console:
      enabled: true
```

### 테스트용 의존성
```gradle
testImplementation 'com.h2database:h2'
```

---

## 7. 확인 및 검증

### 테이블 생성 확인
```sql
SHOW TABLES;

DESCRIBE users;
DESCRIBE products;
DESCRIBE orders;
```

### 초기 데이터 확인
```sql
SELECT * FROM categories;
SELECT * FROM products;
SELECT * FROM users;
SELECT * FROM coupons;
```

### 인덱스 확인
```sql
SHOW INDEX FROM users;
SHOW INDEX FROM products;
SHOW INDEX FROM orders;
```

### 제약 조건 확인
```sql
SELECT
    TABLE_NAME,
    CONSTRAINT_NAME,
    CONSTRAINT_TYPE
FROM information_schema.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA = 'ecommerce';
```

---

## 8. 트러블슈팅

### 문제 1: 연결 오류
```
Communications link failure
```
**해결:**
- MySQL 서버가 실행 중인지 확인
- 포트 번호 확인 (기본 3306)
- 방화벽 설정 확인

### 문제 2: 인코딩 오류
```
Incorrect string value: '\xED\x99\x8D...'
```
**해결:**
```sql
ALTER DATABASE ecommerce CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 문제 3: 타임존 오류
```
The server time zone value 'KST' is unrecognized
```
**해결:**
URL에 `serverTimezone=Asia/Seoul` 추가

### 문제 4: FK 제약 조건 오류
```
Cannot add or update a child row: a foreign key constraint fails
```
**해결:**
- 참조하는 부모 레코드가 존재하는지 확인
- 테이블 생성 순서 확인 (부모 → 자식)

---

## 9. 성능 최적화

### 슬로우 쿼리 로그 활성화
```sql
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 1;  -- 1초 이상 쿼리 로깅
```

### 쿼리 실행 계획 확인
```sql
EXPLAIN SELECT * FROM orders WHERE user_id = 1;
```

### 인덱스 사용 분석
```sql
SHOW STATUS LIKE 'Handler_read%';
```

---

## 10. 백업 및 복구

### 백업
```bash
mysqldump -u root -p ecommerce > backup_$(date +%Y%m%d).sql
```

### 복구
```bash
mysql -u root -p ecommerce < backup_20250109.sql
```

---

## 📚 참고 문서

- [schema.sql](./schema.sql) - 전체 스키마 정의
- [ERD.md](./ERD.md) - ERD 다이어그램 및 설명
- [sample_queries.sql](./sample_queries.sql) - 샘플 쿼리 모음

## ❓ 문의사항

추가 질문이나 문제가 있으면 이슈를 생성해주세요.
