-- ================================================
-- E-Commerce Production Schema
-- ================================================
-- 생성일: 2025-11-09
-- 설명: 현업 스타일 - FK/CHECK 제약조건 없음
-- ================================================
-- 특징:
-- 1. Foreign Key 없음 (성능, 샤딩)
-- 2. CHECK 제약조건 없음 (유연성, 성능)
-- 3. UNIQUE는 최소한만 (email, 장바구니)
-- 4. 모든 검증은 애플리케이션 레벨
-- 5. 인덱스는 충분히 설정
-- ================================================

-- 기존 테이블 삭제
DROP TABLE IF EXISTS coupon_queues;
DROP TABLE IF EXISTS cart_items;
DROP TABLE IF EXISTS refunds;
DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS user_coupons;
DROP TABLE IF EXISTS coupons;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS users;

-- ================================================
-- 1. 사용자 테이블
-- ================================================
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    balance BIGINT NOT NULL DEFAULT 0 COMMENT '잔액 (원 단위)',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,

    -- 인덱스
    UNIQUE KEY uk_email (email),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='사용자';

-- ================================================
-- 2. 카테고리 테이블
-- ================================================
CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='상품 카테고리';

-- ================================================
-- 3. 상품 테이블
-- ================================================
CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price BIGINT NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,

    -- 인덱스
    INDEX idx_category_id (category_id),
    INDEX idx_name (name),
    INDEX idx_stock (stock),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='상품';

-- ================================================
-- 4. 쿠폰 테이블
-- ================================================
CREATE TABLE coupons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    coupon_type VARCHAR(50) NOT NULL COMMENT 'RATE, AMOUNT',
    discount_rate DECIMAL(5,2) NULL,
    discount_amount BIGINT NULL,
    min_order_amount BIGINT NULL,
    total_quantity INT NOT NULL,
    issued_quantity INT NOT NULL DEFAULT 0,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    use_queue BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 인덱스
    INDEX idx_dates (start_date, end_date),
    INDEX idx_issued_quantity (issued_quantity),
    INDEX idx_active (start_date, end_date, issued_quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='쿠폰';

-- ================================================
-- 5. 사용자 쿠폰 테이블
-- ================================================
CREATE TABLE user_coupons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    coupon_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,

    -- 인덱스
    INDEX idx_user_id (user_id),
    INDEX idx_coupon_id (coupon_id),
    INDEX idx_status (status),
    INDEX idx_user_status (user_id, status),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='사용자 쿠폰';

-- ================================================
-- 6. 주문 테이블
-- ================================================
CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    user_coupon_id BIGINT NULL,
    recipient_name VARCHAR(100) NOT NULL,
    shipping_address VARCHAR(500) NOT NULL,
    shipping_phone VARCHAR(20) NOT NULL,
    total_amount BIGINT NOT NULL,
    discount_amount BIGINT NOT NULL DEFAULT 0,
    final_amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,

    -- 인덱스
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_user_status (user_id, status),
    INDEX idx_user_created (user_id, created_at DESC),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='주문';

-- ================================================
-- 7. 주문 아이템 테이블
-- ================================================
CREATE TABLE order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price BIGINT NOT NULL,
    subtotal BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 인덱스
    INDEX idx_order_id (order_id),
    INDEX idx_product_id (product_id),
    INDEX idx_created_at (created_at),
    INDEX idx_created_product (created_at DESC, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='주문 아이템';

-- ================================================
-- 8. 결제 테이블
-- ================================================
CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    paid_amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    data_transmission_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,

    -- 인덱스
    INDEX idx_order_id (order_id),
    INDEX idx_status (status),
    INDEX idx_data_status (data_transmission_status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='결제';

-- ================================================
-- 9. 환불 테이블
-- ================================================
CREATE TABLE refunds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    refund_amount BIGINT NOT NULL,
    reason TEXT,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,

    -- 인덱스
    INDEX idx_order_id (order_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='환불';

-- ================================================
-- 10. 장바구니 테이블
-- ================================================
CREATE TABLE cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,

    -- 인덱스
    INDEX idx_user_id (user_id),
    INDEX idx_product_id (product_id),
    UNIQUE KEY uk_user_product (user_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='장바구니';

-- ================================================
-- 11. 쿠폰 대기열 테이블
-- ================================================
CREATE TABLE coupon_queues (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    coupon_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    queue_position INT NOT NULL,
    processed_at TIMESTAMP NULL,
    failed_reason TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 인덱스
    INDEX idx_user_id (user_id),
    INDEX idx_coupon_id (coupon_id),
    INDEX idx_status (status),
    INDEX idx_coupon_status (coupon_id, status),
    INDEX idx_queue_position (queue_position),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='쿠폰 대기열';

-- ================================================
-- 초기 데이터
-- ================================================

-- 카테고리
INSERT INTO categories (name) VALUES
('전자기기'),
('의류'),
('도서'),
('생활용품'),
('식품');

-- 상품
INSERT INTO products (category_id, name, description, price, stock) VALUES
(1, '노트북', '고성능 노트북', 890000, 10),
(1, '마우스', '무선 마우스', 35000, 50),
(1, '키보드', '기계식 키보드', 120000, 30),
(2, '티셔츠', '면 100% 티셔츠', 25000, 100),
(3, '자바 프로그래밍 입문', 'Java 프로그래밍 도서', 35000, 20);

-- 사용자
INSERT INTO users (name, email, phone, balance) VALUES
('홍길동', 'hong@example.com', '010-1234-5678', 1000000),
('김철수', 'kim@example.com', '010-2345-6789', 500000),
('이영희', 'lee@example.com', '010-3456-7890', 750000);

-- 쿠폰
INSERT INTO coupons (name, coupon_type, discount_rate, discount_amount, min_order_amount, total_quantity, start_date, end_date, use_queue) VALUES
('신규가입 10% 할인', 'RATE', 10.00, NULL, 50000, 1000, '2025-01-01 00:00:00', '2025-12-31 23:59:59', FALSE),
('5만원 할인 쿠폰', 'AMOUNT', NULL, 50000, 100000, 100, '2025-01-01 00:00:00', '2025-12-31 23:59:59', FALSE),
('선착순 100명 특가', 'AMOUNT', NULL, 100000, 500000, 100, '2025-01-01 00:00:00', '2025-12-31 23:59:59', TRUE);

-- ================================================
-- 완료
-- ================================================
-- 모든 비즈니스 규칙은 애플리케이션 레벨에서 관리
-- - balance >= 0 검증
-- - stock >= 0 검증
-- - quantity > 0 검증
-- - issued_quantity <= total_quantity 검증
-- - 외래키 존재 여부 검증
-- ================================================
