-- ==================================================
-- EXPLAIN ANALYZE 인덱스 성능 비교 테스트
-- 인덱스 추가 전후 성능을 직접 비교할 수 있도록 구성
-- ==================================================
-- 사용법:
-- 1. 인덱스 제거: source drop_custom_indexes.sql;
-- 2. [1단계] 쿼리 실행으로 인덱스 없는 상태 측정
-- 3. [2단계] 인덱스 생성
-- 4. [3단계] 동일 쿼리로 인덱스 있는 상태 측정
-- 5. 실행 시간, 스캔 행 수 비교
-- ==================================================

-- ==================================================
-- 1. 카테고리별 상품 조회 (상품 목록 페이지) ⭐ 핵심
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT * FROM products WHERE category_id = 1 LIMIT 20;

-- [2단계] 인덱스 생성
CREATE INDEX idx_category_id ON products(category_id);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT * FROM products WHERE category_id = 1 LIMIT 20;

-- [비교 포인트]
-- - actual time: 풀스캔 시간 → 인덱스 스캔 시간
-- - rows: 전체 테이블 스캔 → 카테고리만 스캔
-- - type: ALL → ref

-- ==================================================
-- 2. 사용자별 주문 목록 조회 (마이페이지) ⭐ 핵심
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT * FROM orders
WHERE user_id = 1
ORDER BY created_at DESC
LIMIT 10;

-- [2단계] 인덱스 생성
CREATE INDEX idx_user_id ON orders(user_id);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT * FROM orders
WHERE user_id = 1
ORDER BY created_at DESC
LIMIT 10;

-- [비교 포인트]
-- - actual time: 전체 주문 스캔 → 사용자 주문만 스캔
-- - rows: 10,000건 → 사용자 주문 수만큼
-- - type: ALL → ref

-- ==================================================
-- 3. 사용자별 특정 상태 주문 조회 ⭐⭐ 복합 조건
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT * FROM orders
WHERE user_id = 1 AND status = 'SHIPPED'
ORDER BY created_at DESC;

-- [2단계] 인덱스 생성 (user_id와 status 모두)
CREATE INDEX idx_user_id ON orders(user_id);
CREATE INDEX idx_status ON orders(status);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT * FROM orders
WHERE user_id = 1 AND status = 'SHIPPED'
ORDER BY created_at DESC;

-- [비교 포인트]
-- - MySQL이 idx_user_id 또는 idx_status 선택
-- - 복합 조건에서 카디널리티 높은 인덱스 사용

-- ==================================================
-- 4. 주문 상태별 조회 (관리자) ⭐
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT * FROM orders
WHERE status = 'DELIVERED'
AND created_at >= CURDATE()
LIMIT 100;

-- [2단계] 인덱스 생성
CREATE INDEX idx_status ON orders(status);
CREATE INDEX idx_created_at ON orders(created_at);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT * FROM orders
WHERE status = 'DELIVERED'
AND created_at >= CURDATE()
LIMIT 100;

-- [비교 포인트]
-- - 복합 조건: status + created_at
-- - MySQL이 선택도 높은 인덱스 선택

-- ==================================================
-- 5. 특정 기간 주문 조회 (월별 매출) ⭐
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT * FROM orders
WHERE created_at BETWEEN '2025-01-01' AND '2025-01-31'
ORDER BY created_at DESC;

-- [2단계] 인덱스 생성
CREATE INDEX idx_created_at ON orders(created_at);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT * FROM orders
WHERE created_at BETWEEN '2025-01-01' AND '2025-01-31'
ORDER BY created_at DESC;

-- [비교 포인트]
-- - BETWEEN + ORDER BY 모두 인덱스 활용
-- - range 타입 스캔

-- ==================================================
-- 6. 주문 상세 조회 (주문 상세 페이지) ⭐ 핵심
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT oi.*, p.name, p.price
FROM order_items oi
JOIN products p ON oi.product_id = p.id
WHERE oi.order_id = 1;

-- [2단계] 인덱스 생성
CREATE INDEX idx_order_id ON order_items(order_id);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT oi.*, p.name, p.price
FROM order_items oi
JOIN products p ON oi.product_id = p.id
WHERE oi.order_id = 1;

-- [비교 포인트]
-- - order_items 테이블 스캔 방식 변화
-- - JOIN 성능 개선

-- ==================================================
-- 7. 특정 상품이 포함된 주문 찾기 (상품 판매 내역) ⭐
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT oi.*, o.order_number, o.created_at
FROM order_items oi
JOIN orders o ON oi.order_id = o.id
WHERE oi.product_id = 100
LIMIT 50;

-- [2단계] 인덱스 생성
CREATE INDEX idx_product_id ON order_items(product_id);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT oi.*, o.order_number, o.created_at
FROM order_items oi
JOIN orders o ON oi.order_id = o.id
WHERE oi.product_id = 100
LIMIT 50;

-- [비교 포인트]
-- - product_id 필터링 성능
-- - 30,000건 order_items 중 해당 상품만 추출

-- ==================================================
-- 8. 주문의 결제 정보 조회 (결제 상세) ⭐ 핵심
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT * FROM payments WHERE order_id = 1;

-- [2단계] 인덱스 생성
CREATE INDEX idx_order_id ON payments(order_id);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT * FROM payments WHERE order_id = 1;

-- [비교 포인트]
-- - 1:1 관계 조회 최적화
-- - order_id UNIQUE 제약 효과

-- ==================================================
-- 9. 결제 상태별 조회 (정산 처리) ⭐
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT * FROM payments
WHERE status = 'COMPLETED'
LIMIT 100;

-- [2단계] 인덱스 생성
CREATE INDEX idx_status ON payments(status);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT * FROM payments
WHERE status = 'COMPLETED'
LIMIT 100;

-- [비교 포인트]
-- - ENUM 타입 컬럼 인덱스 효과
-- - 배치 처리 쿼리 최적화

-- ==================================================
-- 10. 데이터 전송 실패 건 조회 (재전송 처리) ⭐
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT * FROM payments
WHERE data_transmission_status = 'FAILED'
LIMIT 50;

-- [2단계] 인덱스 생성
CREATE INDEX idx_data_transmission_status ON payments(data_transmission_status);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT * FROM payments
WHERE data_transmission_status = 'FAILED'
LIMIT 50;

-- [비교 포인트]
-- - 실패 건만 빠르게 필터링
-- - 배치 재처리 성능

-- ==================================================
-- 11. 발급 가능한 쿠폰 조회 (쿠폰 발급 페이지) ⭐ 핵심
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT * FROM coupons
WHERE issued_quantity < total_quantity
AND start_date <= NOW()
AND end_date >= NOW();

-- [2단계] 인덱스 생성
CREATE INDEX idx_dates ON coupons(start_date, end_date);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT * FROM coupons
WHERE issued_quantity < total_quantity
AND start_date <= NOW()
AND end_date >= NOW();

-- [비교 포인트]
-- - 날짜 범위 조건 최적화
-- - 복합 인덱스 효과

-- ==================================================
-- 12. 특정 타입 쿠폰 조회 ⭐
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT * FROM coupons
WHERE coupon_type = 'PERCENTAGE'
AND start_date <= NOW()
AND end_date >= NOW();

-- [2단계] 인덱스 생성
CREATE INDEX idx_type ON coupons(coupon_type);
CREATE INDEX idx_dates ON coupons(start_date, end_date);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT * FROM coupons
WHERE coupon_type = 'PERCENTAGE'
AND start_date <= NOW()
AND end_date >= NOW();

-- [비교 포인트]
-- - 다중 조건에서 인덱스 선택
-- - type + dates 조합

-- ==================================================
-- 13. 사용자의 사용 가능 쿠폰 조회 (결제 페이지) ⭐⭐ 핵심
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT uc.*, c.name, c.discount_rate, c.discount_amount
FROM user_coupons uc
JOIN coupons c ON uc.coupon_id = c.id
WHERE uc.user_id = 1 AND uc.status = 'AVAILABLE';

-- [2단계] 복합 인덱스 생성
CREATE INDEX idx_user_status ON user_coupons(user_id, status);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT uc.*, c.name, c.discount_rate, c.discount_amount
FROM user_coupons uc
JOIN coupons c ON uc.coupon_id = c.id
WHERE uc.user_id = 1 AND uc.status = 'AVAILABLE';

-- [비교 포인트]
-- - 복합 인덱스 (user_id, status) 효과
-- - WHERE 두 조건 모두 인덱스 활용

-- ==================================================
-- 14. 특정 쿠폰의 발급 내역 조회 (쿠폰 관리) ⭐
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT uc.*, u.name, u.email
FROM user_coupons uc
JOIN users u ON uc.user_id = u.id
WHERE uc.coupon_id = 1
LIMIT 100;

-- [2단계] 인덱스 생성
CREATE INDEX idx_coupon_id ON user_coupons(coupon_id);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT uc.*, u.name, u.email
FROM user_coupons uc
JOIN users u ON uc.user_id = u.id
WHERE uc.coupon_id = 1
LIMIT 100;

-- [비교 포인트]
-- - 쿠폰별 발급 내역 필터링
-- - JOIN 성능 개선

-- ==================================================
-- 15. 만료 임박 쿠폰 조회 (알림 발송용) ⭐
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT uc.*, u.name, c.name as coupon_name
FROM user_coupons uc
JOIN users u ON uc.user_id = u.id
JOIN coupons c ON uc.coupon_id = c.id
WHERE uc.expires_at BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL 7 DAY)
AND uc.status = 'AVAILABLE'
LIMIT 100;

-- [2단계] 인덱스 생성
CREATE INDEX idx_expires_at ON user_coupons(expires_at);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT uc.*, u.name, c.name as coupon_name
FROM user_coupons uc
JOIN users u ON uc.user_id = u.id
JOIN coupons c ON uc.coupon_id = c.id
WHERE uc.expires_at BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL 7 DAY)
AND uc.status = 'AVAILABLE'
LIMIT 100;

-- [비교 포인트]
-- - BETWEEN 날짜 범위 검색
-- - 배치 알림 쿼리 최적화

-- ==================================================
-- 16. 쿠폰별 대기 중인 사용자 조회 (대기열 처리) ⭐ 핵심
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT * FROM coupon_queues
WHERE coupon_id = 7 AND status = 'WAITING'
ORDER BY queue_position
LIMIT 100;

-- [2단계] 복합 인덱스 생성
CREATE INDEX idx_coupon_status ON coupon_queues(coupon_id, status);
CREATE INDEX idx_queue_position ON coupon_queues(queue_position);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT * FROM coupon_queues
WHERE coupon_id = 7 AND status = 'WAITING'
ORDER BY queue_position
LIMIT 100;

-- [비교 포인트]
-- - 복합 조건 + 정렬 최적화
-- - 대기열 순서 보장

-- ==================================================
-- 17. 사용자의 대기열 상태 확인 ⭐ 핵심
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT * FROM coupon_queues
WHERE user_id = 1 AND coupon_id = 7;

-- [2단계] 복합 인덱스 생성
CREATE INDEX idx_user_coupon ON coupon_queues(user_id, coupon_id);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT * FROM coupon_queues
WHERE user_id = 1 AND coupon_id = 7;

-- [비교 포인트]
-- - 복합 인덱스 효과
-- - 특정 사용자+쿠폰 빠른 조회

-- ==================================================
-- 18. 사용자의 장바구니 조회 (장바구니 페이지) ⭐ 핵심
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT ci.*, p.name, p.price, p.stock, (p.price * ci.quantity) as subtotal
FROM cart_items ci
JOIN products p ON ci.product_id = p.id
WHERE ci.user_id = 1;

-- [2단계] 인덱스 생성
CREATE INDEX idx_user_id ON cart_items(user_id);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT ci.*, p.name, p.price, p.stock, (p.price * ci.quantity) as subtotal
FROM cart_items ci
JOIN products p ON ci.product_id = p.id
WHERE ci.user_id = 1;

-- [비교 포인트]
-- - 사용자별 장바구니 필터링
-- - JOIN 성능 개선

-- ==================================================
-- 19. 특정 상품을 장바구니에 담은 사용자 수 ⭐
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT COUNT(DISTINCT user_id) as cart_count
FROM cart_items
WHERE product_id = 1;

-- [2단계] 인덱스 생성
CREATE INDEX idx_product_id ON cart_items(product_id);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT COUNT(DISTINCT user_id) as cart_count
FROM cart_items
WHERE product_id = 1;

-- [비교 포인트]
-- - 상품별 인기도 집계
-- - COUNT DISTINCT 성능

-- ==================================================
-- 20. 사용자의 배송지 목록 조회 (배송지 선택) ⭐ 핵심
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT * FROM shipping_addresses
WHERE user_id = 1
ORDER BY is_default DESC, created_at DESC;

-- [2단계] 인덱스 생성
CREATE INDEX idx_user_id ON shipping_addresses(user_id);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT * FROM shipping_addresses
WHERE user_id = 1
ORDER BY is_default DESC, created_at DESC;

-- [비교 포인트]
-- - user_id 필터링
-- - 정렬 성능

-- ==================================================
-- 21. 사용자의 기본 배송지 조회 ⭐⭐ 핵심
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT * FROM shipping_addresses
WHERE user_id = 1 AND is_default = TRUE
LIMIT 1;

-- [2단계] 복합 인덱스 생성
CREATE INDEX idx_user_default ON shipping_addresses(user_id, is_default);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT * FROM shipping_addresses
WHERE user_id = 1 AND is_default = TRUE
LIMIT 1;

-- [비교 포인트]
-- - 복합 인덱스로 단일 행 즉시 검색
-- - 주문 프로세스 최적화

-- ==================================================
-- 22. 주문의 환불 내역 조회 ⭐ 핵심
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT * FROM refunds WHERE order_id = 2;

-- [2단계] 인덱스 생성
CREATE INDEX idx_order_id ON refunds(order_id);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT * FROM refunds WHERE order_id = 2;

-- [비교 포인트]
-- - 주문별 환불 이력 조회
-- - 1:1 관계 최적화

-- ==================================================
-- 23. 환불 처리 대기 건 조회 (관리자) ⭐
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT r.*, o.order_number, u.name, u.email
FROM refunds r
JOIN orders o ON r.order_id = o.id
JOIN users u ON o.user_id = u.id
WHERE r.status = 'PENDING'
LIMIT 50;

-- [2단계] 인덱스 생성
CREATE INDEX idx_status ON refunds(status);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT r.*, o.order_number, u.name, u.email
FROM refunds r
JOIN orders o ON r.order_id = o.id
JOIN users u ON o.user_id = u.id
WHERE r.status = 'PENDING'
LIMIT 50;

-- [비교 포인트]
-- - 상태별 필터링 + JOIN
-- - 관리자 대시보드 쿼리 최적화

-- ==================================================
-- 24. 발행 대기 이벤트 조회 (배치 처리) ⭐⭐ 핵심
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT * FROM outbox_events
WHERE status = 'PENDING' AND retry_count < 3
ORDER BY created_at
LIMIT 100;

-- [2단계] 복합 인덱스 생성
CREATE INDEX idx_status_retry ON outbox_events(status, retry_count);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT * FROM outbox_events
WHERE status = 'PENDING' AND retry_count < 3
ORDER BY created_at
LIMIT 100;

-- [비교 포인트]
-- - 복합 조건 최적화
-- - 배치 처리 성능 개선

-- ==================================================
-- 25. 실패한 이벤트 재처리 ⭐
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT * FROM outbox_events
WHERE status = 'FAILED' AND retry_count < 5
ORDER BY created_at
LIMIT 100;

-- [2단계] 복합 인덱스 생성 (이미 있으면 생략)
CREATE INDEX idx_status_retry ON outbox_events(status, retry_count);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT * FROM outbox_events
WHERE status = 'FAILED' AND retry_count < 5
ORDER BY created_at
LIMIT 100;

-- [비교 포인트]
-- - 실패 건 재처리 효율성
-- - 배치 작업 최적화

-- ==================================================
-- 26. 오래된 이벤트 정리 (스케줄러) ⭐
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT * FROM outbox_events
WHERE created_at < DATE_SUB(NOW(), INTERVAL 30 DAY)
AND status = 'PUBLISHED'
LIMIT 1000;

-- [2단계] 인덱스 생성
CREATE INDEX idx_created_at ON outbox_events(created_at);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT * FROM outbox_events
WHERE created_at < DATE_SUB(NOW(), INTERVAL 30 DAY)
AND status = 'PUBLISHED'
LIMIT 1000;

-- [비교 포인트]
-- - 날짜 기반 정리 작업
-- - 대량 데이터 정리 성능

-- ==================================================
-- 27. 최근 업데이트된 인기 상품 조회 (메인 페이지) ⭐ 핵심
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT pp.*, p.name, p.price
FROM popular_products pp
JOIN products p ON pp.product_id = p.id
WHERE pp.updated_at >= DATE_SUB(NOW(), INTERVAL 1 DAY)
ORDER BY pp.rank
LIMIT 20;

-- [2단계] 인덱스 생성
CREATE INDEX idx_updated_at ON popular_products(updated_at);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT pp.*, p.name, p.price
FROM popular_products pp
JOIN products p ON pp.product_id = p.id
WHERE pp.updated_at >= DATE_SUB(NOW(), INTERVAL 1 DAY)
ORDER BY pp.rank
LIMIT 20;

-- [비교 포인트]
-- - 최신 데이터 필터링
-- - 메인 페이지 로딩 속도

-- ==================================================
-- 28. 복합 조건 검색 (인덱스 효과 극대화) ⭐⭐⭐ 최우선
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT * FROM orders
WHERE user_id = 1
AND status = 'DELIVERED'
AND created_at >= DATE_SUB(NOW(), INTERVAL 3 MONTH)
ORDER BY created_at DESC;

-- [2단계] 인덱스 생성 (세 조건 모두)
CREATE INDEX idx_user_id ON orders(user_id);
CREATE INDEX idx_status ON orders(status);
CREATE INDEX idx_created_at ON orders(created_at);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT * FROM orders
WHERE user_id = 1
AND status = 'DELIVERED'
AND created_at >= DATE_SUB(NOW(), INTERVAL 3 MONTH)
ORDER BY created_at DESC;

-- [비교 포인트]
-- - 세 조건 중 옵티마이저가 선택한 인덱스
-- - 복합 조건 최적화 전략
-- - 실무에서 가장 많이 사용하는 패턴

-- ==================================================
-- 29. JOIN + WHERE + 정렬 (종합 성능 테스트) ⭐⭐⭐ 최우선
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT
    p.name,
    p.price,
    COUNT(oi.id) as order_count,
    SUM(oi.quantity) as total_sold
FROM products p
JOIN order_items oi ON p.id = oi.product_id
WHERE p.category_id = 1
GROUP BY p.id, p.name, p.price
ORDER BY total_sold DESC
LIMIT 20;

-- [2단계] 인덱스 생성
CREATE INDEX idx_category_id ON products(category_id);
CREATE INDEX idx_product_id ON order_items(product_id);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT
    p.name,
    p.price,
    COUNT(oi.id) as order_count,
    SUM(oi.quantity) as total_sold
FROM products p
JOIN order_items oi ON p.id = oi.product_id
WHERE p.category_id = 1
GROUP BY p.id, p.name, p.price
ORDER BY total_sold DESC
LIMIT 20;

-- [비교 포인트]
-- - JOIN 성능 개선
-- - GROUP BY, ORDER BY 최적화
-- - 집계 쿼리 성능

-- ==================================================
-- 30. 상관 서브쿼리 (N+1 문제 확인) ⭐⭐⭐ 최우선
-- ==================================================

-- [1단계] 인덱스 없이 실행
EXPLAIN ANALYZE
SELECT
    u.id,
    u.name,
    u.email,
    (SELECT COUNT(*) FROM orders WHERE user_id = u.id) as order_count,
    (SELECT SUM(final_amount) FROM orders WHERE user_id = u.id) as total_spent
FROM users u
WHERE u.id BETWEEN 1 AND 100;

-- [2단계] 인덱스 생성
CREATE INDEX idx_user_id ON orders(user_id);

-- [3단계] 인덱스 있을 때 실행
EXPLAIN ANALYZE
SELECT
    u.id,
    u.name,
    u.email,
    (SELECT COUNT(*) FROM orders WHERE user_id = u.id) as order_count,
    (SELECT SUM(final_amount) FROM orders WHERE user_id = u.id) as total_spent
FROM users u
WHERE u.id BETWEEN 1 AND 100;

-- [비교 포인트]
-- - 서브쿼리 성능 (100명 * 2개 = 200번 쿼리)
-- - 인덱스로 각 서브쿼리 최적화
-- - N+1 문제 해결 효과

-- ==================================================
-- 테스트 완료 후 확인 사항
-- ==================================================
--
-- ✅ 확인할 주요 지표:
-- 1. actual time: 실행 시간 (ms)
--    - 인덱스 전: 100ms → 인덱스 후: 1ms (100배 개선)
--
-- 2. rows: 스캔한 행 수
--    - 인덱스 전: 10,000 rows → 인덱스 후: 10 rows
--
-- 3. type: 접근 방식
--    - ALL (풀스캔) → ref/range (인덱스 스캔)
--
-- 4. key: 사용된 인덱스
--    - NULL → idx_name
--
-- 5. Extra: 추가 정보
--    - Using where → Using index condition
--
-- ==================================================
