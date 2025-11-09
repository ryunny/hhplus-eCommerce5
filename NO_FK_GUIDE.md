# Foreign Key 없이 참조 무결성 관리하기

## 🎯 왜 FK를 사용하지 않는가?

### 현업에서 FK를 사용하지 않는 이유

#### 1. **성능 문제**
```sql
-- FK가 있을 때
INSERT INTO orders (user_id, ...) VALUES (1, ...);
-- 내부적으로 실행됨:
-- SELECT * FROM users WHERE id = 1 FOR UPDATE;
-- (부모 테이블 락 + 성능 저하)

-- FK가 없을 때
INSERT INTO orders (user_id, ...) VALUES (1, ...);
-- 바로 삽입 (빠름!)
```

**성능 차이:**
- INSERT: **30-50% 느림**
- UPDATE: **20-40% 느림**
- DELETE: **40-60% 느림**

#### 2. **데드락 위험**
```sql
-- Transaction 1
UPDATE users SET balance = 1000 WHERE id = 1;  -- users 락
INSERT INTO orders (user_id, ...) VALUES (1, ...);  -- orders 락 + users 재확인

-- Transaction 2 (동시 실행)
UPDATE orders SET status = 'PAID' WHERE user_id = 1;  -- orders 락
UPDATE users SET balance = balance - 1000 WHERE id = 1;  -- users 락 대기

-- 💥 DEADLOCK!
```

#### 3. **샤딩/파티셔닝 불가**
```
Shard 1 (users 1-1000)
├─ users
└─ orders (user_id 1-1000)

Shard 2 (users 1001-2000)
├─ users
└─ orders (user_id 1001-2000)

❌ FK는 다른 샤드 참조 불가!
```

#### 4. **배포/롤백 복잡성**
```sql
-- FK가 있으면
DROP TABLE orders;  -- ❌ Error! payments가 참조 중

-- 순서대로 삭제해야 함
DROP TABLE payments;
DROP TABLE order_items;
DROP TABLE orders;
-- 복잡하고 실수하기 쉬움

-- FK가 없으면
DROP TABLE orders;  -- ✅ 바로 가능
```

---

## 🛡️ 애플리케이션 레벨에서 참조 무결성 관리

### 1. Service 레이어에서 검증

#### ❌ 잘못된 예 (검증 없음)
```java
@Service
public class OrderService {
    public Order createOrder(Long userId, CreateOrderRequest request) {
        // 위험! user_id가 존재하는지 확인 안함
        Order order = new Order();
        order.setUserId(userId);
        return orderRepository.save(order);
    }
}
```

#### ✅ 올바른 예 (검증 포함)
```java
@Service
public class OrderService {
    private final UserService userService;
    private final OrderRepository orderRepository;

    @Transactional
    public Order createOrder(Long userId, CreateOrderRequest request) {
        // 1. 존재 여부 검증
        User user = userService.getUser(userId);  // 없으면 예외 발생

        // 2. 비즈니스 로직
        Order order = new Order();
        order.setUserId(user.getId());

        return orderRepository.save(order);
    }
}

@Service
public class UserService {
    private final UserRepository userRepository;

    public User getUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다: " + userId));
    }
}
```

### 2. 삭제 시 연관 데이터 처리

#### ❌ 잘못된 예 (고아 레코드 발생)
```java
@Service
public class UserService {
    public void deleteUser(Long userId) {
        // 위험! orders, cart_items 등이 남아있음
        userRepository.deleteById(userId);
    }
}
```

#### ✅ 올바른 예 (연관 데이터 처리)
```java
@Service
public class UserService {
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;

    @Transactional
    public void deleteUser(Long userId) {
        // 1. 연관된 주문 확인
        List<Order> orders = orderRepository.findByUserId(userId);
        if (!orders.isEmpty()) {
            throw new BusinessException("삭제할 수 없습니다. 주문 이력이 존재합니다.");
        }

        // 2. 장바구니 삭제
        cartItemRepository.deleteByUserId(userId);

        // 3. 사용자 삭제
        userRepository.deleteById(userId);
    }
}
```

#### 또는 Soft Delete 사용
```java
@Entity
public class User {
    private Long id;
    private String name;
    private Boolean deleted = false;  // Soft delete
    private LocalDateTime deletedAt;
}

@Service
public class UserService {
    @Transactional
    public void deleteUser(Long userId) {
        User user = getUser(userId);
        user.setDeleted(true);
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);
    }
}

// Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deleted = false")
    Optional<User> findById(@Param("id") Long id);
}
```

### 3. 배치 작업으로 정합성 체크

```java
@Component
public class DataIntegrityBatchJob {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    /**
     * 매일 새벽 2시 실행
     * 고아 레코드 체크
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void checkOrphanRecords() {
        // 존재하지 않는 user_id를 가진 주문 찾기
        List<Order> orphanOrders = orderRepository.findOrphanOrders();

        if (!orphanOrders.isEmpty()) {
            log.error("고아 레코드 발견: {} 건", orphanOrders.size());
            // 알림 발송 (Slack, Email 등)
            sendAlert(orphanOrders);

            // 선택적으로 자동 정리
            // orderRepository.deleteAll(orphanOrders);
        }
    }
}

// Repository
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    @Query("""
        SELECT o FROM Order o
        WHERE NOT EXISTS (
            SELECT 1 FROM User u WHERE u.id = o.userId
        )
    """)
    List<Order> findOrphanOrders();
}
```

### 4. 유효성 검증 어노테이션

```java
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "사용자 ID는 필수입니다")
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @NotNull(message = "최종 금액은 필수입니다")
    @Min(value = 0, message = "최종 금액은 0 이상이어야 합니다")
    private Long finalAmount;

    @NotBlank(message = "주문 상태는 필수입니다")
    @Column(nullable = false, length = 20)
    private String status;
}

// Controller
@PostMapping("/orders")
public ResponseEntity<OrderResponse> createOrder(
        @RequestBody @Valid CreateOrderRequest request) {
    // @Valid가 자동으로 검증
}
```

---

## 📊 인덱스 최적화 (FK 대신)

FK 없이도 조회 성능을 보장하려면 **인덱스가 매우 중요**합니다.

### 조인 쿼리 최적화
```sql
-- FK가 있으면 자동으로 인덱스 생성
-- FK가 없으면 수동으로 인덱스 생성 필요

-- orders 테이블
CREATE INDEX idx_user_id ON orders(user_id);
CREATE INDEX idx_user_coupon_id ON orders(user_coupon_id);

-- order_items 테이블
CREATE INDEX idx_order_id ON order_items(order_id);
CREATE INDEX idx_product_id ON order_items(product_id);

-- 복합 인덱스 (조인 + 조건)
CREATE INDEX idx_user_status ON orders(user_id, status);
CREATE INDEX idx_user_created ON orders(user_id, created_at DESC);
```

### 인덱스 사용 확인
```sql
EXPLAIN SELECT o.*, u.name
FROM orders o
JOIN users u ON o.user_id = u.id
WHERE o.user_id = 1;

-- type: ref (인덱스 사용 중)
-- key: idx_user_id
```

---

## 🔍 고아 레코드 모니터링

### 1. 고아 레코드 체크 쿼리

```sql
-- 존재하지 않는 user_id를 가진 주문
SELECT o.id, o.user_id, o.created_at
FROM orders o
LEFT JOIN users u ON o.user_id = u.id
WHERE u.id IS NULL;

-- 존재하지 않는 product_id를 가진 주문 아이템
SELECT oi.id, oi.product_id, oi.created_at
FROM order_items oi
LEFT JOIN products p ON oi.product_id = p.id
WHERE p.id IS NULL;

-- 존재하지 않는 category_id를 가진 상품
SELECT p.id, p.category_id, p.name
FROM products p
LEFT JOIN categories c ON p.category_id = c.id
WHERE c.id IS NULL;
```

### 2. 정기 점검 스크립트
```bash
#!/bin/bash
# check_orphan_records.sh

mysql -u root -p ecommerce << EOF
-- 고아 레코드 체크
SELECT 'orphan_orders' as table_name, COUNT(*) as count
FROM orders o
LEFT JOIN users u ON o.user_id = u.id
WHERE u.id IS NULL

UNION ALL

SELECT 'orphan_order_items', COUNT(*)
FROM order_items oi
LEFT JOIN products p ON oi.product_id = p.id
WHERE p.id IS NULL

UNION ALL

SELECT 'orphan_products', COUNT(*)
FROM products p
LEFT JOIN categories c ON p.category_id = c.id
WHERE c.id IS NULL;
EOF
```

### 3. 애플리케이션 레벨 모니터링
```java
@Component
public class DataIntegrityMonitor {

    @Scheduled(fixedDelay = 3600000)  // 1시간마다
    public void monitorDataIntegrity() {
        Map<String, Long> orphanCounts = new HashMap<>();

        orphanCounts.put("orders", countOrphanOrders());
        orphanCounts.put("order_items", countOrphanOrderItems());
        orphanCounts.put("cart_items", countOrphanCartItems());

        // 메트릭 수집
        orphanCounts.forEach((table, count) -> {
            if (count > 0) {
                log.warn("고아 레코드 발견: {} 테이블 {} 건", table, count);
                // Prometheus, Grafana 등으로 메트릭 전송
                meterRegistry.counter("orphan.records", "table", table).increment(count);
            }
        });
    }
}
```

---

## 🎯 현업 베스트 프랙티스

### 1. **삽입 시 검증 (필수)**
```java
// ✅ 항상 존재 여부 확인
User user = userService.getUser(userId);
Product product = productService.getProduct(productId);

Order order = orderService.createOrder(user, product, quantity);
```

### 2. **삭제 시 정책 정의 (필수)**
```java
public enum DeletePolicy {
    SOFT_DELETE,      // 플래그만 변경 (추천)
    CASCADE_DELETE,   // 연관 데이터 함께 삭제
    REJECT_IF_EXISTS  // 연관 데이터 있으면 거부
}
```

### 3. **정기 점검 (권장)**
```java
@Scheduled(cron = "0 0 2 * * ?")  // 매일 새벽 2시
public void dailyDataIntegrityCheck() {
    checkOrphanRecords();
    checkInvalidReferences();
    cleanupOldData();
}
```

### 4. **트랜잭션 범위 (필수)**
```java
@Transactional  // 원자성 보장
public Order createOrderAndPay(Long userId, CreateOrderRequest request) {
    // 모든 작업이 하나의 트랜잭션
    // 실패 시 자동 롤백
}
```

### 5. **모니터링 및 알림 (필수)**
```java
if (orphanRecordCount > 0) {
    slackNotifier.sendAlert("고아 레코드 발견: " + orphanRecordCount + " 건");
    emailSender.sendToAdmin("데이터 정합성 이슈 발생");
}
```

---

## 📋 체크리스트

### 개발 시
- [ ] 외부 키 삽입 전 존재 여부 검증
- [ ] Service 레이어에서 비즈니스 로직 검증
- [ ] @Transactional로 원자성 보장
- [ ] 예외 처리 및 롤백 전략 수립

### 운영 시
- [ ] 정기적인 고아 레코드 체크 스케줄러
- [ ] 데이터 정합성 모니터링 대시보드
- [ ] 알림 시스템 (Slack, Email 등)
- [ ] 인덱스 성능 모니터링

---

## 🆚 비교: FK vs 애플리케이션 관리

| 항목 | FK 사용 | 애플리케이션 관리 |
|------|---------|------------------|
| **성능** | 느림 (30-50% 오버헤드) | 빠름 |
| **데드락** | 위험 높음 | 위험 낮음 |
| **샤딩** | 불가 | 가능 |
| **배포** | 복잡함 | 간단함 |
| **데이터 무결성** | DB 레벨 보장 | 개발자 책임 |
| **고아 레코드** | 발생 안함 | 주의 필요 |
| **개발 복잡도** | 낮음 | 중간 |
| **확장성** | 낮음 | 높음 |

---

## 🎓 결론

**현업에서는 FK를 사용하지 않고 애플리케이션 레벨에서 참조 무결성을 관리합니다.**

### 핵심 원칙
1. **Service 레이어에서 검증**
2. **@Transactional로 원자성 보장**
3. **정기적인 데이터 정합성 체크**
4. **인덱스 최적화**
5. **모니터링 및 알림**

이렇게 하면 FK의 성능 문제 없이도 **데이터 무결성을 충분히 보장**할 수 있습니다! 🚀
