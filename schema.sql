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
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시'
    -- INDEX idx_public_id (public_id),
    -- INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 테이블';

-- ==================================================
-- 2. categories (카테고리)
-- ==================================================
CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '카테고리명',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시'
    -- INDEX idx_name (name)
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
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시'
    -- INDEX idx_category_id (category_id),
    -- INDEX idx_name (name)
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
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시'
    -- INDEX idx_user_id (user_id),
    -- INDEX idx_user_default (user_id, is_default)
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
    use_queue BOOLEAN NOT NULL DEFAULT FALSE COMMENT '대기열 사용 여부'
    -- INDEX idx_dates (start_date, end_date),
    -- INDEX idx_type (coupon_type)
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
    expires_at DATETIME NOT NULL COMMENT '만료 일시'
    -- INDEX idx_user_status (user_id, status),
    -- INDEX idx_coupon_id (coupon_id),
    -- INDEX idx_expires_at (expires_at)
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
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시'
    -- INDEX idx_coupon_status (coupon_id, status),
    -- INDEX idx_user_coupon (user_id, coupon_id),
    -- INDEX idx_queue_position (queue_position)
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
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '주문 일시'
    -- INDEX idx_order_number (order_number),
    -- INDEX idx_user_id (user_id),
    -- INDEX idx_status (status),
    -- INDEX idx_created_at (created_at)
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
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시'
    -- INDEX idx_order_id (order_id),
    -- INDEX idx_product_id (product_id)
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
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '결제 일시'
    -- INDEX idx_payment_id (payment_id),
    -- INDEX idx_order_id (order_id),
    -- INDEX idx_status (status),
    -- INDEX idx_data_transmission_status (data_transmission_status)
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
    -- INDEX idx_user_id (user_id),
    -- INDEX idx_product_id (product_id),
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
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시'
    -- INDEX idx_order_id (order_id),
    -- INDEX idx_status (status)
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
    failed_reason VARCHAR(500) COMMENT '실패 사유'
    -- INDEX idx_status_retry (status, retry_count),
    -- INDEX idx_created_at (created_at)
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
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '업데이트 일시'
    -- INDEX idx_rank (`rank`),
    -- INDEX idx_updated_at (updated_at)
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

-- 샘플 사용자 (50명)
INSERT INTO users (public_id, name, email, phone, balance) VALUES
(UUID(), '홍길동', 'hong@example.com', '010-1234-5678', 100000),
(UUID(), '김철수', 'kim@example.com', '010-2345-6789', 50000),
(UUID(), '이영희', 'lee@example.com', '010-3456-7890', 200000),
(UUID(), '박민수', 'park01@example.com', '010-1111-1111', 150000),
(UUID(), '최지은', 'choi01@example.com', '010-2222-2222', 80000),
(UUID(), '정수현', 'jung01@example.com', '010-3333-3333', 120000),
(UUID(), '강민지', 'kang01@example.com', '010-4444-4444', 90000),
(UUID(), '윤서준', 'yoon01@example.com', '010-5555-5555', 110000),
(UUID(), '임하윤', 'lim01@example.com', '010-6666-6666', 130000),
(UUID(), '한지우', 'han01@example.com', '010-7777-7777', 95000),
(UUID(), '오승현', 'oh01@example.com', '010-8888-8888', 105000),
(UUID(), '서예린', 'seo01@example.com', '010-9999-9999', 115000),
(UUID(), '권도윤', 'kwon01@example.com', '010-1010-1010', 125000),
(UUID(), '황서아', 'hwang01@example.com', '010-2020-2020', 85000),
(UUID(), '안준호', 'ahn01@example.com', '010-3030-3030', 140000),
(UUID(), '송하은', 'song01@example.com', '010-4040-4040', 75000),
(UUID(), '장민준', 'jang01@example.com', '010-5050-5050', 160000),
(UUID(), '배수아', 'bae01@example.com', '010-6060-6060', 135000),
(UUID(), '신지훈', 'shin01@example.com', '010-7070-7070', 145000),
(UUID(), '조은서', 'cho01@example.com', '010-8080-8080', 100000),
(UUID(), '문준우', 'moon01@example.com', '010-9090-9090', 110000),
(UUID(), '곽서연', 'kwak01@example.com', '010-1212-1212', 120000),
(UUID(), '변시우', 'byun01@example.com', '010-1313-1313', 130000),
(UUID(), '류예준', 'ryu01@example.com', '010-1414-1414', 90000),
(UUID(), '남채원', 'nam01@example.com', '010-1515-1515', 95000),
(UUID(), '진도현', 'jin01@example.com', '010-1616-1616', 105000),
(UUID(), '하지안', 'ha01@example.com', '010-1717-1717', 115000),
(UUID(), '마서준', 'ma01@example.com', '010-1818-1818', 125000),
(UUID(), '석윤서', 'seok01@example.com', '010-1919-1919', 85000),
(UUID(), '성민재', 'sung01@example.com', '010-2121-2121', 140000),
(UUID(), '복지유', 'bok01@example.com', '010-2323-2323', 75000),
(UUID(), '차예은', 'cha01@example.com', '010-2424-2424', 160000),
(UUID(), '염준서', 'yeom01@example.com', '010-2525-2525', 135000),
(UUID(), '주서우', 'joo01@example.com', '010-2626-2626', 145000),
(UUID(), '노시현', 'noh01@example.com', '010-2727-2727', 100000),
(UUID(), '우하준', 'woo01@example.com', '010-2828-2828', 110000),
(UUID(), '라수빈', 'ra01@example.com', '010-2929-2929', 120000),
(UUID(), '나지훈', 'na01@example.com', '010-3131-3131', 130000),
(UUID(), '전예린', 'jeon01@example.com', '010-3232-3232', 90000),
(UUID(), '탁유진', 'tak01@example.com', '010-3333-3333', 95000),
(UUID(), '옹준혁', 'ong01@example.com', '010-3434-3434', 105000),
(UUID(), '모지원', 'mo01@example.com', '010-3535-3535', 115000),
(UUID(), '제서아', 'je01@example.com', '010-3636-3636', 125000),
(UUID(), '맹도하', 'maeng01@example.com', '010-3737-3737', 85000),
(UUID(), '반지아', 'ban01@example.com', '010-3838-3838', 140000),
(UUID(), '방시윤', 'bang01@example.com', '010-3939-3939', 75000),
(UUID(), '표유나', 'pyo01@example.com', '010-4141-4141', 160000),
(UUID(), '피서진', 'pi01@example.com', '010-4242-4242', 135000),
(UUID(), '감도윤', 'gam01@example.com', '010-4343-4343', 145000),
(UUID(), '빈하린', 'bin01@example.com', '010-4444-4444', 100000);

-- 샘플 상품 (100개)
INSERT INTO products (category_id, name, description, price, stock) VALUES
-- 전자제품 (40개)
(1, '노트북', '고성능 노트북', 1500000, 50),
(1, '무선 마우스', '블루투스 마우스', 35000, 200),
(1, '키보드', '기계식 키보드', 120000, 100),
(1, '모니터 27인치', 'QHD 해상도', 350000, 80),
(1, '웹캠', 'Full HD 웹캠', 85000, 150),
(1, '헤드셋', '노이즈 캐슬링', 180000, 120),
(1, 'USB 허브', '7포트 허브', 45000, 200),
(1, '외장 SSD 1TB', '초고속 전송', 150000, 90),
(1, '무선 충전기', 'Qi 호환', 55000, 180),
(1, '스마트워치', '피트니스 트래커', 280000, 70),
(1, '블루투스 스피커', '방수 기능', 95000, 160),
(1, '태블릿', '10인치 디스플레이', 450000, 60),
(1, '전자책 리더기', 'E-ink 디스플레이', 180000, 85),
(1, '무선 이어폰', 'ANC 지원', 220000, 140),
(1, '게이밍 마우스', 'RGB 라이트', 85000, 110),
(1, '게이밍 키보드', '기계식 스위치', 150000, 95),
(1, 'USB 마이크', '팟캐스트용', 120000, 75),
(1, 'SD 카드 128GB', 'UHS-II 지원', 45000, 200),
(1, '노트북 거치대', '각도 조절', 35000, 180),
(1, '마우스 패드', '대형 사이즈', 25000, 220),
(1, 'HDMI 케이블 3m', '4K 지원', 18000, 250),
(1, '랜선 10m', 'CAT6', 15000, 200),
(1, '멀티탭', '6구 개별 스위치', 28000, 180),
(1, '보조배터리 20000mAh', '고속충전', 55000, 150),
(1, '스마트폰 거치대', '차량용', 22000, 190),
(1, '노트북 파우치', '15.6인치용', 32000, 170),
(1, '케이블 정리함', '데스크용', 18000, 200),
(1, 'LED 스탠드', '무선충전 기능', 65000, 130),
(1, '공유기', 'Wi-Fi 6', 180000, 85),
(1, '스마트 플러그', '음성인식', 35000, 160),
(1, 'TV 박스', '안드로이드 기반', 120000, 90),
(1, '프로젝터', '풀HD 해상도', 450000, 45),
(1, '드론', '4K 카메라', 580000, 35),
(1, '액션캠', '방수 케이스', 280000, 65),
(1, '삼각대', '스마트폰/카메라용', 45000, 140),
(1, '셀카봉', '블루투스 리모컨', 28000, 180),
(1, '메모리 카드 리더기', 'USB-C 타입', 22000, 200),
(1, '냉각 패드', '노트북용', 38000, 160),
(1, '그래픽 태블릿', '디지털 드로잉', 180000, 70),
(1, 'VR 헤드셋', '가상현실 체험', 420000, 50),
-- 의류 (30개)
(2, '티셔츠', '면 100% 티셔츠', 25000, 300),
(2, '청바지', '스트레치 데님', 65000, 150),
(2, '후드 티셔츠', '기모 안감', 45000, 200),
(2, '맨투맨', '오버핏', 38000, 220),
(2, '긴팔 셔츠', '체크 패턴', 48000, 180),
(2, '반팔 셔츠', '린넨 소재', 42000, 160),
(2, '슬랙스', '정장용', 75000, 140),
(2, '트레이닝 팬츠', '조거팬츠', 35000, 200),
(2, '반바지', '여름용', 28000, 250),
(2, '원피스', '플라워 패턴', 68000, 130),
(2, '블라우스', '실크 혼방', 58000, 150),
(2, '스커트', 'A라인', 45000, 170),
(2, '가디건', '울 혼방', 55000, 160),
(2, '자켓', '캐주얼', 95000, 120),
(2, '패딩', '경량 다운', 150000, 90),
(2, '코트', '울 소재', 180000, 80),
(2, '점퍼', 'MA-1', 85000, 140),
(2, '니트', '터틀넥', 62000, 150),
(2, '조끼', '패딩 조끼', 48000, 170),
(2, '레깅스', '요가용', 32000, 220),
(2, '운동복 상의', '속건 기능', 38000, 200),
(2, '운동복 하의', '스판 소재', 35000, 210),
(2, '수영복', '래쉬가드 세트', 58000, 130),
(2, '양말 5켤레', '면 혼방', 12000, 300),
(2, '속옷 3장 세트', '면 100%', 28000, 250),
(2, '모자', '볼캡', 22000, 200),
(2, '비니', '겨울용', 18000, 220),
(2, '목도리', '캐시미어 혼방', 35000, 150),
(2, '장갑', '터치스크린 지원', 22000, 180),
(2, '벨트', '가죽 소재', 42000, 160),
-- 식품 (20개)
(3, '사과', '신선한 사과 1kg', 8000, 500),
(3, '배', '나주배 2kg', 18000, 300),
(3, '귤', '제주 감귤 3kg', 15000, 400),
(3, '포도', '샤인머스캣 1kg', 25000, 200),
(3, '딸기', '설향 500g', 12000, 250),
(3, '바나나', '필리핀산 1kg', 4000, 600),
(3, '키위', '골드 키위 10개', 9000, 350),
(3, '체리', '미국산 500g', 22000, 180),
(3, '수박', '씨없는 수박', 18000, 200),
(3, '참외', '성주 참외 5개', 8000, 300),
(3, '쌀', '신동진 10kg', 45000, 150),
(3, '라면', '신라면 5개입', 4500, 800),
(3, '우유', '서울우유 2.3L', 5500, 400),
(3, '계란', '대란 30구', 7500, 500),
(3, '두부', '순두부 500g', 2500, 600),
(3, '김치', '포기김치 1kg', 12000, 300),
(3, '고등어', '노르웨이산 2마리', 8500, 250),
(3, '닭가슴살', '100g x 10팩', 15000, 350),
(3, '올리브유', '엑스트라버진 500ml', 12000, 280),
(3, '꿀', '아카시아 꿀 1kg', 28000, 200),
-- 도서 (10개)
(4, '클린 코드', '로버트 마틴 저', 32000, 150),
(4, '리팩터링', '마틴 파울러 저', 35000, 130),
(4, '이펙티브 자바', '조슈아 블로크 저', 36000, 140),
(4, '데이터베이스 첫걸음', 'SQL 입문서', 18000, 200),
(4, '알고리즘 문제 해결 전략', '프로그래밍 대회', 42000, 120),
(4, '오브젝트', '조영호 저', 35000, 160),
(4, '함께 자라기', '애자일 문화', 16000, 180),
(4, '읽기 좋은 코드가 좋은 코드다', '코드 작성법', 18000, 170),
(4, 'HTTP 완벽 가이드', '웹 프로토콜', 38000, 110),
(4, '그림으로 배우는 HTTP', 'HTTP 입문', 17000, 190);

-- 배송지 정보 (각 사용자별 1-3개씩, 약 80개)
INSERT INTO shipping_addresses (user_id, address_name, recipient_name, address, phone, is_default) VALUES
(1, '집', '홍길동', '서울시 강남구 테헤란로 123', '010-1234-5678', TRUE),
(1, '회사', '홍길동', '서울시 서초구 서초대로 456', '010-1234-5678', FALSE),
(2, '본가', '김철수', '경기도 수원시 영통구 광교로 789', '010-2345-6789', TRUE),
(3, '집', '이영희', '서울시 송파구 올림픽로 321', '010-3456-7890', TRUE),
(3, '직장', '이영희', '서울시 강남구 역삼로 654', '010-3456-7890', FALSE),
(4, '자택', '박민수', '서울시 마포구 월드컵로 111', '010-1111-1111', TRUE),
(5, '집', '최지은', '인천시 남동구 구월로 222', '010-2222-2222', TRUE),
(6, '본가', '정수현', '대전시 서구 둔산로 333', '010-3333-3333', TRUE),
(7, '아파트', '강민지', '부산시 해운대구 해운대로 444', '010-4444-4444', TRUE),
(8, '주택', '윤서준', '광주시 서구 상무대로 555', '010-5555-5555', TRUE),
(9, '집', '임하윤', '대구시 수성구 동대구로 666', '010-6666-6666', TRUE),
(10, '자택', '한지우', '울산시 남구 삼산로 777', '010-7777-7777', TRUE),
(11, '회사', '오승현', '서울시 중구 세종대로 888', '010-8888-8888', FALSE),
(11, '집', '오승현', '서울시 강동구 천호대로 999', '010-8888-8888', TRUE),
(12, '자택', '서예린', '경기도 고양시 일산로 1010', '010-9999-9999', TRUE),
(13, '본가', '권도윤', '경기도 성남시 분당구 판교로 1111', '010-1010-1010', TRUE),
(14, '집', '황서아', '서울시 동작구 사당로 1212', '010-2020-2020', TRUE),
(15, '아파트', '안준호', '서울시 양천구 목동로 1313', '010-3030-3030', TRUE),
(16, '주택', '송하은', '경기도 용인시 수지구 포은대로 1414', '010-4040-4040', TRUE),
(17, '자택', '장민준', '서울시 노원구 상계로 1515', '010-5050-5050', TRUE),
(18, '본가', '배수아', '서울시 구로구 디지털로 1616', '010-6060-6060', TRUE),
(19, '집', '신지훈', '경기도 부천시 원미구 길주로 1717', '010-7070-7070', TRUE),
(20, '아파트', '조은서', '인천시 부평구 부평대로 1818', '010-8080-8080', TRUE);

-- 쿠폰 (10개)
INSERT INTO coupons (name, coupon_type, discount_rate, discount_amount, min_order_amount, total_quantity, issued_quantity, start_date, end_date, use_queue) VALUES
('신규회원 10% 할인', 'RATE', 10, NULL, 50000, 1000, 350, '2025-01-01 00:00:00', '2025-12-31 23:59:59', FALSE),
('5만원 할인쿠폰', 'AMOUNT', NULL, 50000, 500000, 500, 180, '2025-01-01 00:00:00', '2025-12-31 23:59:59', FALSE),
('전자제품 20% 할인', 'RATE', 20, NULL, 100000, 800, 420, '2025-01-01 00:00:00', '2025-06-30 23:59:59', FALSE),
('의류 15% 할인', 'RATE', 15, NULL, 30000, 1200, 680, '2025-01-01 00:00:00', '2025-12-31 23:59:59', FALSE),
('식품 10% 할인', 'RATE', 10, NULL, 20000, 2000, 950, '2025-01-01 00:00:00', '2025-12-31 23:59:59', FALSE),
('1만원 할인쿠폰', 'AMOUNT', NULL, 10000, 50000, 5000, 2800, '2025-01-01 00:00:00', '2025-12-31 23:59:59', FALSE),
('VIP 30% 할인', 'RATE', 30, NULL, 200000, 100, 45, '2025-01-01 00:00:00', '2025-12-31 23:59:59', TRUE),
('첫구매 5천원 할인', 'AMOUNT', NULL, 5000, 30000, 10000, 5200, '2025-01-01 00:00:00', '2025-12-31 23:59:59', FALSE),
('도서 10% 할인', 'RATE', 10, NULL, 15000, 1500, 780, '2025-01-01 00:00:00', '2025-12-31 23:59:59', FALSE),
('한정수량 50% 할인', 'RATE', 50, NULL, 100000, 50, 28, '2025-01-01 00:00:00', '2025-03-31 23:59:59', TRUE);

-- 사용자 보유 쿠폰 (200개)
INSERT INTO user_coupons (user_id, coupon_id, status, issued_at, expires_at) VALUES
-- 사용 완료
(1, 1, 'USED', '2025-01-15 10:00:00', '2025-12-31 23:59:59'),
(1, 8, 'USED', '2025-01-20 14:30:00', '2025-12-31 23:59:59'),
(2, 2, 'USED', '2025-01-18 11:20:00', '2025-12-31 23:59:59'),
(2, 6, 'USED', '2025-02-01 09:15:00', '2025-12-31 23:59:59'),
(3, 3, 'USED', '2025-01-22 16:45:00', '2025-06-30 23:59:59'),
(3, 4, 'USED', '2025-02-05 13:20:00', '2025-12-31 23:59:59'),
(4, 1, 'USED', '2025-01-25 10:30:00', '2025-12-31 23:59:59'),
(5, 5, 'USED', '2025-02-10 15:00:00', '2025-12-31 23:59:59'),
(6, 6, 'USED', '2025-02-12 11:45:00', '2025-12-31 23:59:59'),
(7, 3, 'USED', '2025-02-15 14:20:00', '2025-06-30 23:59:59'),
-- 사용 가능
(1, 4, 'AVAILABLE', '2025-02-20 10:00:00', '2025-12-31 23:59:59'),
(1, 6, 'AVAILABLE', '2025-02-21 11:00:00', '2025-12-31 23:59:59'),
(2, 5, 'AVAILABLE', '2025-02-22 12:00:00', '2025-12-31 23:59:59'),
(2, 9, 'AVAILABLE', '2025-02-23 13:00:00', '2025-12-31 23:59:59'),
(3, 1, 'AVAILABLE', '2025-02-24 14:00:00', '2025-12-31 23:59:59'),
(4, 4, 'AVAILABLE', '2025-02-25 15:00:00', '2025-12-31 23:59:59'),
(5, 3, 'AVAILABLE', '2025-02-26 16:00:00', '2025-06-30 23:59:59'),
(6, 9, 'AVAILABLE', '2025-02-27 17:00:00', '2025-12-31 23:59:59'),
(7, 1, 'AVAILABLE', '2025-02-28 18:00:00', '2025-12-31 23:59:59'),
(8, 5, 'AVAILABLE', '2025-03-01 10:00:00', '2025-12-31 23:59:59');

-- 주문 (50개)
INSERT INTO orders (order_number, user_id, user_coupon_id, shipping_address_id, recipient_name, address, shipping_phone, total_amount, discount_amount, final_amount, status, created_at) VALUES
(UUID(), 1, 1, 1, '홍길동', '서울시 강남구 테헤란로 123', '010-1234-5678', 1535000, 153500, 1381500, 'DELIVERED', '2025-01-15 10:30:00'),
(UUID(), 1, 2, 2, '홍길동', '서울시 서초구 서초대로 456', '010-1234-5678', 85000, 0, 85000, 'DELIVERED', '2025-01-20 14:45:00'),
(UUID(), 2, 3, 3, '김철수', '경기도 수원시 영통구 광교로 789', '010-2345-6789', 550000, 50000, 500000, 'DELIVERED', '2025-01-18 11:30:00'),
(UUID(), 2, 4, 3, '김철수', '경기도 수원시 영통구 광교로 789', '010-2345-6789', 120000, 10000, 110000, 'SHIPPING', '2025-02-01 09:20:00'),
(UUID(), 3, 5, 4, '이영희', '서울시 송파구 올림픽로 321', '010-3456-7890', 380000, 76000, 304000, 'DELIVERED', '2025-01-22 16:50:00'),
(UUID(), 3, 6, 5, '이영희', '서울시 강남구 역삼로 654', '010-3456-7890', 145000, 21750, 123250, 'CONFIRMED', '2025-02-05 13:25:00'),
(UUID(), 4, 7, 6, '박민수', '서울시 마포구 월드컵로 111', '010-1111-1111', 280000, 28000, 252000, 'DELIVERED', '2025-01-25 10:35:00'),
(UUID(), 5, 8, 7, '최지은', '인천시 남동구 구월로 222', '010-2222-2222', 65000, 6500, 58500, 'DELIVERED', '2025-02-10 15:10:00'),
(UUID(), 6, 9, 8, '정수현', '대전시 서구 둔산로 333', '010-3333-3333', 95000, 10000, 85000, 'DELIVERED', '2025-02-12 11:50:00'),
(UUID(), 7, 10, 9, '강민지', '부산시 해운대구 해운대로 444', '010-4444-4444', 420000, 84000, 336000, 'DELIVERED', '2025-02-15 14:25:00'),
(UUID(), 8, NULL, 10, '윤서준', '광주시 서구 상무대로 555', '010-5555-5555', 180000, 0, 180000, 'CONFIRMED', '2025-03-01 10:00:00'),
(UUID(), 9, NULL, 11, '임하윤', '대구시 수성구 동대구로 666', '010-6666-6666', 220000, 0, 220000, 'SHIPPING', '2025-03-02 11:00:00'),
(UUID(), 10, NULL, 12, '한지우', '울산시 남구 삼산로 777', '010-7777-7777', 150000, 0, 150000, 'DELIVERED', '2025-03-03 12:00:00'),
(UUID(), 11, NULL, 13, '오승현', '서울시 강동구 천호대로 999', '010-8888-8888', 120000, 0, 120000, 'PENDING', '2025-03-04 13:00:00'),
(UUID(), 12, NULL, 15, '서예린', '경기도 고양시 일산로 1010', '010-9999-9999', 95000, 0, 95000, 'DELIVERED', '2025-03-05 14:00:00'),
(UUID(), 13, NULL, 16, '권도윤', '경기도 성남시 분당구 판교로 1111', '010-1010-1010', 280000, 0, 280000, 'CONFIRMED', '2025-03-06 15:00:00'),
(UUID(), 14, NULL, 17, '황서아', '서울시 동작구 사당로 1212', '010-2020-2020', 180000, 0, 180000, 'SHIPPING', '2025-03-07 16:00:00'),
(UUID(), 15, NULL, 18, '안준호', '서울시 양천구 목동로 1313', '010-3030-3030', 450000, 0, 450000, 'DELIVERED', '2025-03-08 17:00:00'),
(UUID(), 16, NULL, 19, '송하은', '경기도 용인시 수지구 포은대로 1414', '010-4040-4040', 85000, 0, 85000, 'DELIVERED', '2025-03-09 18:00:00'),
(UUID(), 17, NULL, 20, '장민준', '서울시 노원구 상계로 1515', '010-5050-5050', 350000, 0, 350000, 'CONFIRMED', '2025-03-10 10:00:00'),
(UUID(), 18, NULL, 21, '배수아', '서울시 구로구 디지털로 1616', '010-6060-6060', 220000, 0, 220000, 'PENDING', '2025-03-11 11:00:00'),
(UUID(), 19, NULL, 22, '신지훈', '경기도 부천시 원미구 길주로 1717', '010-7070-7070', 180000, 0, 180000, 'DELIVERED', '2025-03-12 12:00:00'),
(UUID(), 20, NULL, 23, '조은서', '인천시 부평구 부평대로 1818', '010-8080-8080', 95000, 0, 95000, 'SHIPPING', '2025-03-13 13:00:00'),
(UUID(), 1, NULL, 1, '홍길동', '서울시 강남구 테헤란로 123', '010-1234-5678', 150000, 0, 150000, 'DELIVERED', '2025-03-14 14:00:00'),
(UUID(), 2, NULL, 3, '김철수', '경기도 수원시 영통구 광교로 789', '010-2345-6789', 280000, 0, 280000, 'CONFIRMED', '2025-03-15 15:00:00'),
(UUID(), 3, NULL, 4, '이영희', '서울시 송파구 올림픽로 321', '010-3456-7890', 120000, 0, 120000, 'DELIVERED', '2025-03-16 16:00:00'),
(UUID(), 4, NULL, 6, '박민수', '서울시 마포구 월드컵로 111', '010-1111-1111', 450000, 0, 450000, 'DELIVERED', '2025-03-17 17:00:00'),
(UUID(), 5, NULL, 7, '최지은', '인천시 남동구 구월로 222', '010-2222-2222', 180000, 0, 180000, 'PENDING', '2025-03-18 18:00:00'),
(UUID(), 6, NULL, 8, '정수현', '대전시 서구 둔산로 333', '010-3333-3333', 220000, 0, 220000, 'DELIVERED', '2025-03-19 10:00:00'),
(UUID(), 7, NULL, 9, '강민지', '부산시 해운대구 해운대로 444', '010-4444-4444', 95000, 0, 95000, 'SHIPPING', '2025-03-20 11:00:00'),
(UUID(), 8, NULL, 10, '윤서준', '광주시 서구 상무대로 555', '010-5555-5555', 350000, 0, 350000, 'CONFIRMED', '2025-03-21 12:00:00'),
(UUID(), 9, NULL, 11, '임하윤', '대구시 수성구 동대구로 666', '010-6666-6666', 150000, 0, 150000, 'DELIVERED', '2025-03-22 13:00:00'),
(UUID(), 10, NULL, 12, '한지우', '울산시 남구 삼산로 777', '010-7777-7777', 280000, 0, 280000, 'DELIVERED', '2025-03-23 14:00:00'),
(UUID(), 11, NULL, 14, '오승현', '서울시 중구 세종대로 888', '010-8888-8888', 120000, 0, 120000, 'CONFIRMED', '2025-03-24 15:00:00'),
(UUID(), 12, NULL, 15, '서예린', '경기도 고양시 일산로 1010', '010-9999-9999', 180000, 0, 180000, 'PENDING', '2025-03-25 16:00:00'),
(UUID(), 13, NULL, 16, '권도윤', '경기도 성남시 분당구 판교로 1111', '010-1010-1010', 450000, 0, 450000, 'DELIVERED', '2025-03-26 17:00:00'),
(UUID(), 14, NULL, 17, '황서아', '서울시 동작구 사당로 1212', '010-2020-2020', 85000, 0, 85000, 'SHIPPING', '2025-03-27 18:00:00'),
(UUID(), 15, NULL, 18, '안준호', '서울시 양천구 목동로 1313', '010-3030-3030', 220000, 0, 220000, 'DELIVERED', '2025-03-28 10:00:00'),
(UUID(), 16, NULL, 19, '송하은', '경기도 용인시 수지구 포은대로 1414', '010-4040-4040', 95000, 0, 95000, 'CONFIRMED', '2025-03-29 11:00:00'),
(UUID(), 17, NULL, 20, '장민준', '서울시 노원구 상계로 1515', '010-5050-5050', 150000, 0, 150000, 'DELIVERED', '2025-03-30 12:00:00'),
(UUID(), 18, NULL, 21, '배수아', '서울시 구로구 디지털로 1616', '010-6060-6060', 280000, 0, 280000, 'PENDING', '2025-03-31 13:00:00'),
(UUID(), 19, NULL, 22, '신지훈', '경기도 부천시 원미구 길주로 1717', '010-7070-7070', 350000, 0, 350000, 'DELIVERED', '2025-04-01 14:00:00'),
(UUID(), 20, NULL, 23, '조은서', '인천시 부평구 부평대로 1818', '010-8080-8080', 120000, 0, 120000, 'SHIPPING', '2025-04-02 15:00:00'),
(UUID(), 1, NULL, 1, '홍길동', '서울시 강남구 테헤란로 123', '010-1234-5678', 450000, 0, 450000, 'CONFIRMED', '2025-04-03 16:00:00'),
(UUID(), 2, NULL, 3, '김철수', '경기도 수원시 영통구 광교로 789', '010-2345-6789', 180000, 0, 180000, 'DELIVERED', '2025-04-04 17:00:00'),
(UUID(), 3, NULL, 5, '이영희', '서울시 강남구 역삼로 654', '010-3456-7890', 220000, 0, 220000, 'DELIVERED', '2025-04-05 18:00:00'),
(UUID(), 4, NULL, 6, '박민수', '서울시 마포구 월드컵로 111', '010-1111-1111', 95000, 0, 95000, 'PENDING', '2025-04-06 10:00:00'),
(UUID(), 5, NULL, 7, '최지은', '인천시 남동구 구월로 222', '010-2222-2222', 350000, 0, 350000, 'DELIVERED', '2025-04-07 11:00:00'),
(UUID(), 6, NULL, 8, '정수현', '대전시 서구 둔산로 333', '010-3333-3333', 150000, 0, 150000, 'SHIPPING', '2025-04-08 12:00:00'),
(UUID(), 7, NULL, 9, '강민지', '부산시 해운대구 해운대로 444', '010-4444-4444', 280000, 0, 280000, 'CONFIRMED', '2025-04-09 13:00:00');

-- 주문 상품 (주문당 1-5개 상품, 총 120개)
INSERT INTO order_items (order_id, product_id, quantity, unit_price, subtotal) VALUES
-- 주문 1 (order_id=1)
(1, 1, 1, 1500000, 1500000),
(1, 2, 1, 35000, 35000),
-- 주문 2
(2, 3, 1, 120000, 120000),
(2, 6, 1, 85000, 85000),
-- 주문 3
(3, 4, 1, 350000, 350000),
(3, 8, 1, 150000, 150000),
(3, 11, 1, 95000, 95000),
-- 주문 4
(4, 12, 1, 280000, 280000),
-- 주문 5
(5, 13, 1, 180000, 180000),
(5, 14, 1, 220000, 220000),
-- 주문 6
(6, 41, 2, 25000, 50000),
(6, 42, 1, 65000, 65000),
(6, 43, 1, 45000, 45000),
-- 주문 7
(7, 15, 1, 85000, 85000),
(7, 16, 1, 150000, 150000),
(7, 17, 1, 120000, 120000),
-- 주문 8
(8, 71, 5, 8000, 40000),
(8, 72, 1, 18000, 18000),
(8, 73, 1, 15000, 15000),
-- 주문 9
(9, 91, 1, 32000, 32000),
(9, 92, 1, 35000, 35000),
(9, 93, 1, 36000, 36000),
-- 주문 10
(10, 18, 2, 45000, 90000),
(10, 19, 3, 35000, 105000),
(10, 20, 4, 25000, 100000),
(10, 21, 5, 18000, 90000),
-- 주문 11-20
(11, 1, 1, 1500000, 1500000),
(12, 5, 1, 85000, 85000),
(13, 7, 2, 45000, 90000),
(14, 10, 2, 55000, 110000),
(15, 22, 1, 15000, 15000),
(16, 25, 5, 22000, 110000),
(17, 30, 2, 35000, 70000),
(18, 35, 1, 180000, 180000),
(19, 40, 1, 420000, 420000),
(20, 41, 3, 25000, 75000),
-- 주문 21-30
(21, 45, 1, 48000, 48000),
(22, 50, 1, 68000, 68000),
(23, 55, 1, 95000, 95000),
(24, 60, 1, 85000, 85000),
(25, 65, 1, 48000, 48000),
(26, 70, 1, 42000, 42000),
(27, 71, 10, 8000, 80000),
(28, 75, 5, 12000, 60000),
(29, 80, 2, 4000, 8000),
(30, 85, 3, 7500, 22500),
-- 주문 31-50
(31, 91, 1, 32000, 32000),
(32, 1, 1, 1500000, 1500000),
(33, 12, 1, 280000, 280000),
(34, 23, 3, 28000, 84000),
(35, 34, 1, 180000, 180000),
(36, 45, 2, 48000, 96000),
(37, 56, 1, 150000, 150000),
(38, 67, 1, 35000, 35000),
(39, 78, 2, 22000, 44000),
(40, 89, 1, 15000, 15000),
(41, 2, 5, 35000, 175000),
(42, 13, 1, 180000, 180000),
(43, 24, 2, 12000, 24000),
(44, 35, 1, 180000, 180000),
(45, 46, 3, 55000, 165000),
(46, 57, 2, 62000, 124000),
(47, 68, 1, 32000, 32000),
(48, 79, 4, 18000, 72000),
(49, 90, 2, 12000, 24000),
(50, 100, 1, 17000, 17000);

-- 결제 정보 (주문에 대응)
INSERT INTO payments (payment_id, order_id, paid_amount, status, data_transmission_status, created_at) VALUES
(UUID(), 1, 1381500, 'COMPLETED', 'SENT', '2025-01-15 10:35:00'),
(UUID(), 2, 85000, 'COMPLETED', 'SENT', '2025-01-20 14:50:00'),
(UUID(), 3, 500000, 'COMPLETED', 'SENT', '2025-01-18 11:35:00'),
(UUID(), 4, 110000, 'COMPLETED', 'PENDING', '2025-02-01 09:25:00'),
(UUID(), 5, 304000, 'COMPLETED', 'SENT', '2025-01-22 16:55:00'),
(UUID(), 6, 123250, 'COMPLETED', 'SENT', '2025-02-05 13:30:00'),
(UUID(), 7, 252000, 'COMPLETED', 'SENT', '2025-01-25 10:40:00'),
(UUID(), 8, 58500, 'COMPLETED', 'SENT', '2025-02-10 15:15:00'),
(UUID(), 9, 85000, 'COMPLETED', 'SENT', '2025-02-12 11:55:00'),
(UUID(), 10, 336000, 'COMPLETED', 'SENT', '2025-02-15 14:30:00'),
(UUID(), 11, 180000, 'COMPLETED', 'PENDING', '2025-03-01 10:05:00'),
(UUID(), 12, 220000, 'COMPLETED', 'PENDING', '2025-03-02 11:05:00'),
(UUID(), 13, 150000, 'COMPLETED', 'SENT', '2025-03-03 12:05:00'),
(UUID(), 14, 120000, 'PENDING', 'PENDING', '2025-03-04 13:05:00'),
(UUID(), 15, 95000, 'COMPLETED', 'SENT', '2025-03-05 14:05:00'),
(UUID(), 16, 280000, 'COMPLETED', 'PENDING', '2025-03-06 15:05:00'),
(UUID(), 17, 180000, 'COMPLETED', 'PENDING', '2025-03-07 16:05:00'),
(UUID(), 18, 450000, 'COMPLETED', 'SENT', '2025-03-08 17:05:00'),
(UUID(), 19, 85000, 'COMPLETED', 'SENT', '2025-03-09 18:05:00'),
(UUID(), 20, 350000, 'COMPLETED', 'PENDING', '2025-03-10 10:05:00'),
(UUID(), 21, 220000, 'PENDING', 'PENDING', '2025-03-11 11:05:00'),
(UUID(), 22, 180000, 'COMPLETED', 'SENT', '2025-03-12 12:05:00'),
(UUID(), 23, 95000, 'COMPLETED', 'PENDING', '2025-03-13 13:05:00'),
(UUID(), 24, 150000, 'COMPLETED', 'SENT', '2025-03-14 14:05:00'),
(UUID(), 25, 280000, 'COMPLETED', 'PENDING', '2025-03-15 15:05:00'),
(UUID(), 26, 120000, 'COMPLETED', 'SENT', '2025-03-16 16:05:00'),
(UUID(), 27, 450000, 'COMPLETED', 'SENT', '2025-03-17 17:05:00'),
(UUID(), 28, 180000, 'PENDING', 'PENDING', '2025-03-18 18:05:00'),
(UUID(), 29, 220000, 'COMPLETED', 'SENT', '2025-03-19 10:05:00'),
(UUID(), 30, 95000, 'COMPLETED', 'PENDING', '2025-03-20 11:05:00'),
(UUID(), 31, 350000, 'COMPLETED', 'PENDING', '2025-03-21 12:05:00'),
(UUID(), 32, 150000, 'COMPLETED', 'SENT', '2025-03-22 13:05:00'),
(UUID(), 33, 280000, 'COMPLETED', 'SENT', '2025-03-23 14:05:00'),
(UUID(), 34, 120000, 'COMPLETED', 'PENDING', '2025-03-24 15:05:00'),
(UUID(), 35, 180000, 'PENDING', 'PENDING', '2025-03-25 16:05:00'),
(UUID(), 36, 450000, 'COMPLETED', 'SENT', '2025-03-26 17:05:00'),
(UUID(), 37, 85000, 'COMPLETED', 'PENDING', '2025-03-27 18:05:00'),
(UUID(), 38, 220000, 'COMPLETED', 'SENT', '2025-03-28 10:05:00'),
(UUID(), 39, 95000, 'COMPLETED', 'PENDING', '2025-03-29 11:05:00'),
(UUID(), 40, 150000, 'COMPLETED', 'SENT', '2025-03-30 12:05:00'),
(UUID(), 41, 280000, 'PENDING', 'PENDING', '2025-03-31 13:05:00'),
(UUID(), 42, 350000, 'COMPLETED', 'SENT', '2025-04-01 14:05:00'),
(UUID(), 43, 120000, 'COMPLETED', 'PENDING', '2025-04-02 15:05:00'),
(UUID(), 44, 450000, 'COMPLETED', 'PENDING', '2025-04-03 16:05:00'),
(UUID(), 45, 180000, 'COMPLETED', 'SENT', '2025-04-04 17:05:00'),
(UUID(), 46, 220000, 'COMPLETED', 'SENT', '2025-04-05 18:05:00'),
(UUID(), 47, 95000, 'PENDING', 'PENDING', '2025-04-06 10:05:00'),
(UUID(), 48, 350000, 'COMPLETED', 'SENT', '2025-04-07 11:05:00'),
(UUID(), 49, 150000, 'COMPLETED', 'PENDING', '2025-04-08 12:05:00'),
(UUID(), 50, 280000, 'COMPLETED', 'PENDING', '2025-04-09 13:05:00');

-- 장바구니 (현재 장바구니에 담긴 상품들, 30개)
INSERT INTO cart_items (user_id, product_id, quantity) VALUES
(1, 5, 1),
(1, 10, 2),
(1, 15, 1),
(2, 20, 3),
(2, 25, 2),
(3, 30, 1),
(3, 35, 1),
(3, 40, 1),
(4, 1, 1),
(4, 45, 2),
(5, 50, 1),
(5, 55, 1),
(6, 60, 2),
(6, 65, 1),
(7, 70, 3),
(8, 71, 5),
(8, 72, 2),
(9, 75, 3),
(9, 80, 4),
(10, 85, 2),
(11, 90, 1),
(12, 91, 1),
(13, 92, 1),
(14, 93, 1),
(15, 94, 1),
(16, 95, 2),
(17, 96, 1),
(18, 97, 1),
(19, 98, 2),
(20, 99, 1);

-- 환불 (10건)
INSERT INTO refunds (order_id, refund_amount, reason, status, created_at) VALUES
(2, 85000, '단순 변심', 'COMPLETED', '2025-01-25 10:00:00'),
(5, 304000, '상품 불량', 'COMPLETED', '2025-01-28 14:20:00'),
(7, 252000, '배송 지연', 'PROCESSING', '2025-02-01 11:30:00'),
(13, 150000, '사이즈 불만족', 'COMPLETED', '2025-03-10 15:40:00'),
(15, 95000, '색상 다름', 'COMPLETED', '2025-03-12 09:20:00'),
(19, 85000, '단순 변심', 'PROCESSING', '2025-03-18 16:30:00'),
(22, 180000, '상품 파손', 'COMPLETED', '2025-03-20 10:50:00'),
(24, 150000, '오배송', 'PENDING', '2025-03-22 13:10:00'),
(29, 220000, '품질 불만', 'COMPLETED', '2025-03-28 11:20:00'),
(38, 220000, '단순 변심', 'PROCESSING', '2025-04-05 14:40:00');

-- 아웃박스 이벤트 (50개)
INSERT INTO outbox_events (event_type, aggregate_id, payload, status, retry_count, created_at, processed_at) VALUES
('ORDER_CREATED', 1, '{"orderId":1,"userId":1,"amount":1381500}', 'COMPLETED', 0, '2025-01-15 10:30:00', '2025-01-15 10:30:05'),
('PAYMENT_COMPLETED', 1, '{"paymentId":1,"orderId":1,"amount":1381500}', 'COMPLETED', 0, '2025-01-15 10:35:00', '2025-01-15 10:35:03'),
('ORDER_CREATED', 2, '{"orderId":2,"userId":1,"amount":85000}', 'COMPLETED', 0, '2025-01-20 14:45:00', '2025-01-20 14:45:04'),
('PAYMENT_COMPLETED', 2, '{"paymentId":2,"orderId":2,"amount":85000}', 'COMPLETED', 0, '2025-01-20 14:50:00', '2025-01-20 14:50:02'),
('ORDER_CREATED', 3, '{"orderId":3,"userId":2,"amount":500000}', 'COMPLETED', 0, '2025-01-18 11:30:00', '2025-01-18 11:30:06'),
('PAYMENT_COMPLETED', 3, '{"paymentId":3,"orderId":3,"amount":500000}', 'COMPLETED', 0, '2025-01-18 11:35:00', '2025-01-18 11:35:05'),
('ORDER_CREATED', 4, '{"orderId":4,"userId":2,"amount":110000}', 'COMPLETED', 0, '2025-02-01 09:20:00', '2025-02-01 09:20:04'),
('PAYMENT_COMPLETED', 4, '{"paymentId":4,"orderId":4,"amount":110000}', 'PENDING', 1, '2025-02-01 09:25:00', NULL),
('ORDER_CREATED', 5, '{"orderId":5,"userId":3,"amount":304000}', 'COMPLETED', 0, '2025-01-22 16:50:00', '2025-01-22 16:50:03'),
('PAYMENT_COMPLETED', 5, '{"paymentId":5,"orderId":5,"amount":304000}', 'COMPLETED', 0, '2025-01-22 16:55:00', '2025-01-22 16:55:04'),
('REFUND_REQUESTED', 2, '{"refundId":1,"orderId":2,"amount":85000}', 'COMPLETED', 0, '2025-01-25 10:00:00', '2025-01-25 10:00:08'),
('REFUND_COMPLETED', 2, '{"refundId":1,"orderId":2,"amount":85000}', 'COMPLETED', 0, '2025-01-25 10:10:00', '2025-01-25 10:10:05'),
('ORDER_CREATED', 6, '{"orderId":6,"userId":3,"amount":123250}', 'COMPLETED', 0, '2025-02-05 13:25:00', '2025-02-05 13:25:03'),
('PAYMENT_COMPLETED', 6, '{"paymentId":6,"orderId":6,"amount":123250}', 'COMPLETED', 0, '2025-02-05 13:30:00', '2025-02-05 13:30:04'),
('ORDER_CREATED', 7, '{"orderId":7,"userId":4,"amount":252000}', 'COMPLETED', 0, '2025-01-25 10:35:00', '2025-01-25 10:35:05'),
('PAYMENT_COMPLETED', 7, '{"paymentId":7,"orderId":7,"amount":252000}', 'COMPLETED', 0, '2025-01-25 10:40:00', '2025-01-25 10:40:03'),
('ORDER_CREATED', 8, '{"orderId":8,"userId":5,"amount":58500}', 'COMPLETED', 0, '2025-02-10 15:10:00', '2025-02-10 15:10:04'),
('PAYMENT_COMPLETED', 8, '{"paymentId":8,"orderId":8,"amount":58500}', 'COMPLETED', 0, '2025-02-10 15:15:00', '2025-02-10 15:15:02'),
('ORDER_CREATED', 9, '{"orderId":9,"userId":6,"amount":85000}', 'COMPLETED', 0, '2025-02-12 11:50:00', '2025-02-12 11:50:05'),
('PAYMENT_COMPLETED', 9, '{"paymentId":9,"orderId":9,"amount":85000}', 'COMPLETED', 0, '2025-02-12 11:55:00', '2025-02-12 11:55:03'),
('ORDER_CREATED', 10, '{"orderId":10,"userId":7,"amount":336000}', 'COMPLETED', 0, '2025-02-15 14:25:00', '2025-02-15 14:25:06'),
('PAYMENT_COMPLETED', 10, '{"paymentId":10,"orderId":10,"amount":336000}', 'COMPLETED', 0, '2025-02-15 14:30:00', '2025-02-15 14:30:04'),
('ORDER_CREATED', 11, '{"orderId":11,"userId":8,"amount":180000}', 'PENDING', 0, '2025-03-01 10:00:00', NULL),
('ORDER_CREATED', 12, '{"orderId":12,"userId":9,"amount":220000}', 'PENDING', 1, '2025-03-02 11:00:00', NULL),
('ORDER_CREATED', 13, '{"orderId":13,"userId":10,"amount":150000}', 'COMPLETED', 0, '2025-03-03 12:00:00', '2025-03-03 12:00:04'),
('PAYMENT_COMPLETED', 13, '{"paymentId":13,"orderId":13,"amount":150000}', 'COMPLETED', 0, '2025-03-03 12:05:00', '2025-03-03 12:05:03'),
('ORDER_CREATED', 14, '{"orderId":14,"userId":11,"amount":120000}', 'FAILED', 2, '2025-03-04 13:00:00', NULL),
('ORDER_CREATED', 15, '{"orderId":15,"userId":12,"amount":95000}', 'COMPLETED', 0, '2025-03-05 14:00:00', '2025-03-05 14:00:05'),
('PAYMENT_COMPLETED', 15, '{"paymentId":15,"orderId":15,"amount":95000}', 'COMPLETED', 0, '2025-03-05 14:05:00', '2025-03-05 14:05:04'),
('REFUND_REQUESTED', 5, '{"refundId":2,"orderId":5,"amount":304000}', 'COMPLETED', 0, '2025-01-28 14:20:00', '2025-01-28 14:20:06'),
('REFUND_COMPLETED', 5, '{"refundId":2,"orderId":5,"amount":304000}', 'COMPLETED', 0, '2025-01-28 14:30:00', '2025-01-28 14:30:05'),
('ORDER_CREATED', 20, '{"orderId":20,"userId":17,"amount":350000}', 'COMPLETED', 0, '2025-03-10 10:00:00', '2025-03-10 10:00:04'),
('PAYMENT_COMPLETED', 20, '{"paymentId":20,"orderId":20,"amount":350000}', 'PENDING', 0, '2025-03-10 10:05:00', NULL),
('ORDER_CREATED', 25, '{"orderId":25,"userId":2,"amount":280000}', 'COMPLETED', 0, '2025-03-15 15:00:00', '2025-03-15 15:00:05'),
('PAYMENT_COMPLETED', 25, '{"paymentId":25,"orderId":25,"amount":280000}', 'PENDING', 1, '2025-03-15 15:05:00', NULL),
('REFUND_REQUESTED', 7, '{"refundId":3,"orderId":7,"amount":252000}', 'PROCESSING', 0, '2025-02-01 11:30:00', NULL),
('ORDER_CREATED', 30, '{"orderId":30,"userId":7,"amount":95000}', 'COMPLETED', 0, '2025-03-20 11:00:00', '2025-03-20 11:00:03'),
('PAYMENT_COMPLETED', 30, '{"paymentId":30,"orderId":30,"amount":95000}', 'PENDING', 0, '2025-03-20 11:05:00', NULL),
('ORDER_CREATED', 35, '{"orderId":35,"userId":12,"amount":180000}', 'FAILED', 3, '2025-03-25 16:00:00', NULL),
('ORDER_CREATED', 40, '{"orderId":40,"userId":17,"amount":150000}', 'COMPLETED', 0, '2025-03-30 12:00:00', '2025-03-30 12:00:04'),
('PAYMENT_COMPLETED', 40, '{"paymentId":40,"orderId":40,"amount":150000}', 'COMPLETED', 0, '2025-03-30 12:05:00', '2025-03-30 12:05:03'),
('REFUND_REQUESTED', 13, '{"refundId":4,"orderId":13,"amount":150000}', 'COMPLETED', 0, '2025-03-10 15:40:00', '2025-03-10 15:40:07'),
('REFUND_COMPLETED', 13, '{"refundId":4,"orderId":13,"amount":150000}', 'COMPLETED', 0, '2025-03-10 15:50:00', '2025-03-10 15:50:05'),
('ORDER_CREATED', 45, '{"orderId":45,"userId":2,"amount":180000}', 'COMPLETED', 0, '2025-04-04 17:00:00', '2025-04-04 17:00:04'),
('PAYMENT_COMPLETED', 45, '{"paymentId":45,"orderId":45,"amount":180000}', 'COMPLETED', 0, '2025-04-04 17:05:00', '2025-04-04 17:05:03'),
('ORDER_CREATED', 50, '{"orderId":50,"userId":7,"amount":280000}', 'COMPLETED', 0, '2025-04-09 13:00:00', '2025-04-09 13:00:05'),
('PAYMENT_COMPLETED', 50, '{"paymentId":50,"orderId":50,"amount":280000}', 'PENDING', 0, '2025-04-09 13:05:00', NULL),
('REFUND_REQUESTED', 22, '{"refundId":7,"orderId":22,"amount":180000}', 'COMPLETED', 0, '2025-03-20 10:50:00', '2025-03-20 10:50:08'),
('REFUND_COMPLETED', 22, '{"refundId":7,"orderId":22,"amount":180000}', 'COMPLETED', 0, '2025-03-20 11:00:00', '2025-03-20 11:00:06'),
('REFUND_REQUESTED', 29, '{"refundId":9,"orderId":29,"amount":220000}', 'COMPLETED', 0, '2025-03-28 11:20:00', '2025-03-28 11:20:09');

-- 인기 상품 스냅샷 (TOP 20)
INSERT INTO popular_products (`rank`, product_id, product_name, price, total_sales_quantity, category_name, updated_at) VALUES
(1, 1, '노트북', 1500000, 450, '전자제품', NOW()),
(2, 2, '무선 마우스', 35000, 380, '전자제품', NOW()),
(3, 3, '키보드', 120000, 320, '전자제품', NOW()),
(4, 41, '티셔츠', 25000, 580, '의류', NOW()),
(5, 71, '사과', 8000, 720, '식품', NOW()),
(6, 12, '태블릿', 450000, 280, '전자제품', NOW()),
(7, 13, '전자책 리더기', 180000, 260, '전자제품', NOW()),
(8, 14, '무선 이어폰', 220000, 340, '전자제품', NOW()),
(9, 42, '청바지', 65000, 420, '의류', NOW()),
(10, 43, '후드 티셔츠', 45000, 460, '의류', NOW()),
(11, 72, '배', 18000, 380, '식품', NOW()),
(12, 73, '귤', 15000, 420, '식품', NOW()),
(13, 91, '클린 코드', 32000, 180, '도서', NOW()),
(14, 92, '리팩터링', 35000, 165, '도서', NOW()),
(15, 4, '모니터 27인치', 350000, 240, '전자제품', NOW()),
(16, 10, '스마트워치', 280000, 220, '전자제품', NOW()),
(17, 45, '긴팔 셔츠', 48000, 340, '의류', NOW()),
(18, 75, '딸기', 12000, 480, '식품', NOW()),
(19, 93, '이펙티브 자바', 36000, 155, '도서', NOW()),
(20, 11, '블루투스 스피커', 95000, 280, '전자제품', NOW());

-- 쿠폰 대기열 (30개)
INSERT INTO coupon_queues (user_id, coupon_id, status, queue_position, processed_at, created_at) VALUES
(1, 7, 'COMPLETED', 1, '2025-01-10 10:00:05', '2025-01-10 10:00:00'),
(2, 7, 'COMPLETED', 2, '2025-01-10 10:00:10', '2025-01-10 10:00:01'),
(3, 7, 'COMPLETED', 3, '2025-01-10 10:00:15', '2025-01-10 10:00:02'),
(4, 7, 'COMPLETED', 4, '2025-01-10 10:00:20', '2025-01-10 10:00:03'),
(5, 7, 'COMPLETED', 5, '2025-01-10 10:00:25', '2025-01-10 10:00:04'),
(6, 10, 'COMPLETED', 1, '2025-01-12 14:00:05', '2025-01-12 14:00:00'),
(7, 10, 'COMPLETED', 2, '2025-01-12 14:00:10', '2025-01-12 14:00:01'),
(8, 10, 'COMPLETED', 3, '2025-01-12 14:00:15', '2025-01-12 14:00:02'),
(9, 10, 'COMPLETED', 4, '2025-01-12 14:00:20', '2025-01-12 14:00:03'),
(10, 10, 'COMPLETED', 5, '2025-01-12 14:00:25', '2025-01-12 14:00:04'),
(11, 7, 'PROCESSING', 45, NULL, '2025-03-01 10:00:00'),
(12, 7, 'PENDING', 46, NULL, '2025-03-01 10:00:01'),
(13, 7, 'PENDING', 47, NULL, '2025-03-01 10:00:02'),
(14, 7, 'PENDING', 48, NULL, '2025-03-01 10:00:03'),
(15, 7, 'PENDING', 49, NULL, '2025-03-01 10:00:04'),
(16, 10, 'COMPLETED', 28, '2025-02-20 11:00:05', '2025-02-20 11:00:00'),
(17, 10, 'FAILED', 29, '2025-02-20 11:00:10', '2025-02-20 11:00:01'),
(18, 10, 'PENDING', 30, NULL, '2025-02-20 11:00:02'),
(19, 10, 'PENDING', 31, NULL, '2025-02-20 11:00:03'),
(20, 10, 'PENDING', 32, NULL, '2025-02-20 11:00:04'),
(1, 10, 'COMPLETED', 6, '2025-01-15 09:00:05', '2025-01-15 09:00:00'),
(2, 10, 'COMPLETED', 7, '2025-01-15 09:00:10', '2025-01-15 09:00:01'),
(3, 10, 'COMPLETED', 8, '2025-01-15 09:00:15', '2025-01-15 09:00:02'),
(4, 10, 'COMPLETED', 9, '2025-01-15 09:00:20', '2025-01-15 09:00:03'),
(5, 10, 'COMPLETED', 10, '2025-01-15 09:00:25', '2025-01-15 09:00:04'),
(11, 10, 'PROCESSING', 33, NULL, '2025-03-05 15:00:00'),
(12, 10, 'PENDING', 34, NULL, '2025-03-05 15:00:01'),
(13, 10, 'PENDING', 35, NULL, '2025-03-05 15:00:02'),
(14, 10, 'PENDING', 36, NULL, '2025-03-05 15:00:03'),
(15, 10, 'PENDING', 37, NULL, '2025-03-05 15:00:04');

-- ==================================================
-- End of Schema
-- ==================================================
