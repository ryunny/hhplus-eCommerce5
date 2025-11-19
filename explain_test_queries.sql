-- ==================================================
-- EXPLAIN ANALYZE 테스트용 쿼리 모음
-- ==================================================
-- 사용법:
-- 1. 인덱스 없는 상태에서 쿼리 복사해서 MySQL에 실행 (EXPLAIN ANALYZE 이미 포함)
-- 2. 실행 계획의 type, rows, key, Extra 컬럼 확인
-- 3. schema.sql의 인덱스 주석 해제 후 DB 재생성
-- 4. 동일 쿼리로 성능 비교 (type=ref, rows 감소, key=인덱스명 확인)
-- ==================================================

-- ==================================================
-- 1. users 테이블 테스트
-- ==================================================

-- 1-1. public_id로 조회 (idx_public_id 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM users WHERE public_id = (SELECT public_id FROM users LIMIT 1);

--인덱스 없을 경우의 결과값
-> Rows fetched before execution  (cost=0..0 rows=1) (actual time=56e-6..102e-6 rows=1 loops=1)


-- 1-2. email로 조회 (idx_email 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM users WHERE email = 'hong@example.com';

--인덱스 없을 경우의 결과값
-> Rows fetched before execution  (cost=0..0 rows=1) (actual time=179e-6..209e-6 rows=1 loops=1)

-- 1-3. 잔액 범위 조회
EXPLAIN ANALYZE SELECT * FROM users WHERE balance >= 100000 ORDER BY balance DESC;

--인덱스 없을 경우의 결과값
-> Sort: users.balance DESC  (cost=0.65 rows=4) (actual time=0.121..0.123 rows=36 loops=1)
    -> Filter: (users.balance >= 100000)  (cost=0.65 rows=4) (actual time=0.0408..0.0779 rows=36 loops=1)
        -> Table scan on users  (cost=0.65 rows=4) (actual time=0.0279..0.0619 rows=50 loops=1)

-- 1-4. 이름 검색
EXPLAIN ANALYZE SELECT * FROM users WHERE name LIKE '%홍%';

--인덱스 없을 경우의 결과값
-> Filter: (users.`name` like '%홍%')  (cost=0.65 rows=1) (actual time=0.0489..0.0489 rows=0 loops=1)
    -> Table scan on users  (cost=0.65 rows=4) (actual time=0.0234..0.0397 rows=50 loops=1)

-- ==================================================
-- 2. categories 테이블 테스트
-- ==================================================

-- 2-1. 카테고리명으로 조회 (idx_name 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM categories WHERE name = '전자제품';

--인덱스 없을 경우의 결과값
-> Filter: (categories.`name` = '전자제품')  (cost=1.4 rows=1) (actual time=1.75..1.75 rows=0 loops=1)
    -> Table scan on categories  (cost=1.4 rows=4) (actual time=1.74..1.74 rows=4 loops=1)


-- 2-2. 카테고리별 상품 수 집계
EXPLAIN ANALYZE SELECT c.name, COUNT(p.id) as product_count
FROM categories c
LEFT JOIN products p ON c.id = p.category_id
GROUP BY c.id, c.name;

--인덱스 없을 경우의 결과값
-> Table scan on <temporary>  (actual time=10.5..10.5 rows=4 loops=1)
    -> Aggregate using temporary table  (actual time=10.4..10.4 rows=4 loops=1)
        -> Left hash join (p.category_id = c.id)  (cost=42.8 rows=400) (actual time=0.137..0.168 rows=100 loops=1)
            -> Table scan on c  (cost=0.65 rows=4) (actual time=0.00788..0.024 rows=4 loops=1)
            -> Hash
                -> Table scan on p  (cost=2.56 rows=100) (actual time=0.0772..0.0887 rows=100 loops=1)


-- ==================================================
-- 3. products 테이블 테스트
-- ==================================================

-- 3-1. category_id로 조회 (idx_category_id 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM products WHERE category_id = 1;

--인덱스 없을 경우의 결과값
-> Filter: (products.category_id = 1)  (cost=10.2 rows=10) (actual time=0.0326..0.109 rows=40 loops=1)
    -> Table scan on products  (cost=10.2 rows=100) (actual time=0.0308..0.102 rows=100 loops=1)

-- 3-2. 상품명 검색 (idx_name 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM products WHERE name LIKE '%노트북%';

--인덱스 없을 경우의 결과값
-> Filter: (products.`name` like '%노트북%')  (cost=10.2 rows=11.1) (actual time=0.121..0.121 rows=0 loops=1)
    -> Table scan on products  (cost=10.2 rows=100) (actual time=0.03..0.0934 rows=100 loops=1)

-- 3-3. 가격 범위 조회
EXPLAIN ANALYZE SELECT * FROM products WHERE price BETWEEN 10000 AND 100000;

--인덱스 없을 경우의 결과값
-> Filter: (products.price between 10000 and 100000)  (cost=10.2 rows=11.1) (actual time=0.0335..0.0858 rows=71 loops=1)
    -> Table scan on products  (cost=10.2 rows=100) (actual time=0.0282..0.0778 rows=100 loops=1)

-- 3-4. 재고가 있는 상품 조회
EXPLAIN ANALYZE SELECT * FROM products WHERE stock > 0 ORDER BY stock DESC;

--인덱스 없을 경우의 결과값
-> Sort: products.stock DESC  (cost=10.2 rows=100) (actual time=0.101..0.114 rows=100 loops=1)
    -> Filter: (products.stock > 0)  (cost=10.2 rows=100) (actual time=0.0251..0.0721 rows=100 loops=1)
        -> Table scan on products  (cost=10.2 rows=100) (actual time=0.0235..0.0661 rows=100 loops=1)

-- 3-5. 카테고리별 평균 가격
EXPLAIN ANALYZE SELECT c.name, AVG(p.price) as avg_price, COUNT(p.id) as product_count
FROM categories c
JOIN products p ON c.id = p.category_id
GROUP BY c.id, c.name;

--인덱스 없을 경우의 결과값
-> Table scan on <temporary>  (actual time=0.258..0.259 rows=4 loops=1)
    -> Aggregate using temporary table  (actual time=0.258..0.258 rows=4 loops=1)
        -> Inner hash join (p.category_id = c.id)  (cost=40.9 rows=40) (actual time=0.131..0.165 rows=100 loops=1)
            -> Table scan on p  (cost=0.313 rows=100) (actual time=0.092..0.112 rows=100 loops=1)
            -> Hash
                -> Table scan on c  (cost=0.65 rows=4) (actual time=0.0263..0.0296 rows=4 loops=1)

-- ==================================================
-- 4. shipping_addresses 테이블 테스트
-- ==================================================

-- 4-1. user_id로 조회 (idx_user_id 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM shipping_addresses WHERE user_id = 1;

--인덱스 없을 경우의 결과값
-> Filter: (shipping_addresses.user_id = 1)  (cost=3.3 rows=2.3) (actual time=10.6..10.6 rows=2 loops=1)
    -> Table scan on shipping_addresses  (cost=3.3 rows=23) (actual time=10.6..10.6 rows=23 loops=1)

-- 4-2. 기본 배송지 조회 (idx_user_default 복합 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM shipping_addresses WHERE user_id = 1 AND is_default = TRUE;

--인덱스 없을 경우의 결과값
-> Filter: ((shipping_addresses.is_default = true) and (shipping_addresses.user_id = 1))  (cost=2.55 rows=1) (actual time=0.0275..0.0398 rows=1 loops=1)
    -> Table scan on shipping_addresses  (cost=2.55 rows=23) (actual time=0.0245..0.0353 rows=23 loops=1)

-- 4-3. 사용자별 배송지 개수
EXPLAIN ANALYZE SELECT user_id, COUNT(*) as address_count
FROM shipping_addresses
GROUP BY user_id
HAVING COUNT(*) > 1;

--인덱스 없을 경우의 결과값
-> Filter: (`count(0)` > 1)  (actual time=0.0456..0.0477 rows=3 loops=1)
    -> Table scan on <temporary>  (actual time=0.0433..0.0445 rows=20 loops=1)
        -> Aggregate using temporary table  (actual time=0.0427..0.0427 rows=20 loops=1)
            -> Table scan on shipping_addresses  (cost=2.55 rows=23) (actual time=0.0232..0.0274 rows=23 loops=1)

-- ==================================================
-- 5. coupons 테이블 테스트
-- ==================================================

-- 5-1. 쿠폰 타입으로 조회 (idx_type 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM coupons WHERE coupon_type = 'RATE';

--인덱스 없을 경우의 결과값
-> Filter: (coupons.coupon_type = 'RATE')  (cost=2 rows=1) (actual time=10.9..10.9 rows=7 loops=1)
    -> Table scan on coupons  (cost=2 rows=10) (actual time=10.9..10.9 rows=10 loops=1)

-- 5-2. 유효기간 내 쿠폰 조회 (idx_dates 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM coupons
WHERE start_date <= NOW() AND end_date >= NOW();

--인덱스 없을 경우의 결과값
-> Filter: ((coupons.start_date <= <cache>(now())) and (coupons.end_date >= <cache>(now())))  (cost=0.361 rows=1.11) (actual time=0.0355..0.0425 rows=8 loops=1)
    -> Table scan on coupons  (cost=0.361 rows=10) (actual time=0.0277..0.0334 rows=10 loops=1)

-- 5-3. 발급 가능한 쿠폰 조회
EXPLAIN ANALYZE SELECT * FROM coupons
WHERE issued_quantity < total_quantity
AND start_date <= NOW()
AND end_date >= NOW();

--인덱스 없을 경우의 결과값
-> Filter: ((coupons.issued_quantity < coupons.total_quantity) and (coupons.start_date <= <cache>(now())) and (coupons.end_date >= <cache>(now())))  (cost=1.02 rows=1) (actual time=0.0302..0.0372 rows=8 loops=1)
    -> Table scan on coupons  (cost=1.02 rows=10) (actual time=0.0236..0.0293 rows=10 loops=1)

-- 5-4. 대기열 사용 쿠폰 조회
EXPLAIN ANALYZE SELECT * FROM coupons WHERE use_queue = TRUE;

--인덱스 없을 경우의 결과값
-> Filter: (coupons.use_queue = true)  (cost=1.25 rows=1) (actual time=0.0346..0.0388 rows=2 loops=1)
    -> Table scan on coupons  (cost=1.25 rows=10) (actual time=0.0295..0.0356 rows=10 loops=1)


-- ==================================================
-- 6. user_coupons 테이블 테스트
-- ==================================================

-- 6-1. 사용자별 사용 가능 쿠폰 조회 (idx_user_status 복합 인덱스 필요)
EXPLAIN ANALYZE SELECT uc.*, c.name, c.discount_rate, c.discount_amount
FROM user_coupons uc
JOIN coupons c ON uc.coupon_id = c.id
WHERE uc.user_id = 1 AND uc.status = 'AVAILABLE';

--인덱스 없을 경우의 결과값
-> Nested loop inner join  (cost=3.35 rows=1) (actual time=10.7..10.7 rows=2 loops=1)
    -> Filter: ((uc.user_id = 1) and (uc.`status` = 'AVAILABLE'))  (cost=3 rows=1) (actual time=10.7..10.7 rows=2 loops=1)
        -> Table scan on uc  (cost=3 rows=20) (actual time=10.7..10.7 rows=20 loops=1)
    -> Single-row index lookup on c using PRIMARY (id=uc.coupon_id)  (cost=0.35 rows=1) (actual time=0.0136..0.0136 rows=1 loops=2)

-- 6-2. 쿠폰 ID로 발급 내역 조회 (idx_coupon_id 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM user_coupons WHERE coupon_id = 1;

--인덱스 없을 경우의 결과값
-> Filter: (user_coupons.coupon_id = 1)  (cost=2.25 rows=2) (actual time=0.0362..0.0445 rows=4 loops=1)
    -> Table scan on user_coupons  (cost=2.25 rows=20) (actual time=0.0338..0.0407 rows=20 loops=1)

-- 6-3. 만료 예정 쿠폰 조회 (idx_expires_at 인덱스 필요)
EXPLAIN ANALYZE SELECT uc.*, u.name, c.name as coupon_name
FROM user_coupons uc
JOIN users u ON uc.user_id = u.id
JOIN coupons c ON uc.coupon_id = c.id
WHERE uc.expires_at BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL 7 DAY)
AND uc.status = 'AVAILABLE';

--인덱스 없을 경우의 결과값
-> Nested loop inner join  (cost=2.85 rows=1) (actual time=0.039..0.039 rows=0 loops=1)
    -> Nested loop inner join  (cost=2.5 rows=1) (actual time=0.0383..0.0383 rows=0 loops=1)
        -> Filter: ((uc.expires_at between <cache>(now()) and <cache>((now() + interval 7 day))) and (uc.`status` = 'AVAILABLE'))  (cost=2.15 rows=1) (actual time=0.0378..0.0378 rows=0 loops=1)
            -> Table scan on uc  (cost=2.15 rows=20) (actual time=0.0223..0.027 rows=20 loops=1)
        -> Single-row index lookup on u using PRIMARY (id=uc.user_id)  (cost=0.35 rows=1) (never executed)
    -> Single-row index lookup on c using PRIMARY (id=uc.coupon_id)  (cost=0.35 rows=1) (never executed)

-- 6-4. 사용자별 쿠폰 사용 통계
EXPLAIN ANALYZE SELECT user_id, status, COUNT(*) as count
FROM user_coupons
GROUP BY user_id, status;

--인덱스 없을 경우의 결과값
-> Table scan on <temporary>  (actual time=0.107..0.109 rows=15 loops=1)
    -> Aggregate using temporary table  (actual time=0.106..0.106 rows=15 loops=1)
        -> Table scan on user_coupons  (cost=2.25 rows=20) (actual time=0.026..0.0821 rows=20 loops=1)

-- ==================================================
-- 7. coupon_queues 테이블 테스트
-- ==================================================

-- 7-1. 쿠폰별 대기열 조회 (idx_coupon_status 복합 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM coupon_queues
WHERE coupon_id = 7 AND status = 'PENDING'
ORDER BY queue_position;

--인덱스 없을 경우의 결과값
-> Sort: coupon_queues.queue_position  (cost=1.1 rows=1) (actual time=10.8..10.8 rows=4 loops=1)
    -> Filter: ((coupon_queues.coupon_id = 7) and (coupon_queues.`status` = 'PENDING'))  (cost=1.1 rows=1) (actual time=10.7..10.7 rows=4 loops=1)
        -> Table scan on coupon_queues  (cost=1.1 rows=1) (actual time=10.7..10.7 rows=30 loops=1)


-- 7-2. 사용자별 대기열 조회 (idx_user_coupon 복합 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM coupon_queues
WHERE user_id = 1 AND coupon_id = 7;

--인덱스 없을 경우의 결과값
-> Filter: ((coupon_queues.coupon_id = 7) and (coupon_queues.user_id = 1))  (cost=0.35 rows=1) (actual time=0.437..0.513 rows=1 loops=1)
    -> Table scan on coupon_queues  (cost=0.35 rows=1) (actual time=0.419..0.49 rows=30 loops=1)

-- 7-3. 대기 순번으로 조회 (idx_queue_position 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM coupon_queues
WHERE queue_position <= 100
ORDER BY queue_position;

--인덱스 없을 경우의 결과값
-> Sort: coupon_queues.queue_position  (cost=0.35 rows=1) (actual time=0.0533..0.0549 rows=30 loops=1)
    -> Filter: (coupon_queues.queue_position <= 100)  (cost=0.35 rows=1) (actual time=0.0228..0.0388 rows=30 loops=1)
        -> Table scan on coupon_queues  (cost=0.35 rows=1) (actual time=0.0213..0.0359 rows=30 loops=1)

-- 7-4. 쿠폰별 대기 현황 통계
EXPLAIN ANALYZE SELECT coupon_id, status, COUNT(*) as count, MIN(queue_position) as min_pos, MAX(queue_position) as max_pos
FROM coupon_queues
GROUP BY coupon_id, status;

--인덱스 없을 경우의 결과값
-> Table scan on <temporary>  (actual time=0.0647..0.0655 rows=7 loops=1)
    -> Aggregate using temporary table  (actual time=0.0641..0.0641 rows=7 loops=1)
        -> Table scan on coupon_queues  (cost=0.35 rows=1) (actual time=0.0234..0.0362 rows=30 loops=1)

-- ==================================================
-- 8. orders 테이블 테스트
-- ==================================================

-- 8-1. 주문번호로 조회 (idx_order_number 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM orders WHERE order_number = (SELECT order_number FROM orders LIMIT 1);

--인덱스 없을 경우의 결과값
-> Rows fetched before execution  (cost=0..0 rows=1) (actual time=56e-6..100e-6 rows=1 loops=1)

-- 8-2. 사용자별 주문 조회 (idx_user_id 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM orders WHERE user_id = 1 ORDER BY created_at DESC;

--인덱스 없을 경우의 결과값
-> Sort: orders.created_at DESC  (cost=5.25 rows=50) (actual time=0.0846..0.085 rows=4 loops=1)
    -> Filter: (orders.user_id = 1)  (cost=5.25 rows=50) (actual time=0.0341..0.0715 rows=4 loops=1)
        -> Table scan on orders  (cost=5.25 rows=50) (actual time=0.0322..0.0671 rows=50 loops=1)

-- 8-3. 주문 상태별 조회 (idx_status 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM orders WHERE status = 'DELIVERED';

--인덱스 없을 경우의 결과값
-> Filter: (orders.`status` = 'DELIVERED')  (cost=5.25 rows=5) (actual time=0.028..0.0546 rows=26 loops=1)
    -> Table scan on orders  (cost=5.25 rows=50) (actual time=0.025..0.0476 rows=50 loops=1)

-- 8-4. 날짜 범위로 조회 (idx_created_at 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM orders
WHERE created_at BETWEEN '2025-01-01' AND '2025-01-31'
ORDER BY created_at DESC;

--인덱스 없을 경우의 결과값
-> Sort: orders.created_at DESC  (cost=5.25 rows=50) (actual time=0.143..0.143 rows=5 loops=1)
    -> Filter: (orders.created_at between '2025-01-01' and '2025-01-31')  (cost=5.25 rows=50) (actual time=0.0557..0.123 rows=5 loops=1)
        -> Table scan on orders  (cost=5.25 rows=50) (actual time=0.0443..0.0956 rows=50 loops=1)

-- 8-5. 사용자별 주문 통계
EXPLAIN ANALYZE SELECT user_id, COUNT(*) as order_count, SUM(final_amount) as total_amount
FROM orders
GROUP BY user_id
ORDER BY total_amount DESC;

--인덱스 없을 경우의 결과값
-> Sort: total_amount DESC  (actual time=1.04..1.05 rows=20 loops=1)
    -> Table scan on <temporary>  (actual time=1.01..1.01 rows=20 loops=1)
        -> Aggregate using temporary table  (actual time=1..1 rows=20 loops=1)
            -> Table scan on orders  (cost=5.25 rows=50) (actual time=0.0362..0.0955 rows=50 loops=1)

-- 8-6. 상태별 주문 통계
EXPLAIN ANALYZE SELECT status, COUNT(*) as count, SUM(final_amount) as total_amount
FROM orders
GROUP BY status;

--인덱스 없을 경우의 결과값
-> Table scan on <temporary>  (actual time=0.0801..0.0806 rows=4 loops=1)
    -> Aggregate using temporary table  (actual time=0.0795..0.0795 rows=4 loops=1)
        -> Table scan on orders  (cost=5.25 rows=50) (actual time=0.0258..0.0407 rows=50 loops=1)


-- 8-7. 일별 주문 통계
EXPLAIN ANALYZE SELECT DATE(created_at) as order_date, COUNT(*) as order_count, SUM(final_amount) as daily_revenue
FROM orders
GROUP BY DATE(created_at)
ORDER BY order_date DESC;

--인덱스 없을 경우의 결과값
-> Sort: order_date DESC  (actual time=0.115..0.116 rows=50 loops=1)
    -> Table scan on <temporary>  (actual time=0.0961..0.0993 rows=50 loops=1)
        -> Aggregate using temporary table  (actual time=0.0951..0.0951 rows=50 loops=1)
            -> Table scan on orders  (cost=5.25 rows=50) (actual time=0.0271..0.0578 rows=50 loops=1)


-- ==================================================
-- 9. order_items 테이블 테스트
-- ==================================================

-- 9-1. 주문별 상품 조회 (idx_order_id 인덱스 필요)
EXPLAIN ANALYZE SELECT oi.*, p.name, p.price
FROM order_items oi
JOIN products p ON oi.product_id = p.id
WHERE oi.order_id = 1;

--인덱스 없을 경우의 결과값
-> Nested loop inner join  (cost=9.91 rows=6.6) (actual time=10.9..10.9 rows=2 loops=1)
    -> Filter: (oi.order_id = 1)  (cost=7.6 rows=6.6) (actual time=10.9..10.9 rows=2 loops=1)
        -> Table scan on oi  (cost=7.6 rows=66) (actual time=10.9..10.9 rows=66 loops=1)
    -> Single-row index lookup on p using PRIMARY (id=oi.product_id)  (cost=0.265 rows=1) (actual time=0.0172..0.0173 rows=1 loops=2)

-- 9-2. 상품별 판매 내역 (idx_product_id 인덱스 필요)
EXPLAIN ANALYZE SELECT product_id, SUM(quantity) as total_sold, SUM(subtotal) as total_revenue
FROM order_items
GROUP BY product_id
ORDER BY total_sold DESC
LIMIT 20;

--인덱스 없을 경우의 결과값
-> Limit: 20 row(s)  (actual time=0.209..0.211 rows=20 loops=1)
    -> Sort: total_sold DESC, limit input to 20 row(s) per chunk  (actual time=0.208..0.209 rows=20 loops=1)
        -> Table scan on <temporary>  (actual time=0.105..0.11 rows=56 loops=1)
            -> Aggregate using temporary table  (actual time=0.104..0.104 rows=56 loops=1)
                -> Table scan on order_items  (cost=6.85 rows=66) (actual time=0.0452..0.055 rows=66 loops=1)

-- 9-3. 베스트셀러 상품 조회
EXPLAIN ANALYZE SELECT p.name, p.category_id, SUM(oi.quantity) as total_sold
FROM order_items oi
JOIN products p ON oi.product_id = p.id
GROUP BY p.id, p.name, p.category_id
ORDER BY total_sold DESC
LIMIT 10;

--인덱스 없을 경우의 결과값
-> Limit: 10 row(s)  (actual time=0.338..0.339 rows=10 loops=1)
    -> Sort: total_sold DESC, limit input to 10 row(s) per chunk  (actual time=0.338..0.338 rows=10 loops=1)
        -> Table scan on <temporary>  (actual time=0.3..0.305 rows=56 loops=1)
            -> Aggregate using temporary table  (actual time=0.299..0.299 rows=56 loops=1)
                -> Nested loop inner join  (cost=30 rows=66) (actual time=0.0661..0.15 rows=66 loops=1)
                    -> Table scan on oi  (cost=6.85 rows=66) (actual time=0.0414..0.0507 rows=66 loops=1)
                    -> Single-row index lookup on p using PRIMARY (id=oi.product_id)  (cost=0.252 rows=1) (actual time=0.00135..0.00137 rows=1 loops=66)

-- ==================================================
-- 10. payments 테이블 테스트
-- ==================================================

-- 10-1. payment_id로 조회 (idx_payment_id 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM payments WHERE payment_id = (SELECT payment_id FROM payments LIMIT 1);

--인덱스 없을 경우의 결과값
-> Rows fetched before execution  (cost=0..0 rows=1) (actual time=67e-6..116e-6 rows=1 loops=1)

-- 10-2. 주문별 결제 조회 (idx_order_id 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM payments WHERE order_id = 1;

--인덱스 없을 경우의 결과값
-> Filter: (payments.order_id = 1)  (cost=5.25 rows=5) (actual time=0.0362..0.0593 rows=1 loops=1)
    -> Table scan on payments  (cost=5.25 rows=50) (actual time=0.0343..0.0551 rows=50 loops=1)

-- 10-3. 결제 상태별 조회 (idx_status 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM payments WHERE status = 'COMPLETED';

--인덱스 없을 경우의 결과값
-> Filter: (payments.`status` = 'COMPLETED')  (cost=5.25 rows=5) (actual time=0.0264..0.0443 rows=44 loops=1)
    -> Table scan on payments  (cost=5.25 rows=50) (actual time=0.0235..0.0367 rows=50 loops=1)

-- 10-4. 데이터 전송 상태별 조회 (idx_data_transmission_status 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM payments WHERE data_transmission_status = 'PENDING';

--인덱스 없을 경우의 결과값
-> Filter: (payments.data_transmission_status = 'PENDING')  (cost=5.25 rows=5) (actual time=0.0409..0.0666 rows=23 loops=1)
    -> Table scan on payments  (cost=5.25 rows=50) (actual time=0.0343..0.0566 rows=50 loops=1)

-- 10-5. 결제 완료 건수 및 금액 집계
EXPLAIN ANALYZE SELECT status, COUNT(*) as count, SUM(paid_amount) as total_amount
FROM payments
GROUP BY status;

--인덱스 없을 경우의 결과값
-> Table scan on <temporary>  (actual time=0.0736..0.0739 rows=2 loops=1)
    -> Aggregate using temporary table  (actual time=0.073..0.073 rows=2 loops=1)
        -> Table scan on payments  (cost=5.25 rows=50) (actual time=0.0237..0.0355 rows=50 loops=1)

-- 10-6. 일별 결제 통계
EXPLAIN ANALYZE SELECT DATE(created_at) as payment_date,
       COUNT(*) as payment_count,
       SUM(paid_amount) as daily_revenue
FROM payments
WHERE status = 'COMPLETED'
GROUP BY DATE(created_at)
ORDER BY payment_date DESC;

--인덱스 없을 경우의 결과값
-> Sort: payment_date DESC  (actual time=0.121..0.123 rows=44 loops=1)
    -> Table scan on <temporary>  (actual time=0.1..0.103 rows=44 loops=1)
        -> Aggregate using temporary table  (actual time=0.0996..0.0996 rows=44 loops=1)
            -> Filter: (payments.`status` = 'COMPLETED')  (cost=5.25 rows=5) (actual time=0.0373..0.0634 rows=44 loops=1)
                -> Table scan on payments  (cost=5.25 rows=50) (actual time=0.0336..0.0519 rows=50 loops=1)


-- ==================================================
-- 11. cart_items 테이블 테스트
-- ==================================================

-- 11-1. 사용자별 장바구니 조회 (idx_user_id 인덱스 필요)
EXPLAIN ANALYZE SELECT ci.*, p.name, p.price, (p.price * ci.quantity) as subtotal
FROM cart_items ci
JOIN products p ON ci.product_id = p.id
WHERE ci.user_id = 1;

--인덱스 없을 경우의 결과값
-> Nested loop inner join  (cost=1.85 rows=3) (actual time=0.0714..0.0755 rows=3 loops=1)
    -> Index lookup on ci using uk_user_product (user_id=1)  (cost=0.8 rows=3) (actual time=0.0633..0.065 rows=3 loops=1)
    -> Single-row index lookup on p using PRIMARY (id=ci.product_id)  (cost=0.283 rows=1) (actual time=0.00283..0.00286 rows=1 loops=3)


-- 11-2. 상품별 장바구니 담김 수 (idx_product_id 인덱스 필요)
EXPLAIN ANALYZE SELECT product_id, COUNT(*) as cart_count, SUM(quantity) as total_quantity
FROM cart_items
GROUP BY product_id
ORDER BY cart_count DESC;

--인덱스 없을 경우의 결과값
-> Sort: cart_count DESC  (actual time=0.241..0.243 rows=30 loops=1)
    -> Table scan on <temporary>  (actual time=0.13..0.134 rows=30 loops=1)
        -> Aggregate using temporary table  (actual time=0.129..0.129 rows=30 loops=1)
            -> Table scan on cart_items  (cost=0.35 rows=1) (actual time=0.031..0.049 rows=30 loops=1)

-- 11-3. 특정 상품을 담은 사용자 조회 (UNIQUE KEY uk_user_product 활용)
EXPLAIN ANALYZE SELECT ci.*, u.name, u.email
FROM cart_items ci
JOIN users u ON ci.user_id = u.id
WHERE ci.product_id = 1;

--인덱스 없을 경우의 결과값
-> Nested loop inner join  (cost=0.7 rows=1) (actual time=0.0517..0.0589 rows=1 loops=1)
    -> Filter: (ci.product_id = 1)  (cost=0.35 rows=1) (actual time=0.029..0.0359 rows=1 loops=1)
        -> Table scan on ci  (cost=0.35 rows=1) (actual time=0.0195..0.032 rows=30 loops=1)
    -> Single-row index lookup on u using PRIMARY (id=ci.user_id)  (cost=0.35 rows=1) (actual time=0.0207..0.0207 rows=1 loops=1)

-- ==================================================
-- 12. refunds 테이블 테스트
-- ==================================================

-- 12-1. 주문별 환불 조회 (idx_order_id 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM refunds WHERE order_id = 2;

--인덱스 없을 경우의 결과값
-> Filter: (refunds.order_id = 2)  (cost=0.35 rows=1) (actual time=0.0238..0.0417 rows=1 loops=1)
    -> Table scan on refunds  (cost=0.35 rows=1) (actual time=0.0225..0.0395 rows=10 loops=1)

-- 12-2. 환불 상태별 조회 (idx_status 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM refunds WHERE status = 'COMPLETED';

--인덱스 없을 경우의 결과값
-> Filter: (refunds.`status` = 'COMPLETED')  (cost=0.35 rows=1) (actual time=0.0266..0.0358 rows=6 loops=1)
    -> Table scan on refunds  (cost=0.35 rows=1) (actual time=0.023..0.031 rows=10 loops=1)

-- 12-3. 환불 통계
EXPLAIN ANALYZE SELECT status, COUNT(*) as count, SUM(refund_amount) as total_refund
FROM refunds
GROUP BY status;

--인덱스 없을 경우의 결과값
-> Table scan on <temporary>  (actual time=0.0506..0.051 rows=3 loops=1)
    -> Aggregate using temporary table  (actual time=0.0498..0.0498 rows=3 loops=1)
        -> Table scan on refunds  (cost=0.35 rows=1) (actual time=0.0205..0.0278 rows=10 loops=1)


-- 12-4. 환불 사유별 통계
EXPLAIN ANALYZE SELECT reason, COUNT(*) as count, SUM(refund_amount) as total_amount
FROM refunds
GROUP BY reason
ORDER BY count DESC;

--인덱스 없을 경우의 결과값
-> Sort: count DESC  (actual time=0.0613..0.0617 rows=8 loops=1)
    -> Table scan on <temporary>  (actual time=0.051..0.0518 rows=8 loops=1)
        -> Aggregate using temporary table  (actual time=0.0505..0.0505 rows=8 loops=1)
            -> Table scan on refunds  (cost=0.35 rows=1) (actual time=0.0209..0.0278 rows=10 loops=1)


-- ==================================================
-- 13. outbox_events 테이블 테스트
-- ==================================================

-- 13-1. 상태별 이벤트 조회 (idx_status_retry 복합 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM outbox_events
WHERE status = 'PENDING' AND retry_count < 3
ORDER BY created_at;

--인덱스 없을 경우의 결과값
-> Sort: outbox_events.created_at  (cost=0.35 rows=1) (actual time=0.0852..0.086 rows=7 loops=1)
    -> Filter: ((outbox_events.`status` = 'PENDING') and (outbox_events.retry_count < 3))  (cost=0.35 rows=1) (actual time=0.0485..0.0706 rows=7 loops=1)
        -> Table scan on outbox_events  (cost=0.35 rows=1) (actual time=0.0386..0.064 rows=50 loops=1)

-- 13-2. 실패한 이벤트 조회
EXPLAIN ANALYZE SELECT * FROM outbox_events
WHERE status = 'FAILED'
ORDER BY retry_count DESC;

--인덱스 없을 경우의 결과값
-> Sort: outbox_events.retry_count DESC  (cost=0.35 rows=1) (actual time=0.0738..0.0741 rows=2 loops=1)
    -> Filter: (outbox_events.`status` = 'FAILED')  (cost=0.35 rows=1) (actual time=0.0458..0.0602 rows=2 loops=1)
        -> Table scan on outbox_events  (cost=0.35 rows=1) (actual time=0.0269..0.0534 rows=50 loops=1)

-- 13-3. 날짜별 이벤트 조회 (idx_created_at 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM outbox_events
WHERE created_at >= '2025-01-15 00:00:00'
ORDER BY created_at DESC;

--인덱스 없을 경우의 결과값
-> Sort: outbox_events.created_at DESC  (cost=0.35 rows=1) (actual time=0.0713..0.0768 rows=50 loops=1)
    -> Filter: (outbox_events.created_at >= TIMESTAMP'2025-01-15 00:00:00')  (cost=0.35 rows=1) (actual time=0.0226..0.0507 rows=50 loops=1)
        -> Table scan on outbox_events  (cost=0.35 rows=1) (actual time=0.0205..0.0461 rows=50 loops=1)



-- 13-4. 이벤트 타입별 통계
EXPLAIN ANALYZE SELECT event_type, status, COUNT(*) as count
FROM outbox_events
GROUP BY event_type, status
ORDER BY event_type, status;

--인덱스 없을 경우의 결과값
-> Sort: outbox_events.event_type, outbox_events.`status`  (actual time=0.207..0.208 rows=8 loops=1)
    -> Table scan on <temporary>  (actual time=0.183..0.185 rows=8 loops=1)
        -> Aggregate using temporary table  (actual time=0.182..0.182 rows=8 loops=1)
            -> Table scan on outbox_events  (cost=0.35 rows=1) (actual time=0.0256..0.0567 rows=50 loops=1)


-- 13-5. 처리 대기 중인 이벤트
EXPLAIN ANALYZE SELECT * FROM outbox_events
WHERE status IN ('PENDING', 'PROCESSING')
ORDER BY created_at
LIMIT 100;

--인덱스 없을 경우의 결과값
-> Limit: 100 row(s)  (cost=0.35 rows=1) (actual time=0.068..0.0693 rows=8 loops=1)
    -> Sort: outbox_events.created_at, limit input to 100 row(s) per chunk  (cost=0.35 rows=1) (actual time=0.0673..0.0683 rows=8 loops=1)
        -> Filter: (outbox_events.`status` in ('PENDING','PROCESSING'))  (cost=0.35 rows=1) (actual time=0.0319..0.0547 rows=8 loops=1)
            -> Table scan on outbox_events  (cost=0.35 rows=1) (actual time=0.023..0.0484 rows=50 loops=1)

-- ==================================================
-- 14. popular_products 테이블 테스트
-- ==================================================

-- 14-1. 순위별 조회 (idx_rank 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM popular_products
WHERE `rank` <= 10
ORDER BY `rank`;

--인덱스 없을 경우의 결과값
-> Sort: popular_products.`rank`  (cost=0.35 rows=1) (actual time=0.0518..0.0526 rows=10 loops=1)
    -> Filter: (popular_products.`rank` <= 10)  (cost=0.35 rows=1) (actual time=0.0189..0.0417 rows=10 loops=1)
        -> Table scan on popular_products  (cost=0.35 rows=1) (actual time=0.0177..0.039 rows=20 loops=1)

-- 14-2. 최근 업데이트 조회 (idx_updated_at 인덱스 필요)
EXPLAIN ANALYZE SELECT * FROM popular_products
WHERE updated_at >= DATE_SUB(NOW(), INTERVAL 1 DAY)
ORDER BY `rank`;

--인덱스 없을 경우의 결과값
-> Sort: popular_products.`rank`  (cost=0.35 rows=1) (actual time=0.11..0.112 rows=20 loops=1)
    -> Filter: (popular_products.updated_at >= <cache>((now() - interval 1 day)))  (cost=0.35 rows=1) (actual time=0.072..0.0923 rows=20 loops=1)
        -> Table scan on popular_products  (cost=0.35 rows=1) (actual time=0.0275..0.0459 rows=20 loops=1)


-- 14-3. 카테고리별 인기 상품
EXPLAIN ANALYZE SELECT category_name, COUNT(*) as count, AVG(total_sales_quantity) as avg_sales
FROM popular_products
GROUP BY category_name;

--인덱스 없을 경우의 결과값
-> Table scan on <temporary>  (actual time=0.0595..0.0599 rows=4 loops=1)
    -> Aggregate using temporary table  (actual time=0.0589..0.0589 rows=4 loops=1)
        -> Table scan on popular_products  (cost=0.35 rows=1) (actual time=0.0215..0.0318 rows=20 loops=1)

-- ==================================================
-- 복합 쿼리 (JOIN이 많은 실전 쿼리)
-- ==================================================

-- 15-1. 사용자 주문 상세 정보 조회 (여러 인덱스 활용)
EXPLAIN ANALYZE SELECT
    o.order_number,
    o.created_at,
    o.status,
    o.total_amount,
    o.discount_amount,
    o.final_amount,
    u.name as user_name,
    u.email as user_email,
    p.status as payment_status,
    p.payment_id,
    uc.status as coupon_status,
    c.name as coupon_name
FROM orders o
JOIN users u ON o.user_id = u.id
LEFT JOIN payments p ON o.id = p.order_id
LEFT JOIN user_coupons uc ON o.user_coupon_id = uc.id
LEFT JOIN coupons c ON uc.coupon_id = c.id
WHERE o.user_id = 1
ORDER BY o.created_at DESC;

--인덱스 없을 경우의 결과값
-> Sort: o.created_at DESC  (actual time=0.189..0.19 rows=4 loops=1)
    -> Stream results  (cost=78.8 rows=250) (actual time=0.145..0.175 rows=4 loops=1)
        -> Nested loop left join  (cost=78.8 rows=250) (actual time=0.138..0.165 rows=4 loops=1)
            -> Nested loop left join  (cost=52.6 rows=250) (actual time=0.119..0.145 rows=4 loops=1)
                -> Left hash join (p.order_id = o.id)  (cost=26.3 rows=250) (actual time=0.11..0.133 rows=4 loops=1)
                    -> Filter: (o.user_id = 1)  (cost=5.25 rows=5) (actual time=0.0578..0.0795 rows=4 loops=1)
                        -> Table scan on o  (cost=5.25 rows=50) (actual time=0.0565..0.0761 rows=50 loops=1)
                    -> Hash
                        -> Table scan on p  (cost=1.05 rows=50) (actual time=0.017..0.029 rows=50 loops=1)
                -> Single-row index lookup on uc using PRIMARY (id=o.user_coupon_id)  (cost=0.0054 rows=1) (actual time=0.00258..0.00259 rows=0.5 loops=4)
            -> Single-row index lookup on c using PRIMARY (id=uc.coupon_id)  (cost=0.0054 rows=1) (actual time=0.00477..0.00479 rows=0.5 loops=4)


-- 15-2. 주문 전체 상세 (주문 + 주문상품 + 상품 + 결제)
EXPLAIN ANALYZE SELECT
    o.order_number,
    o.status as order_status,
    o.created_at,
    oi.quantity,
    oi.unit_price,
    oi.subtotal,
    p.name as product_name,
    c.name as category_name,
    py.status as payment_status,
    py.payment_id
FROM orders o
JOIN order_items oi ON o.id = oi.order_id
JOIN products p ON oi.product_id = p.id
JOIN categories c ON p.category_id = c.id
JOIN payments py ON o.id = py.order_id
WHERE o.id = 1;

--인덱스 없을 경우의 결과값
-> Inner hash join (no condition)  (cost=19.7 rows=3.3) (actual time=0.0892..0.0986 rows=2 loops=1)
    -> Filter: (py.order_id = 1)  (cost=0.822 rows=5) (actual time=0.00569..0.0146 rows=1 loops=1)
        -> Table scan on py  (cost=0.822 rows=50) (actual time=0.00551..0.013 rows=50 loops=1)
    -> Hash
        -> Nested loop inner join  (cost=11.5 rows=6.6) (actual time=0.048..0.0601 rows=2 loops=1)
            -> Nested loop inner join  (cost=9.16 rows=6.6) (actual time=0.0431..0.0548 rows=2 loops=1)
                -> Filter: (oi.order_id = 1)  (cost=6.85 rows=6.6) (actual time=0.016..0.026 rows=2 loops=1)
                    -> Table scan on oi  (cost=6.85 rows=66) (actual time=0.0152..0.0225 rows=66 loops=1)
                -> Single-row index lookup on p using PRIMARY (id=oi.product_id)  (cost=0.265 rows=1) (actual time=0.0134..0.0135 rows=1 loops=2)
            -> Single-row index lookup on c using PRIMARY (id=p.category_id)  (cost=0.265 rows=1) (actual time=0.00216..0.0022 rows=1 loops=2)

-- 15-3. 사용자별 구매 패턴 분석
EXPLAIN ANALYZE SELECT
    u.name,
    u.email,
    COUNT(DISTINCT o.id) as total_orders,
    SUM(o.final_amount) as total_spent,
    AVG(o.final_amount) as avg_order_amount,
    COUNT(DISTINCT oi.product_id) as unique_products
FROM users u
LEFT JOIN orders o ON u.id = o.user_id
LEFT JOIN order_items oi ON o.id = oi.order_id
GROUP BY u.id, u.name, u.email
HAVING total_orders > 0
ORDER BY total_spent DESC;

--인덱스 없을 경우의 결과값
-> Sort: total_spent DESC  (actual time=0.303..0.304 rows=20 loops=1)
    -> Filter: (total_orders > 0)  (actual time=0.241..0.295 rows=20 loops=1)
        -> Stream results  (actual time=0.239..0.292 rows=50 loops=1)
            -> Group aggregate: count(distinct orders.id), sum(orders.final_amount), avg(orders.final_amount), count(distinct order_items.product_id)  (actual time=0.235..0.27 rows=50 loops=1)
                -> Sort: u.id, u.`name`, u.email  (actual time=0.227..0.231 rows=96 loops=1)
                    -> Stream results  (cost=1321 rows=13200) (actual time=0.079..0.132 rows=96 loops=1)
                        -> Left hash join (oi.order_id = o.id)  (cost=1321 rows=13200) (actual time=0.0756..0.106 rows=96 loops=1)
                            -> Left hash join (o.user_id = u.id)  (cost=21.6 rows=200) (actual time=0.0264..0.0476 rows=80 loops=1)
                                -> Table scan on u  (cost=0.65 rows=4) (actual time=0.00348..0.0182 rows=50 loops=1)
                                -> Hash
                                    -> Table scan on o  (cost=1.31 rows=50) (actual time=0.00498..0.0154 rows=50 loops=1)
                            -> Hash
                                -> Table scan on oi  (cost=0.0356 rows=66) (actual time=0.0301..0.0358 rows=66 loops=1)


-- 15-4. 카테고리별 매출 통계
EXPLAIN ANALYZE SELECT
    c.name as category_name,
    COUNT(DISTINCT p.id) as product_count,
    COUNT(DISTINCT oi.order_id) as order_count,
    SUM(oi.quantity) as total_quantity_sold,
    SUM(oi.subtotal) as total_revenue
FROM categories c
JOIN products p ON c.id = p.category_id
LEFT JOIN order_items oi ON p.id = oi.product_id
GROUP BY c.id, c.name
ORDER BY total_revenue DESC;

--인덱스 없을 경우의 결과값
-> Sort: total_revenue DESC  (actual time=0.251..0.251 rows=4 loops=1)
    -> Stream results  (actual time=0.228..0.246 rows=4 loops=1)
        -> Group aggregate: count(distinct products.id), count(distinct order_items.order_id), sum(order_items.quantity), sum(order_items.subtotal)  (actual time=0.225..0.242 rows=4 loops=1)
            -> Sort: c.id, c.`name`  (actual time=0.204..0.208 rows=110 loops=1)
                -> Stream results  (cost=264 rows=2640) (actual time=0.0821..0.135 rows=110 loops=1)
                    -> Left hash join (oi.product_id = p.id)  (cost=264 rows=2640) (actual time=0.0788..0.111 rows=110 loops=1)
                        -> Inner hash join (p.category_id = c.id)  (cost=40.9 rows=40) (actual time=0.0218..0.0424 rows=100 loops=1)
                            -> Table scan on p  (cost=0.313 rows=100) (actual time=0.0135..0.0232 rows=100 loops=1)
                            -> Hash
                                -> Table scan on c  (cost=0.65 rows=4) (actual time=0.00399..0.00476 rows=4 loops=1)
                        -> Hash
                            -> Table scan on oi  (cost=0.172 rows=66) (actual time=0.0338..0.0404 rows=66 loops=1)


-- 15-5. 쿠폰 사용 효과 분석
EXPLAIN ANALYZE SELECT
    c.name as coupon_name,
    c.coupon_type,
    c.discount_rate,
    c.discount_amount,
    COUNT(DISTINCT uc.id) as issued_count,
    COUNT(DISTINCT CASE WHEN uc.status = 'USED' THEN uc.id END) as used_count,
    COUNT(DISTINCT o.id) as order_count,
    SUM(o.discount_amount) as total_discount_given,
    SUM(o.final_amount) as total_revenue_with_coupon
FROM coupons c
LEFT JOIN user_coupons uc ON c.id = uc.coupon_id
LEFT JOIN orders o ON uc.id = o.user_coupon_id
GROUP BY c.id, c.name, c.coupon_type, c.discount_rate, c.discount_amount
ORDER BY issued_count DESC;

--인덱스 없을 경우의 결과값
-> Sort: issued_count DESC  (actual time=0.238..0.238 rows=10 loops=1)
    -> Stream results  (actual time=0.216..0.232 rows=10 loops=1)
        -> Group aggregate: count(distinct user_coupons.id), count(distinct tmp_field), count(distinct orders.id), sum(orders.discount_amount), sum(orders.final_amount)  (actual time=0.172..0.183 rows=10 loops=1)
            -> Sort: c.id, c.`name`, c.coupon_type, c.discount_rate, c.discount_amount  (actual time=0.161..0.163 rows=22 loops=1)
                -> Stream results  (cost=1000 rows=10000) (actual time=0.109..0.127 rows=22 loops=1)
                    -> Left hash join (o.user_coupon_id = uc.id)  (cost=1000 rows=10000) (actual time=0.101..0.109 rows=22 loops=1)
                        -> Left hash join (uc.coupon_id = c.id)  (cost=20.5 rows=200) (actual time=0.0243..0.0301 rows=22 loops=1)
                            -> Table scan on c  (cost=1.25 rows=10) (actual time=0.00781..0.0108 rows=10 loops=1)
                            -> Hash
                                -> Table scan on uc  (cost=0.226 rows=20) (actual time=0.00719..0.00888 rows=20 loops=1)
                        -> Hash
                            -> Table scan on o  (cost=0.0269 rows=50) (actual time=0.0477..0.0625 rows=50 loops=1)


-- ==================================================
-- 성능 테스트 핵심 쿼리
-- ==================================================

-- 16-1. 인덱스 효과가 큰 쿼리: user_id + status 복합 조건
EXPLAIN ANALYZE SELECT * FROM orders
WHERE user_id = 1 AND status = 'DELIVERED'
ORDER BY created_at DESC;

--인덱스 없을 경우의 결과값
-> Sort: orders.created_at DESC  (cost=5.25 rows=50) (actual time=0.0648..0.0652 rows=3 loops=1)
    -> Filter: ((orders.user_id = 1) and (orders.`status` = 'DELIVERED'))  (cost=5.25 rows=50) (actual time=0.0281..0.0548 rows=3 loops=1)
        -> Table scan on orders  (cost=5.25 rows=50) (actual time=0.025..0.0491 rows=50 loops=1)


-- 16-2. 인덱스 효과가 큰 쿼리: 범위 검색 + 정렬
EXPLAIN ANALYZE SELECT * FROM products
WHERE category_id = 1 AND price BETWEEN 50000 AND 500000
ORDER BY price DESC;

--인덱스 없을 경우의 결과값
-> Sort: products.price DESC  (cost=10.2 rows=100) (actual time=0.101..0.104 rows=22 loops=1)
    -> Filter: ((products.category_id = 1) and (products.price between 50000 and 500000))  (cost=10.2 rows=100) (actual time=0.033..0.0849 rows=22 loops=1)
        -> Table scan on products  (cost=10.2 rows=100) (actual time=0.0264..0.0754 rows=100 loops=1)


-- 16-3. 인덱스 효과가 큰 쿼리: JOIN + WHERE + GROUP BY
EXPLAIN ANALYZE SELECT
    p.name,
    COUNT(oi.id) as order_count,
    SUM(oi.quantity) as total_sold
FROM products p
JOIN order_items oi ON p.id = oi.product_id
WHERE p.category_id = 1
GROUP BY p.id, p.name
ORDER BY total_sold DESC;

--인덱스 없을 경우의 결과값
-> Sort: total_sold DESC  (actual time=0.175..0.176 rows=28 loops=1)
    -> Table scan on <temporary>  (actual time=0.158..0.161 rows=28 loops=1)
        -> Aggregate using temporary table  (actual time=0.158..0.158 rows=28 loops=1)
            -> Nested loop inner join  (cost=30 rows=6.6) (actual time=0.0442..0.113 rows=34 loops=1)
                -> Table scan on oi  (cost=6.85 rows=66) (actual time=0.0312..0.0399 rows=66 loops=1)
                -> Filter: (p.category_id = 1)  (cost=0.25 rows=0.1) (actual time=964e-6..989e-6 rows=0.515 loops=66)
                    -> Single-row index lookup on p using PRIMARY (id=oi.product_id)  (cost=0.25 rows=1) (actual time=843e-6..858e-6 rows=1 loops=66)


-- 16-4. 인덱스 효과가 큰 쿼리: 서브쿼리
EXPLAIN ANALYZE SELECT * FROM orders o
WHERE o.id IN (
    EXPLAIN ANALYZE SELECT DISTINCT order_id
    FROM order_items
    WHERE product_id = 1
);

--인덱스 없을 경우의 결과값
-> Nested loop inner join  (cost=11 rows=6.6) (actual time=0.0545..0.0588 rows=3 loops=1)
    -> Table scan on <subquery2>  (cost=7.9..10.1 rows=6.6) (actual time=0.0424..0.0427 rows=3 loops=1)
        -> Materialize with deduplication  (cost=7.51..7.51 rows=6.6) (actual time=0.0414..0.0414 rows=3 loops=1)
            -> Filter: (order_items.product_id = 1)  (cost=6.85 rows=6.6) (actual time=0.0284..0.0371 rows=3 loops=1)
                -> Table scan on order_items  (cost=6.85 rows=66) (actual time=0.0266..0.0328 rows=66 loops=1)
    -> Single-row index lookup on o using PRIMARY (id=`<subquery2>`.order_id)  (cost=0.35 rows=1) (actual time=0.00455..0.00458 rows=1 loops=3)


-- 16-5. 인덱스 효과가 큰 쿼리: 상관 서브쿼리
EXPLAIN ANALYZE SELECT u.name, u.email,
    (SELECT COUNT(*) FROM orders WHERE user_id = u.id) as order_count,
    (SELECT SUM(final_amount) FROM orders WHERE user_id = u.id) as total_spent
FROM users u
WHERE u.balance > 100000;

--인덱스 없을 경우의 결과값
-> Filter: (u.balance > 100000)  (cost=0.65 rows=1.33) (actual time=0.0275..0.0458 rows=32 loops=1)
    -> Table scan on u  (cost=0.65 rows=4) (actual time=0.0239..0.04 rows=50 loops=1)
-> Select #2 (subquery in projection; dependent)
    -> Aggregate: count(0)  (cost=1.25 rows=1) (actual time=0.0106..0.0107 rows=1 loops=32)
        -> Filter: (orders.user_id = u.id)  (cost=0.75 rows=5) (actual time=0.00818..0.0103 rows=0.875 loops=32)
            -> Table scan on orders  (cost=0.75 rows=50) (actual time=0.00322..0.00865 rows=50 loops=32)
-> Select #3 (subquery in projection; dependent)
    -> Aggregate: sum(orders.final_amount)  (cost=1.25 rows=1) (actual time=0.0127..0.0127 rows=1 loops=32)
        -> Filter: (orders.user_id = u.id)  (cost=0.75 rows=5) (actual time=0.00944..0.0124 rows=0.875 loops=32)
            -> Table scan on orders  (cost=0.75 rows=50) (actual time=0.00201..0.0107 rows=50 loops=32)


-- ==================================================
-- 테스트 방법
-- ==================================================
--
-- 1. 각 쿼리 앞에 EXPLAIN ANALYZE 키워드를 붙여 실행:
--    EXPLAIN ANALYZE SELECT * FROM users WHERE email = 'hong@example.com';
--
-- 2. 실행 계획에서 확인할 주요 항목:
--    - type: 조인 타입 (ALL이면 풀스캔, ref/eq_ref이면 인덱스 사용)
--    - possible_keys: 사용 가능한 인덱스 목록
--    - key: 실제 사용된 인덱스
--    - rows: 검사할 예상 행 수 (적을수록 좋음)
--    - Extra: 추가 정보 (Using where, Using index, Using filesort 등)
--
-- 3. 인덱스 전/후 비교:
--    - 인덱스 없을 때: type=ALL, rows=많음, key=NULL
--    - 인덱스 있을 때: type=ref, rows=적음, key=인덱스명
--
-- ==================================================
