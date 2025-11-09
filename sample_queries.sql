-- ================================================
-- E-Commerce 샘플 쿼리 모음
-- ================================================
-- 자주 사용되는 쿼리들의 예시 모음

-- ================================================
-- 1. 사용자 관련 쿼리
-- ================================================

-- 사용자 조회 (이메일로)
SELECT * FROM users WHERE email = 'hong@example.com';

-- 사용자 잔액 조회
SELECT id, name, balance FROM users WHERE id = 1;

-- 잔액 충전
UPDATE users SET balance = balance + 100000 WHERE id = 1;

-- 잔액 차감 (잔액 부족 체크)
UPDATE users
SET balance = balance - 50000
WHERE id = 1 AND balance >= 50000;

-- ================================================
-- 2. 상품 관련 쿼리
-- ================================================

-- 전체 상품 조회
SELECT p.*, c.name as category_name
FROM products p
JOIN categories c ON p.category_id = c.id
ORDER BY p.created_at DESC;

-- 카테고리별 상품 조회
SELECT p.*
FROM products p
WHERE p.category_id = 1
  AND p.stock > 0
ORDER BY p.name;

-- 상품 검색 (이름으로)
SELECT * FROM products
WHERE name LIKE '%노트북%'
  AND stock > 0;

-- 재고 차감 (비관적 락)
-- JPA: @Lock(LockModeType.PESSIMISTIC_WRITE)
SELECT * FROM products WHERE id = 1 FOR UPDATE;
UPDATE products SET stock = stock - 1 WHERE id = 1;

-- 재고 복구
UPDATE products SET stock = stock + 1 WHERE id = 1;

-- ================================================
-- 3. 쿠폰 관련 쿼리
-- ================================================

-- 발급 가능한 쿠폰 조회
SELECT * FROM coupons
WHERE start_date <= NOW()
  AND end_date >= NOW()
  AND issued_quantity < total_quantity;

-- 쿠폰 발급 (동시성 제어)
SELECT * FROM coupons WHERE id = 1 FOR UPDATE;
UPDATE coupons SET issued_quantity = issued_quantity + 1 WHERE id = 1;
INSERT INTO user_coupons (user_id, coupon_id, status, expires_at)
VALUES (1, 1, 'UNUSED', '2025-12-31 23:59:59');

-- 사용자의 사용 가능한 쿠폰 조회
SELECT uc.*, c.name, c.discount_rate, c.discount_amount, c.min_order_amount
FROM user_coupons uc
JOIN coupons c ON uc.coupon_id = c.id
WHERE uc.user_id = 1
  AND uc.status = 'UNUSED'
  AND uc.expires_at >= NOW();

-- 쿠폰 사용
UPDATE user_coupons SET status = 'USED' WHERE id = 1;

-- 만료된 쿠폰 일괄 처리 (배치 작업)
UPDATE user_coupons
SET status = 'EXPIRED'
WHERE status = 'UNUSED'
  AND expires_at < NOW();

-- ================================================
-- 4. 주문 관련 쿼리
-- ================================================

-- 주문 생성
INSERT INTO orders (user_id, user_coupon_id, recipient_name, shipping_address,
                   shipping_phone, total_amount, discount_amount, final_amount, status)
VALUES (1, 1, '홍길동', '서울시 강남구', '010-1234-5678',
        890000, 89000, 801000, 'PENDING');

-- 주문 아이템 추가
INSERT INTO order_items (order_id, product_id, quantity, unit_price, subtotal)
VALUES (1, 1, 1, 890000, 890000);

-- 사용자의 주문 목록 조회 (최신순)
SELECT o.*,
       COUNT(oi.id) as item_count
FROM orders o
LEFT JOIN order_items oi ON o.id = oi.order_id
WHERE o.user_id = 1
GROUP BY o.id
ORDER BY o.created_at DESC;

-- 주문 상세 조회 (주문 아이템 포함)
SELECT o.*,
       oi.*,
       p.name as product_name,
       p.price as current_price
FROM orders o
JOIN order_items oi ON o.id = oi.order_id
JOIN products p ON oi.product_id = p.id
WHERE o.id = 1;

-- 주문 상태 변경
UPDATE orders SET status = 'PAID' WHERE id = 1;

-- 특정 기간 주문 통계
SELECT
    DATE(created_at) as order_date,
    COUNT(*) as order_count,
    SUM(final_amount) as total_revenue
FROM orders
WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
  AND status = 'PAID'
GROUP BY DATE(created_at)
ORDER BY order_date DESC;

-- ================================================
-- 5. 결제 관련 쿼리
-- ================================================

-- 결제 생성
INSERT INTO payments (order_id, paid_amount, status, data_transmission_status)
VALUES (1, 801000, 'COMPLETED', 'PENDING');

-- 결제 상태 조회
SELECT p.*, o.user_id, o.final_amount
FROM payments p
JOIN orders o ON p.order_id = o.id
WHERE p.id = 1;

-- 데이터 전송 상태 업데이트
UPDATE payments
SET data_transmission_status = 'SUCCESS'
WHERE id = 1;

-- 전송 실패 건 재처리 대상 조회
SELECT * FROM payments
WHERE data_transmission_status = 'FAILED'
ORDER BY created_at ASC
LIMIT 100;

-- ================================================
-- 6. 장바구니 관련 쿼리
-- ================================================

-- 장바구니에 상품 추가 (UPSERT)
INSERT INTO cart_items (user_id, product_id, quantity)
VALUES (1, 1, 1)
ON DUPLICATE KEY UPDATE
    quantity = quantity + VALUES(quantity);

-- 사용자의 장바구니 조회
SELECT ci.*,
       p.name as product_name,
       p.price,
       p.stock,
       (p.price * ci.quantity) as subtotal
FROM cart_items ci
JOIN products p ON ci.product_id = p.id
WHERE ci.user_id = 1;

-- 장바구니 수량 변경
UPDATE cart_items
SET quantity = 3
WHERE user_id = 1 AND product_id = 1;

-- 장바구니 아이템 삭제
DELETE FROM cart_items
WHERE user_id = 1 AND product_id = 1;

-- 장바구니 전체 삭제
DELETE FROM cart_items WHERE user_id = 1;

-- ================================================
-- 7. 통계 쿼리
-- ================================================

-- 인기 상품 Top 5 (최근 3일)
SELECT
    p.id,
    p.name,
    p.price,
    c.name as category_name,
    SUM(oi.quantity) as total_sales
FROM order_items oi
JOIN products p ON oi.product_id = p.id
JOIN categories c ON p.category_id = c.id
WHERE oi.created_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)
GROUP BY p.id, p.name, p.price, c.name
ORDER BY total_sales DESC
LIMIT 5;

-- 카테고리별 매출 통계
SELECT
    c.name as category_name,
    COUNT(DISTINCT o.id) as order_count,
    SUM(oi.subtotal) as total_revenue
FROM order_items oi
JOIN products p ON oi.product_id = p.id
JOIN categories c ON p.category_id = c.id
JOIN orders o ON oi.order_id = o.id
WHERE o.status = 'PAID'
  AND o.created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
GROUP BY c.id, c.name
ORDER BY total_revenue DESC;

-- 사용자별 구매 통계
SELECT
    u.id,
    u.name,
    u.email,
    COUNT(o.id) as order_count,
    SUM(o.final_amount) as total_spent,
    MAX(o.created_at) as last_order_date
FROM users u
LEFT JOIN orders o ON u.id = o.user_id AND o.status = 'PAID'
GROUP BY u.id, u.name, u.email
ORDER BY total_spent DESC
LIMIT 10;

-- 월별 매출 추이
SELECT
    DATE_FORMAT(created_at, '%Y-%m') as month,
    COUNT(*) as order_count,
    SUM(final_amount) as total_revenue,
    AVG(final_amount) as avg_order_value
FROM orders
WHERE status = 'PAID'
  AND created_at >= DATE_SUB(NOW(), INTERVAL 12 MONTH)
GROUP BY DATE_FORMAT(created_at, '%Y-%m')
ORDER BY month DESC;

-- ================================================
-- 8. 쿠폰 대기열 관련 쿼리
-- ================================================

-- 대기열 진입
INSERT INTO coupon_queues (user_id, coupon_id, status, queue_position)
SELECT 1, 3, 'WAITING',
       COALESCE(MAX(queue_position), 0) + 1
FROM coupon_queues
WHERE coupon_id = 3
  AND status IN ('WAITING', 'PROCESSING');

-- 대기 순번 조회
SELECT * FROM coupon_queues
WHERE user_id = 1 AND coupon_id = 3;

-- 대기 중인 사람 처리 (스케줄러)
SELECT * FROM coupon_queues
WHERE coupon_id = 3
  AND status = 'WAITING'
ORDER BY queue_position ASC, created_at ASC
LIMIT 10 FOR UPDATE;

-- 대기열 상태 변경
UPDATE coupon_queues
SET status = 'PROCESSING'
WHERE id = 1;

-- 대기열 완료 처리
UPDATE coupon_queues
SET status = 'COMPLETED', processed_at = NOW()
WHERE id = 1;

-- 대기열 실패 처리
UPDATE coupon_queues
SET status = 'FAILED',
    processed_at = NOW(),
    failed_reason = '쿠폰 수량 소진'
WHERE id = 1;

-- ================================================
-- 9. 환불 관련 쿼리
-- ================================================

-- 환불 요청
INSERT INTO refunds (order_id, refund_amount, reason, status)
VALUES (1, 801000, '단순 변심', 'REQUESTED');

-- 환불 상태 조회
SELECT r.*,
       o.user_id,
       o.final_amount,
       o.status as order_status
FROM refunds r
JOIN orders o ON r.order_id = o.id
WHERE r.id = 1;

-- 환불 승인
UPDATE refunds SET status = 'APPROVED' WHERE id = 1;

-- 환불 처리 완료 (잔액 복구)
UPDATE refunds SET status = 'COMPLETED' WHERE id = 1;
UPDATE users SET balance = balance + 801000 WHERE id = 1;
UPDATE orders SET status = 'CANCELLED' WHERE id = 1;

-- ================================================
-- 10. 관리자용 쿼리
-- ================================================

-- 재고 부족 상품 조회
SELECT * FROM products
WHERE stock < 10
ORDER BY stock ASC;

-- 만료 임박 쿠폰 조회
SELECT uc.*, u.name, u.email, c.name as coupon_name
FROM user_coupons uc
JOIN users u ON uc.user_id = u.id
JOIN coupons c ON uc.coupon_id = c.id
WHERE uc.status = 'UNUSED'
  AND uc.expires_at BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL 7 DAY);

-- 발급률이 높은 쿠폰 조회
SELECT
    c.*,
    (c.issued_quantity / c.total_quantity * 100) as issue_rate
FROM coupons c
WHERE c.issued_quantity / c.total_quantity > 0.8
ORDER BY issue_rate DESC;

-- 오래된 장바구니 조회 (30일 이상)
SELECT ci.*, u.name, u.email, p.name as product_name
FROM cart_items ci
JOIN users u ON ci.user_id = u.id
JOIN products p ON ci.product_id = p.id
WHERE ci.created_at < DATE_SUB(NOW(), INTERVAL 30 DAY);

-- 데이터 전송 실패 건수
SELECT
    data_transmission_status,
    COUNT(*) as count
FROM payments
GROUP BY data_transmission_status;

-- ================================================
-- 11. 성능 모니터링 쿼리
-- ================================================

-- 테이블별 레코드 수
SELECT
    'users' as table_name, COUNT(*) as row_count FROM users
UNION ALL
SELECT 'products', COUNT(*) FROM products
UNION ALL
SELECT 'orders', COUNT(*) FROM orders
UNION ALL
SELECT 'order_items', COUNT(*) FROM order_items
UNION ALL
SELECT 'payments', COUNT(*) FROM payments;

-- 주문 상태별 건수
SELECT status, COUNT(*) as count
FROM orders
GROUP BY status;

-- 쿠폰 상태별 건수
SELECT status, COUNT(*) as count
FROM user_coupons
GROUP BY status;

-- 평균 주문 금액
SELECT
    AVG(final_amount) as avg_order_amount,
    MIN(final_amount) as min_order_amount,
    MAX(final_amount) as max_order_amount
FROM orders
WHERE status = 'PAID';

-- ================================================
-- 12. 데이터 정리/유지보수 쿼리
-- ================================================

-- 완료된 대기열 기록 삭제 (90일 이전)
DELETE FROM coupon_queues
WHERE status IN ('COMPLETED', 'FAILED')
  AND processed_at < DATE_SUB(NOW(), INTERVAL 90 DAY);

-- 오래된 장바구니 자동 삭제 (90일 이전)
DELETE FROM cart_items
WHERE created_at < DATE_SUB(NOW(), INTERVAL 90 DAY);

-- 만료된 쿠폰 정리
UPDATE user_coupons
SET status = 'EXPIRED'
WHERE status = 'UNUSED'
  AND expires_at < NOW();

-- ================================================
-- 실행 계획 확인 (성능 튜닝)
-- ================================================

-- 인기 상품 쿼리 실행 계획
EXPLAIN
SELECT p.id, p.name, SUM(oi.quantity) as total_sales
FROM order_items oi
JOIN products p ON oi.product_id = p.id
WHERE oi.created_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)
GROUP BY p.id, p.name
ORDER BY total_sales DESC
LIMIT 5;

-- 사용자 주문 조회 실행 계획
EXPLAIN
SELECT * FROM orders
WHERE user_id = 1
ORDER BY created_at DESC;
