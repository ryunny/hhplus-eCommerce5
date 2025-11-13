-- ==================================================
-- E-Commerce Database Schema
-- ==================================================
-- MySQL 8.0
-- Character Set: utf8mb4
-- Collation: utf8mb4_unicode_ci
-- ==================================================

-- Drop tables if exists (역순으로 삭제)
DROP TABLE IF EXISTS popular_products;
DROP TABLE IF EXISTS outbox_events;
DROP TABLE IF EXISTS refunds;
DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS shipping_addresses;
DROP TABLE IF EXISTS cart_items;
DROP TABLE IF EXISTS coupon_queues;
DROP TABLE IF EXISTS user_coupons;
DROP TABLE IF EXISTS coupons;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS users;

-- ==================================================
-- 1. users (사용자)
-- ==================================================
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE COMMENT '공개 ID (UUID)',
    name VARCHAR(100) NOT NULL COMMENT '사용자 이름',
    email VARCHAR(255) NOT NULL UNIQUE COMMENT '이메일 주소',
    phone VARCHAR(20) NOT NULL COMMENT '전화번호',
    balance BIGINT NOT NULL DEFAULT 0 COMMENT '잔액 (원)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    INDEX idx_public_id (public_id),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 테이블';

-- ==================================================
-- 2. categories (카테고리)
-- ==================================================
CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '카테고리명',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='상품 카테고리';

-- ==================================================
-- 3. products (상품)
-- ==================================================
CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT NOT NULL COMMENT '카테고리 ID',
    name VARCHAR(255) NOT NULL COMMENT '상품명',
    description TEXT COMMENT '상품 설명',
    price BIGINT NOT NULL COMMENT '가격 (원)',
    stock INT NOT NULL DEFAULT 0 COMMENT '재고 수량',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    INDEX idx_category_id (category_id),
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='상품';

-- ==================================================
-- 4. shipping_addresses (배송지 정보)
-- ==================================================
CREATE TABLE shipping_addresses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    address_name VARCHAR(50) NOT NULL COMMENT '배송지 별칭',
    recipient_name VARCHAR(100) NOT NULL COMMENT '수령인 이름',
    address VARCHAR(500) NOT NULL COMMENT '배송 주소',
    phone VARCHAR(20) NOT NULL COMMENT '연락처',
    is_default BOOLEAN NOT NULL DEFAULT FALSE COMMENT '기본 배송지 여부',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    INDEX idx_user_id (user_id),
    INDEX idx_user_default (user_id, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='배송지 정보';

-- ==================================================
-- 5. coupons (쿠폰)
-- ==================================================
CREATE TABLE coupons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '쿠폰명',
    coupon_type VARCHAR(50) NOT NULL COMMENT '쿠폰 타입',
    discount_rate INT COMMENT '할인율',
    discount_amount BIGINT COMMENT '할인 금액',
    min_order_amount BIGINT COMMENT '최소 주문 금액',
    total_quantity INT NOT NULL COMMENT '총 발급 수량',
    issued_quantity INT NOT NULL DEFAULT 0 COMMENT '발급된 수량',
    start_date DATETIME NOT NULL COMMENT '쿠폰 시작일',
    end_date DATETIME NOT NULL COMMENT '쿠폰 종료일',
    use_queue BOOLEAN NOT NULL DEFAULT FALSE COMMENT '대기열 사용 여부',
    INDEX idx_dates (start_date, end_date),
    INDEX idx_type (coupon_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='쿠폰 마스터';

-- ==================================================
-- 6. user_coupons (사용자 보유 쿠폰)
-- ==================================================
CREATE TABLE user_coupons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    coupon_id BIGINT NOT NULL COMMENT '쿠폰 ID',
    status VARCHAR(20) NOT NULL COMMENT '상태',
    issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '발급 일시',
    expires_at DATETIME NOT NULL COMMENT '만료 일시',
    INDEX idx_user_status (user_id, status),
    INDEX idx_coupon_id (coupon_id),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 보유 쿠폰';

-- ==================================================
-- 7. coupon_queues (쿠폰 발급 대기열)
-- ==================================================
CREATE TABLE coupon_queues (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    coupon_id BIGINT NOT NULL COMMENT '쿠폰 ID',
    status VARCHAR(20) NOT NULL COMMENT '상태',
    queue_position INT NOT NULL COMMENT '대기 순번',
    processed_at DATETIME COMMENT '처리 완료 일시',
    failed_reason VARCHAR(500) COMMENT '실패 사유',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    INDEX idx_coupon_status (coupon_id, status),
    INDEX idx_user_coupon (user_id, coupon_id),
    INDEX idx_queue_position (queue_position)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='쿠폰 발급 대기열';

-- ==================================================
-- 8. orders (주문)
-- ==================================================
CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(36) NOT NULL UNIQUE COMMENT '주문 번호',
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    user_coupon_id BIGINT COMMENT '사용한 쿠폰 ID',
    shipping_address_id BIGINT COMMENT '배송지 ID',
    recipient_name VARCHAR(100) NOT NULL COMMENT '수령인 이름',
    address VARCHAR(500) NOT NULL COMMENT '배송 주소',
    shipping_phone VARCHAR(20) NOT NULL COMMENT '배송지 연락처',
    total_amount BIGINT NOT NULL COMMENT '총 주문 금액',
    discount_amount BIGINT NOT NULL DEFAULT 0 COMMENT '할인 금액',
    final_amount BIGINT NOT NULL COMMENT '최종 결제 금액',
    status VARCHAR(20) NOT NULL COMMENT '주문 상태',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '주문 일시',
    INDEX idx_order_number (order_number),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='주문';

-- ==================================================
-- 9. order_items (주문 상품)
-- ==================================================
CREATE TABLE order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL COMMENT '주문 ID',
    product_id BIGINT NOT NULL COMMENT '상품 ID',
    quantity INT NOT NULL COMMENT '수량',
    unit_price BIGINT NOT NULL COMMENT '단가',
    subtotal BIGINT NOT NULL COMMENT '소계',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    INDEX idx_order_id (order_id),
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='주문 상품 상세';

-- ==================================================
-- 10. payments (결제)
-- ==================================================
CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id VARCHAR(36) NOT NULL UNIQUE COMMENT '결제 ID',
    order_id BIGINT NOT NULL COMMENT '주문 ID',
    paid_amount BIGINT NOT NULL COMMENT '결제 금액',
    status VARCHAR(20) NOT NULL COMMENT '결제 상태',
    data_transmission_status VARCHAR(20) NOT NULL COMMENT '데이터 전송 상태',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '결제 일시',
    INDEX idx_payment_id (payment_id),
    INDEX idx_order_id (order_id),
    INDEX idx_status (status),
    INDEX idx_data_transmission_status (data_transmission_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='결제 정보';

-- ==================================================
-- 11. cart_items (장바구니)
-- ==================================================
CREATE TABLE cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    product_id BIGINT NOT NULL COMMENT '상품 ID',
    quantity INT NOT NULL COMMENT '수량',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    INDEX idx_user_id (user_id),
    INDEX idx_product_id (product_id),
    UNIQUE KEY uk_user_product (user_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='장바구니';

-- ==================================================
-- 12. refunds (환불)
-- ==================================================
CREATE TABLE refunds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL COMMENT '주문 ID',
    refund_amount BIGINT NOT NULL COMMENT '환불 금액',
    reason VARCHAR(500) COMMENT '환불 사유',
    status VARCHAR(20) NOT NULL COMMENT '환불 상태',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    INDEX idx_order_id (order_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='환불';

-- ==================================================
-- 13. outbox_events (아웃박스 이벤트)
-- ==================================================
CREATE TABLE outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL COMMENT '이벤트 타입',
    aggregate_id BIGINT NOT NULL COMMENT '집합 루트 ID',
    payload TEXT NOT NULL COMMENT '이벤트 페이로드',
    status VARCHAR(20) NOT NULL COMMENT '처리 상태',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '재시도 횟수',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    processed_at DATETIME COMMENT '처리 완료 일시',
    failed_reason VARCHAR(500) COMMENT '실패 사유',
    INDEX idx_status_retry (status, retry_count),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='아웃박스 패턴 이벤트';

-- ==================================================
-- 14. popular_products (인기 상품 스냅샷)
-- ==================================================
CREATE TABLE popular_products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    `rank` INT NOT NULL UNIQUE COMMENT '순위',
    product_id BIGINT NOT NULL COMMENT '상품 ID',
    product_name VARCHAR(200) NOT NULL COMMENT '상품명',
    price BIGINT NOT NULL COMMENT '가격',
    total_sales_quantity INT NOT NULL COMMENT '총 판매 수량',
    category_name VARCHAR(100) NOT NULL COMMENT '카테고리명',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '업데이트 일시',
    INDEX idx_rank (`rank`),
    INDEX idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='인기 상품 캐시 테이블';

-- ==================================================
-- Initial Data
-- ==================================================

-- 샘플 카테고리
INSERT INTO categories (name) VALUES
('전자제품'),
('의류'),
('식품'),
('도서');

-- 샘플 사용자
INSERT INTO users (public_id, name, email, phone, balance) VALUES
(UUID(), '홍길동', 'hong@example.com', '010-1234-5678', 100000),
(UUID(), '김철수', 'kim@example.com', '010-2345-6789', 50000),
(UUID(), '이영희', 'lee@example.com', '010-3456-7890', 200000);

-- 샘플 상품
INSERT INTO products (category_id, name, description, price, stock) VALUES
(1, '노트북', '고성능 노트북', 1500000, 50),
(1, '무선 마우스', '블루투스 마우스', 35000, 200),
(1, '키보드', '기계식 키보드', 120000, 100),
(2, '티셔츠', '면 100% 티셔츠', 25000, 300),
(3, '사과', '신선한 사과 1kg', 8000, 500);

-- ==================================================
-- End of Schema
-- ==================================================
